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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import se.roadcast.core.model.AskOverlayState
import se.roadcast.core.model.HostId
import se.roadcast.core.model.PlaceCandidate
import se.roadcast.core.model.PlaceCategory
import se.roadcast.core.model.PodcastSegment
import se.roadcast.core.model.PreviewPlaybackState

@Composable
fun PlayerScreen(
    state: PlayerUiState,
    onToggleJourney: () -> Unit,
    onPlayPause: () -> Unit,
    onReplay: () -> Unit,
    onSkip: () -> Unit,
    onAsk: () -> Unit,
    onTellMeMore: () -> Unit,
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
                .padding(20.dp),
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
                is PlayerUiState.Success -> SuccessPlayer(
                    state = state,
                    onToggleJourney = onToggleJourney,
                    onPlayPause = onPlayPause,
                    onReplay = onReplay,
                    onSkip = onSkip,
                    onAsk = onAsk,
                    onTellMeMore = onTellMeMore,
                    onOpenDebug = onOpenDebug,
                )
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
private fun SuccessPlayer(
    state: PlayerUiState.Success,
    onToggleJourney: () -> Unit,
    onPlayPause: () -> Unit,
    onReplay: () -> Unit,
    onSkip: () -> Unit,
    onAsk: () -> Unit,
    onTellMeMore: () -> Unit,
    onOpenDebug: () -> Unit,
) {
    val progress = playbackProgress(state.playback, state.segment)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StatusStrip(
            autoPlayEnabled = state.autoPlayEnabled,
            playback = state.playback,
            upcomingCount = state.alternatives,
            canTellMeMore = state.canTellMeMore,
            followUpCount = state.followUpCount,
        )
        Spacer(Modifier.height(14.dp))
        PlaceHero(place = state.selected)
        Spacer(Modifier.height(16.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 2.dp,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(state.selected.name, style = MaterialTheme.typography.headlineSmall)
                Text(
                    formatDistance(state.selected.distanceMeters) +
                        " · " +
                        state.selected.category.name.lowercase().replaceFirstChar { it.uppercase() },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.segment?.let { segment ->
                    Text(segment.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        buildString {
                            append("About ${segment.estimatedDurationSeconds}s · ${segment.dialogue.size} lines")
                            if (state.followUpCount > 0) append(" · deeper pass")
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp)),
                    )
                    Text(
                        progressLabel(state.playback, segment),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } ?: Text(
                    state.selected.shortDescription,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        PlaybackDetails(
            playback = state.playback,
            lastAnswer = state.lastAnswer,
            canTellMeMore = state.canTellMeMore,
        )
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
            Button(
                onClick = onTellMeMore,
                enabled = state.canTellMeMore &&
                    state.segment != null &&
                    state.ask == null &&
                    state.playback !is PreviewPlaybackState.Initializing &&
                    state.playback !is PreviewPlaybackState.Answering,
            ) {
                Text("Tell me more")
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onOpenDebug) { Text("Why this place?") }
    }
}

@Composable
private fun StatusStrip(
    autoPlayEnabled: Boolean,
    playback: PreviewPlaybackState,
    upcomingCount: Int,
    canTellMeMore: Boolean,
    followUpCount: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            statusLabel(autoPlayEnabled, playback, canTellMeMore, followUpCount),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(
                if (upcomingCount == 1) "1 upcoming" else "$upcomingCount upcoming",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun PlaceHero(place: PlaceCandidate) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(24.dp)),
    ) {
        CategoryCover(place.category, place.name)
        place.imageUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = place.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun CategoryCover(category: PlaceCategory, name: String) {
    val colors = categoryColors(category)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.take(1).uppercase(),
            style = MaterialTheme.typography.displayLarge,
            color = Color.White.copy(alpha = 0.85f),
        )
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
private fun PlaybackDetails(
    playback: PreviewPlaybackState,
    lastAnswer: String?,
    canTellMeMore: Boolean,
) {
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
        PreviewPlaybackState.Completed -> Text(
            if (canTellMeMore) {
                "Story finished · Tell me more or skip to continue"
            } else {
                "Story finished · advancing when ready"
            },
        )
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
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TranscriptCard(host: HostId, text: String, status: String) {
    val hostName = if (host == HostId.HOST_A) "Liv" else "Nils"
    val hostRole = if (host == HostId.HOST_A) "storyteller" else "specialist"
    val avatarColor = if (host == HostId.HOST_A) {
        Color(0xFF2F6F6A)
    } else {
        Color(0xFF6B4E3D)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(avatarColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    hostName.take(1),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "$status · $hostName, $hostRole",
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(text, style = MaterialTheme.typography.bodyLarge)
            }
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

private fun statusLabel(
    autoPlayEnabled: Boolean,
    playback: PreviewPlaybackState,
    canTellMeMore: Boolean,
    followUpCount: Int,
): String =
    when (playback) {
        is PreviewPlaybackState.Playing -> if (followUpCount > 0) "DEEPER PASS" else "NOW PLAYING"
        is PreviewPlaybackState.Paused -> "PAUSED"
        is PreviewPlaybackState.Answering -> "ASK ANSWER"
        PreviewPlaybackState.Initializing -> "PREPARING"
        PreviewPlaybackState.Completed -> if (canTellMeMore) "MORE AVAILABLE" else "FINISHED"
        is PreviewPlaybackState.Error -> "NEEDS ATTENTION"
        PreviewPlaybackState.Idle -> if (autoPlayEnabled) "UP NEXT" else "READY"
    }

private fun playbackProgress(playback: PreviewPlaybackState, segment: PodcastSegment?): Float {
    val total = segment?.dialogue?.size?.takeIf { it > 0 } ?: return 0f
    return when (playback) {
        is PreviewPlaybackState.Playing -> (playback.lineIndex + 1f) / total
        is PreviewPlaybackState.Paused -> (playback.lineIndex + 1f) / total
        is PreviewPlaybackState.Answering -> {
            val index = playback.resumeFromLineIndex.coerceIn(0, total)
            index.toFloat() / total
        }
        PreviewPlaybackState.Completed -> 1f
        else -> 0f
    }.coerceIn(0f, 1f)
}

private fun progressLabel(playback: PreviewPlaybackState, segment: PodcastSegment): String {
    val total = segment.dialogue.size
    return when (playback) {
        is PreviewPlaybackState.Playing -> "Line ${playback.lineIndex + 1} of $total"
        is PreviewPlaybackState.Paused -> "Line ${playback.lineIndex + 1} of $total · paused"
        is PreviewPlaybackState.Answering -> "Answering · resumes at line ${playback.resumeFromLineIndex + 1}"
        PreviewPlaybackState.Completed -> "Complete"
        PreviewPlaybackState.Initializing -> "Building dialogue…"
        else -> "$total dialogue lines"
    }
}

private fun formatDistance(meters: Double): String = when {
    meters < 1000 -> "${meters.toInt()} m ahead"
    else -> String.format("%.1f km ahead", meters / 1000.0)
}

private fun categoryColors(category: PlaceCategory): List<Color> = when (category) {
    PlaceCategory.HISTORY -> listOf(Color(0xFF5C4033), Color(0xFFA67C52))
    PlaceCategory.ARCHITECTURE -> listOf(Color(0xFF4A5568), Color(0xFF718096))
    PlaceCategory.NATURE -> listOf(Color(0xFF2F5D50), Color(0xFF6B8F71))
    PlaceCategory.ENGINEERING -> listOf(Color(0xFF37474F), Color(0xFF78909C))
    PlaceCategory.CULTURE -> listOf(Color(0xFF3D4A6B), Color(0xFF7A6B8A))
    PlaceCategory.INDUSTRY -> listOf(Color(0xFF455A64), Color(0xFF90A4AE))
    PlaceCategory.PERSON -> listOf(Color(0xFF5D4037), Color(0xFFA1887F))
    PlaceCategory.LEGEND -> listOf(Color(0xFF4A3F6B), Color(0xFF8E7DB0))
    PlaceCategory.OTHER -> listOf(Color(0xFF546E7A), Color(0xFF90A4AE))
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
