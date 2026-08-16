package se.roadcast.core.model

import kotlinx.serialization.Serializable

@Serializable
data class GeoPoint(val latitude: Double, val longitude: Double) {
    init {
        require(latitude in -90.0..90.0)
        require(longitude in -180.0..180.0)
    }
}

@Serializable
data class TravelState(
    val currentPosition: GeoPoint,
    val bearingDegrees: Double? = null,
    val speedMetersPerSecond: Double? = null,
    val accuracyMeters: Double? = null,
    val timestampEpochMillis: Long,
)

@Serializable
enum class PlaceCategory {
    HISTORY, ARCHITECTURE, NATURE, ENGINEERING, CULTURE, INDUSTRY, PERSON, LEGEND, OTHER
}

@Serializable
enum class SourceKind { OFFICIAL, MUSEUM, ENCYCLOPEDIA, EDITORIAL, COMMUNITY }

@Serializable
data class SourceReference(
    val id: String,
    val title: String,
    val url: String? = null,
    val kind: SourceKind,
    val retrievedAtEpochMillis: Long? = null,
)

@Serializable
data class VerifiedFact(
    val id: String,
    val statement: String,
    val sourceIds: List<String>,
    val confidence: Double,
)

@Serializable
data class StoryAngle(
    val id: String,
    val title: String,
    val premise: String,
    val relatedFactIds: List<String>,
)

@Serializable
data class UncertainClaim(
    val id: String,
    val claim: String,
    val reason: String,
    val sourceIds: List<String> = emptyList(),
)

@Serializable
data class PlaceKnowledgePackage(
    val placeId: String,
    val overview: String,
    val facts: List<VerifiedFact>,
    val stories: List<StoryAngle>,
    val sources: List<SourceReference>,
    val pronunciation: String? = null,
    val uncertainClaims: List<UncertainClaim> = emptyList(),
    val sourceQualityScore: Double,
)

@Serializable
data class PlaceCandidate(
    val id: String,
    val name: String,
    val position: GeoPoint,
    val distanceMeters: Double,
    val bearingFromUser: Double,
    val category: PlaceCategory,
    val importanceScore: Double,
    val imageUrl: String? = null,
    val shortDescription: String,
    val knowledgePackage: PlaceKnowledgePackage? = null,
)

@Serializable
enum class HostId { HOST_A, HOST_B }

@Serializable
data class DialogueLine(
    val id: String,
    val speaker: HostId,
    val text: String,
    val interruptibleAfter: Boolean,
    val sourceIds: List<String> = emptyList(),
    val factIds: List<String> = emptyList(),
)

@Serializable
data class PodcastSegment(
    val id: String,
    val placeId: String,
    val title: String,
    val intro: String? = null,
    val dialogue: List<DialogueLine>,
    val estimatedDurationSeconds: Int,
    val generatedAtEpochMillis: Long,
    val sourceIds: List<String> = emptyList(),
    val storyAngleId: String? = null,
)

sealed interface PreviewPlaybackState {
    @Serializable data object Idle : PreviewPlaybackState
    @Serializable data object Initializing : PreviewPlaybackState
    @Serializable
    data class Playing(
        val lineIndex: Int,
        val line: DialogueLine,
    ) : PreviewPlaybackState

    @Serializable
    data class Paused(
        val lineIndex: Int,
        val line: DialogueLine,
    ) : PreviewPlaybackState

    @Serializable
    data class Answering(
        val question: String,
        val answerText: String,
        val speaker: HostId,
        val resumeFromLineIndex: Int,
    ) : PreviewPlaybackState

    @Serializable data object Completed : PreviewPlaybackState
    @Serializable data class Error(val message: String) : PreviewPlaybackState
}

@Serializable
sealed interface AskOverlayState {
    @Serializable
    data class Editing(
        val question: String = "",
        val suggestedQuestions: List<String> = emptyList(),
        val errorMessage: String? = null,
    ) : AskOverlayState

