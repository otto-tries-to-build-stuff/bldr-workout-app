package com.ottotech.workoutbuilder.ui.coach

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ottotech.workoutbuilder.WorkoutApp
import com.ottotech.workoutbuilder.ai.CoachClient
import com.ottotech.workoutbuilder.ai.CoachConversation
import com.ottotech.workoutbuilder.ai.CoachPhase
import com.ottotech.workoutbuilder.ai.CoachPrompt
import com.ottotech.workoutbuilder.ai.CoachReply
import com.ottotech.workoutbuilder.ai.CoachSession
import com.ottotech.workoutbuilder.data.ApiKeyStore
import com.ottotech.workoutbuilder.data.ExerciseRepository
import com.ottotech.workoutbuilder.data.WorkoutRepository
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

data class CoachUiState(
    val hasKey: Boolean,
    val goal: String = "",
    val minutes: Int? = null,
    val equipment: Set<String> = emptySet(),
    val tweak: String = "",
    val phase: CoachPhase = CoachPhase.Compose,
)

/**
 * Wires [CoachSession] (the decisions) to the key store, the network and the repositories.
 * Nothing is written until [save]. Leaving mid-request just cancels [viewModelScope]; the typed
 * goal — and a tweak that failed — survive every error so the user never retypes them.
 */
class CoachViewModel(
    private val keys: ApiKeyStore,
    private val exercises: ExerciseRepository,
    private val workouts: WorkoutRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CoachUiState(hasKey = keys.hasKey))
    val state: StateFlow<CoachUiState> = _state.asStateFlow()

    private val session = CoachSession(newId = { UUID.randomUUID().toString() }, now = Instant::now)

    /**
     * Built for one key; replaced when the saved key changes (e.g. after a rejected key is
     * replaced). Assigned on the main thread and built lazily (synchronised), so a request made
     * during the ~4 s warm-up waits for the same client instead of building a second one.
     */
    private var client: Pair<String, Lazy<CoachClient>>? = null

    private fun clientFor(key: String): Lazy<CoachClient> =
        client?.takeIf { it.first == key }?.second ?: lazy { CoachClient(key) }.also { client = key to it }

    /** Called on every resume, including the first — the user may come back from Settings with a key. */
    fun refreshKey() {
        val key = keys.get()
        _state.update { it.copy(hasKey = key != null) }
        if (key != null && client?.first != key) {
            val pending = clientFor(key)
            viewModelScope.launch(Dispatchers.IO) { pending.value.warmUp() }
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
        request { session.start(CoachPrompt.request(s.goal, s.minutes, s.equipment)) }
    }

    fun regenerate() = request { session.regenerate() }

    fun applyTweak() {
        val text = _state.value.tweak
        if (text.isBlank()) return
        request { session.tweak(text) }
    }

    fun retry() = request { session.retry() }

    fun backToCompose() = _state.update { it.copy(phase = CoachPhase.Compose) }

    fun save(onSaved: () -> Unit) {
        val preview = _state.value.phase as? CoachPhase.Preview ?: return
        viewModelScope.launch {
            exercises.upsertCustom(preview.result.newExercises)
            workouts.save(preview.result.workout)
            onSaved()
        }
    }

    /**
     * The key is checked before the session is asked, so a missing key never leaves the session
     * thinking a request is in flight. [pick] returns null when one already is: do nothing.
     */
    private fun request(pick: () -> CoachConversation?) {
        val key = keys.get() ?: return _state.update { it.copy(hasKey = false) }
        val next = pick() ?: return
        val coach = clientFor(key)
        _state.update { it.copy(phase = CoachPhase.Loading) }
        viewModelScope.launch {
            val library = exercises.observeAll().first()
            val reply = withContext(Dispatchers.IO) {
                coach.value.generate(CoachPrompt.system(library), next.turns)
            }
            if (reply is CoachReply.Failed) Log.w(TAG, "Coach request failed: ${reply.kind} ${reply.detail.orEmpty()}")
            if (reply is CoachReply.Refused) Log.w(TAG, "Coach refused: ${reply.message}")
            val phase = session.onReply(reply, library)
            _state.update {
                // A tweak that produced a fresh draft is done; one that failed stays in the box.
                val tweakDone = phase is CoachPhase.Preview && phase.notice == null
                it.copy(phase = phase, tweak = if (tweakDone) "" else it.tweak)
            }
        }
    }

    companion object {
        private const val TAG = "Coach"

        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WorkoutApp
                CoachViewModel(app.apiKeys, app.exercises, app.workouts)
            }
        }
    }
}
