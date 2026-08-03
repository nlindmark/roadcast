package se.roadcast.feature.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import se.roadcast.core.location.LocationSource
import se.roadcast.core.model.TravelState
import se.roadcast.simulation.PipelineSnapshot
import se.roadcast.simulation.SimulationPipeline
import javax.inject.Inject

sealed interface DebugUiState {
    data object Loading : DebugUiState
    data class Success(
        val travel: TravelState,
        val running: Boolean,
        val speedMultiplier: Double,
        val pipeline: PipelineSnapshot,
    ) : DebugUiState
    data class Empty(val message: String) : DebugUiState
    data class Error(val message: String) : DebugUiState
}

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val locationSource: LocationSource,
    pipeline: SimulationPipeline,
) : ViewModel() {
    val uiState: StateFlow<DebugUiState> = combine(
        locationSource.travelState,
        locationSource.isRunning,
        locationSource.speedMultiplier,
        pipeline.snapshot,
    ) { travel, running, speed, snapshot ->
        DebugUiState.Success(travel, running, speed, snapshot)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DebugUiState.Loading)

    fun toggle() = if (locationSource.isRunning.value) locationSource.pause() else locationSource.start()
    fun reset() = locationSource.reset()
    fun jump() = locationSource.jumpToNextPlace()
    fun setSpeed(multiplier: Double) = locationSource.setSpeedMultiplier(multiplier)
}
