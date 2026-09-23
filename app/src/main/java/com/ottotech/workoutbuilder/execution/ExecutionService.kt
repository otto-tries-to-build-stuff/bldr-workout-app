package com.ottotech.workoutbuilder.execution

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import com.ottotech.workoutbuilder.WorkoutApp
import com.ottotech.workoutbuilder.domain.Cue
import com.ottotech.workoutbuilder.domain.SegmentCompiler
import com.ottotech.workoutbuilder.domain.WorkoutSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ExecutionState(
    val workoutName: String = "",
    val session: WorkoutSession? = null,
    val muted: Boolean = false,
)

/**
 * Owns the clock and the audio for a running workout.
 *
 * The UI deliberately holds none of this: a Compose screen dies with the Activity, and the
 * whole point of the phase is cues that keep firing with the screen off. The service ticks
 * the pure [WorkoutSession] at 10 Hz and publishes the result; the screen is a view of
 * [state] and nothing more.
 */
class ExecutionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val app: WorkoutApp get() = application as WorkoutApp

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ExecutionNotification.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> intent.getStringExtra(EXTRA_WORKOUT_ID)?.let { begin(it) }
            ACTION_PAUSE -> update { it.pause(now()) }
            ACTION_RESUME -> update { it.resume(now()) }
            ACTION_SKIP -> updateWithCues { it.skip(now()) }
            ACTION_COMPLETE -> updateWithCues { it.complete(now()) }
            ACTION_STOP -> stopEverything()
        }
        return START_NOT_STICKY
    }

    private fun now() = SystemClock.elapsedRealtime()

    private fun begin(workoutId: String) {
        startForeground(ExecutionNotification.ID, ExecutionNotification.build(this, "Starting…", "", paused = false))
        scope.launch {
            val workout = app.workouts.load(workoutId) ?: run { stopEverything(); return@launch }
            val library = app.exercises.observeAll().first().associateBy { it.id }
            val segments = SegmentCompiler.compile(workout, library)
            val (session, _) = WorkoutSession.start(segments, now())
            _state.value = ExecutionState(workout.name, session, app.prefs.cuesMuted)
            refreshNotification()
            runClock()
        }
    }

    /** 10 Hz. Fine enough that a cue lands within 100ms of its second, cheap enough to ignore. */
    private suspend fun runClock() {
        while (scope.isActive) {
            val session = _state.value.session ?: return
            if (!session.finished) {
                updateWithCues { it.tick(now()) }
            }
            delay(TICK_MS)
        }
    }

    private inline fun update(transform: (WorkoutSession) -> WorkoutSession) {
        val session = _state.value.session ?: return
        _state.value = _state.value.copy(session = transform(session))
        refreshNotification()
    }

    private inline fun updateWithCues(transform: (WorkoutSession) -> com.ottotech.workoutbuilder.domain.SessionTick) {
        val session = _state.value.session ?: return
        val (next, cues) = transform(session)
        if (next == session && cues.isEmpty()) return
        _state.value = _state.value.copy(session = next)
        cues.forEach { play(it) }
        refreshNotification()
    }

    /** Replaced with real audio in Task 4. */
    private fun play(cue: Cue) = Unit

    private fun refreshNotification() {
        val state = _state.value
        val session = state.session ?: return
        val segment = session.current
        val text = when {
            session.finished -> "Workout complete"
            segment == null -> ""
            else -> session.remainingSecDisplay(now())?.let { "$it s left" } ?: segment.label
        }
        val title = if (session.finished) state.workoutName else segment?.exerciseName ?: state.workoutName
        getSystemService(android.app.NotificationManager::class.java)
            .notify(ExecutionNotification.ID, ExecutionNotification.build(this, title, text, session.isPaused))
    }

    private fun stopEverything() {
        _state.value = ExecutionState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "start"
        const val ACTION_PAUSE = "pause"
        const val ACTION_RESUME = "resume"
        const val ACTION_SKIP = "skip"
        const val ACTION_COMPLETE = "complete"
        const val ACTION_STOP = "stop"
        const val EXTRA_WORKOUT_ID = "workoutId"
        private const val TICK_MS = 100L

        /**
         * Process-wide, deliberately not per-instance.
         *
         * The screen navigates and starts collecting *before* `startForegroundService` has
         * actually constructed the service, so a flow hanging off the instance would be null
         * at exactly the moment the UI needs it and the screen would sit blank forever. A
         * companion-level flow always exists; it simply holds an empty state until the
         * service fills it. That also makes a bound service unnecessary — this app has one
         * execution screen and one service, and binding would add a connection lifecycle to
         * juggle through rotation for no benefit.
         */
        private val _state = MutableStateFlow(ExecutionState())
        val state: StateFlow<ExecutionState> = _state.asStateFlow()

        private fun command(context: Context, action: String, workoutId: String? = null) {
            val intent = Intent(context, ExecutionService::class.java).setAction(action)
            workoutId?.let { intent.putExtra(EXTRA_WORKOUT_ID, it) }
            context.startForegroundService(intent)
        }

        fun start(context: Context, workoutId: String) = command(context, ACTION_START, workoutId)
        fun pause(context: Context) = command(context, ACTION_PAUSE)
        fun resume(context: Context) = command(context, ACTION_RESUME)
        fun skip(context: Context) = command(context, ACTION_SKIP)
        fun complete(context: Context) = command(context, ACTION_COMPLETE)
        fun stop(context: Context) = command(context, ACTION_STOP)
    }
}
