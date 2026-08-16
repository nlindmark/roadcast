package se.roadcast.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import se.roadcast.core.model.GeoPoint
import se.roadcast.core.model.TravelState
import se.roadcast.simulation.FixtureStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FusedGpsLocationSource @Inject constructor(
    @ApplicationContext private val context: Context,
    fixtures: FixtureStore,
) : LocationSource {
    private val client = LocationServices.getFusedLocationProviderClient(context)
    private val filter = TravelMotionFilter()
    private val warmStart = fixtures.route.firstOrNull()
        ?: GeoPoint(latitude = 57.70887, longitude = 11.97354)

    private val _travelState = MutableStateFlow(
        TravelState(
            currentPosition = warmStart,
            bearingDegrees = null,
            speedMetersPerSecond = null,
            accuracyMeters = null,
            timestampEpochMillis = System.currentTimeMillis(),
        ),
    )
    private val _isRunning = MutableStateFlow(false)
    private val _speedMultiplier = MutableStateFlow(1.0)
    private val _permissionStatus = MutableStateFlow(readPermissionStatus())

    override val travelState: StateFlow<TravelState> = _travelState.asStateFlow()
    override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    override val speedMultiplier: StateFlow<Double> = _speedMultiplier.asStateFlow()
    val permissionStatus: StateFlow<LocationPermissionStatus> = _permissionStatus.asStateFlow()

    private val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_500L)
        .setMinUpdateIntervalMillis(1_000L)
        .setMinUpdateDistanceMeters(3f)
        .build()

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            _travelState.value = filter.ingest(location.toSample())
        }
    }

    fun refreshPermissionStatus() {
        _permissionStatus.value = readPermissionStatus()
    }

    fun onPermissionResult(granted: Boolean) {
        _permissionStatus.value = if (granted) {
            LocationPermissionStatus.Granted
        } else {
            LocationPermissionStatus.Denied
        }
    }

    @SuppressLint("MissingPermission")
    override fun start() {
        refreshPermissionStatus()
        if (_permissionStatus.value !is LocationPermissionStatus.Granted) {
            return
        }
        if (_isRunning.value) return
        filter.reset()
        _isRunning.value = true
        runCatching {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            client.lastLocation.addOnSuccessListener { location ->
                if (location != null && _isRunning.value) {
                    _travelState.value = filter.ingest(location.toSample())
                }
            }
        }.onFailure { error ->
            _isRunning.value = false
            _permissionStatus.value = LocationPermissionStatus.Unavailable(
                error.message ?: "Location updates are unavailable.",
            )
        }
    }

    override fun pause() {
        if (!_isRunning.value) return
        client.removeLocationUpdates(callback)
        _isRunning.value = false
    }

    override fun reset() {
        pause()
        filter.reset()
        _travelState.value = TravelState(
            currentPosition = warmStart,
            bearingDegrees = null,
            speedMetersPerSecond = null,
            accuracyMeters = null,
            timestampEpochMillis = System.currentTimeMillis(),
        )
    }

    override fun setSpeedMultiplier(multiplier: Double) {
        // Real GPS does not support simulated speed multipliers.
        _speedMultiplier.value = 1.0
    }

    override fun jumpToNextPlace() {
        // No-op: GPS follows the device, not the fixture route.
    }

    private fun readPermissionStatus(): LocationPermissionStatus {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return if (fine || coarse) {
            LocationPermissionStatus.Granted
        } else {
            LocationPermissionStatus.NeedsPermission
        }
    }

    private fun TravelMotionFilter.ingest(sample: LocationSample): TravelState = ingest(
        position = sample.position,
        timestampEpochMillis = sample.timestampEpochMillis,
        accuracyMeters = sample.accuracyMeters,
        reportedSpeedMps = sample.speedMps,
        reportedBearingDegrees = sample.bearingDegrees,
        hasReportedBearing = sample.hasBearing,
    )

    private fun Location.toSample(): LocationSample = LocationSample(
        position = GeoPoint(latitude, longitude),
        timestampEpochMillis = time.takeIf { it > 0L } ?: System.currentTimeMillis(),
        accuracyMeters = accuracy.takeIf { hasAccuracy() }?.toDouble(),
        speedMps = speed.takeIf { hasSpeed() }?.toDouble(),
        bearingDegrees = bearing.takeIf { hasBearing() }?.toDouble(),
        hasBearing = hasBearing(),
    )

    private data class LocationSample(
        val position: GeoPoint,
        val timestampEpochMillis: Long,
        val accuracyMeters: Double?,
        val speedMps: Double?,
        val bearingDegrees: Double?,
        val hasBearing: Boolean,
    )
}