    @Serializable
    data class Submitting(
        val question: String,
        val suggestedQuestions: List<String> = emptyList(),
    ) : AskOverlayState
}

@Serializable
data class GeneratedSpeech(
    val uri: String,
    val durationMillis: Long,
    val text: String,
    val speaker: HostId,
)

@Serializable
data class GeneratedAudioLine(
    val dialogueLineId: String,
    val speech: GeneratedSpeech,
)

@Serializable
enum class QueueItemStatus { DISCOVERED, PREPARING, READY, PLAYING, PLAYED, SKIPPED, FAILED }

@Serializable
data class PodcastQueueItem(
    val id: String,
    val place: PlaceCandidate,
    val segment: PodcastSegment? = null,
    val audioLines: List<GeneratedAudioLine> = emptyList(),
    val status: QueueItemStatus = QueueItemStatus.DISCOVERED,
)

@Serializable
data class PodcastPreferences(
    val interests: Map<PlaceCategory, Double> = emptyMap(),
    val preferredHosts: List<HostId> = listOf(HostId.HOST_A, HostId.HOST_B),
    val targetSegmentMinutes: Int = 5,
    val autoplay: Boolean = true,
)

@Serializable
data class PreviousPodcastContext(
    val recentPlaceIds: List<String> = emptyList(),
    val segmentSummaries: List<String> = emptyList(),
    val recentDialogue: List<DialogueLine> = emptyList(),
)

@Serializable
data class ConversationalAnswer(
    val text: String,
    val sourceIds: List<String>,
    val factIds: List<String>,
    val confidence: Double,
)

@Serializable
sealed interface SpeechRecognitionState {
    @Serializable data object Idle : SpeechRecognitionState
    @Serializable data object Listening : SpeechRecognitionState
    @Serializable data object Processing : SpeechRecognitionState
    @Serializable data class Error(val message: String) : SpeechRecognitionState
}

@Serializable
sealed interface PodcastOrchestratorState {
    @Serializable data object Idle : PodcastOrchestratorState
    @Serializable data object Locating : PodcastOrchestratorState
    @Serializable data class Discovering(val travelState: TravelState) : PodcastOrchestratorState
    @Serializable data class Preparing(val place: PlaceCandidate) : PodcastOrchestratorState
    @Serializable data class Ready(val queue: List<PodcastQueueItem>) : PodcastOrchestratorState
    @Serializable
    data class Playing(
        val currentItem: PodcastQueueItem,
        val upcomingItems: List<PodcastQueueItem>,
    ) : PodcastOrchestratorState

    @Serializable data class AnsweringQuestion(val question: String) : PodcastOrchestratorState
    @Serializable data class Error(val message: String, val recoverable: Boolean) : PodcastOrchestratorState
}

@Serializable
sealed interface RoadcastError {
    val message: String

    @Serializable data class LocationUnavailable(override val message: String) : RoadcastError
    @Serializable data class ProviderFailure(val provider: String, override val message: String) : RoadcastError
    @Serializable data class InadequateSources(val placeId: String, override val message: String) : RoadcastError
    @Serializable data class AudioFailure(override val message: String) : RoadcastError
}

@Serializable
data class ScoreBreakdown(
    val importance: Double,
    val routeAlignment: Double,
    val proximity: Double,
    val userInterest: Double,
    val novelty: Double,
    val sourceQuality: Double,
    val repetitionPenalty: Double,
    val passedPlacePenalty: Double,
) {
    val total: Double
        get() = importance + routeAlignment + proximity + userInterest + novelty + sourceQuality -
            repetitionPenalty - passedPlacePenalty
}

@Serializable
data class RankedCandidate(
    val candidate: PlaceCandidate,
    val corridorDistanceMeters: Double,
    val bearingDifferenceDegrees: Double,
    val etaSeconds: Double?,
    val score: ScoreBreakdown,
    val rationale: String,
)
