package se.roadcast.core.audio

import kotlinx.coroutines.flow.StateFlow
import se.roadcast.core.model.PodcastSegment
import se.roadcast.core.model.PreviewPlaybackState

interface PodcastPreviewPlayer {
    val state: StateFlow<PreviewPlaybackState>

    suspend fun play(segment: PodcastSegment)
    fun pause()
    fun resume()
    fun replay()
    fun stop()
}
