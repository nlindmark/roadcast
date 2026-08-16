package se.roadcast.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import se.roadcast.core.ai.DialogueGenerator
import se.roadcast.core.ai.SuggestedQuestions
import se.roadcast.core.audio.PodcastPreviewPlayer
import se.roadcast.core.database.HistoryStore
import se.roadcast.core.location.LocationSource
import se.roadcast.core.model.AskOverlayState
import se.roadcast.core.model.HostId
import se.roadcast.core.model.PlaceCandidate
import se.roadcast.core.model.PlaceCategory
import se.roadcast.core.model.PodcastPreferences
import se.roadcast.core.model.PodcastSegment
import se.roadcast.core.model.PreviousPodcastContext
import se.roadcast.core.model.PreviewPlaybackState
import se.roadcast.core.network.PlaceKnowledgeRepository
import se.roadcast.core.network.RankedPlaceDiscoveryRepository
import javax.inject.Inject

sealed interface PlayerUiState {
    data object Loading : PlayerUiState
    data class Success(
        val selected: PlaceCandidate,
        val alternatives: Int,
        val segment: PodcastSegment? = null,
        val playback: PreviewPlaybackState = PreviewPlaybackState.Idle,
        val simulationRunning: Boolean = false,
        val autoPlayEnabled: Boolean = true,
        val ask: AskOverlayState? = null,
        val lastAnswer: String? = null,
    ) : PlayerUiState
    data class Empty(val message: String, val simulationRunning: Boolean = false) : PlayerUiState
    data class Error(val message: String) : PlayerUiState
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val locationSource: LocationSource,
    private val historyStore: HistoryStore,
    private val discoveryRepository: RankedPlaceDiscoveryRepository,
    private val knowledgeRepository: PlaceKnowledgeRepository,
    private val dialogueGenerator: DialogueGenerator,
    private val previewPlayer: PodcastPreviewPlayer,
) : ViewModel() {
    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val preferences = PodcastPreferences(
        interests = mapOf(
            PlaceCategory.HISTORY to 0.8,
            PlaceCategory.CULTURE to 0.9,
            PlaceCategory.ARCHITECTURE to 0.7,
        ),
        autoplay = true,
    )
    private var prepareJob: Job? = null
    private var askJob: Job? = null
    private var preparingPlaceId: String? = null
    private var recentPlaceIds = emptyList<String>()
    private var recentSummaries = emptyList<String>()
    private var resumeAfterAskIndex = 0

    init {
        viewModelScope.launch {
            combine(
                locationSource.travelState,
                historyStore.playedPlaceIds,
                locationSource.isRunning,
            ) { travel, played, running ->
                Triple(travel, played, running)
            }.collect { (travel, played, running) ->
                runCatching { discoveryRepository.findRankedPlacesAhead(travel, preferences, played) }
                    .onSuccess { ranked ->
                        val selected = ranked.firstOrNull()
                        if (selected == null) {
                            _uiState.value = PlayerUiState.Empty(
                                message = "No eligible stories remain on this route.",
                                simulationRunning = running,
                            )
                            return@onSuccess
                        }
                        val current = _uiState.value as? PlayerUiState.Success
                        if (current?.selected?.id == selected.candidate.id) {
                            _uiState.value = current.copy(
                                selected = selected.candidate,
                                alternatives = ranked.size - 1,
                                simulationRunning = running,
                                autoPlayEnabled = preferences.autoplay,
                            )
                        } else {
                            previewPlayer.stop()
                            preparingPlaceId = null
                            askJob?.cancel()
                            val next = PlayerUiState.Success(
                                selected = selected.candidate,
                                alternatives = ranked.size - 1,
                                simulationRunning = running,
                                autoPlayEnabled = preferences.autoplay,
                            )
                            _uiState.value = next
                            if (preferences.autoplay && running) {
                                prepareAndPlay(next)
                            }
                        }
                    }
                    .onFailure {
                        _uiState.value = PlayerUiState.Error(it.message ?: "Discovery failed")
                    }
            }
        }
        viewModelScope.launch {
            previewPlayer.state.collect { playback ->
                val current = _uiState.value as? PlayerUiState.Success ?: return@collect
                val askClosed = if (playback is PreviewPlaybackState.Playing ||
                    playback is PreviewPlaybackState.Completed
                ) {
                    null
                } else {
                    current.ask
                }
                _uiState.value = current.copy(
                    playback = playback,
                    ask = askClosed,
                    lastAnswer = when (playback) {
                        is PreviewPlaybackState.Answering -> playback.answerText
                        else -> current.lastAnswer
                    },
                )
                if (playback is PreviewPlaybackState.Completed && current.segment != null) {
                    onSegmentCompleted(current)
                }
            }
        }
    }

    fun toggleJourney() {
        if (locationSource.isRunning.value) {
            locationSource.pause()
            previewPlayer.pause()
        } else {
            locationSource.start()
            val current = _uiState.value as? PlayerUiState.Success ?: return
            if (preferences.autoplay && current.segment == null) {
                prepareAndPlay(current)
            } else if (current.playback is PreviewPlaybackState.Paused) {
                previewPlayer.resume()
            }
        }
    }

    fun playOrPause() {
        val current = _uiState.value as? PlayerUiState.Success ?: return
        if (current.ask != null) return
        when (current.playback) {
            is PreviewPlaybackState.Playing -> previewPlayer.pause()
            is PreviewPlaybackState.Paused -> previewPlayer.resume()
            is PreviewPlaybackState.Answering -> Unit
            PreviewPlaybackState.Completed -> previewPlayer.replay()
            PreviewPlaybackState.Initializing -> Unit
            PreviewPlaybackState.Idle, is PreviewPlaybackState.Error -> prepareAndPlay(current)
        }
    }

    fun replay() {
        val current = _uiState.value as? PlayerUiState.Success ?: return
        if (current.ask != null) return
        if (current.segment == null) prepareAndPlay(current) else previewPlayer.replay()
    }

    fun skip() {
        val current = _uiState.value as? PlayerUiState.Success ?: return
        viewModelScope.launch {
            askJob?.cancel()
            previewPlayer.stop()
            preparingPlaceId = null
            historyStore.markPlayed(current.selected.id)
            recentPlaceIds = (recentPlaceIds + current.selected.id).takeLast(5)
            recentSummaries = (recentSummaries + listOfNotNull(current.segment?.title)).takeLast(5)
        }
    }

    fun openAsk() {
        val current = _uiState.value as? PlayerUiState.Success ?: return
        if (current.segment == null) return
        resumeAfterAskIndex = previewPlayer.pauseForAsk()
        val suggestions = current.selected.knowledgePackage
            ?.let(SuggestedQuestions::from)
            .orEmpty()
        _uiState.value = current.copy(
            ask = AskOverlayState.Editing(suggestedQuestions = suggestions),
            playback = previewPlayer.state.value,
        )
    }

    fun updateAskQuestion(question: String) {
        val current = _uiState.value as? PlayerUiState.Success ?: return
        val ask = current.ask as? AskOverlayState.Editing ?: return
        _uiState.value = current.copy(ask = ask.copy(question = question, errorMessage = null))
    }

    fun useSuggestedQuestion(question: String) {
        updateAskQuestion(question)
    }

    fun cancelAsk() {
        val current = _uiState.value as? PlayerUiState.Success ?: return
        askJob?.cancel()
        _uiState.value = current.copy(ask = null)
        if (current.playback is PreviewPlaybackState.Paused) {
            previewPlayer.resume()
        }
    }

    fun submitAsk() {
        val current = _uiState.value as? PlayerUiState.Success ?: return
        val ask = current.ask as? AskOverlayState.Editing ?: return
        val question = ask.question.trim()
        if (question.isEmpty()) {
            _uiState.value = current.copy(ask = ask.copy(errorMessage = "Type a question first."))
            return
        }
        askJob?.cancel()
        askJob = viewModelScope.launch {
            _uiState.value = current.copy(
                ask = AskOverlayState.Submitting(question, ask.suggestedQuestions),
            )
            runCatching {
                val knowledge = knowledgeRepository.buildKnowledgePackage(current.selected)
                val answer = dialogueGenerator.answerQuestion(
                    question = question,
                    currentKnowledge = knowledge,
                    recentDialogue = current.segment?.dialogue.orEmpty(),
                    preferences = preferences,
                )
                previewPlayer.playAnswerThenResume(
                    question = question,
                    answerText = answer.text,
                    speaker = HostId.HOST_B,
                    resumeFromLineIndex = resumeAfterAskIndex,
                )
                val latest = _uiState.value as? PlayerUiState.Success ?: return@launch
                _uiState.value = latest.copy(
                    ask = null,
                    lastAnswer = answer.text,
                )
            }.onFailure { error ->
                val latest = _uiState.value as? PlayerUiState.Success ?: return@launch
                _uiState.value = latest.copy(
                    ask = AskOverlayState.Editing(
                        question = question,
                        suggestedQuestions = ask.suggestedQuestions,
                        errorMessage = error.message ?: "Could not answer that question.",
                    ),
                )
            }
        }
    }

    private fun prepareAndPlay(current: PlayerUiState.Success) {
        if (preparingPlaceId == current.selected.id) return
        prepareJob?.cancel()
        prepareJob = viewModelScope.launch {
            preparingPlaceId = current.selected.id
            _uiState.value = current.copy(playback = PreviewPlaybackState.Initializing, ask = null)
            runCatching {
                val knowledge = knowledgeRepository.buildKnowledgePackage(current.selected)
                val context = PreviousPodcastContext(
                    recentPlaceIds = recentPlaceIds,
                    segmentSummaries = recentSummaries,
                )
                val segment = dialogueGenerator.generateSegment(knowledge, context, preferences)
                val latest = _uiState.value as? PlayerUiState.Success
                if (latest?.selected?.id != current.selected.id) return@launch
                _uiState.value = latest.copy(
                    segment = segment,
                    playback = PreviewPlaybackState.Initializing,
                )
                previewPlayer.play(segment)
            }.onFailure { error ->
                preparingPlaceId = null
                val latest = _uiState.value as? PlayerUiState.Success ?: return@launch
                _uiState.value = latest.copy(
                    playback = PreviewPlaybackState.Error(
                        error.message ?: "Could not prepare the preview.",
                    ),
                )
            }
        }
    }

    private suspend fun onSegmentCompleted(current: PlayerUiState.Success) {
        if (current.selected.id in historyStore.playedPlaceIds.value) return
        recentPlaceIds = (recentPlaceIds + current.selected.id).takeLast(5)
        recentSummaries = (recentSummaries + listOfNotNull(current.segment?.title)).takeLast(5)
        preparingPlaceId = null
        historyStore.markPlayed(current.selected.id)
    }

    override fun onCleared() {
        prepareJob?.cancel()
        askJob?.cancel()
        previewPlayer.stop()
        super.onCleared()
    }
}
