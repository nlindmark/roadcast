package se.roadcast.core.database

import kotlinx.coroutines.flow.StateFlow

interface HistoryStore {
    val playedPlaceIds: StateFlow<Set<String>>
    suspend fun markPlayed(placeId: String)
    suspend fun clear()
}
