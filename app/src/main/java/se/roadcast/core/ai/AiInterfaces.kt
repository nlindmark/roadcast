package se.roadcast.core.ai

import kotlinx.coroutines.flow.StateFlow
import se.roadcast.core.model.ConversationalAnswer
import se.roadcast.core.model.DialogueLine
import se.roadcast.core.model.PlaceKnowledgePackage
import se.roadcast.core.model.PodcastPreferences
import se.roadcast.core.model.PodcastSegment
import se.roadcast.core.model.PreviousPodcastContext
import se.roadcast.core.model.SpeechRecognitionState

interface DialogueGenerator {
    suspend fun generateSegment(
        knowledge: PlaceKnowledgePackage,
        previousContext: PreviousPodcastContext?,
        preferences: PodcastPreferences,
    ): PodcastSegment

    suspend fun answerQuestion(
        question: String,
        currentKnowledge: PlaceKnowledgePackage,
        recentDialogue: List<DialogueLine>,
        preferences: PodcastPreferences,
    ): ConversationalAnswer
}

interface UserSpeechRecognizer {
    val state: StateFlow<SpeechRecognitionState>
    suspend fun startListening()
    suspend fun stopListening()
    suspend fun cancel()
}
