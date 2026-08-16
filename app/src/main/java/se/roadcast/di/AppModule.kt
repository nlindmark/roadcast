package se.roadcast.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import se.roadcast.BuildConfig
import se.roadcast.core.ai.DialogueGenerator
import se.roadcast.core.ai.DialogueValidator
import se.roadcast.core.ai.UserSpeechRecognizer
import se.roadcast.core.audio.Media3PodcastPreviewPlayer
import se.roadcast.core.audio.PodcastOrchestrator
import se.roadcast.core.audio.PodcastPreviewPlayer
import se.roadcast.core.audio.SpeechGenerator
import se.roadcast.core.database.HistoryDao
import se.roadcast.core.database.HistoryStore
import se.roadcast.core.database.RoadcastDatabase
import se.roadcast.core.database.RoomHistoryStore
import se.roadcast.core.location.CandidateRanker
import se.roadcast.core.location.DelegatingLocationSource
import se.roadcast.core.location.LocationModeController
import se.roadcast.core.location.LocationSource
import se.roadcast.core.network.ContentSourceController
import se.roadcast.core.network.DefaultContentSourceController
import se.roadcast.core.network.PlaceDiscoveryRepository
import se.roadcast.core.network.PlaceKnowledgeRepository
import se.roadcast.core.network.RankedPlaceDiscoveryRepository
import se.roadcast.core.network.api.ApiEndpointConfig
import se.roadcast.core.network.api.createRoadcastHttpClient
import se.roadcast.core.network.api.createRoadcastJson
import se.roadcast.core.network.remote.RoutingDialogueGenerator
import se.roadcast.core.network.remote.RoutingPlaceRepository
import se.roadcast.core.network.remote.RoutingSpeechGenerator
import se.roadcast.simulation.FakePodcastOrchestrator
import se.roadcast.simulation.FakePlaceRepository
import se.roadcast.simulation.FakeSpeechRecognizer
import se.roadcast.simulation.SimulationPipeline
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SimulationBindings {
    @Binds abstract fun location(implementation: DelegatingLocationSource): LocationSource
    @Binds abstract fun locationMode(implementation: DelegatingLocationSource): LocationModeController
    @Binds abstract fun contentSource(implementation: DefaultContentSourceController): ContentSourceController
    @Binds abstract fun discovery(implementation: RoutingPlaceRepository): PlaceDiscoveryRepository
    @Binds abstract fun rankedDiscovery(implementation: RoutingPlaceRepository): RankedPlaceDiscoveryRepository
    @Binds abstract fun knowledge(implementation: RoutingPlaceRepository): PlaceKnowledgeRepository
    @Binds abstract fun pipeline(implementation: FakePlaceRepository): SimulationPipeline
    @Binds abstract fun history(implementation: RoomHistoryStore): HistoryStore
    @Binds abstract fun dialogue(implementation: RoutingDialogueGenerator): DialogueGenerator
    @Binds abstract fun speech(implementation: RoutingSpeechGenerator): SpeechGenerator
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

    @Provides
    @Singleton
    fun apiEndpointConfig(): ApiEndpointConfig = ApiEndpointConfig(BuildConfig.API_BASE_URL)

    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient = createRoadcastHttpClient()

    @Provides
    @Singleton
    fun json(): Json = createRoadcastJson()

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): RoadcastDatabase =
        Room.databaseBuilder(context, RoadcastDatabase::class.java, "roadcast.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun historyDao(database: RoadcastDatabase): HistoryDao = database.historyDao()
}
