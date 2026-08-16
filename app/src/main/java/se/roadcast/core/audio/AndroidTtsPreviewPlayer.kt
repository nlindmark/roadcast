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
    private var lineIndex = 0
    private var resumeAfterAnswerIndex = 0
    private var mode = Mode.SEGMENT
    private var pendingAnswerQuestion: String? = null
    private var pendingAnswerText: String? = null
    private var pendingAnswerSpeaker: HostId = HostId.HOST_B

    override val currentLineIndex: Int
        get() = synchronized(lock) { lineIndex }

    private val engine = TextToSpeech(context) { status ->
        ready.complete(status == TextToSpeech.SUCCESS)
    }

    init {
        engine.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) = Unit

                override fun onDone(utteranceId: String) {
                    synchronized(lock) {
                        when (mode) {
                            Mode.ANSWER -> {
                                mode = Mode.SEGMENT
                                pendingAnswerQuestion = null
                                pendingAnswerText = null
                                val segment = currentSegment
                                if (segment == null) {
                                    _state.value = PreviewPlaybackState.Idle
                                    return
                                }
                                if (resumeAfterAnswerIndex > segment.dialogue.lastIndex) {
                                    _state.value = PreviewPlaybackState.Completed
                                } else {
                                    speakLine(segment, resumeAfterAnswerIndex)
                                }
                            }
                            Mode.SEGMENT -> {
                                val segment = currentSegment ?: return
                                val nextIndex = lineIndex + 1
                                if (nextIndex > segment.dialogue.lastIndex) {
                                    _state.value = PreviewPlaybackState.Completed
                                } else {
                                    speakLine(segment, nextIndex)
                                }
                            }
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
            mode = Mode.SEGMENT
            currentSegment = segment
            engine.stop()
            speakLine(segment, 0)
        }
    }

    override fun pause() {
        synchronized(lock) {
            if (mode == Mode.ANSWER) return
            val segment = currentSegment ?: return
            val line = segment.dialogue.getOrNull(lineIndex) ?: return
            engine.stop()
            _state.value = PreviewPlaybackState.Paused(lineIndex, line)
        }
    }

    override fun resume() {
        synchronized(lock) {
            val segment = currentSegment ?: return
            if (_state.value is PreviewPlaybackState.Paused) {
                mode = Mode.SEGMENT
                speakLine(segment, lineIndex)
            }
        }
    }

    override fun replay() {
        synchronized(lock) {
            val segment = currentSegment ?: return
            mode = Mode.SEGMENT
            engine.stop()
            speakLine(segment, 0)
        }
    }

    override fun stop() {
        synchronized(lock) {
            engine.stop()
            mode = Mode.SEGMENT
            currentSegment = null
            lineIndex = 0
            resumeAfterAnswerIndex = 0
            pendingAnswerQuestion = null
            pendingAnswerText = null
            _state.value = PreviewPlaybackState.Idle
        }
    }

    override fun pauseForAsk(): Int = synchronized(lock) {
        val segment = currentSegment ?: return 0
        val resumeIndex = (lineIndex + 1).coerceAtMost(segment.dialogue.size)
        engine.stop()
        mode = Mode.SEGMENT
        val line = segment.dialogue.getOrNull(lineIndex)
        if (line != null) {
            _state.value = PreviewPlaybackState.Paused(lineIndex, line)
        }
        resumeIndex
    }

    override suspend fun playAnswerThenResume(
        question: String,
        answerText: String,
        speaker: HostId,
        resumeFromLineIndex: Int,
    ) {
        if (!ready.await()) {
            _state.value = PreviewPlaybackState.Error(
                "Text-to-speech is unavailable. Install or enable an Android speech engine.",
            )
            return
        }
        synchronized(lock) {
            val segment = currentSegment ?: return
            mode = Mode.ANSWER
            pendingAnswerQuestion = question
            pendingAnswerText = answerText
            pendingAnswerSpeaker = speaker
            resumeAfterAnswerIndex = resumeFromLineIndex.coerceIn(0, segment.dialogue.size)
            configureHost(speaker)
            _state.value = PreviewPlaybackState.Answering(
                question = question,
                answerText = answerText,
                speaker = speaker,
                resumeFromLineIndex = resumeAfterAnswerIndex,
            )
            val result = engine.speak(
                answerText,
                TextToSpeech.QUEUE_FLUSH,
                Bundle(),
                "answer:${segment.id}:${answerText.hashCode()}",
            )
            if (result == TextToSpeech.ERROR) reportError()
        }
    }

    private fun speakLine(segment: PodcastSegment, index: Int) {
        val line = segment.dialogue.getOrNull(index) ?: run {
            _state.value = PreviewPlaybackState.Completed
            return
        }
        lineIndex = index
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

    private enum class Mode { SEGMENT, ANSWER }
}
