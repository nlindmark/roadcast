package se.roadcast.core.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import se.roadcast.core.model.HostId
import se.roadcast.core.model.PodcastSegment
import se.roadcast.core.model.PreviewPlaybackState

class FakePodcastPreviewPlayer : PodcastPreviewPlayer {
    private val _state = MutableStateFlow<PreviewPlaybackState>(PreviewPlaybackState.Idle)
    override val state: StateFlow<PreviewPlaybackState> = _state.asStateFlow()

    private var segment: PodcastSegment? = null
    private var lineIndex = 0
    override val currentLineIndex: Int get() = lineIndex

    var resumeCalls = 0
        private set
    var lastAnswerText: String? = null
        private set
    var lastResumeIndex: Int? = null
        private set

    override suspend fun play(segment: PodcastSegment) {
        this.segment = segment
        lineIndex = 0
        val line = segment.dialogue.first()
        _state.value = PreviewPlaybackState.Playing(0, line)
    }

    override fun pause() {
        val current = segment ?: return
        val line = current.dialogue[lineIndex]
        _state.value = PreviewPlaybackState.Paused(lineIndex, line)
    }

    override fun resume() {
        resumeCalls += 1
        val current = segment ?: return
        val line = current.dialogue[lineIndex]
        _state.value = PreviewPlaybackState.Playing(lineIndex, line)
    }

    override fun replay() {
        val current = segment ?: return
        lineIndex = 0
        _state.value = PreviewPlaybackState.Playing(0, current.dialogue.first())
    }

    override fun stop() {
        segment = null
        lineIndex = 0
        _state.value = PreviewPlaybackState.Idle
    }

    override fun pauseForAsk(): Int {
        val current = segment ?: return 0
        val resumeIndex = lineIndex + 1
        val line = current.dialogue[lineIndex]
        _state.value = PreviewPlaybackState.Paused(lineIndex, line)
        return resumeIndex
    }

    override suspend fun playAnswerThenResume(
        question: String,
        answerText: String,
        speaker: HostId,
        resumeFromLineIndex: Int,
    ) {
        lastAnswerText = answerText
        lastResumeIndex = resumeFromLineIndex
        val current = segment ?: return
        _state.value = PreviewPlaybackState.Answering(question, answerText, speaker, resumeFromLineIndex)
        if (resumeFromLineIndex > current.dialogue.lastIndex) {
            _state.value = PreviewPlaybackState.Completed
        } else {
            lineIndex = resumeFromLineIndex
            _state.value = PreviewPlaybackState.Playing(
                resumeFromLineIndex,
                current.dialogue[resumeFromLineIndex],
            )
        }
    }

    fun advanceToLine(index: Int) {
        val current = segment ?: return
        lineIndex = index
        _state.value = PreviewPlaybackState.Playing(index, current.dialogue[index])
    }
}
