package se.roadcast.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import se.roadcast.core.model.HostId
import se.roadcast.core.model.PlaceKnowledgePackage
import se.roadcast.core.model.SourceKind
import se.roadcast.core.model.SourceReference
import se.roadcast.core.model.VerifiedFact

class DialogueValidatorTest {
    private val source = SourceReference("official", "Official", kind = SourceKind.OFFICIAL)
    private val knowledge = PlaceKnowledgePackage(
        placeId = "place",
        overview = "Overview",
        facts = listOf(
            VerifiedFact("f1", "Fact one", listOf(source.id), 0.9),
            VerifiedFact("f2", "Fact two", listOf(source.id), 0.9),
        ),
        stories = emptyList(),
        sources = listOf(source),
        sourceQualityScore = 0.9,
    )
    private val validator = DialogueValidator()

    @Test
    fun `rejects empty dialogue`() {
        val dto = DialogueSegmentDto(
            id = "s1",
            placeId = "place",
            title = "Title",
            dialogue = emptyList(),
            estimatedDurationSeconds = 90,
        )
        assertThrows(DialogueValidationException::class.java) {
            validator.validateOrRepair(dto, knowledge)
        }
    }

    @Test
    fun `rejects unknown speakers`() {
        val dto = sampleDto(
            DialogueLineDto("1", "HOST_Z", "Hello", true),
        )
        assertThrows(DialogueValidationException::class.java) {
            validator.validateOrRepair(dto, knowledge)
        }
    }

    @Test
    fun `repairs unsupported facts sources and duration`() {
        val longText = "x".repeat(320)
        val dto = sampleDto(
            DialogueLineDto("1", HostId.HOST_A.name, longText, true, listOf("missing"), listOf("ghost")),
            DialogueLineDto("2", HostId.HOST_B.name, "A short line", true, listOf(source.id), listOf("f1")),
        ).copy(estimatedDurationSeconds = 12, sourceIds = listOf("missing"))

        val result = validator.validateOrRepair(dto, knowledge)
        assertEquals(280, result.dto.dialogue.first().text.length)
        assertTrue(result.dto.dialogue.first().factIds.isEmpty())
        assertTrue(result.dto.dialogue.first().sourceIds.isEmpty())
        assertTrue(result.dto.estimatedDurationSeconds in 60..180)
        assertEquals(listOf(source.id), result.dto.sourceIds)
        assertTrue(result.repairs.isNotEmpty())
    }

    @Test
    fun `rejects more than four consecutive lines from same host`() {
        val dto = sampleDto(
            DialogueLineDto("1", HostId.HOST_A.name, "One", true),
            DialogueLineDto("2", HostId.HOST_A.name, "Two", true),
            DialogueLineDto("3", HostId.HOST_A.name, "Three", true),
            DialogueLineDto("4", HostId.HOST_A.name, "Four", true),
            DialogueLineDto("5", HostId.HOST_A.name, "Five", true),
        )
        assertThrows(DialogueValidationException::class.java) {
            validator.validateOrRepair(dto, knowledge)
        }
    }

    private fun sampleDto(vararg lines: DialogueLineDto) = DialogueSegmentDto(
        id = "s1",
        placeId = "place",
        title = "Title",
        dialogue = lines.toList(),
        estimatedDurationSeconds = 90,
        sourceIds = listOf(source.id),
    )
}
