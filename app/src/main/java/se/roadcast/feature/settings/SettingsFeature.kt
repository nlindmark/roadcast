package se.roadcast.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import se.roadcast.core.database.SettingsRepository
import se.roadcast.core.location.LocationMode
import se.roadcast.core.location.LocationModeController
import se.roadcast.core.location.LocationPermissionStatus
import se.roadcast.core.network.ContentSource
import se.roadcast.core.network.ContentSourceController
import javax.inject.Inject

sealed interface SettingsUiState {
    data object Loading : SettingsUiState
    data class Success(
        val simulationEnabled: Boolean,
        val remoteEnabled: Boolean,
        val remoteConfigured: Boolean,
        val autoPlay: Boolean,
        val permissionStatus: LocationPermissionStatus,
        val statusMessage: String?,
    ) : SettingsUiState
    data class Empty(val message: String) : SettingsUiState
    data class Error(val message: String) : SettingsUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val locationModeController: LocationModeController,
    private val contentSourceController: ContentSourceController,
    private val settingsStore: SettingsRepository,
) : ViewModel() {
    private val _permissionRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val permissionRequests = _permissionRequests.asSharedFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        locationModeController.mode,
        locationModeController.permissionStatus,
        contentSourceController.source,
        contentSourceController.lastError,
        settingsStore.settings,
    ) { mode, permission, contentSource, lastError, settings ->
        SettingsUiState.Success(
            simulationEnabled = mode == LocationMode.SIMULATION,
            remoteEnabled = contentSource == ContentSource.REMOTE,
            remoteConfigured = contentSourceController.remoteConfigured,
            autoPlay = settings.autoPlay,
            permissionStatus = permission,
            statusMessage = statusMessage(mode, permission, contentSource, lastError),
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsUiState.Loading,
    )

    fun setAutoPlay(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setAutoPlay(enabled) }
    }

    fun setSimulationEnabled(enabled: Boolean) {
        if (enabled) {
            locationModeController.setMode(LocationMode.SIMULATION)
            return
        }
        locationModeController.refreshPermissionStatus()
        when (locationModeController.permissionStatus.value) {
            LocationPermissionStatus.Granted ->
                locationModeController.setMode(LocationMode.GPS)
            LocationPermissionStatus.NeedsPermission,
            LocationPermissionStatus.Denied,
            -> _permissionRequests.tryEmit(Unit)
            is LocationPermissionStatus.Unavailable -> Unit
        }
    }

    fun setRemoteEnabled(enabled: Boolean) {
        contentSourceController.setSource(
            if (enabled) ContentSource.REMOTE else ContentSource.SIMULATION,
        )
    }

    fun onPermissionResult(granted: Boolean) {
        locationModeController.onPermissionResult(granted)
        if (granted) {
            locationModeController.setMode(LocationMode.GPS)
        } else {
            locationModeController.setMode(LocationMode.SIMULATION)
        }
    }

    private fun statusMessage(
        mode: LocationMode,
        permission: LocationPermissionStatus,
        contentSource: ContentSource,
        lastError: String?,
    ): String? {
        if (lastError != null) return lastError
        return when {
            contentSource == ContentSource.REMOTE ->
                "Remote APIs enabled. Failures fall back to local simulation."
            mode == LocationMode.GPS && permission is LocationPermissionStatus.Granted ->
                "Using real GPS. Fixture places are centered on Gothenburg."
            permission is LocationPermissionStatus.Denied ->
                "Location permission denied. Simulation mode stays available."
            permission is LocationPermissionStatus.Unavailable ->
                permission.message
            mode == LocationMode.GPS && permission is LocationPermissionStatus.NeedsPermission ->
                "Allow location access to follow a real journey."
            else -> null
        }
    }
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onAutoPlay: (Boolean) -> Unit,
    onSimulationEnabled: (Boolean) -> Unit,
    onRemoteEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        when (state) {
            SettingsUiState.Loading -> Text("Loading…")
            is SettingsUiState.Empty -> Text(state.message)
            is SettingsUiState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
            is SettingsUiState.Success -> {
                SettingRow(
                    title = "Simulation location",
                    subtitle = "Bundled Gothenburg route. Turn off to use real GPS.",
                    checked = state.simulationEnabled,
                    onChange = onSimulationEnabled,
                )
                SettingRow(
                    title = "Remote content APIs",
                    subtitle = if (state.remoteConfigured) {
                        "Call discover/knowledge/dialogue/speech over HTTP"
                    } else {
                        "Add roadcast.api.baseUrl in local.properties first"
                    },
                    checked = state.remoteEnabled,
                    onChange = onRemoteEnabled,
                    enabled = state.remoteConfigured || state.remoteEnabled,
                )
                SettingRow(
                    title = "Autoplay stories",
                    subtitle = "Prepare the best upcoming place",
                    checked = state.autoPlay,
                    onChange = onAutoPlay,
                )
                state.statusMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Privacy by design", style = MaterialTheme.typography.titleMedium)
                        Text(
                            when {
                                state.remoteEnabled ->
                                    "Remote mode sends travel context and knowledge requests to your API base URL."
                                state.simulationEnabled ->
                                    "This MVP sends no location or speech data to a network service."
                                else ->
                                    "GPS stays on-device for ranking local fixtures. No location is uploaded."
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
