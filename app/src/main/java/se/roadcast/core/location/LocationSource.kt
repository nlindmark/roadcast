package se.roadcast.core.location

import kotlinx.coroutines.flow.StateFlow
import se.roadcast.core.model.TravelState

enum class LocationMode {
    SIMULATION,
    GPS,
}

sealed interface LocationPermissionStatus {
    data object Granted : LocationPermissionStatus
    data object NeedsPermission : LocationPermissionStatus
    data object Denied : LocationPermissionStatus
    data class Unavailable(val message: String) : LocationPermissionStatus
}

interface LocationSource {
    val travelState: StateFlow<TravelState>
    val isRunning: StateFlow<Boolean>
    val speedMultiplier: StateFlow<Double>
    fun start()
    fun pause()
    fun reset()
    fun setSpeedMultiplier(multiplier: Double)
    fun jumpToNextPlace()
}

interface LocationModeController {
    val mode: StateFlow<LocationMode>
    val permissionStatus: StateFlow<LocationPermissionStatus>
    val simulationControlsEnabled: StateFlow<Boolean>
    fun setMode(mode: LocationMode)
    fun refreshPermissionStatus()
    fun onPermissionResult(granted: Boolean)
}
