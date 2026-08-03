package se.roadcast.core.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import se.roadcast.core.model.GeoPoint
import se.roadcast.core.model.PlaceCandidate
import se.roadcast.core.model.PlaceCategory
import se.roadcast.core.model.PlaceKnowledgePackage
import se.roadcast.core.model.PodcastPreferences
import se.roadcast.core.model.SourceKind
import se.roadcast.core.model.SourceReference
import se.roadcast.core.model.StoryAngle
import se.roadcast.core.model.TravelState
import se.roadcast.core.model.VerifiedFact

class CandidateRankerTest {
    private val ranker = CandidateRanker()
    private val travel = TravelState(
        currentPosition = GeoPoint(57.70, 12.00),
        bearingDegrees = 0.0,
        speedMetersPerSecond = 15.0,
        accuracyMeters = 5.0,
        timestampEpochMillis = 0L,
    )

    @Test
    fun `famous place ahead outranks minor place behind`() {
        val ahead = candidate("ahead", GeoPoint(57.71, 12.00), importance = 0.95)
        val behind = candidate("behind", GeoPoint(57.69, 12.00), importance = 0.20)
        val ranked = ranker.rank(listOf(behind, ahead), travel, PodcastPreferences(), emptySet())
        assertEquals("ahead", ranked.first().candidate.id)
        assertTrue(ranked.first().score.passedPlacePenalty == 0.0)
        assertTrue(ranked.last().score.passedPlacePenalty > 0.0)
    }

    @Test
    fun `candidate inside travel corridor gets stronger alignment`() {
        val inside = candidate("inside", GeoPoint(57.72, 12.00))
        val outside = candidate("outside", GeoPoint(57.70, 12.04))
        val ranked = ranker.rank(listOf(outside, inside), travel, PodcastPreferences(), emptySet())
        val insideScore = ranked.first { it.candidate.id == "inside" }
        val outsideScore = ranked.first { it.candidate.id == "outside" }
        assertTrue(insideScore.score.routeAlignment > outsideScore.score.routeAlignment)
    }

    @Test
    fun `recent place receives novelty and repetition penalties`() {
        val fresh = candidate("fresh", GeoPoint(57.71, 12.00))
        val recent = candidate("recent", GeoPoint(57.71, 12.00))
        val ranked = ranker.rank(listOf(recent, fresh), travel, PodcastPreferences(), setOf("recent"))
        assertEquals("fresh", ranked.first().candidate.id)
        val recentScore = ranked.first { it.candidate.id == "recent" }.score
        assertEquals(0.0, recentScore.novelty, 0.0)
        assertTrue(recentScore.repetitionPenalty > 0.0)
    }

    @Test
    fun `interest contribution is bounded`() {
        val place = candidate("interest", GeoPoint(57.71, 12.00))
        val high = ranker.rank(
            listOf(place),
            travel,
            PodcastPreferences(interests = mapOf(PlaceCategory.HISTORY to 100.0)),
            emptySet(),
        ).single()
        val low = ranker.rank(
            listOf(place),
            travel,
            PodcastPreferences(interests = mapOf(PlaceCategory.HISTORY to -100.0)),
            emptySet(),
        ).single()
        assertEquals(12.0, high.score.userInterest, 0.001)
        assertEquals(0.0, low.score.userInterest, 0.001)
    }

    @Test
    fun `interest cannot override a clearly irrelevant passed place`() {
        val relevant = candidate("relevant", GeoPoint(57.71, 12.00), importance = 0.85)
        val preferredButPassed = candidate(
            "preferred-passed",
            GeoPoint(57.69, 12.00),
            category = PlaceCategory.ENGINEERING,
            importance = 0.25,
        )
        val ranked = ranker.rank(
            listOf(preferredButPassed, relevant),
            travel,
            PodcastPreferences(
                interests = mapOf(
                    PlaceCategory.ENGINEERING to 1.0,
                    PlaceCategory.HISTORY to 0.0,
                ),
            ),
            emptySet(),
        )
        assertEquals("relevant", ranked.first().candidate.id)
    }

    @Test
    fun `inadequate source material is excluded`() {
        val inadequate = candidate("weak", GeoPoint(57.71, 12.00)).copy(
            knowledgePackage = PlaceKnowledgePackage(
                "weak", "Unsupported", emptyList(), emptyList(), emptyList(), sourceQualityScore = 0.9,
            ),
        )
        assertTrue(ranker.rank(listOf(inadequate), travel, PodcastPreferences(), emptySet()).isEmpty())
    }

    @Test
    fun `history policy removes played IDs and duplicate candidates`() {
        val first = candidate("first", GeoPoint(57.71, 12.00))
        val duplicate = first.copy(name = "Duplicate payload")
        val played = candidate("played", GeoPoint(57.72, 12.00))
        val eligible = CandidateHistoryPolicy.eligible(listOf(first, duplicate, played), setOf("played"))
        assertEquals(listOf("first"), eligible.map { it.id })
    }

    @Test
    fun `missing bearing and speed use neutral route score without ETA`() {
        val unknownMotion = travel.copy(bearingDegrees = null, speedMetersPerSecond = null)
        val ranked = ranker.rank(
            listOf(candidate("unknown-motion", GeoPoint(57.71, 12.00))),
            unknownMotion,
            PodcastPreferences(),
            emptySet(),
        ).single()
        assertEquals(11.0, ranked.score.routeAlignment, 0.001)
        assertNull(ranked.etaSeconds)
    }

    private fun candidate(
        id: String,
        point: GeoPoint,
        category: PlaceCategory = PlaceCategory.HISTORY,
        importance: Double = 0.7,
    ): PlaceCandidate {
        val source = SourceReference("official", "Official record", kind = SourceKind.OFFICIAL)
        return PlaceCandidate(
            id = id,
            name = id,
            position = point,
            distanceMeters = 0.0,
            bearingFromUser = 0.0,
            category = category,
            importanceScore = importance,
            shortDescription = "A supported place.",
            knowledgePackage = PlaceKnowledgePackage(
                placeId = id,
                overview = "A supported place.",
                facts = listOf(
                    VerifiedFact("$id-1", "Fact one", listOf(source.id), 0.9),
                    VerifiedFact("$id-2", "Fact two", listOf(source.id), 0.9),
                ),
                stories = listOf(StoryAngle("$id-story", id, "A story", listOf("$id-1", "$id-2"))),
                sources = listOf(source),
                sourceQualityScore = 0.9,
            ),
        )
    }
}
