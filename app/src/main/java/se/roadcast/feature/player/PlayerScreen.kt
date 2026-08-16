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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import se.roadcast.core.model.AskOverlayState
import se.roadcast.core.model.HostId
import se.roadcast.core.model.PreviewPlaybackState

@Composable
fun PlayerScreen(
    state: PlayerUiState,
    onToggleJourney: () -> Unit,
    onPlayPause: () -> Unit,
    onReplay: () -> Unit,
    onSkip: () -> Unit,
    onAsk: () -> Unit,
    onAskQuestionChange: (String) -> Unit,
    onSuggestedQuestion: (String) -> Unit,
    onSubmitAsk: () -> Unit,
    onCancelAsk: () -> Unit,
    onOpenDebug: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                PlayerUiState.Loading -> CircularProgressIndicator()
                is PlayerUiState.Empty -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    MessageCard("The road is quiet", state.message)
                    Button(onClick = onToggleJourney) {
                        Text(if (state.simulationRunning) "Pause journey" else "Start journey")
                    }
                }
                is PlayerUiState.Error -> MessageCard("Something interrupted the journey", state.message)
                is PlayerUiState.Success -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (state.autoPlayEnabled) "NOW PLAYING" else "UP NEXT",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        tonalElevation = 3.dp,
                    ) {
                        Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(state.selected.name, style = MaterialTheme.typography.headlineMedium)
                            state.segment?.let {
                                Text(it.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "About ${it.estimatedDurationSeconds}s · ${it.dialogue.size} lines",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } ?: Text(state.selected.shortDescription, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${state.selected.category.name.lowercase().replaceFirstChar { it.uppercase() }} · " +
                                    "${state.alternatives} more ranked stories",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    PlaybackDetails(state.playback, state.lastAnswer)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onToggleJourney) {
                            Text(if (state.simulationRunning) "Pause journey" else "Start journey")
                        }
                        Button(
                            onClick = onPlayPause,
                            enabled = state.ask == null && state.playback !is PreviewPlaybackState.Initializing,
                        ) {
                            Text(playbackAction(state.playback))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = onReplay,
                            enabled = state.ask == null &&
                                (state.segment != null || state.playback !is PreviewPlaybackState.Initializing),
                        ) {
                            Text("Replay")
                        }
                        OutlinedButton(onClick = onSkip, enabled = state.ask == null) { Text("Skip place") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = onAsk,
                            enabled = state.segment != null &&
                                state.ask == null &&
                                state.playback !is PreviewPlaybackState.Initializing &&
                                state.playback !is PreviewPlaybackState.Answering,
                        ) {
                            Text("Ask")
                        }
                        OutlinedButton(onClick = onOpenDebug) { Text("Why this place?") }
                    }
                }
            }
        }

        val success = state as? PlayerUiState.Success
        val ask = success?.ask
        if (success != null && ask != null) {
            AskOverlay(
                placeName = success.selected.name,
                state = ask,
                onQuestionChange = onAskQuestionChange,
                onSuggested = onSuggestedQuestion,
                onSend = onSubmitAsk,
                onCancel = onCancelAsk,
            )
        }
    }
}

@Composable
private fun AskOverlay(
    placeName: String,
    state: AskOverlayState,
    onQuestionChange: (String) -> Unit,
    onSuggested: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
        ) {
            Column(
                Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Ask about $placeName", style = MaterialTheme.typography.headlineSmall)
                when (state) {
                    is AskOverlayState.Editing -> {
                        OutlinedTextField(
                            value = state.question,
                            onValueChange = onQuestionChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Your question") },
                            minLines = 2,
                        )
                        state.errorMessage?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                        Text("Suggested", style = MaterialTheme.typography.labelLarge)
                        state.suggestedQuestions.forEach { suggestion ->
                            TextButton(onClick = { onSuggested(suggestion) }) {
                                Text(suggestion)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = onSend) { Text("Send") }
                            OutlinedButton(onClick = onCancel) { Text("Cancel") }
                        }
                    }
                    is AskOverlayState.Submitting -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator()
                            Text("Finding a grounded answer…")
                        }
                        Text(state.question, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = onCancel, enabled = false) { Text("Cancel") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaybackDetails(playback: PreviewPlaybackState, lastAnswer: String?) {
    when (playback) {
        PreviewPlaybackState.Idle -> Text(
            "Start the journey to autoplay the next story",
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
        is PreviewPlaybackState.Answering -> TranscriptCard(
            host = playback.speaker,
            text = playback.answerText,
            status = "Answering",
        )
        PreviewPlaybackState.Completed -> Text("Story finished · advancing when ready")
        is PreviewPlaybackState.Error -> Text(
            playback.message,
            color = MaterialTheme.colorScheme.error,
        )
    }
    if (lastAnswer != null && playback !is PreviewPlaybackState.Answering) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Last answer: $lastAnswer",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    is PreviewPlaybackState.Answering -> "Answering"
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
