package se.roadcast.simulation

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import se.roadcast.core.ai.DialogueValidator
import se.roadcast.core.model.HostId
import se.roadcast.core.model.PlaceKnowledgePackage
import se.roadcast.core.model.PodcastPreferences
import se.roadcast.core.model.PreviousPodcastContext
import se.roadcast.core.model.SourceKind
import se.roadcast.core.model.SourceReference
import se.roadcast.core.model.StoryAngle
import se.roadcast.core.model.UncertainClaim
import se.roadcast.core.model.VerifiedFact

class FakeDialogueGeneratorTest {
    private val source = SourceReference(
        id = "museum",
        title = "Museum record",
        kind = SourceKind.MUSEUM,
    )
    private val knowledge = PlaceKnowledgePackage(
        placeId = "sample",
        overview = "A documented local landmark.",
        facts = listOf(
            VerifiedFact("fact-1", "It opened in 1900.", listOf(source.id), 0.98),
            VerifiedFact("fact-2", "The city owns the site.", listOf(source.id), 0.95),
        ),
        stories = listOf(
            StoryAngle("angle", "Sample Place", "A documented local landmark.", listOf("fact-1", "fact-2")),
        ),
        sources = listOf(source),
        uncertainClaims = listOf(
            UncertainClaim("u1", "Some visitors claim a secret tunnel.", "No official source confirms that."),
        ),
        sourceQualityScore = 0.9,
    )
    private val generator = FakeDialogueGenerator(DialogueValidator())

    @Test
    fun `preview uses both hosts and mentions place early`() = runBlocking {
        val segment = generator.generateSegment(knowledge, null, PodcastPreferences())

        assertTrue(segment.dialogue.size >= 5)
        assertEquals(setOf(HostId.HOST_A, HostId.HOST_B), segment.dialogue.map { it.speaker }.toSet())
        assertTrue(segment.dialogue.any { it.text.contains("Sample Place") })
        assertTrue(segment.estimatedDurationSeconds in 60..180)
    }

    @Test
    fun `all cited facts and sources come from knowledge package`() = runBlocking {
        val segment = generator.generateSegment(knowledge, null, PodcastPreferences())
        val supportedFactIds = knowledge.facts.mapTo(mutableSetOf()) { it.id }
        val supportedSourceIds = knowledge.sources.mapTo(mutableSetOf()) { it.id }

        segment.dialogue.forEach { line ->
            assertTrue(supportedFactIds.containsAll(line.factIds))
            assertTrue(supportedSourceIds.containsAll(line.sourceIds))
        }
        assertTrue(supportedSourceIds.containsAll(segment.sourceIds))
    }

    @Test
    fun `previous context creates a bridge without inventing facts`() = runBlocking {
        val segment = generator.generateSegment(
            knowledge,
            PreviousPodcastContext(recentPlaceIds = listOf("ullevi"), segmentSummaries = listOf("Ullevi")),
            PodcastPreferences(),
        )
        assertTrue(segment.dialogue.first().text.contains("ullevi"))
        assertTrue(segment.intro!!.contains("ullevi"))
    }
}
