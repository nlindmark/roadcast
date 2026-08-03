package se.roadcast.simulation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import se.roadcast.core.location.GeoMath
import se.roadcast.core.location.LocationSource
import se.roadcast.core.model.TravelState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SimulationLocationSource @Inject constructor(
    fixtures: FixtureStore,
) : LocationSource {
    private val route = fixtures.route
    private val placeRouteIndices = fixtures.places
        .map { place -> route.indices.minBy { GeoMath.haversineMeters(route[it], place.position) } }
        .distinct()
        .sorted()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var routeIndex = 0
    private var ticker: Job? = null
    private val _isRunning = MutableStateFlow(false)
    private val _speedMultiplier = MutableStateFlow(1.0)
    private val _travelState = MutableStateFlow(stateAt(0))

    override val travelState: StateFlow<TravelState> = _travelState.asStateFlow()
    override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    override val speedMultiplier: StateFlow<Double> = _speedMultiplier.asStateFlow()

    override fun start() {
        if (_isRunning.value) return
        _isRunning.value = true
        ticker = scope.launch {
            while (isActive && _isRunning.value) {
                delay((1_500.0 / _speedMultiplier.value).toLong().coerceAtLeast(100L))
                routeIndex = (routeIndex + 1).coerceAtMost(route.lastIndex)
                _travelState.value = stateAt(routeIndex)
                if (routeIndex == route.lastIndex) _isRunning.value = false
            }
        }
    }

    override fun pause() {
        _isRunning.value = false
        ticker?.cancel()
        ticker = null
    }

    override fun reset() {
        pause()
        routeIndex = 0
        _travelState.value = stateAt(routeIndex)
    }

    override fun setSpeedMultiplier(multiplier: Double) {
        _speedMultiplier.value = multiplier.coerceIn(0.5, 8.0)
    }

    override fun jumpToNextPlace() {
        routeIndex = placeRouteIndices.firstOrNull { it > routeIndex } ?: route.lastIndex
        _travelState.value = stateAt(routeIndex)
    }

    private fun stateAt(index: Int): TravelState {
        val nextIndex = (index + 1).coerceAtMost(route.lastIndex)
        val bearing = if (nextIndex == index) {
            route.getOrNull(index - 1)?.let { previous ->
                GeoMath.initialBearingDegrees(previous, route[index])
            }
        } else {
            GeoMath.initialBearingDegrees(route[index], route[nextIndex])
        }
        return TravelState(
            currentPosition = route[index],
            accuracyMeters = 4.0,
            speedMetersPerSecond = 13.9 * _speedMultiplier.value,
            bearingDegrees = bearing,
            timestampEpochMillis = SimulationStartEpochMillis + index * 1_500L,
        )
    }

    private companion object {
        const val SimulationStartEpochMillis = 1_767_268_800_000L
    }
}
