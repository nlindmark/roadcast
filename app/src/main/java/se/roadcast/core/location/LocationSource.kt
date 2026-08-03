package se.roadcast.core.location

import kotlinx.coroutines.flow.StateFlow
import se.roadcast.core.model.TravelState

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
