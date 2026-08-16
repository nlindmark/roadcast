package se.roadcast.feature.map

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import se.roadcast.core.location.LocationSource
import se.roadcast.core.model.GeoPoint
import se.roadcast.core.model.PlaceCandidate
import se.roadcast.core.model.TravelState
import se.roadcast.simulation.FixtureStore
import javax.inject.Inject

data class MapUiModel(
    val travel: TravelState,
    val places: List<PlaceCandidate>,
    val route: List<GeoPoint>,
)

sealed interface MapUiState {
    data object Loading : MapUiState
    data class Ready(val model: MapUiModel) : MapUiState
}

@HiltViewModel
class MapViewModel @Inject constructor(
    locationSource: LocationSource,
    fixtures: FixtureStore,
) : ViewModel() {
    val uiState: StateFlow<MapUiState> = combine(
        locationSource.travelState,
        locationSource.isRunning,
    ) { travel, _ ->
        MapUiState.Ready(
            MapUiModel(
                travel = travel,
                places = fixtures.places,
                route = fixtures.route,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState.Loading)
}

@Composable
fun MapScreen(state: MapUiState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(modifier.fillMaxSize()) {
        when (state) {
            MapUiState.Loading -> Text(
                "Loading map…",
                modifier = Modifier.align(Alignment.Center),
            )
            is MapUiState.Ready -> {
                OsmMapView(model = state.model, context = context)
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp),
                    tonalElevation = 2.dp,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        "OpenStreetMap · ${state.model.places.size} places",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun OsmMapView(model: MapUiModel, context: Context) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = context.cacheDir
            osmdroidTileCache = context.cacheDir.resolve("osmdroid")
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(14.0)
        }
    }
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { mapView },
        update = { view ->
            view.overlays.clear()
            val routePoints = model.route.map { OsmGeoPoint(it.latitude, it.longitude) }
            if (routePoints.size >= 2) {
                view.overlays += Polyline().apply {
                    setPoints(routePoints)
                    outlinePaint.color = AndroidColor.parseColor("#006A62")
                    outlinePaint.strokeWidth = 8f
                }
            }
            model.places.forEach { place ->
                view.overlays += Marker(view).apply {
                    position = OsmGeoPoint(place.position.latitude, place.position.longitude)
                    title = place.name
                    snippet = place.shortDescription
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
            }
            val you = OsmGeoPoint(
                model.travel.currentPosition.latitude,
                model.travel.currentPosition.longitude,
            )
            view.overlays += Marker(view).apply {
                position = you
                title = "You are here"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            }
            view.controller.setCenter(you)
            view.invalidate()
        },
    )
}
