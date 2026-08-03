package se.roadcast.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import se.roadcast.core.database.HistoryStore
import se.roadcast.core.location.LocationSource
import se.roadcast.core.model.PlaceCandidate
import se.roadcast.core.model.PlaceCategory
import se.roadcast.core.model.PodcastPreferences
import se.roadcast.core.network.RankedPlaceDiscoveryRepository
import javax.inject.Inject

sealed interface PlayerUiState {
    data object Loading : PlayerUiState
    data class Success(val selected: PlaceCandidate, val alternatives: Int) : PlayerUiState
    data class Empty(val message: String) : PlayerUiState
    data class Error(val message: String) : PlayerUiState
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    locationSource: LocationSource,
    historyStore: HistoryStore,
    private val discoveryRepository: RankedPlaceDiscoveryRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val preferences = PodcastPreferences(
        interests = mapOf(
            PlaceCategory.HISTORY to 0.8,
            PlaceCategory.CULTURE to 0.9,
            PlaceCategory.ARCHITECTURE to 0.7,
        ),
    )

    init {
        viewModelScope.launch {
            combine(locationSource.travelState, historyStore.playedPlaceIds) { travel, played -> travel to played }
                .collect { (travel, played) ->
                    _uiState.value = PlayerUiState.Loading
                    runCatching { discoveryRepository.findRankedPlacesAhead(travel, preferences, played) }
                        .onSuccess { ranked ->
                            _uiState.value = ranked.firstOrNull()?.let {
                                PlayerUiState.Success(it.candidate, ranked.size - 1)
                            } ?: PlayerUiState.Empty("No eligible stories remain on this route.")
                        }
                        .onFailure { _uiState.value = PlayerUiState.Error(it.message ?: "Discovery failed") }
                }
        }
    }
}
