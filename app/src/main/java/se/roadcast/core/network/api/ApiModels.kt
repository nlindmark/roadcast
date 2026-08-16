package se.roadcast.core.network.api

import kotlinx.serialization.Serializable
import se.roadcast.core.model.ConversationalAnswer
import se.roadcast.core.model.DialogueLine
import se.roadcast.core.model.GeneratedSpeech
import se.roadcast.core.model.HostId
import se.roadcast.core.model.PlaceCandidate
import se.roadcast.core.model.PlaceKnowledgePackage
import se.roadcast.core.model.PodcastPreferences
import se.roadcast.core.model.PreviousPodcastContext
import se.roadcast.core.model.TravelState
import se.roadcast.core.ai.DialogueSegmentDto

@Serializable
data class DiscoverPlacesRequest(
    val travelState: TravelState,
    val horizonMeters: Double = 20_000.0,
    val corridorWidthMeters: Double = 2_500.0,
)

@Serializable
data class DiscoverPlacesResponse(
    val places: List<PlaceCandidate>,
)

@Serializable
data class BuildKnowledgeRequest(
    val place: PlaceCandidate,
)

@Serializable
data class BuildKnowledgeResponse(
    val knowledge: PlaceKnowledgePackage,
)

@Serializable
data class GeneratePodcastRequest(
    val knowledge: PlaceKnowledgePackage,
    val previousContext: PreviousPodcastContext? = null,
    val preferences: PodcastPreferences = PodcastPreferences(),
)

@Serializable
data class GeneratePodcastResponse(
    val segment: DialogueSegmentDto,
)

@Serializable
data class AnswerQuestionRequest(
    val question: String,
    val knowledge: PlaceKnowledgePackage,
    val recentDialogue: List<DialogueLine> = emptyList(),
    val preferences: PodcastPreferences = PodcastPreferences(),
)

@Serializable
data class AnswerQuestionResponse(
    val answer: ConversationalAnswer,
)

@Serializable
data class GenerateSpeechRequest(
    val text: String,
    val speaker: HostId,
)

@Serializable
data class GenerateSpeechResponse(
    val speech: GeneratedSpeech,
)
