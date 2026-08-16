package se.roadcast.feature.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import se.roadcast.core.model.RankedCandidate

@Composable
fun DebugScreen(
    state: DebugUiState,
    onToggle: () -> Unit,
    onReset: () -> Unit,
    onJump: () -> Unit,
    onSpeed: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Simulation lab", style = MaterialTheme.typography.headlineMedium)
        when (state) {
            DebugUiState.Loading -> Text("Loading deterministic route…")
            is DebugUiState.Empty -> Text(state.message)
            is DebugUiState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
            is DebugUiState.Success -> {
                Text(
                    "Location source: ${state.modeLabel}",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (!state.simulationControlsEnabled) {
                    Text(
                        "Jump and speed controls apply only in simulation mode.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Route telemetry", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "%.5f, %.5f".format(
                                state.travel.currentPosition.latitude,
                                state.travel.currentPosition.longitude,
                            ),
                        )
                        Text(
                            "Bearing ${state.travel.bearingDegrees?.let { "%.0f°".format(it) } ?: "—"} · " +
                                "${state.travel.speedMetersPerSecond?.let { "%.1f m/s".format(it) } ?: "speed —"} · " +
                                "${state.travel.accuracyMeters?.let { "±%.0f m".format(it) } ?: "accuracy —"}",
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onToggle) { Text(if (state.running) "Pause" else "Start") }
                    OutlinedButton(onClick = onReset) { Text("Reset") }
                    OutlinedButton(
                        onClick = onJump,
                        enabled = state.simulationControlsEnabled,
                    ) { Text("Next place") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.5, 1.0, 2.0, 4.0).forEach { speed ->
                        FilterChip(
                            selected = state.speedMultiplier == speed,
                            onClick = { onSpeed(speed) },
                            enabled = state.simulationControlsEnabled,
                            label = { Text("${speed}×") },
                        )
                    }
                }
                Text(state.pipeline.state, style = MaterialTheme.typography.titleMedium)
                state.pipeline.selected?.let {
                    Text("Selected: ${it.candidate.name}", color = MaterialTheme.colorScheme.primary)
                    Text(it.rationale)
                }
                state.pipeline.rankedCandidates.forEachIndexed { index, candidate ->
                    CandidateScore(index + 1, candidate)
                }
            }
        }
    }
}

@Composable
private fun CandidateScore(rank: Int, ranked: RankedCandidate) {
    val score = ranked.score
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$rank. ${ranked.candidate.name}", style = MaterialTheme.typography.titleMedium)
            Text(ranked.rationale, style = MaterialTheme.typography.bodySmall)
            Text(
                "importance %.1f · route %.1f · proximity %.1f · interest %.1f".format(
                    score.importance,
                    score.routeAlignment,
                    score.proximity,
                    score.userInterest,
                ),
            )
            Text(
                "novelty %.1f · sources %.1f · repeat −%.1f · passed −%.1f".format(
                    score.novelty,
                    score.sourceQuality,
                    score.repetitionPenalty,
                    score.passedPlacePenalty,
                ),
            )
            Text("Total %.1f".format(score.total), style = MaterialTheme.typography.labelLarge)
        }
    }
}
