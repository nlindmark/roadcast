package se.roadcast.core.network.remote

import se.roadcast.core.ai.DialogueGenerator
import se.roadcast.core.ai.toDomain
import se.roadcast.core.audio.SpeechGenerator
import se.roadcast.core.location.CandidateHistoryPolicy
import se.roadcast.core.location.CandidateRanker
import se.roadcast.core.model.ConversationalAnswer
import se.roadcast.core.model.DialogueLine
import se.roadcast.core.model.GeneratedSpeech
import se.roadcast.core.model.HostId
import se.roadcast.core.model.PlaceCandidate
import se.roadcast.core.model.PlaceKnowledgePackage
import se.roadcast.core.model.PodcastPreferences
import se.roadcast.core.model.PodcastSegment
import se.roadcast.core.model.PreviousPodcastContext
import se.roadcast.core.model.RankedCandidate
import se.roadcast.core.model.TravelState
import se.roadcast.core.network.ContentSourceController
import se.roadcast.core.network.PlaceDiscoveryRepository
import se.roadcast.core.network.PlaceKnowledgeRepository
import se.roadcast.core.network.RankedPlaceDiscoveryRepository
import se.roadcast.core.network.api.AnswerQuestionRequest
import se.roadcast.core.network.api.BuildKnowledgeRequest
import se.roadcast.core.network.api.DiscoverPlacesRequest
import se.roadcast.core.network.api.GeneratePodcastRequest
import se.roadcast.core.network.api.GenerateSpeechRequest
import se.roadcast.core.network.api.RoadcastApiClient
import se.roadcast.simulation.FakeDialogueGenerator
import se.roadcast.simulation.FakePlaceRepository
import se.roadcast.simulation.PipelineSnapshot
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoutingPlaceRepository @Inject constructor(
    private val local: FakePlaceRepository,
    private val api: RoadcastApiClient,
    private val contentSource: ContentSourceController,
    private val ranker: CandidateRanker,
) : PlaceDiscoveryRepository, RankedPlaceDiscoveryRepository, PlaceKnowledgeRepository {
    override suspend fun findPlacesAhead(
        travelState: TravelState,
        horizonMeters: Double,
        corridorWidthMeters: Double,
    ): List<PlaceCandidate> {
        if (!contentSource.shouldUseRemote()) {
            return local.findPlacesAhead(travelState, horizonMeters, corridorWidthMeters)
        }
        return runCatching {
            api.discoverPlaces(
                DiscoverPlacesRequest(travelState, horizonMeters, corridorWidthMeters),
            ).places
        }.onFailure { contentSource.reportError(it.message) }
            .getOrElse {
                contentSource.reportError(it.message ?: "Remote discover failed; using simulation.")
                local.findPlacesAhead(travelState, horizonMeters, corridorWidthMeters)
            }
    }

    override suspend fun findRankedPlacesAhead(
        travelState: TravelState,
        preferences: PodcastPreferences,
        playedPlaceIds: Set<String>,
    ): List<RankedCandidate> {
        if (!contentSource.shouldUseRemote()) {
            return local.findRankedPlacesAhead(travelState, preferences, playedPlaceIds)
        }
        return runCatching {
            val places = api.discoverPlaces(DiscoverPlacesRequest(travelState)).places
            val available = CandidateHistoryPolicy.eligible(places, playedPlaceIds)
            val ranked = ranker.rank(available, travelState, preferences, playedPlaceIds)
            local.publishSnapshot(
                PipelineSnapshot(
                    state = if (ranked.isEmpty()) {
                        "Remote: no eligible places"
                    } else {
                        "Remote ranked ${ranked.size} eligible places"
                    },
                    rankedCandidates = ranked,
                    selected = ranked.firstOrNull(),
                ),
            )
            contentSource.reportError(null)
            ranked
        }.getOrElse { error ->
            contentSource.reportError(error.message ?: "Remote discover failed; using simulation.")
            local.findRankedPlacesAhead(travelState, preferences, playedPlaceIds)
        }
    }

    override suspend fun buildKnowledgePackage(place: PlaceCandidate): PlaceKnowledgePackage {
        if (!contentSource.shouldUseRemote()) {
            return local.buildKnowledgePackage(place)
        }
        return runCatching {
            api.buildKnowledge(BuildKnowledgeRequest(place)).knowledge
                .also { contentSource.reportError(null) }
        }.getOrElse { error ->
            contentSource.reportError(error.message ?: "Remote knowledge failed; using simulation.")
            local.buildKnowledgePackage(place)
        }
    }
}

@Singleton
class RoutingDialogueGenerator @Inject constructor(
    private val local: FakeDialogueGenerator,
    private val api: RoadcastApiClient,
    private val contentSource: ContentSourceController,
) : DialogueGenerator {
    override suspend fun generateSegment(
        knowledge: PlaceKnowledgePackage,
        previousContext: PreviousPodcastContext?,
        preferences: PodcastPreferences,
    ): PodcastSegment {
        if (!contentSource.shouldUseRemote()) {
            return local.generateSegment(knowledge, previousContext, preferences)
        }
        return runCatching {
            api.generatePodcast(
                GeneratePodcastRequest(knowledge, previousContext, preferences),
            ).segment.toDomain(System.currentTimeMillis())
                .also { contentSource.reportError(null) }
        }.getOrElse { error ->
            contentSource.reportError(error.message ?: "Remote dialogue failed; using simulation.")
            local.generateSegment(knowledge, previousContext, preferences)
        }
    }

    override suspend fun answerQuestion(
        question: String,
        currentKnowledge: PlaceKnowledgePackage,
        recentDialogue: List<DialogueLine>,
        preferences: PodcastPreferences,
    ): ConversationalAnswer {
        if (!contentSource.shouldUseRemote()) {
            return local.answerQuestion(question, currentKnowledge, recentDialogue, preferences)
        }
        return runCatching {
            api.answerQuestion(
                AnswerQuestionRequest(question, currentKnowledge, recentDialogue, preferences),
            ).answer.also { contentSource.reportError(null) }
        }.getOrElse { error ->
            contentSource.reportError(error.message ?: "Remote answer failed; using simulation.")
            local.answerQuestion(question, currentKnowledge, recentDialogue, preferences)
        }
    }
}

@Singleton
class RoutingSpeechGenerator @Inject constructor(
    private val local: se.roadcast.core.audio.AndroidTtsSpeechGenerator,
    private val api: RoadcastApiClient,
    private val contentSource: ContentSourceController,
) : SpeechGenerator {
    override suspend fun synthesize(text: String, speaker: HostId): GeneratedSpeech {
        if (!contentSource.shouldUseRemote()) {
            return local.synthesize(text, speaker)
        }
        return runCatching {
            api.generateSpeech(GenerateSpeechRequest(text, speaker)).speech
                .also { contentSource.reportError(null) }
        }.getOrElse { error ->
            contentSource.reportError(error.message ?: "Remote speech failed; using on-device TTS.")
            local.synthesize(text, speaker)
        }
    }
}
