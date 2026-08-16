package se.roadcast.feature.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import se.roadcast.core.location.LocationMode
import se.roadcast.core.location.LocationModeController
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
        val simulationControlsEnabled: Boolean,
        val modeLabel: String,
    ) : DebugUiState
    data class Empty(val message: String) : DebugUiState
    data class Error(val message: String) : DebugUiState
}

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val locationSource: LocationSource,
    private val locationModeController: LocationModeController,
    pipeline: SimulationPipeline,
) : ViewModel() {
    val uiState: StateFlow<DebugUiState> = combine(
        locationSource.travelState,
        locationSource.isRunning,
        locationSource.speedMultiplier,
        pipeline.snapshot,
        locationModeController.mode,
    ) { travel, running, speed, snapshot, mode ->
        DebugUiState.Success(
            travel = travel,
            running = running,
            speedMultiplier = speed,
            pipeline = snapshot,
            simulationControlsEnabled = mode == LocationMode.SIMULATION,
            modeLabel = if (mode == LocationMode.SIMULATION) "Simulation" else "GPS",
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DebugUiState.Loading)

    fun toggle() = if (locationSource.isRunning.value) locationSource.pause() else locationSource.start()
    fun reset() = locationSource.reset()
    fun jump() {
        if (locationModeController.mode.value == LocationMode.SIMULATION) {
            locationSource.jumpToNextPlace()
        }
    }
    fun setSpeed(multiplier: Double) {
        if (locationModeController.mode.value == LocationMode.SIMULATION) {
            locationSource.setSpeedMultiplier(multiplier)
        }
    }
}
