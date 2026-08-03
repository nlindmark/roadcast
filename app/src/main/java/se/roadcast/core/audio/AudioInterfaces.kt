package se.roadcast.core.audio

import kotlinx.coroutines.flow.StateFlow
import se.roadcast.core.model.GeneratedSpeech
import se.roadcast.core.model.HostId
import se.roadcast.core.model.PodcastOrchestratorState

interface SpeechGenerator {
    suspend fun synthesize(text: String, speaker: HostId): GeneratedSpeech
}

interface PodcastOrchestrator {
    val state: StateFlow<PodcastOrchestratorState>
    suspend fun start()
    suspend fun stop()
    suspend fun refreshUpcomingPlaces()
    suspend fun prepareNextSegment()
    suspend fun skipCurrentSegment()
    suspend fun requestMoreAboutCurrentPlace()
    suspend fun askQuestion(question: String)
}
