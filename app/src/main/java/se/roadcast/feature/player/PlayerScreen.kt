package se.roadcast.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import se.roadcast.core.model.HostId
import se.roadcast.core.model.PreviewPlaybackState

@Composable
fun PlayerScreen(
    state: PlayerUiState,
    onPlayPause: () -> Unit,
    onReplay: () -> Unit,
    onOpenDebug: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        when (state) {
            PlayerUiState.Loading -> CircularProgressIndicator()
            is PlayerUiState.Empty -> MessageCard("The road is quiet", state.message)
            is PlayerUiState.Error -> MessageCard("Something interrupted the journey", state.message)
            is PlayerUiState.Success -> Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("UP NEXT", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    tonalElevation = 3.dp,
                ) {
                    Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(state.selected.name, style = MaterialTheme.typography.headlineMedium)
                        Text(state.selected.shortDescription, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${state.selected.category.name.lowercase().replaceFirstChar { it.uppercase() }} · " +
                                "${state.alternatives} more ranked stories",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                PlaybackDetails(state.playback)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onPlayPause,
                        enabled = state.playback !is PreviewPlaybackState.Initializing,
                    ) {
                        Text(playbackAction(state.playback))
                    }
                    OutlinedButton(
                        onClick = onReplay,
                        enabled = state.segment != null || state.playback !is PreviewPlaybackState.Initializing,
                    ) {
                        Text("Replay")
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenDebug) { Text("Why this place?") }
            }
        }
    }
}

@Composable
private fun PlaybackDetails(playback: PreviewPlaybackState) {
    when (playback) {
        PreviewPlaybackState.Idle -> Text(
            "Ready for a two-host preview",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PreviewPlaybackState.Initializing -> Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator()
            Text("Preparing voices…")
        }
        is PreviewPlaybackState.Playing -> TranscriptCard(
            host = playback.line.speaker,
            text = playback.line.text,
            status = "Playing",
        )
        is PreviewPlaybackState.Paused -> TranscriptCard(
            host = playback.line.speaker,
            text = playback.line.text,
            status = "Paused",
        )
        PreviewPlaybackState.Completed -> Text("Preview finished")
        is PreviewPlaybackState.Error -> Text(
            playback.message,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun TranscriptCard(host: HostId, text: String, status: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "$status · ${if (host == HostId.HOST_A) "Liv, storyteller" else "Nils, specialist"}",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun playbackAction(playback: PreviewPlaybackState): String = when (playback) {
    is PreviewPlaybackState.Playing -> "Pause"
    is PreviewPlaybackState.Paused -> "Resume"
    PreviewPlaybackState.Completed -> "Play again"
    PreviewPlaybackState.Initializing -> "Preparing"
    PreviewPlaybackState.Idle, is PreviewPlaybackState.Error -> "Play preview"
}

@Composable
private fun MessageCard(title: String, message: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
            .padding(24.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(message)
    }
}
