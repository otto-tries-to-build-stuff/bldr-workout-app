package com.ottotech.workoutbuilder.execution

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import android.os.SystemClock
import com.ottotech.workoutbuilder.WorkoutApp
import com.ottotech.workoutbuilder.audio.CuePlayer
import com.ottotech.workoutbuilder.domain.Cue
import com.ottotech.workoutbuilder.domain.SegmentCompiler
import com.ottotech.workoutbuilder.domain.WorkoutSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ExecutionState(
    val workoutName: String = "",
    /** Empty when no workout has ever been started. Lets [ExecutionService.begin] tell a
     *  "return to the workout already running" ACTION_START apart from a genuinely new one. */
    val workoutId: String = "",
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

    private val cues by lazy { CuePlayer(this) }

    /**
     * Exposes pause/resume/skip to the lock screen and headset buttons. Its `isActive` state
     * and whether its token reaches [ExecutionNotification.build] are kept in lockstep with
     * the session in [refreshNotification] and [stopEverything], so the app stops looking
     * like a media app the moment the workout finishes or is quit — an active session left
     * behind after that would otherwise linger on the lock screen with nothing real behind it.
     */
    private lateinit var mediaSession: MediaSession

    /**
     * As [CuePlayer.released]: [refreshNotification] runs not only from [onStartCommand] on
     * the main thread but also from [runClock]'s tick on [Dispatchers.Default], and coroutine
     * cancellation is cooperative — a tick already past its `delay()` and mid-[refreshNotification]
     * is not interrupted by [scope]`.cancel()`. Set as the first statement of [onDestroy],
     * before [mediaSession]`.release()`, so a late tick sees it and backs off instead of
     * calling into a released [MediaSession] (which throws, and since it's on an
     * uncaught-exception coroutine, takes the process down).
     */
    @Volatile private var destroyed = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ExecutionNotification.ensureChannel(this)
        mediaSession = MediaSession(this, "workout").apply {
            setCallback(object : MediaSession.Callback() {
                // The default onMediaButtonEvent() dispatches a headset PLAY_PAUSE toggle to
                // onPlay/onPause based on the PlaybackState most recently published below, so
                // a single physical button still maps to the right action.
                override fun onPlay() { update { it.resume(now()) } }
                override fun onPause() { update { it.pause(now()) } }
                override fun onSkipToNext() { updateWithCues { it.skip(now()) } }
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> intent.getStringExtra(EXTRA_WORKOUT_ID)?.let { begin(it) }
            ACTION_PAUSE -> update { it.pause(now()) }
            ACTION_RESUME -> update { it.resume(now()) }
            ACTION_SKIP -> updateWithCues { it.skip(now()) }
            ACTION_COMPLETE -> updateWithCues { it.complete(now()) }
            ACTION_TOGGLE_MUTE -> handleToggleMute()
            ACTION_STOP -> stopEverything()
        }
        return START_NOT_STICKY
    }

    private fun now() = SystemClock.elapsedRealtime()

    /** The single running clock loop, so a second ACTION_START can never stack a duplicate. */
    private var clockJob: Job? = null

    /** Set once, in [begin], for the currently-running workout — the id [markPerformed] writes. */
    private var runningWorkoutId: String = ""

    /**
     * Guards against the 10Hz tick writing the "performed" row repeatedly once finished, and
     * against a workout that compiled to zero segments (which finishes at [WorkoutSession.start],
     * never reaching [updateWithCues]) ever being recorded at all.
     */
    private var performedRecorded = false

    private fun begin(workoutId: String) {
        val current = _state.value
        val alreadyRunningThisWorkout =
            current.session?.let { !it.finished } == true && current.workoutId == workoutId
        if (alreadyRunningThisWorkout) {
            // The notification's contentIntent re-enters this route on every tap, which fires
            // ACTION_START again. Tapping the notification to check on a workout must return
            // to it as it is, not restart it from segment 1 — so this is a deliberate no-op.
            return
        }

        // Anything else — no session yet, a finished one, or a different workout id — starts
        // fresh. Cancel whatever clock loop is running first so exactly one is ever ticking.
        clockJob?.cancel()
        startForeground(ExecutionNotification.ID, ExecutionNotification.build(this, "Starting…", "", paused = false))
        clockJob = scope.launch {
            val workout = app.workouts.load(workoutId) ?: run { stopEverything(); return@launch }
            runningWorkoutId = workoutId
            // A fresh session, whether this is the service's first workout or (defensively)
            // a second one run without the instance being torn down in between — either way
            // nothing has been recorded for it yet.
            performedRecorded = false
            val library = app.exercises.observeAll().first().associateBy { it.id }
            val segments = SegmentCompiler.compile(workout, library)
            val (session, sessionCues) = WorkoutSession.start(segments, now())
            cues.muted = app.prefs.cuesMuted
            cues.prime()
            _state.value = ExecutionState(
                workoutName = workout.name,
                workoutId = workoutId,
                session = session,
                muted = app.prefs.cuesMuted,
            )
            sessionCues.forEach { play(it) } // the opening GO — start() emits it for any non-empty workout
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

    /**
     * Both this and [updateWithCues] read-modify-write the shared, process-wide [_state]:
     * [onStartCommand] calls them synchronously on the main thread, and [runClock] calls
     * [updateWithCues] ~10x/second on [Dispatchers.Default]. A plain `_state.value = ...`
     * read-then-assign is not atomic, so a pause landing in the same window as a tick can be
     * silently overwritten by it. `MutableStateFlow.update` retries its block under
     * contention until its compare-and-set wins, closing that window.
     */
    private inline fun update(transform: (WorkoutSession) -> WorkoutSession) {
        var applied = false
        _state.update { current ->
            applied = false
            val session = current.session ?: return@update current
            applied = true
            current.copy(session = transform(session))
        }
        if (applied) refreshNotification()
    }

    /**
     * As [update], but for transforms that can also emit cues. `update`'s block can run more
     * than once under contention, so cues are not played from inside it — [pendingCues] is
     * simply overwritten by whichever invocation of the block runs last, and that last
     * invocation is always the one immediately before the winning compare-and-set (the loop
     * returns as soon as one succeeds). Cues are only played once that block has returned, so
     * exactly the cues belonging to the transition that actually got committed play, exactly
     * once — never one from an invocation whose result was discarded and retried.
     */
    private inline fun updateWithCues(transform: (WorkoutSession) -> com.ottotech.workoutbuilder.domain.SessionTick) {
        var applied = false
        var pendingCues: List<Cue> = emptyList()
        var committed: WorkoutSession? = null
        _state.update { current ->
            applied = false
            val session = current.session ?: return@update current
            val (next, cues) = transform(session)
            if (next == session && cues.isEmpty()) return@update current
            applied = true
            pendingCues = cues
            committed = next
            current.copy(session = next)
        }
        if (applied) {
            pendingCues.forEach { play(it) }
            refreshNotification()
            // As with pendingCues above: `committed` is the session from whichever invocation
            // of the update block actually won the compare-and-set, so this check and the
            // markPerformed write it can trigger happen exactly once per real transition —
            // never from a retried, discarded invocation, and never inside the update{}
            // lambda itself (which could re-run under contention and write the row twice).
            val next = committed
            if (next != null && next.finished && !performedRecorded && next.segments.isNotEmpty()) {
                performedRecorded = true
                scope.launch { app.workouts.markPerformed(runningWorkoutId) }
            }
        }
    }

    /**
     * As [update], the plain `_state.value = ...` this replaced could lose a concurrent tick's
     * write; `update {}` closes that window here too.
     */
    private fun handleToggleMute() {
        var muted = false
        _state.update {
            muted = !it.muted
            it.copy(muted = muted)
        }
        app.prefs.cuesMuted = muted
        cues.muted = muted
    }

    private fun play(cue: Cue) = cues.play(cue)

    private fun refreshNotification() {
        // Guards every mediaSession/NotificationManager call below — including the
        // notify() at the end of this function — against a tick that lost the race with
        // onDestroy(). See [destroyed].
        if (destroyed) return
        val state = _state.value
        val session = state.session ?: return
        val segment = session.current
        val text = when {
            session.finished -> "Workout complete"
            segment == null -> ""
            else -> session.remainingSecDisplay(now())?.let { "$it s left" } ?: segment.label
        }
        val title = if (session.finished) state.workoutName else segment?.exerciseName ?: state.workoutName

        // Active only while there is a real, unfinished workout behind it — a finished session
        // still lingers in state until the finish screen quits, and the lock screen must not
        // keep offering transport controls for a workout that is over.
        mediaSession.isActive = !session.finished
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT,
                )
                .setState(
                    if (session.isPaused) PlaybackState.STATE_PAUSED else PlaybackState.STATE_PLAYING,
                    PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                    1f,
                )
                .build(),
        )
        getSystemService(android.app.NotificationManager::class.java)
            .notify(
                ExecutionNotification.ID,
                ExecutionNotification.build(
                    this,
                    title,
                    text,
                    session.isPaused,
                    if (session.finished) null else mediaSession.sessionToken,
                ),
            )
    }

    private fun stopEverything() {
        // Also reachable off the main thread (begin()'s scope.launch calls this when a
        // workout id fails to load), so it gets the same released-mediaSession guard as
        // refreshNotification.
        if (destroyed) return
        _state.value = ExecutionState()
        // Belt and braces alongside onDestroy's release(): a mid-workout quit (ACTION_STOP)
        // must drop the lock screen controls immediately, not wait for teardown to catch up.
        mediaSession.isActive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        destroyed = true
        cues.release()
        mediaSession.release()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "start"
        const val ACTION_PAUSE = "pause"
        const val ACTION_RESUME = "resume"
        const val ACTION_SKIP = "skip"
        const val ACTION_COMPLETE = "complete"
        const val ACTION_TOGGLE_MUTE = "toggle_mute"
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
        fun toggleMute(context: Context) = command(context, ACTION_TOGGLE_MUTE)
        fun stop(context: Context) = command(context, ACTION_STOP)
    }
}
