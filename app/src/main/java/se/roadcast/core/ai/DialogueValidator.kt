package se.roadcast.core.ai

import se.roadcast.core.model.HostId
import se.roadcast.core.model.PlaceKnowledgePackage

data class DialogueValidationResult(
    val dto: DialogueSegmentDto,
    val repairs: List<String> = emptyList(),
)

class DialogueValidationException(message: String) : IllegalArgumentException(message)

class DialogueValidator(
    private val maxLineCharacters: Int = 280,
    private val minDurationSeconds: Int = 60,
    private val maxDurationSeconds: Int = 180,
    private val maxConsecutiveSameHost: Int = 4,
) {
    fun validateOrRepair(
        dto: DialogueSegmentDto,
        knowledge: PlaceKnowledgePackage,
    ): DialogueValidationResult {
        val repairs = mutableListOf<String>()
        if (dto.dialogue.isEmpty()) {
            throw DialogueValidationException("Dialogue is empty")
        }

        val knownSources = knowledge.sources.mapTo(mutableSetOf()) { it.id }
        val knownFacts = knowledge.facts.mapTo(mutableSetOf()) { it.id }
        val lines = mutableListOf<DialogueLineDto>()
        var consecutive = 0
        var lastSpeaker: String? = null

        dto.dialogue.forEachIndexed { index, line ->
            val speaker = runCatching { HostId.valueOf(line.speaker) }.getOrNull()
                ?: throw DialogueValidationException("Unknown speaker: ${line.speaker}")
            val text = line.text.trim()
            if (text.isEmpty()) {
                repairs += "Dropped empty line ${line.id}"
                return@forEachIndexed
            }
            val clipped = if (text.length > maxLineCharacters) {
                repairs += "Truncated line ${line.id}"
                text.take(maxLineCharacters - 1).trimEnd() + "…"
            } else {
                text
            }
            val validSources = line.sourceIds.filter { it in knownSources }
            val validFacts = line.factIds.filter { it in knownFacts }
            if (validSources.size != line.sourceIds.size) {
                repairs += "Removed unsupported sources from ${line.id}"
            }
            if (validFacts.size != line.factIds.size) {
                repairs += "Removed unsupported facts from ${line.id}"
            }
            if (lastSpeaker == speaker.name) {
                consecutive += 1
            } else {
                consecutive = 1
                lastSpeaker = speaker.name
            }
            if (consecutive > maxConsecutiveSameHost) {
                throw DialogueValidationException(
                    "More than $maxConsecutiveSameHost consecutive lines from ${speaker.name}",
                )
            }
            lines += line.copy(
                id = line.id.ifBlank { "line-$index" },
                speaker = speaker.name,
                text = clipped,
                sourceIds = validSources,
                factIds = validFacts,
            )
        }
        if (lines.isEmpty()) {
            throw DialogueValidationException("Dialogue became empty after repair")
        }

        val estimated = dto.estimatedDurationSeconds
            .takeIf { it in minDurationSeconds..maxDurationSeconds }
            ?: estimateDurationSeconds(lines).also {
                repairs += "Clamped estimated duration to $it seconds"
            }.coerceIn(minDurationSeconds, maxDurationSeconds)

        val sourceIds = (dto.sourceIds + lines.flatMap { it.sourceIds })
            .distinct()
            .filter { it in knownSources }
        if (sourceIds.isEmpty() && knowledge.sources.isNotEmpty()) {
            repairs += "Attached package sources because segment had none"
        }

        return DialogueValidationResult(
            dto = dto.copy(
                dialogue = lines,
                estimatedDurationSeconds = estimated,
                sourceIds = sourceIds.ifEmpty { knowledge.sources.map { it.id } },
            ),
            repairs = repairs,
        )
    }

    fun estimateDurationSeconds(lines: List<DialogueLineDto>): Int {
        val words = lines.sumOf { line -> line.text.split(Regex("\\s+")).count { it.isNotBlank() } }
        return (words * 0.45).toInt().coerceIn(minDurationSeconds, maxDurationSeconds)
    }
}
