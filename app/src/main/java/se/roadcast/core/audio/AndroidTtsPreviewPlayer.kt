package se.roadcast.core.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import se.roadcast.core.model.HostId
import se.roadcast.core.model.PodcastSegment
import se.roadcast.core.model.PreviewPlaybackState
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidTtsPreviewPlayer @Inject constructor(
    @ApplicationContext context: Context,
) : PodcastPreviewPlayer {
    private val lock = Any()
    private val ready = CompletableDeferred<Boolean>()
    private val _state = MutableStateFlow<PreviewPlaybackState>(PreviewPlaybackState.Idle)
    override val state: StateFlow<PreviewPlaybackState> = _state.asStateFlow()

    private var currentSegment: PodcastSegment? = null
    private var currentLineIndex = 0
    private val engine = TextToSpeech(context) { status ->
        ready.complete(status == TextToSpeech.SUCCESS)
    }

    init {
        engine.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) = Unit

                override fun onDone(utteranceId: String) {
                    synchronized(lock) {
                        val segment = currentSegment ?: return
                        val nextIndex = currentLineIndex + 1
                        if (nextIndex > segment.lines.lastIndex) {
                            _state.value = PreviewPlaybackState.Completed
                        } else {
                            speakLine(segment, nextIndex)
                        }
                    }
                }

                @Deprecated("Required by older Android TTS implementations")
                override fun onError(utteranceId: String) {
                    reportError()
                }

                override fun onError(utteranceId: String, errorCode: Int) {
                    reportError()
                }
            },
        )
    }

    override suspend fun play(segment: PodcastSegment) {
        _state.value = PreviewPlaybackState.Initializing
        if (!ready.await()) {
            _state.value = PreviewPlaybackState.Error(
                "Text-to-speech is unavailable. Install or enable an Android speech engine.",
            )
            return
        }
        synchronized(lock) {
            currentSegment = segment
            engine.stop()
            speakLine(segment, 0)
        }
    }

    override fun pause() {
        synchronized(lock) {
            val segment = currentSegment ?: return
            val line = segment.lines.getOrNull(currentLineIndex) ?: return
            engine.stop()
            _state.value = PreviewPlaybackState.Paused(currentLineIndex, line)
        }
    }

    override fun resume() {
        synchronized(lock) {
            val segment = currentSegment ?: return
            if (_state.value is PreviewPlaybackState.Paused) {
                speakLine(segment, currentLineIndex)
            }
        }
    }

    override fun replay() {
        synchronized(lock) {
            val segment = currentSegment ?: return
            engine.stop()
            speakLine(segment, 0)
        }
    }

    override fun stop() {
        synchronized(lock) {
            engine.stop()
            currentSegment = null
            currentLineIndex = 0
            _state.value = PreviewPlaybackState.Idle
        }
    }

    private fun speakLine(segment: PodcastSegment, index: Int) {
        val line = segment.lines.getOrNull(index) ?: run {
            _state.value = PreviewPlaybackState.Completed
            return
        }
        currentLineIndex = index
        configureHost(line.speaker)
        _state.value = PreviewPlaybackState.Playing(index, line)
        val result = engine.speak(
            line.text,
            TextToSpeech.QUEUE_FLUSH,
            Bundle(),
            "${segment.id}:$index",
        )
        if (result == TextToSpeech.ERROR) reportError()
    }

    private fun configureHost(host: HostId) {
        engine.language = Locale.UK
        val voices = engine.voices
            ?.filter { !it.isNetworkConnectionRequired && it.locale.language == Locale.ENGLISH.language }
            ?.sortedBy { it.name }
            .orEmpty()
        when (host) {
            HostId.HOST_A -> {
                voices.getOrNull(0)?.let(engine::setVoice)
                engine.setPitch(0.92f)
                engine.setSpeechRate(0.94f)
            }
            HostId.HOST_B -> {
                voices.getOrNull(1)?.let(engine::setVoice)
                engine.setPitch(1.08f)
                engine.setSpeechRate(1.02f)
            }
        }
    }

    private fun reportError() {
        _state.value = PreviewPlaybackState.Error(
            "Android could not play this line. Check the installed text-to-speech engine.",
        )
    }
}
