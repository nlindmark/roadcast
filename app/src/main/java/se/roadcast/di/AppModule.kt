package se.roadcast.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import se.roadcast.core.ai.DialogueGenerator
import se.roadcast.core.ai.DialogueValidator
import se.roadcast.core.ai.UserSpeechRecognizer
import se.roadcast.core.audio.AndroidTtsSpeechGenerator
import se.roadcast.core.audio.Media3PodcastPreviewPlayer
import se.roadcast.core.audio.PodcastOrchestrator
import se.roadcast.core.audio.PodcastPreviewPlayer
import se.roadcast.core.audio.SpeechGenerator
import se.roadcast.core.database.HistoryStore
import se.roadcast.core.location.CandidateRanker
import se.roadcast.core.location.DelegatingLocationSource
import se.roadcast.core.location.LocationModeController
import se.roadcast.core.location.LocationSource
import se.roadcast.core.network.PlaceDiscoveryRepository
import se.roadcast.core.network.PlaceKnowledgeRepository
import se.roadcast.core.network.RankedPlaceDiscoveryRepository
import se.roadcast.simulation.FakeDialogueGenerator
import se.roadcast.simulation.FakePlaceRepository
import se.roadcast.simulation.FakePodcastOrchestrator
import se.roadcast.simulation.FakeSpeechRecognizer
import se.roadcast.simulation.InMemoryHistoryStore
import se.roadcast.simulation.SimulationPipeline
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SimulationBindings {
    @Binds abstract fun location(implementation: DelegatingLocationSource): LocationSource
    @Binds abstract fun locationMode(implementation: DelegatingLocationSource): LocationModeController
    @Binds abstract fun discovery(implementation: FakePlaceRepository): PlaceDiscoveryRepository
    @Binds abstract fun rankedDiscovery(implementation: FakePlaceRepository): RankedPlaceDiscoveryRepository
    @Binds abstract fun knowledge(implementation: FakePlaceRepository): PlaceKnowledgeRepository
    @Binds abstract fun pipeline(implementation: FakePlaceRepository): SimulationPipeline
    @Binds abstract fun history(implementation: InMemoryHistoryStore): HistoryStore
    @Binds abstract fun dialogue(implementation: FakeDialogueGenerator): DialogueGenerator
    @Binds abstract fun speech(implementation: AndroidTtsSpeechGenerator): SpeechGenerator
    @Binds abstract fun previewPlayer(implementation: Media3PodcastPreviewPlayer): PodcastPreviewPlayer
    @Binds abstract fun recognizer(implementation: FakeSpeechRecognizer): UserSpeechRecognizer
    @Binds abstract fun orchestrator(implementation: FakePodcastOrchestrator): PodcastOrchestrator
}

@Module
@InstallIn(SingletonComponent::class)
object RankingModule {
    @Provides
    @Singleton
    fun candidateRanker(): CandidateRanker = CandidateRanker()

    @Provides
    @Singleton
    fun dialogueValidator(): DialogueValidator = DialogueValidator()
}
