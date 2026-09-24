package com.ottotech.workoutbuilder.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ottotech.workoutbuilder.WorkoutApp
import com.ottotech.workoutbuilder.ai.CoachClient
import com.ottotech.workoutbuilder.ai.CoachConversation
import com.ottotech.workoutbuilder.ai.CoachPrompt
import com.ottotech.workoutbuilder.ai.CoachReply
import com.ottotech.workoutbuilder.ai.CoachResponseMapper
import com.ottotech.workoutbuilder.ai.CoachResult
import com.ottotech.workoutbuilder.ai.FailureKind
import com.ottotech.workoutbuilder.data.ApiKeyStore
import com.ottotech.workoutbuilder.data.ExerciseRepository
import com.ottotech.workoutbuilder.data.WorkoutRepository
import com.ottotech.workoutbuilder.domain.Exercise
import com.ottotech.workoutbuilder.domain.WorkoutSummarizer
import com.ottotech.workoutbuilder.domain.WorkoutSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID

sealed interface CoachPhase {
    data object Compose : CoachPhase
    data object Loading : CoachPhase
    /** [exerciseNames] covers library and newly invented exercises, keyed by id, for the preview list. */
    data class Preview(
        val result: CoachResult.Ok,
        val summary: WorkoutSummary,
        val exerciseNames: Map<String, String>,
    ) : CoachPhase
    data class Problem(val message: String, val canRetry: Boolean, val keyProblem: Boolean) : CoachPhase
}

data class CoachUiState(
    val hasKey: Boolean,
    val goal: String = "",
    val minutes: Int? = null,
    val equipment: Set<String> = emptySet(),
    val tweak: String = "",
    val phase: CoachPhase = CoachPhase.Compose,
)

/**
 * Nothing is written until [save]. Leaving mid-request just cancels [viewModelScope]; the
 * typed goal survives every error so the user never retypes it.
 */
class CoachViewModel(
    private val keys: ApiKeyStore,
    private val exercises: ExerciseRepository,
    private val workouts: WorkoutRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CoachUiState(hasKey = keys.hasKey))
    val state: StateFlow<CoachUiState> = _state.asStateFlow()

    /** Built for one key; rebuilt when the saved key changes (e.g. after a rejected key is replaced). */
    @Volatile private var client: Pair<String, CoachClient>? = null
    private var conversation: CoachConversation? = null

    private fun clientFor(key: String): CoachClient =
        client?.takeIf { it.first == key }?.second ?: CoachClient(key).also { client = key to it }

    init { refreshKey() }

    /** Called on resume too — the user may come back from Settings with a key. */
    fun refreshKey() {
        val key = keys.get()
        _state.update { it.copy(hasKey = key != null) }
        if (key != null && client?.first != key) {
            viewModelScope.launch(Dispatchers.IO) { clientFor(key).warmUp() }
        }
    }

    fun onGoal(text: String) = _state.update { it.copy(goal = text) }
    fun onMinutes(m: Int?) = _state.update { it.copy(minutes = if (it.minutes == m) null else m) }
    fun onEquipment(value: String) = _state.update {
        it.copy(equipment = if (value in it.equipment) it.equipment - value else it.equipment + value)
    }
    fun onTweakText(text: String) = _state.update { it.copy(tweak = text) }

    fun generate() {
        val s = _state.value
        if (s.goal.isBlank()) return
        run(CoachConversation.start(CoachPrompt.request(s.goal, s.minutes, s.equipment)))
    }

    fun regenerate() { conversation?.let { run(it.forRegenerate()) } }

    fun applyTweak() {
        val c = conversation ?: return
        val text = _state.value.tweak
        if (text.isBlank()) return
        _state.update { it.copy(tweak = "") }
        run(c.tweak(text))
    }

    fun backToCompose() = _state.update { it.copy(phase = CoachPhase.Compose) }

    fun save(onSaved: () -> Unit) {
        val preview = _state.value.phase as? CoachPhase.Preview ?: return
        viewModelScope.launch {
            exercises.upsertCustom(preview.result.newExercises)
            workouts.save(preview.result.workout)
            onSaved()
        }
    }

    private fun run(next: CoachConversation) {
        val key = keys.get() ?: return _state.update { it.copy(hasKey = false) }
        _state.update { it.copy(phase = CoachPhase.Loading) }
        viewModelScope.launch {
            val library = exercises.observeAll().first()
            val reply = withContext(Dispatchers.IO) {
                clientFor(key).generate(CoachPrompt.system(library), next.turns)
            }
            _state.update { it.copy(phase = phaseFor(reply, next, library)) }
        }
    }

    private fun phaseFor(reply: CoachReply, sent: CoachConversation, library: List<Exercise>): CoachPhase =
        when (reply) {
            is CoachReply.Json -> when (val r = CoachResponseMapper.map(reply.text, library, { UUID.randomUUID().toString() }, Instant.now())) {
                is CoachResult.Ok -> {
                    conversation = sent.withReply(reply.text)
                    val all = (library + r.newExercises).associateBy { it.id }
                    CoachPhase.Preview(r, WorkoutSummarizer.summarize(r.workout, all), all.mapValues { it.value.name })
                }
                is CoachResult.Invalid -> {
                    conversation = sent
                    CoachPhase.Problem("Couldn't build that one.", canRetry = true, keyProblem = false)
                }
            }
            is CoachReply.Refused -> { conversation = null; CoachPhase.Problem(reply.message, canRetry = false, keyProblem = false) }
            is CoachReply.Failed -> {
                conversation = sent
                when (reply.kind) {
                    FailureKind.NO_NETWORK -> CoachPhase.Problem("No connection. Check your internet and try again.", true, false)
                    FailureKind.BAD_KEY -> CoachPhase.Problem("Anthropic didn't accept your API key.", false, true)
                    FailureKind.BUSY -> CoachPhase.Problem("The Coach is busy right now. Try again in a moment.", true, false)
                    FailureKind.OTHER -> CoachPhase.Problem("Something went wrong. Try again.", true, false)
                }
            }
        }

    /** Retry re-sends exactly what failed. */
    fun retry() { conversation?.let { run(it.forRegenerate()) } ?: generate() }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WorkoutApp
                CoachViewModel(app.apiKeys, app.exercises, app.workouts)
            }
        }
    }
}
