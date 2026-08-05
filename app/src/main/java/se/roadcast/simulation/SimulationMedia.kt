package se.roadcast.simulation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import se.roadcast.core.ai.DialogueGenerator
import se.roadcast.core.ai.UserSpeechRecognizer
import se.roadcast.core.audio.PodcastOrchestrator
import se.roadcast.core.audio.SpeechGenerator
import se.roadcast.core.model.ConversationalAnswer
import se.roadcast.core.model.DialogueLine
import se.roadcast.core.model.GeneratedAudioLine
import se.roadcast.core.model.GeneratedSpeech
import se.roadcast.core.model.HostId
import se.roadcast.core.model.PlaceKnowledgePackage
import se.roadcast.core.model.PodcastOrchestratorState
import se.roadcast.core.model.PodcastPreferences
import se.roadcast.core.model.PodcastQueueItem
import se.roadcast.core.model.PodcastSegment
import se.roadcast.core.model.PreviousPodcastContext
import se.roadcast.core.model.QueueItemStatus
import se.roadcast.core.model.SpeechRecognitionState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeDialogueGenerator @Inject constructor() : DialogueGenerator {
    override suspend fun generateSegment(
        knowledge: PlaceKnowledgePackage,
        previousContext: PreviousPodcastContext?,
        preferences: PodcastPreferences,
    ): PodcastSegment {
        val angle = knowledge.stories.firstOrNull()
        val firstFact = knowledge.facts.firstOrNull()
        val secondFact = knowledge.facts.drop(1).firstOrNull()
        val placeName = angle?.title ?: "this place"
        return PodcastSegment(
            id = "segment-${knowledge.placeId}-${previousContext?.segmentSummaries?.size ?: 0}",
            placeId = knowledge.placeId,
            title = angle?.title ?: "Roadcast story",
            storyAngleId = angle?.id,
            lines = listOf(
                DialogueLine(
                    id = "${knowledge.placeId}-host-a-intro",
                    speaker = HostId.HOST_A,
                    text = "Coming up is $placeName. ${angle?.premise ?: knowledge.overview}",
                    interruptibleAfter = true,
                    sourceIds = knowledge.sources.map { it.id },
                    factIds = knowledge.facts.map { it.id },
                ),
                DialogueLine(
                    id = "${knowledge.placeId}-host-b-detail",
                    speaker = HostId.HOST_B,
                    text = firstFact?.let { "One detail we can establish is that ${it.statement}" }
                        ?: "The local fixture does not contain a verified detail beyond that summary.",
                    interruptibleAfter = true,
                    sourceIds = firstFact?.sourceIds.orEmpty(),
                    factIds = listOfNotNull(firstFact?.id),
                ),
                DialogueLine(
                    id = "${knowledge.placeId}-host-a-follow-up",
                    speaker = HostId.HOST_A,
                    text = "And what is the other detail worth carrying into the story?",
                    interruptibleAfter = true,
                ),
                DialogueLine(
                    id = "${knowledge.placeId}-host-b-close",
                    speaker = HostId.HOST_B,
                    text = secondFact?.statement
                        ?: "There is no second verified fact in this local package, so we will leave it there.",
                    interruptibleAfter = true,
                    sourceIds = secondFact?.sourceIds.orEmpty(),
                    factIds = listOfNotNull(secondFact?.id),
                ),
            ),
        )
    }

    override suspend fun answerQuestion(
        question: String,
        currentKnowledge: PlaceKnowledgePackage,
        recentDialogue: List<DialogueLine>,
        preferences: PodcastPreferences,
    ): ConversationalAnswer {
        val fact = currentKnowledge.facts.firstOrNull()
        return ConversationalAnswer(
            text = fact?.statement ?: "The simulation has no verified answer for “$question”.",
            sourceIds = fact?.sourceIds.orEmpty(),
            factIds = listOfNotNull(fact?.id),
            confidence = fact?.confidence ?: 0.0,
        )
    }
}

@Singleton
class FakeSpeechGenerator @Inject constructor() : SpeechGenerator {
    override suspend fun synthesize(text: String, speaker: HostId): GeneratedSpeech = GeneratedSpeech(
        uri = "simulation://speech/${speaker.name.lowercase()}/${text.hashCode()}",
        durationMillis = (text.length * 55L).coerceAtLeast(1_000L),
        text = text,
        speaker = speaker,
    )
}

