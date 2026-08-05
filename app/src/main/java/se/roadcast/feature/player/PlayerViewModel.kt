package se.roadcast.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import se.roadcast.core.ai.DialogueGenerator
import se.roadcast.core.audio.PodcastPreviewPlayer
import se.roadcast.core.database.HistoryStore
import se.roadcast.core.location.LocationSource
import se.roadcast.core.model.PlaceCandidate
import se.roadcast.core.model.PlaceCategory
import se.roadcast.core.model.PodcastPreferences
import se.roadcast.core.model.PodcastSegment
import se.roadcast.core.model.PreviewPlaybackState
import se.roadcast.core.network.RankedPlaceDiscoveryRepository
import se.roadcast.core.network.PlaceKnowledgeRepository
import javax.inject.Inject

sealed interface PlayerUiState {
    data object Loading : PlayerUiState
    data class Success(
        val selected: PlaceCandidate,
        val alternatives: Int,
        val segment: PodcastSegment? = null,
        val playback: PreviewPlaybackState = PreviewPlaybackState.Idle,
    ) : PlayerUiState
    data class Empty(val message: String) : PlayerUiState
    data class Error(val message: String) : PlayerUiState
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    locationSource: LocationSource,
    historyStore: HistoryStore,
    private val discoveryRepository: RankedPlaceDiscoveryRepository,
    private val knowledgeRepository: PlaceKnowledgeRepository,
    private val dialogueGenerator: DialogueGenerator,
    private val previewPlayer: PodcastPreviewPlayer,
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
                    runCatching { discoveryRepository.findRankedPlacesAhead(travel, preferences, played) }
                        .onSuccess { ranked ->
                            _uiState.value = ranked.firstOrNull()?.let { selected ->
                                val current = _uiState.value as? PlayerUiState.Success
                                if (current?.selected?.id == selected.candidate.id) {
                                    current.copy(
                                        selected = selected.candidate,
                                        alternatives = ranked.size - 1,
                                    )
                                } else {
                                    previewPlayer.stop()
                                    PlayerUiState.Success(selected.candidate, ranked.size - 1)
                                }
                            } ?: PlayerUiState.Empty("No eligible stories remain on this route.")
                        }
                        .onFailure { _uiState.value = PlayerUiState.Error(it.message ?: "Discovery failed") }
                }
        }
        viewModelScope.launch {
            previewPlayer.state.collect { playback ->
                val current = _uiState.value as? PlayerUiState.Success ?: return@collect
                _uiState.value = current.copy(playback = playback)
            }
        }
    }

    fun playOrPause() {
        val current = _uiState.value as? PlayerUiState.Success ?: return
        when (current.playback) {
            is PreviewPlaybackState.Playing -> previewPlayer.pause()
            is PreviewPlaybackState.Paused -> previewPlayer.resume()
            PreviewPlaybackState.Completed -> previewPlayer.replay()
            PreviewPlaybackState.Initializing -> Unit
            PreviewPlaybackState.Idle, is PreviewPlaybackState.Error -> prepareAndPlay(current)
        }
    }

    fun replay() {
        val current = _uiState.value as? PlayerUiState.Success ?: return
        if (current.segment == null) prepareAndPlay(current) else previewPlayer.replay()
    }

    private fun prepareAndPlay(current: PlayerUiState.Success) {
        viewModelScope.launch {
            _uiState.value = current.copy(playback = PreviewPlaybackState.Initializing)
            runCatching {
                val knowledge = knowledgeRepository.buildKnowledgePackage(current.selected)
                val segment = dialogueGenerator.generateSegment(knowledge, null, preferences)
                _uiState.value = current.copy(
                    segment = segment,
                    playback = PreviewPlaybackState.Initializing,
                )
                previewPlayer.play(segment)
            }.onFailure { error ->
                _uiState.value = current.copy(
                    playback = PreviewPlaybackState.Error(error.message ?: "Could not prepare the preview."),
                )
            }
        }
    }

    override fun onCleared() {
        previewPlayer.stop()
        super.onCleared()
    }
}
