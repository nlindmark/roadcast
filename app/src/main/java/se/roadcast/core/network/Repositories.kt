package se.roadcast.core.network

import se.roadcast.core.model.PlaceCandidate
import se.roadcast.core.model.PlaceKnowledgePackage
import se.roadcast.core.model.PodcastPreferences
import se.roadcast.core.model.RankedCandidate
import se.roadcast.core.model.TravelState

interface PlaceDiscoveryRepository {
    suspend fun findPlacesAhead(
        travelState: TravelState,
        horizonMeters: Double,
        corridorWidthMeters: Double,
    ): List<PlaceCandidate>
}

interface RankedPlaceDiscoveryRepository {
    suspend fun findRankedPlacesAhead(
        travelState: TravelState,
        preferences: PodcastPreferences,
        playedPlaceIds: Set<String>,
    ): List<RankedCandidate>
}

interface PlaceKnowledgeRepository {
    suspend fun buildKnowledgePackage(place: PlaceCandidate): PlaceKnowledgePackage
}
