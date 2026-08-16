package se.roadcast.simulation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import se.roadcast.core.database.HistoryStore
import se.roadcast.core.location.CandidateHistoryPolicy
import se.roadcast.core.location.CandidateRanker
import se.roadcast.core.model.PlaceCandidate
import se.roadcast.core.model.PlaceKnowledgePackage
import se.roadcast.core.model.PodcastPreferences
import se.roadcast.core.model.RankedCandidate
import se.roadcast.core.model.TravelState
import se.roadcast.core.network.PlaceDiscoveryRepository
import se.roadcast.core.network.PlaceKnowledgeRepository
import se.roadcast.core.network.RankedPlaceDiscoveryRepository
import javax.inject.Inject
import javax.inject.Singleton

data class PipelineSnapshot(
    val state: String = "Waiting for location",
    val rankedCandidates: List<RankedCandidate> = emptyList(),
    val selected: RankedCandidate? = null,
)

interface SimulationPipeline {
    val snapshot: StateFlow<PipelineSnapshot>
}

@Singleton
class FakePlaceRepository @Inject constructor(
    private val fixtures: FixtureStore,
    private val ranker: CandidateRanker,
) : PlaceDiscoveryRepository, RankedPlaceDiscoveryRepository, PlaceKnowledgeRepository, SimulationPipeline {
    private val _snapshot = MutableStateFlow(PipelineSnapshot())
    override val snapshot: StateFlow<PipelineSnapshot> = _snapshot.asStateFlow()

    fun publishSnapshot(snapshot: PipelineSnapshot) {
        _snapshot.value = snapshot
    }

    override suspend fun findPlacesAhead(
        travelState: TravelState,
        horizonMeters: Double,
        corridorWidthMeters: Double,
    ): List<PlaceCandidate> = ranker
        .rank(fixtures.places, travelState, PodcastPreferences(), emptySet())
        .filter { ranked ->
            ranked.candidate.distanceMeters <= horizonMeters &&
                ranked.corridorDistanceMeters <= corridorWidthMeters &&
                (travelState.bearingDegrees == null || ranked.bearingDifferenceDegrees <= 90.0)
        }
        .map { it.candidate }

    override suspend fun findRankedPlacesAhead(
        travelState: TravelState,
        preferences: PodcastPreferences,
        playedPlaceIds: Set<String>,
    ): List<RankedCandidate> {
        val available = CandidateHistoryPolicy.eligible(fixtures.places, playedPlaceIds)
        val ranked = ranker.rank(available, travelState, preferences, playedPlaceIds)
        _snapshot.value = PipelineSnapshot(
            state = if (ranked.isEmpty()) "No eligible places" else "Ranked ${ranked.size} eligible places",
            rankedCandidates = ranked,
            selected = ranked.firstOrNull(),
        )
        return ranked
    }

    override suspend fun buildKnowledgePackage(place: PlaceCandidate): PlaceKnowledgePackage =
        requireNotNull(fixtures.places.first { it.id == place.id }.knowledgePackage)
}

@Singleton
class InMemoryHistoryStore @Inject constructor() : HistoryStore {
    private val _playedPlaceIds = MutableStateFlow<Set<String>>(emptySet())
    override val playedPlaceIds: StateFlow<Set<String>> = _playedPlaceIds.asStateFlow()

    override suspend fun markPlayed(placeId: String) {
        _playedPlaceIds.value = _playedPlaceIds.value + placeId
    }

    override suspend fun clear() {
        _playedPlaceIds.value = emptySet()
    }
}
