package se.roadcast.core.ai

import org.junit.Assert.assertTrue
import org.junit.Test
import se.roadcast.core.model.PlaceKnowledgePackage
import se.roadcast.core.model.SourceKind
import se.roadcast.core.model.SourceReference
import se.roadcast.core.model.StoryAngle
import se.roadcast.core.model.VerifiedFact

class SuggestedQuestionsTest {
    @Test
    fun `builds grounded suggestions from facts and stories`() {
        val source = SourceReference("s1", "Source", kind = SourceKind.OFFICIAL)
        val knowledge = PlaceKnowledgePackage(
            placeId = "p1",
            overview = "Overview",
            facts = listOf(VerifiedFact("f1", "It opened in 1900.", listOf(source.id), 0.9)),
            stories = listOf(StoryAngle("a1", "Sample Place", "Premise", listOf("f1"))),
            sources = listOf(source),
            sourceQualityScore = 0.9,
        )
        val suggestions = SuggestedQuestions.from(knowledge)
        assertTrue(suggestions.any { it.contains("1900") })
        assertTrue(suggestions.any { it.contains("Sample Place") })
    }
}