@Singleton
class FakeSpeechRecognizer @Inject constructor() : UserSpeechRecognizer {
    private val _state = MutableStateFlow<SpeechRecognitionState>(SpeechRecognitionState.Idle)
    override val state: StateFlow<SpeechRecognitionState> = _state.asStateFlow()

    override suspend fun startListening() {
        _state.value = SpeechRecognitionState.Listening
    }

    override suspend fun stopListening() {
        _state.value = SpeechRecognitionState.Processing
    }

    override suspend fun cancel() {
        _state.value = SpeechRecognitionState.Idle
    }
}

@Singleton
class FakePodcastOrchestrator @Inject constructor(
    private val fixtures: FixtureStore,
    private val dialogueGenerator: DialogueGenerator,
    private val speechGenerator: SpeechGenerator,
) : PodcastOrchestrator {
    private val preferences = PodcastPreferences()
    private var currentItem: PodcastQueueItem? = null
    private var upcoming: List<PodcastQueueItem> = emptyList()
    private val _state = MutableStateFlow<PodcastOrchestratorState>(PodcastOrchestratorState.Idle)
    override val state: StateFlow<PodcastOrchestratorState> = _state.asStateFlow()

    override suspend fun start() {
        val current = currentItem
        _state.value = if (current == null) {
            PodcastOrchestratorState.Locating
        } else {
            PodcastOrchestratorState.Playing(current, upcoming)
        }
    }

    override suspend fun stop() {
        _state.value = PodcastOrchestratorState.Idle
    }

    override suspend fun refreshUpcomingPlaces() {
        val currentId = currentItem?.place?.id
        upcoming = fixtures.places
            .filterNot { it.id == currentId }
            .take(3)
            .map { PodcastQueueItem(id = "queue-${it.id}", place = it) }
        _state.value = PodcastOrchestratorState.Ready(listOfNotNull(currentItem) + upcoming)
    }

    override suspend fun prepareNextSegment() {
        if (upcoming.isEmpty()) refreshUpcomingPlaces()
        val item = upcoming.firstOrNull() ?: return
        val knowledge = requireNotNull(item.place.knowledgePackage)
        _state.value = PodcastOrchestratorState.Preparing(item.place)
        val segment = dialogueGenerator.generateSegment(knowledge, PreviousPodcastContext(), preferences)
        val audio = segment.lines.map { line ->
            GeneratedAudioLine(line.id, speechGenerator.synthesize(line.text, line.speaker))
        }
        val prepared = item.copy(segment = segment, audioLines = audio, status = QueueItemStatus.READY)
        currentItem = prepared
        upcoming = upcoming.drop(1)
        _state.value = PodcastOrchestratorState.Ready(listOf(prepared) + upcoming)
    }

    override suspend fun skipCurrentSegment() {
        currentItem = null
        prepareNextSegment()
    }

    override suspend fun requestMoreAboutCurrentPlace() {
        val current = currentItem ?: return
        val knowledge = requireNotNull(current.place.knowledgePackage)
        val context = PreviousPodcastContext(
            recentPlaceIds = listOf(current.place.id),
            segmentSummaries = listOfNotNull(current.segment?.title),
            recentDialogue = current.segment?.lines.orEmpty(),
        )
        val segment = dialogueGenerator.generateSegment(knowledge, context, preferences)
        currentItem = current.copy(segment = segment, status = QueueItemStatus.PREPARING)
        _state.value = PodcastOrchestratorState.Preparing(current.place)
    }

    override suspend fun askQuestion(question: String) {
        val current = requireNotNull(currentItem) { "No current place" }
        val knowledge = requireNotNull(current.place.knowledgePackage)
        _state.value = PodcastOrchestratorState.AnsweringQuestion(question)
        dialogueGenerator.answerQuestion(
            question,
            knowledge,
            current.segment?.lines.orEmpty(),
            preferences,
        )
        _state.value = PodcastOrchestratorState.Ready(listOf(current) + upcoming)
    }
}
