package se.roadcast.core.audio

import kotlinx.coroutines.flow.StateFlow
import se.roadcast.core.model.HostId
import se.roadcast.core.model.PodcastSegment
import se.roadcast.core.model.PreviewPlaybackState

interface PodcastPreviewPlayer {
    val state: StateFlow<PreviewPlaybackState>
    val currentLineIndex: Int

    suspend fun play(segment: PodcastSegment)
    fun pause()
    fun resume()
    fun replay()
    fun stop()

    /** Pause immediately and return the dialogue index to resume after an answer. */
    fun pauseForAsk(): Int

    suspend fun playAnswerThenResume(
        question: String,
        answerText: String,
        speaker: HostId,
        resumeFromLineIndex: Int,
    )
}
