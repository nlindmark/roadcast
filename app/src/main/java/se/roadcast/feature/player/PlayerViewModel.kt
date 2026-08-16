package se.roadcast.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import se.roadcast.core.ai.DialogueGenerator
import se.roadcast.core.ai.SuggestedQuestions
import se.roadcast.core.audio.PodcastPreviewPlayer
import se.roadcast.core.database.HistoryStore
import se.roadcast.core.location.LocationMode
import se.roadcast.core.location.LocationModeController
import se.roadcast.core.location.LocationPermissionStatus
import se.roadcast.core.location.LocationSource
import se.roadcast.core.model.AskOverlayState
import se.roadcast.core.model.HostId
import se.roadcast.core.model.PlaceCandidate
import se.roadcast.core.model.PlaceCategory
import se.roadcast.core.model.PlaceKnowledgePackage
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
        val followUpCount: Int = 0,
        val canTellMeMore: Boolean = false,
        val usingGps: Boolean = false,
        val locationMessage: String? = null,
    ) : PlayerUiState
    data class Empty(
        val message: String,
        val simulationRunning: Boolean = false,
        val usingGps: Boolean = false,
        val locationMessage: String? = null,
    ) : PlayerUiState
    data class Error(val message: String) : PlayerUiState
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val locationSource: LocationSource,
    private val locationModeController: LocationModeController,
    private val historyStore: HistoryStore,
    private val discoveryRepository: RankedPlaceDiscoveryRepository,
    private val knowledgeRepository: PlaceKnowledgeRepository,
    private val dialogueGenerator: DialogueGenerator,
    private val previewPlayer: PodcastPreviewPlayer,
) : ViewModel() {
    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _permissionRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val permissionRequests = _permissionRequests.asSharedFlow()

    private var startAfterPermission = false

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

    companion object {
        private const val MaxFollowUps = 1
    }

    init {
        viewModelScope.launch {
            combine(
                locationSource.travelState,
                historyStore.playedPlaceIds,
                locationSource.isRunning,
                locationModeController.mode,
                locationModeController.permissionStatus,
            ) { travel, played, running, mode, permission ->
                TravelSnapshot(travel, played, running, mode, permission)
            }.collect { snapshot ->
                val usingGps = snapshot.mode == LocationMode.GPS
                val locationMessage = locationMessage(snapshot.mode, snapshot.permission)
                runCatching {
                    discoveryRepository.findRankedPlacesAhead(
                        snapshot.travel,
                        preferences,
                        snapshot.played,
                    )
                }
                    .onSuccess { ranked ->
                        val selected = ranked.firstOrNull()
                        if (selected == null) {
                            _uiState.value = PlayerUiState.Empty(
                                message = if (usingGps) {
                                    "No eligible fixture stories near this GPS position. " +
                                        "Try Gothenburg or switch back to simulation."
                                } else {
                                    "No eligible stories remain on this route."
                                },
                                simulationRunning = snapshot.running,
                                usingGps = usingGps,
                                locationMessage = locationMessage,
                            )
                            return@onSuccess
                        }
                        val current = _uiState.value as? PlayerUiState.Success
                        if (current?.selected?.id == selected.candidate.id) {
                            _uiState.value = current.copy(
                                selected = selected.candidate,
                                alternatives = ranked.size - 1,
                                simulationRunning = snapshot.running,
                                autoPlayEnabled = preferences.autoplay,
                                usingGps = usingGps,
                                locationMessage = locationMessage,
                            )
                        } else {
                            previewPlayer.stop()
                            preparingPlaceId = null
                            askJob?.cancel()
                            val next = PlayerUiState.Success(
                                selected = selected.candidate,
                                alternatives = ranked.size - 1,
                                simulationRunning = snapshot.running,
                                autoPlayEnabled = preferences.autoplay,
                                usingGps = usingGps,
                                locationMessage = locationMessage,
                            )
                            _uiState.value = next
                            if (preferences.autoplay && snapshot.running) {
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
            return
        }
        locationModeController.refreshPermissionStatus()
        if (locationModeController.mode.value == LocationMode.GPS &&
            locationModeController.permissionStatus.value !is LocationPermissionStatus.Granted
        ) {
            startAfterPermission = true
            _permissionRequests.tryEmit(Unit)
            return
        }
        beginJourney()
    }

    fun onPermissionResult(granted: Boolean) {
        locationModeController.onPermissionResult(granted)
        if (granted && startAfterPermission) {
            startAfterPermission = false
            beginJourney()
        } else {
            startAfterPermission = false
        }
    }

    private fun beginJourney() {
        locationSource.start()
        val current = _uiState.value as? PlayerUiState.Success ?: return
        if (preferences.autoplay && current.segment == null) {
            prepareAndPlay(current)
        } else if (current.playback is PreviewPlaybackState.Paused) {
            previewPlayer.resume()
        }
    }

    private fun locationMessage(
        mode: LocationMode,
        permission: LocationPermissionStatus,
    ): String? = when {
        mode == LocationMode.GPS && permission is LocationPermissionStatus.Granted ->
            "GPS mode"
        mode == LocationMode.GPS && permission is LocationPermissionStatus.NeedsPermission ->
            "Location permission needed for GPS mode"
        mode == LocationMode.GPS && permission is LocationPermissionStatus.Denied ->
            "Location permission denied"
        permission is LocationPermissionStatus.Unavailable -> permission.message
        else -> null
    }

    private data class TravelSnapshot(
        val travel: se.roadcast.core.model.TravelState,
        val played: Set<String>,
        val running: Boolean,
        val mode: LocationMode,
        val permission: LocationPermissionStatus,
    )

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

    fun tellMeMore() {
        val current = _uiState.value as? PlayerUiState.Success ?: return
        if (!current.canTellMeMore || current.segment == null || current.ask != null) return
        if (current.playback is PreviewPlaybackState.Initializing ||
            current.playback is PreviewPlaybackState.Answering
        ) {
            return
        }
        askJob?.cancel()
        prepareJob?.cancel()
        preparingPlaceId = null
        prepareJob = viewModelScope.launch {
            preparingPlaceId = current.selected.id
            _uiState.value = current.copy(playback = PreviewPlaybackState.Initializing, ask = null)
            runCatching {
                val previousSegment = current.segment ?: return@launch
                val knowledge = knowledgeRepository.buildKnowledgePackage(current.selected)
                val context = PreviousPodcastContext(
                    recentPlaceIds = recentPlaceIds + current.selected.id,
                    segmentSummaries = recentSummaries + listOf(previousSegment.title),
                    recentDialogue = previousSegment.dialogue,
                )
                val segment = dialogueGenerator.generateSegment(knowledge, context, preferences)
                val latest = _uiState.value as? PlayerUiState.Success
                if (latest?.selected?.id != current.selected.id) return@launch
                val followUpCount = latest.followUpCount + 1
                _uiState.value = latest.copy(
                    segment = segment,
                    followUpCount = followUpCount,
                    canTellMeMore = followUpCount < MaxFollowUps &&
                        hasUnusedFacts(knowledge, segment.dialogue.flatMap { it.factIds }.toSet()),
                    playback = PreviewPlaybackState.Initializing,
                    lastAnswer = null,
                )
                previewPlayer.play(segment)
            }.onFailure { error ->
                preparingPlaceId = null
                val latest = _uiState.value as? PlayerUiState.Success ?: return@launch
                _uiState.value = latest.copy(
                    playback = PreviewPlaybackState.Error(
                        error.message ?: "Could not prepare a deeper segment.",
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
            _uiState.value = current.copy(
                playback = PreviewPlaybackState.Initializing,
                ask = null,
                followUpCount = 0,
                canTellMeMore = false,
            )
            runCatching {
                val knowledge = knowledgeRepository.buildKnowledgePackage(current.selected)
                val context = PreviousPodcastContext(
                    recentPlaceIds = recentPlaceIds,
                    segmentSummaries = recentSummaries,
                )
                val segment = dialogueGenerator.generateSegment(knowledge, context, preferences)
                val latest = _uiState.value as? PlayerUiState.Success
                if (latest?.selected?.id != current.selected.id) return@launch
                val usedFactIds = segment.dialogue.flatMap { it.factIds }.toSet()
                _uiState.value = latest.copy(
                    segment = segment,
                    followUpCount = 0,
                    canTellMeMore = hasUnusedFacts(knowledge, usedFactIds),
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

    private fun hasUnusedFacts(
        knowledge: PlaceKnowledgePackage,
        usedFactIds: Set<String>,
    ): Boolean = knowledge.facts.any { it.id !in usedFactIds }

    private suspend fun onSegmentCompleted(current: PlayerUiState.Success) {
        if (current.selected.id in historyStore.playedPlaceIds.value) return
        preparingPlaceId = null
        recentSummaries = (recentSummaries + listOfNotNull(current.segment?.title)).takeLast(5)
        if (current.canTellMeMore) {
            return
        }
        recentPlaceIds = (recentPlaceIds + current.selected.id).takeLast(5)
        historyStore.markPlayed(current.selected.id)
    }

    override fun onCleared() {
        prepareJob?.cancel()
        askJob?.cancel()
        previewPlayer.stop()
        super.onCleared()
    }
}
