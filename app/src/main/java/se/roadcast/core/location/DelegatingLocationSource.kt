package se.roadcast.core.location

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import se.roadcast.core.model.TravelState
import se.roadcast.simulation.SimulationLocationSource
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class DelegatingLocationSource @Inject constructor(
    private val simulation: SimulationLocationSource,
    private val gps: FusedGpsLocationSource,
) : LocationSource, LocationModeController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _mode = MutableStateFlow(LocationMode.SIMULATION)

    override val mode: StateFlow<LocationMode> = _mode.asStateFlow()

    override val permissionStatus: StateFlow<LocationPermissionStatus> =
        gps.permissionStatus

    override val simulationControlsEnabled: StateFlow<Boolean> =
        _mode.map { it == LocationMode.SIMULATION }
            .stateIn(scope, SharingStarted.Eagerly, true)

    override val travelState: StateFlow<TravelState> = _mode
        .flatMapLatest { mode ->
            when (mode) {
                LocationMode.SIMULATION -> simulation.travelState
                LocationMode.GPS -> gps.travelState
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, simulation.travelState.value)

    override val isRunning: StateFlow<Boolean> = _mode
        .flatMapLatest { mode ->
            when (mode) {
                LocationMode.SIMULATION -> simulation.isRunning
                LocationMode.GPS -> gps.isRunning
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, false)

    override val speedMultiplier: StateFlow<Double> = _mode
        .flatMapLatest { mode ->
            when (mode) {
                LocationMode.SIMULATION -> simulation.speedMultiplier
                LocationMode.GPS -> gps.speedMultiplier
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, 1.0)

    override fun setMode(mode: LocationMode) {
        if (_mode.value == mode) return
        active().pause()
        _mode.value = mode
        refreshPermissionStatus()
    }

    override fun refreshPermissionStatus() {
        gps.refreshPermissionStatus()
    }

    override fun onPermissionResult(granted: Boolean) {
        gps.onPermissionResult(granted)
        if (granted && _mode.value == LocationMode.GPS && !isRunning.value) {
            // Leave start to the user / player journey control.
        }
    }

    override fun start() = active().start()
    override fun pause() = active().pause()
    override fun reset() = active().reset()
    override fun setSpeedMultiplier(multiplier: Double) = active().setSpeedMultiplier(multiplier)
    override fun jumpToNextPlace() = active().jumpToNextPlace()

    private fun active(): LocationSource = when (_mode.value) {
        LocationMode.SIMULATION -> simulation
        LocationMode.GPS -> gps
    }
}
