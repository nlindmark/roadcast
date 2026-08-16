package se.roadcast.core.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import se.roadcast.R
import se.roadcast.core.model.GeneratedAudioLine
import se.roadcast.core.model.HostId
import se.roadcast.core.model.PodcastSegment
import se.roadcast.core.model.PreviewPlaybackState
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Media3PodcastPreviewPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val speechGenerator: SpeechGenerator,
    private val playbackServiceController: PlaybackServiceController,
) : PodcastPreviewPlayer {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val prepareMutex = Mutex()
    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
    private val _state = MutableStateFlow<PreviewPlaybackState>(PreviewPlaybackState.Idle)
    override val state: StateFlow<PreviewPlaybackState> = _state.asStateFlow()

    private var currentSegment: PodcastSegment? = null
    private var audioLines: List<GeneratedAudioLine> = emptyList()
    private var mode = Mode.SEGMENT
    private var resumeAfterAnswerIndex = 0
    private var lineIndex = 0

    override val currentLineIndex: Int
        get() = lineIndex

    init {
        player.addListener(
            object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    if (mode != Mode.SEGMENT) return
                    publishSegmentState(playing = player.playWhenReady)
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        playbackServiceController.ensureStarted()
                    }
                    if (mode != Mode.SEGMENT) return
                    if (isPlaying) {
                        publishSegmentState(playing = true)
                    } else if (_state.value is PreviewPlaybackState.Playing) {
                        publishSegmentState(playing = false)
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_ENDED -> onPlaybackEnded()
                        Player.STATE_READY -> {
                            if (mode == Mode.SEGMENT && player.playWhenReady) {
                                publishSegmentState(playing = true)
                            }
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    _state.value = PreviewPlaybackState.Error(
                        error.message ?: "Media playback failed.",
                    )
                }
            },
        )
    }

    override suspend fun play(segment: PodcastSegment) {
        _state.value = PreviewPlaybackState.Initializing
        prepareMutex.withLock {
            val synthesized = segment.dialogue.map { line ->
                GeneratedAudioLine(
                    dialogueLineId = line.id,
                    speech = speechGenerator.synthesize(line.text, line.speaker),
                )
            }
            currentSegment = segment
            audioLines = synthesized
            mode = Mode.SEGMENT
            resumeAfterAnswerIndex = 0
            lineIndex = 0
            withContext(Dispatchers.Main) {
                player.stop()
                player.clearMediaItems()
                player.setMediaItems(
                    synthesized.mapIndexed { index, audio ->
                        audio.toMediaItem(segment, index)
                    },
                )
                player.prepare()
                player.seekTo(0, 0L)
                player.play()
            }
            val first = segment.dialogue.firstOrNull()
            if (first != null) {
                _state.value = PreviewPlaybackState.Playing(0, first)
            }
        }
    }

    override fun pause() {
        if (mode == Mode.ANSWER) return
        runOnMainBlocking {
            player.pause()
            publishSegmentState(playing = false)
        }
    }

    override fun resume() {
        if (mode == Mode.ANSWER) return
        if (_state.value !is PreviewPlaybackState.Paused) return
        runOnMainBlocking {
            player.play()
            publishSegmentState(playing = true)
        }
    }

    override fun replay() {
        if (audioLines.isEmpty()) return
        val segment = currentSegment ?: return
        mode = Mode.SEGMENT
        runOnMainBlocking {
            player.setMediaItems(
                audioLines.mapIndexed { index, audio ->
                    audio.toMediaItem(segment, index)
                },
            )
            player.prepare()
            player.seekTo(0, 0L)
            player.play()
            publishSegmentState(playing = true)
        }
    }

    override fun stop() {
        runOnMainBlocking {
            player.stop()
            player.clearMediaItems()
        }
        mode = Mode.SEGMENT
        currentSegment = null
        audioLines = emptyList()
        lineIndex = 0
        resumeAfterAnswerIndex = 0
        _state.value = PreviewPlaybackState.Idle
        playbackServiceController.stop()
    }

    override fun pauseForAsk(): Int {
        val segment = currentSegment ?: return 0
        var resumeIndex = 0
        runOnMainBlocking {
            player.pause()
            publishSegmentState(playing = false)
            resumeIndex = (lineIndex + 1).coerceAtMost(segment.dialogue.size)
        }
        return resumeIndex
    }

    override suspend fun playAnswerThenResume(
        question: String,
        answerText: String,
        speaker: HostId,
        resumeFromLineIndex: Int,
    ) {
        val segment = currentSegment ?: return
        val speech = speechGenerator.synthesize(answerText, speaker)
        mode = Mode.ANSWER
        resumeAfterAnswerIndex = resumeFromLineIndex.coerceIn(0, segment.dialogue.size)
        _state.value = PreviewPlaybackState.Answering(
            question = question,
            answerText = answerText,
            speaker = speaker,
            resumeFromLineIndex = resumeAfterAnswerIndex,
        )
        withContext(Dispatchers.Main) {
            player.stop()
            player.clearMediaItems()
            player.setMediaItem(
                MediaItem.Builder()
                    .setUri(speech.uri)
                    .setMediaId("answer:${segment.id}:${answerText.hashCode()}")
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(segment.title)
                            .setArtist(hostLabel(speaker))
                            .setSubtitle(answerText.take(120))
                            .setAlbumTitle("Roadcast")
                            .setIsPlayable(true)
                            .build(),
                    )
                    .build(),
            )
            player.prepare()
            player.play()
        }
    }

    private fun onPlaybackEnded() {
        when (mode) {
            Mode.ANSWER -> {
                mode = Mode.SEGMENT
                val segment = currentSegment
                if (segment == null) {
                    _state.value = PreviewPlaybackState.Idle
                    return
                }
                if (resumeAfterAnswerIndex > segment.dialogue.lastIndex || audioLines.isEmpty()) {
                    _state.value = PreviewPlaybackState.Completed
                    return
                }
                mainHandler.post {
                    player.setMediaItems(
                        audioLines.mapIndexed { index, audio ->
                            audio.toMediaItem(segment, index)
                        },
                    )
                    player.prepare()
                    player.seekTo(resumeAfterAnswerIndex, 0L)
                    player.play()
                    lineIndex = resumeAfterAnswerIndex
                    val line = segment.dialogue[resumeAfterAnswerIndex]
                    _state.value = PreviewPlaybackState.Playing(resumeAfterAnswerIndex, line)
                }
            }
            Mode.SEGMENT -> {
                _state.value = PreviewPlaybackState.Completed
            }
        }
    }

    private fun publishSegmentState(playing: Boolean) {
        val segment = currentSegment ?: return
        val index = player.currentMediaItemIndex.coerceAtLeast(0)
        val line = segment.dialogue.getOrNull(index) ?: return
        lineIndex = index
        _state.value = if (playing) {
            PreviewPlaybackState.Playing(index, line)
        } else {
            PreviewPlaybackState.Paused(index, line)
        }
    }

    private fun GeneratedAudioLine.toMediaItem(
        segment: PodcastSegment,
        index: Int,
    ): MediaItem {
        val line = segment.dialogue.getOrNull(index)
        return MediaItem.Builder()
            .setUri(speech.uri)
            .setMediaId(dialogueLineId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(segment.title)
                    .setArtist(line?.speaker?.let(::hostLabel) ?: "Roadcast")
                    .setSubtitle(line?.text?.take(120))
                    .setAlbumTitle("Roadcast")
                    .setTrackNumber(index + 1)
                    .setIsPlayable(true)
                    .build(),
            )
            .build()
    }

    private fun hostLabel(host: HostId): String = when (host) {
        HostId.HOST_A -> context.getString(R.string.playback_host_a)
        HostId.HOST_B -> context.getString(R.string.playback_host_b)
    }

    private fun runOnMainBlocking(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
            return
        }
        val done = CountDownLatch(1)
        mainHandler.post {
            try {
                block()
            } finally {
                done.countDown()
            }
        }
        done.await(3, TimeUnit.SECONDS)
    }

    private enum class Mode { SEGMENT, ANSWER }
}
