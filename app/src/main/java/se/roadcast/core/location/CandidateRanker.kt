package se.roadcast.core.location

import se.roadcast.core.model.PlaceCandidate
import se.roadcast.core.model.PodcastPreferences
import se.roadcast.core.model.RankedCandidate
import se.roadcast.core.model.ScoreBreakdown
import se.roadcast.core.model.TravelState
import java.util.Locale
import kotlin.math.exp

data class RankingConfig(
    val corridorLengthMeters: Double = 20_000.0,
    val corridorWidthMeters: Double = 2_500.0,
    val proximityScaleMeters: Double = 8_000.0,
    val minimumFacts: Int = 2,
    val minimumSourceQuality: Double = 0.45,
    val importanceWeight: Double = 24.0,
    val routeAlignmentWeight: Double = 22.0,
    val proximityWeight: Double = 18.0,
    val interestWeight: Double = 12.0,
    val noveltyWeight: Double = 8.0,
    val sourceQualityWeight: Double = 16.0,
    val repetitionPenaltyWeight: Double = 35.0,
    val passedPlacePenaltyWeight: Double = 30.0,
)

object CandidateHistoryPolicy {
    fun eligible(candidates: List<PlaceCandidate>, playedPlaceIds: Set<String>): List<PlaceCandidate> =
        candidates.distinctBy { it.id }.filterNot { it.id in playedPlaceIds }
}

class CandidateRanker(private val config: RankingConfig = RankingConfig()) {
    fun rank(
        candidates: List<PlaceCandidate>,
        travelState: TravelState,
        preferences: PodcastPreferences,
        recentPlaceIds: Set<String>,
    ): List<RankedCandidate> {
        val corridorEnd = travelState.bearingDegrees?.let { bearing ->
            GeoMath.project(travelState.currentPosition, bearing, config.corridorLengthMeters)
        }
        return candidates
            .asSequence()
            .filter(::hasAdequateSources)
            .map { candidate ->
                score(candidate, travelState, corridorEnd, preferences, recentPlaceIds)
            }
            .sortedWith(compareByDescending<RankedCandidate> { it.score.total }.thenBy { it.candidate.id })
            .toList()
    }

    private fun hasAdequateSources(candidate: PlaceCandidate): Boolean {
        val knowledge = candidate.knowledgePackage ?: return false
        val knownSourceIds = knowledge.sources.mapTo(mutableSetOf()) { it.id }
        return knowledge.facts.size >= config.minimumFacts &&
            knowledge.sourceQualityScore >= config.minimumSourceQuality &&
            knowledge.facts.all { it.sourceIds.isNotEmpty() && knownSourceIds.containsAll(it.sourceIds) }
    }

    private fun score(
        candidate: PlaceCandidate,
        travelState: TravelState,
        corridorEnd: se.roadcast.core.model.GeoPoint?,
        preferences: PodcastPreferences,
        recentPlaceIds: Set<String>,
    ): RankedCandidate {
        val distance = GeoMath.haversineMeters(travelState.currentPosition, candidate.position)
        val bearingFromUser = GeoMath.initialBearingDegrees(travelState.currentPosition, candidate.position)
        val bearingDifference = travelState.bearingDegrees?.let {
            GeoMath.angularDifferenceDegrees(it, bearingFromUser)
        } ?: 0.0
        val corridorDistance = corridorEnd?.let {
            GeoMath.distanceToFiniteCorridorMeters(candidate.position, travelState.currentPosition, it)
        } ?: distance
        val ahead = travelState.bearingDegrees?.let {
            GeoMath.isAhead(travelState.currentPosition, it, candidate.position)
        } ?: true
        val routeFit = if (corridorEnd == null) {
            0.5
        } else {
            (1.0 - corridorDistance / config.corridorWidthMeters).coerceIn(0.0, 1.0)
        }
        val interest = (preferences.interests[candidate.category] ?: 0.5).coerceIn(0.0, 1.0)
        val repeated = candidate.id in recentPlaceIds
        val knowledge = checkNotNull(candidate.knowledgePackage)
        val breakdown = ScoreBreakdown(
            importance = candidate.importanceScore.coerceIn(0.0, 1.0) * config.importanceWeight,
            routeAlignment = routeFit * config.routeAlignmentWeight,
            proximity = exp(-distance / config.proximityScaleMeters) * config.proximityWeight,
            userInterest = interest * config.interestWeight,
            novelty = if (repeated) 0.0 else config.noveltyWeight,
            sourceQuality = knowledge.sourceQualityScore.coerceIn(0.0, 1.0) * config.sourceQualityWeight,
            repetitionPenalty = if (repeated) config.repetitionPenaltyWeight else 0.0,
            passedPlacePenalty = if (ahead) 0.0 else config.passedPlacePenaltyWeight,
        )
        val rationale = buildString {
            if (travelState.bearingDegrees == null) {
                append("Direction unavailable, neutral route alignment")
            } else {
                append(if (ahead) "Ahead" else "Behind")
                append(", ")
                append(
                    if (corridorDistance <= config.corridorWidthMeters) {
                        "inside route corridor"
                    } else {
                        "outside route corridor"
                    },
                )
            }
            append(", ${distance.toInt()} m away")
            if (repeated) append(", recently played")
            append("; score ${String.format(Locale.ROOT, "%.1f", breakdown.total)}")
        }
        return RankedCandidate(
            candidate = candidate.copy(distanceMeters = distance, bearingFromUser = bearingFromUser),
            corridorDistanceMeters = corridorDistance,
            bearingDifferenceDegrees = bearingDifference,
            etaSeconds = GeoMath.etaUntilPassingSeconds(
                origin = travelState.currentPosition,
                headingDegrees = travelState.bearingDegrees,
                speedMetersPerSecond = travelState.speedMetersPerSecond,
                point = candidate.position,
            ),
            score = breakdown,
            rationale = rationale,
        )
    }
}
