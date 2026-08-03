package se.roadcast.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import se.roadcast.core.database.HistoryStore
import javax.inject.Inject

sealed interface HistoryUiState {
    data object Loading : HistoryUiState
    data class Success(val placeIds: List<String>) : HistoryUiState
    data class Empty(val message: String) : HistoryUiState
    data class Error(val message: String) : HistoryUiState
}

@HiltViewModel
class HistoryViewModel @Inject constructor(historyStore: HistoryStore) : ViewModel() {
    val uiState: StateFlow<HistoryUiState> = historyStore.playedPlaceIds
        .map { ids ->
            if (ids.isEmpty()) HistoryUiState.Empty("Played stories will appear here.")
            else HistoryUiState.Success(ids.sorted())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState.Loading)
}

@Composable
fun HistoryScreen(state: HistoryUiState, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Journey history", style = MaterialTheme.typography.headlineMedium)
        when (state) {
            HistoryUiState.Loading -> Text("Loading…")
            is HistoryUiState.Empty -> Text(state.message)
            is HistoryUiState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
            is HistoryUiState.Success -> state.placeIds.forEach { id ->
                Card(Modifier.fillMaxWidth()) { Text(id, Modifier.padding(18.dp)) }
            }
        }
    }
}
