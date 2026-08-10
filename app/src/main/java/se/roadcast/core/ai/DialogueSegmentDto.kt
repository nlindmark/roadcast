package se.roadcast.core.ai

import kotlinx.serialization.Serializable
import se.roadcast.core.model.DialogueLine
import se.roadcast.core.model.HostId
import se.roadcast.core.model.PodcastSegment

@Serializable
data class DialogueSegmentDto(
    val id: String,
    val placeId: String,
    val title: String,
    val intro: String? = null,
    val dialogue: List<DialogueLineDto>,
    val estimatedDurationSeconds: Int,
    val sourceIds: List<String> = emptyList(),
    val storyAngleId: String? = null,
)

@Serializable
data class DialogueLineDto(
    val id: String,
    val speaker: String,
    val text: String,
    val interruptibleAfter: Boolean = true,
    val sourceIds: List<String> = emptyList(),
    val factIds: List<String> = emptyList(),
)

fun DialogueSegmentDto.toDomain(generatedAtEpochMillis: Long): PodcastSegment =
    PodcastSegment(
        id = id,
        placeId = placeId,
        title = title,
        intro = intro,
        dialogue = dialogue.map { it.toDomain() },
        estimatedDurationSeconds = estimatedDurationSeconds,
        generatedAtEpochMillis = generatedAtEpochMillis,
        sourceIds = sourceIds,
        storyAngleId = storyAngleId,
    )

fun DialogueLineDto.toDomain(): DialogueLine =
    DialogueLine(
        id = id,
        speaker = HostId.valueOf(speaker),
        text = text,
        interruptibleAfter = interruptibleAfter,
        sourceIds = sourceIds,
        factIds = factIds,
    )
