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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

sealed interface SettingsUiState {
    data object Loading : SettingsUiState
    data class Success(val simulationEnabled: Boolean, val autoPlay: Boolean) : SettingsUiState
    data class Empty(val message: String) : SettingsUiState
    data class Error(val message: String) : SettingsUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Success(true, true))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun setAutoPlay(enabled: Boolean) {
        val current = _uiState.value as? SettingsUiState.Success ?: return
        _uiState.value = current.copy(autoPlay = enabled)
    }
}

@Composable
fun SettingsScreen(state: SettingsUiState, onAutoPlay: (Boolean) -> Unit, modifier: Modifier = Modifier) {
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
                SettingRow("Simulation mode", "Uses bundled Swedish route fixtures", state.simulationEnabled, {})
                SettingRow("Autoplay stories", "Prepare the best upcoming place", state.autoPlay, onAutoPlay)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Privacy by design", style = MaterialTheme.typography.titleMedium)
                        Text("This MVP sends no location or speech data to a network service.")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
