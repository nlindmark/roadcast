package se.roadcast.simulation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import se.roadcast.core.ai.DialogueGenerator
import se.roadcast.core.ai.DialogueLineDto
import se.roadcast.core.ai.DialogueSegmentDto
import se.roadcast.core.ai.DialogueValidator
import se.roadcast.core.ai.UserSpeechRecognizer
import se.roadcast.core.ai.toDomain
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
class FakeDialogueGenerator @Inject constructor(
    private val validator: DialogueValidator,
) : DialogueGenerator {
    override suspend fun generateSegment(
        knowledge: PlaceKnowledgePackage,
        previousContext: PreviousPodcastContext?,
        preferences: PodcastPreferences,
    ): PodcastSegment {
        val isFollowUp = previousContext?.recentPlaceIds?.lastOrNull() == knowledge.placeId
        return if (isFollowUp) {
            generateFollowUpSegment(knowledge, previousContext)
        } else {
            generatePrimarySegment(knowledge, previousContext)
        }
    }

    private fun generatePrimarySegment(
        knowledge: PlaceKnowledgePackage,
        previousContext: PreviousPodcastContext?,
    ): PodcastSegment {
        val angle = knowledge.stories.firstOrNull()
        val firstFact = knowledge.facts.getOrNull(0)
        val secondFact = knowledge.facts.getOrNull(1)
        val placeName = angle?.title ?: knowledge.placeId
        val previousPlace = previousContext?.recentPlaceIds?.lastOrNull()
        val bridge = previousPlace?.let {
            "We just left $it behind, and the next stop has a different texture."
        }
        val uncertain = knowledge.uncertainClaims.firstOrNull()

        val lines = buildList {
            if (bridge != null) {
                add(
                    DialogueLineDto(
                        id = "${knowledge.placeId}-bridge",
                        speaker = HostId.HOST_A.name,
                        text = bridge,
                        interruptibleAfter = true,
                    ),
                )
            }
            add(
                DialogueLineDto(
                    id = "${knowledge.placeId}-host-a-intro",
                    speaker = HostId.HOST_A.name,
                    text = "Coming up is $placeName. ${angle?.premise ?: knowledge.overview}",
                    interruptibleAfter = true,
                    sourceIds = knowledge.sources.map { it.id },
                    factIds = listOfNotNull(firstFact?.id),
                ),
            )
            add(
                DialogueLineDto(
                    id = "${knowledge.placeId}-host-b-detail",
                    speaker = HostId.HOST_B.name,
                    text = firstFact?.let {
                        "One detail we can establish is that ${it.statement.trimEnd('.')}"
                    } ?: "The local package does not include a second verified detail.",
                    interruptibleAfter = true,
                    sourceIds = firstFact?.sourceIds.orEmpty(),
                    factIds = listOfNotNull(firstFact?.id),
                ),
            )
            add(
                DialogueLineDto(
                    id = "${knowledge.placeId}-host-a-follow-up",
                    speaker = HostId.HOST_A.name,
                    text = "So what else belongs in this story without stretching the evidence?",
                    interruptibleAfter = true,
                ),
            )
            add(
                DialogueLineDto(
                    id = "${knowledge.placeId}-host-b-close",
                    speaker = HostId.HOST_B.name,
                    text = secondFact?.let {
                        "${it.statement.trimEnd('.')} That is the stronger second anchor."
                    } ?: "There is no second verified fact here, so we should stop before guessing.",
                    interruptibleAfter = true,
                    sourceIds = secondFact?.sourceIds.orEmpty(),
                    factIds = listOfNotNull(secondFact?.id),
                ),
            )
            if (uncertain != null) {
                add(
                    DialogueLineDto(
                        id = "${knowledge.placeId}-host-a-uncertain",
                        speaker = HostId.HOST_A.name,
                        text = "And the uncertain claim floating around is worth a caution.",
                        interruptibleAfter = true,
                        sourceIds = uncertain.sourceIds,
                    ),
                )
                add(
                    DialogueLineDto(
                        id = "${knowledge.placeId}-host-b-uncertain",
                        speaker = HostId.HOST_B.name,
                        text = "${uncertain.claim} ${uncertain.reason}",
                        interruptibleAfter = true,
                        sourceIds = uncertain.sourceIds,
                    ),
                )
            }
            add(
                DialogueLineDto(
                    id = "${knowledge.placeId}-host-a-outro",
                    speaker = HostId.HOST_A.name,
                    text = "That is enough for $placeName for now. Tap Tell me more if you want the next layer.",
                    interruptibleAfter = true,
                ),
            )
        }

        return validate(
            knowledge = knowledge,
            segmentId = "segment-${knowledge.placeId}-0",
            title = placeName,
            intro = bridge,
            lines = lines,
            storyAngleId = angle?.id,
        )
    }

    private fun generateFollowUpSegment(
        knowledge: PlaceKnowledgePackage,
        previousContext: PreviousPodcastContext?,
    ): PodcastSegment {
        val placeName = knowledge.stories.firstOrNull()?.title ?: knowledge.placeId
        val usedFactIds = previousContext?.recentDialogue
            ?.flatMap { it.factIds }
            ?.toSet()
            .orEmpty()
        val unusedFacts = knowledge.facts.filterNot { it.id in usedFactIds }
            .ifEmpty { knowledge.facts.drop(2) }
            .ifEmpty { knowledge.facts.takeLast(2) }
        val first = unusedFacts.getOrNull(0)
        val second = unusedFacts.getOrNull(1) ?: unusedFacts.getOrNull(0)
        val depth = (previousContext?.segmentSummaries?.count {
            it.contains(placeName, ignoreCase = true) || it.startsWith("More:")
        } ?: 0) + 1

        val lines = buildList {
            add(
                DialogueLineDto(
                    id = "${knowledge.placeId}-more-a-open",
                    speaker = HostId.HOST_A.name,
                    text = "Alright, staying with $placeName a little longer. There is another layer worth hearing.",
                    interruptibleAfter = true,
                    sourceIds = knowledge.sources.map { it.id },
                ),
            )
            add(
                DialogueLineDto(
                    id = "${knowledge.placeId}-more-b-fact-1",
                    speaker = HostId.HOST_B.name,
                    text = first?.let {
                        "Here is a detail we have not used yet: ${it.statement.trimEnd('.')}"
                    } ?: "We do not have unused verified facts left for this place.",
                    interruptibleAfter = true,
                    sourceIds = first?.sourceIds.orEmpty(),
                    factIds = listOfNotNull(first?.id),
                ),
            )
            add(
                DialogueLineDto(
                    id = "${knowledge.placeId}-more-a-prompt",
                    speaker = HostId.HOST_A.name,
                    text = "Does that change how we should picture the place from the road?",
                    interruptibleAfter = true,
                ),
            )
            add(
                DialogueLineDto(
                    id = "${knowledge.placeId}-more-b-fact-2",
                    speaker = HostId.HOST_B.name,
                    text = second?.let {
                        "Yes. ${it.statement.trimEnd('.')} That keeps us inside the sourced material."
                    } ?: "Without another sourced fact, we should not invent color.",
                    interruptibleAfter = true,
                    sourceIds = second?.sourceIds.orEmpty(),
                    factIds = listOfNotNull(second?.id),
                ),
            )
            add(
                DialogueLineDto(
                    id = "${knowledge.placeId}-more-a-close",
                    speaker = HostId.HOST_A.name,
                    text = "Good. That is the deeper pass on $placeName. Ask a question anytime, or skip when you are ready to move on.",
                    interruptibleAfter = true,
                ),
            )
            add(
                DialogueLineDto(
                    id = "${knowledge.placeId}-more-b-close",
                    speaker = HostId.HOST_B.name,
                    text = "And if the package had more verified facts, we would keep going. For now, this is the honest remainder.",
                    interruptibleAfter = true,
                    sourceIds = knowledge.sources.map { it.id },
                    factIds = unusedFacts.map { it.id },
                ),
            )
        }

        return validate(
            knowledge = knowledge,
            segmentId = "segment-${knowledge.placeId}-more-$depth",
            title = "More: $placeName",
            intro = "Staying with $placeName for a deeper pass.",
            lines = lines,
            storyAngleId = knowledge.stories.firstOrNull()?.id,
        )
    }

    private fun validate(
        knowledge: PlaceKnowledgePackage,
        segmentId: String,
        title: String,
        intro: String?,
        lines: List<DialogueLineDto>,
        storyAngleId: String?,
    ): PodcastSegment {
        val draft = DialogueSegmentDto(
            id = segmentId,
            placeId = knowledge.placeId,
            title = title,
            intro = intro,
            dialogue = lines,
            estimatedDurationSeconds = validator.estimateDurationSeconds(lines),
            sourceIds = knowledge.sources.map { it.id },
            storyAngleId = storyAngleId,
        )
        val validated = validator.validateOrRepair(draft, knowledge)
        return validated.dto.toDomain(generatedAtEpochMillis = System.currentTimeMillis())
    }

    override suspend fun answerQuestion(
        question: String,
        currentKnowledge: PlaceKnowledgePackage,
        recentDialogue: List<DialogueLine>,
        preferences: PodcastPreferences,
    ): ConversationalAnswer {
        val fact = currentKnowledge.facts.firstOrNull {
            question.contains(it.statement.take(12), ignoreCase = true)
        } ?: currentKnowledge.facts.firstOrNull()
        return ConversationalAnswer(
            text = fact?.statement
                ?: "The simulation has no verified answer for “$question”.",
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
        val audio = segment.dialogue.map { line ->
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
            recentDialogue = current.segment?.dialogue.orEmpty(),
        )
        val segment = dialogueGenerator.generateSegment(knowledge, context, preferences)
        val audio = segment.dialogue.map { line ->
            GeneratedAudioLine(line.id, speechGenerator.synthesize(line.text, line.speaker))
        }
        val prepared = current.copy(
            segment = segment,
            audioLines = audio,
            status = QueueItemStatus.READY,
        )
        currentItem = prepared
        _state.value = PodcastOrchestratorState.Ready(listOf(prepared) + upcoming)
    }

    override suspend fun askQuestion(question: String) {
        val current = requireNotNull(currentItem) { "No current place" }
        val knowledge = requireNotNull(current.place.knowledgePackage)
        _state.value = PodcastOrchestratorState.AnsweringQuestion(question)
        dialogueGenerator.answerQuestion(
            question,
            knowledge,
            current.segment?.dialogue.orEmpty(),
            preferences,
        )
        _state.value = PodcastOrchestratorState.Ready(listOf(current) + upcoming)
    }
}
