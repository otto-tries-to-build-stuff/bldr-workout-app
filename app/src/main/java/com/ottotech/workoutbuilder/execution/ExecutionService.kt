package com.ottotech.workoutbuilder.execution

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.ottotech.workoutbuilder.WorkoutApp
import com.ottotech.workoutbuilder.audio.CuePlayer
import com.ottotech.workoutbuilder.domain.Cue
import com.ottotech.workoutbuilder.domain.SegmentCompiler
import com.ottotech.workoutbuilder.domain.WorkoutSession
import kotlinx.coroutines.CancellationException
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
     * A foreground service alone does not keep the CPU awake — with the screen off the
     * device can still suspend, and the 10Hz clock in [runClock] stops with it, which is
     * exactly the failure this phase exists to prevent. Held for the whole running session
     * (not released across pause) since a pause is expected to be brief and toggling it on
     * every pause/resume would multiply the release paths that must stay exhaustive, for
     * no real battery benefit. `setReferenceCounted(false)` makes repeated `acquire()`
     * calls (e.g. a second workout starting in the same service instance) idempotent rather
     * than stacking, and makes `release()` always fully release regardless of how many
     * times `acquire()` was called.
     */
    private val wakeLock: PowerManager.WakeLock by lazy {
        getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "workoutbuilder:execution")
            .apply { setReferenceCounted(false) }
    }

    /** [wakeLock] is held only when actually acquired — guards a release with nothing held. */
    private fun acquireWakeLock() {
        // A crash between acquire() and release() must not hold the CPU awake forever; this
        // timeout is far longer than any real workout and is purely a backstop.
        if (!wakeLock.isHeld) wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)
    }

    /** Safe to call any number of times, including when nothing is held. */
    private fun releaseWakeLock() {
        if (wakeLock.isHeld) wakeLock.release()
    }

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

    /**
     * Guards the defensive [startForeground] call in [onStartCommand] so it only ever fires
     * once per instance, on whichever command happens to arrive first. Without this it would
     * fire on every command — including a plain pause, skip or mute toggle sent to an
     * already-running instance — and stomp the real notification with blank placeholder
     * content between that call and whatever the command itself goes on to publish.
     */
    private var foregroundStarted = false

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
        if (!foregroundStarted) {
            // Every instance is created via startForegroundService() (see `command()` below)
            // — including, now, a bare ACTION_STOP delivered to a freshly (re)created
            // instance: stopForFinish() below already stops the service the moment a workout
            // finishes, and Done still sends ACTION_STOP on the way out (by design — it's
            // what resets the published state). The platform requires startForeground()
            // within the launch window of *every* instance started this way, regardless of
            // which action it is actually servicing, or a fresh instance crashes with
            // ForegroundServiceDidNotStartInTimeException. Calling it unconditionally on an
            // instance's first command satisfies that; begin() and
            // stopEverything()/stopForFinish() then immediately replace or remove it as
            // appropriate. Gated on [foregroundStarted] so it never fires again for that same
            // instance and stomps the real notification a later pause/skip/mute-toggle sets.
            startForeground(ExecutionNotification.ID, ExecutionNotification.build(this, "", "", paused = false))
            foregroundStarted = true
        }
        when (intent?.action) {
            ACTION_START -> intent.getStringExtra(EXTRA_WORKOUT_ID)?.let { begin(it) }
            ACTION_PAUSE -> update { it.pause(now()) }
            ACTION_RESUME -> update { it.resume(now()) }
            ACTION_SKIP -> updateWithCues { it.skip(now()) }
            ACTION_COMPLETE -> updateWithCues { it.complete(now()) }
            ACTION_RESTART -> updateWithCues { it.restart(now()) }
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
            try {
                val workout = app.workouts.load(workoutId) ?: run { stopEverything(); return@launch }
                runningWorkoutId = workoutId
                // A fresh session, whether this is the service's first workout or (defensively)
                // a second one run without the instance being torn down in between — either way
                // nothing has been recorded for it yet.
                performedRecorded = false
                val library = app.exercises.observeAll().first().associateBy { it.id }
                val segments = SegmentCompiler.compile(workout, library)
                // Primed before the clock starts, not after: prime() generates three WAVs and
                // waits on SoundPool decode, and the spec's own mitigation is to have that done
                // "before the first segment begins". Starting the session first would run that
                // wait against the segment's own clock, silently stealing that time off it.
                cues.muted = app.prefs.cuesMuted
                cues.prime()
                val (session, sessionCues) = WorkoutSession.start(segments, now(), LEAD_IN_SEC)
                acquireWakeLock()
                _state.value = ExecutionState(
                    workoutName = workout.name,
                    workoutId = workoutId,
                    session = session,
                    muted = app.prefs.cuesMuted,
                )
                sessionCues.forEach { play(it) } // the lead-in's opening "3" beep
                refreshNotification()
                runClock()
            } catch (e: CancellationException) {
                throw e // cooperative cancellation (onDestroy, or a second ACTION_START) — not a failure
            } catch (e: Exception) {
                // SegmentCompiler.compile throws on an unresolvable exercise id, and the Room
                // reads above it can throw too. Uncaught, either takes the whole process down —
                // in a SupervisorJob scope with no exception handler, an uncaught exception in a
                // child coroutine still crashes, it just does not cancel siblings first. Fail
                // the start cleanly instead: same outcome (no workout, no notification, no
                // service) without the crash. WorkoutSummarizer takes the equivalent defensive
                // stance for Home, by dropping the unresolvable item instead of crashing the list.
                Log.e(TAG, "begin($workoutId) failed to start", e)
                stopEverything()
            }
        }
    }

    /** 10 Hz. Fine enough that a cue lands within 100ms of its second, cheap enough to ignore. */
    private suspend fun runClock() {
        // The notification is rebuilt at ~1Hz, not on every tick: a rebuild is a real
        // NotificationManager.notify() call, and the countdown display only needs to be
        // accurate to the second a person can actually read.
        var lastNotifyAt = now()
        while (scope.isActive) {
            val session = _state.value.session ?: return
            if (session.finished) {
                // Nothing calling this before Plan 1D's final fix wave meant the service,
                // its notification and this very loop all ran forever once a workout ended —
                // a phone left in a pocket kept a foreground service (and, now, a wake lock)
                // alive until reboot. _state is deliberately left alone: it lives on the
                // companion object, survives this service instance, and the finish screen
                // reads it after the service is long gone.
                stopForFinish()
                return
            }
            updateWithCues { it.tick(now()) }
            val t = now()
            if (t - lastNotifyAt >= NOTIFY_INTERVAL_MS) {
                lastNotifyAt = t
                refreshNotification()
            }
            delay(TICK_MS)
        }
    }

    /**
     * The teardown for a workout that finished on its own (or was compiled to zero segments,
     * which starts already finished) — as opposed to [stopEverything], which is what a user
     * quitting or tapping Done on the finish screen triggers. Unlike that one, this must NOT
     * reset [_state]: the finish screen renders from it, and it has to still read correctly
     * after the service that reached it is gone.
     */
    private fun stopForFinish() {
        if (destroyed) return
        clockJob?.cancel()
        mediaSession.isActive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        releaseWakeLock()
        stopSelf()
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
                    finished = session.finished,
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
        releaseWakeLock()
        stopSelf()
    }

    override fun onDestroy() {
        destroyed = true
        cues.release()
        mediaSession.release()
        // A backstop, not the normal path: stopEverything() and stopForFinish() already
        // release it on every route out of a running workout. release() only acts when
        // something is actually held, so this is safe even when they already have.
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "start"
        const val ACTION_PAUSE = "pause"
        const val ACTION_RESUME = "resume"
        const val ACTION_SKIP = "skip"
        const val ACTION_COMPLETE = "complete"
        const val ACTION_RESTART = "restart"
        const val ACTION_TOGGLE_MUTE = "toggle_mute"
        const val ACTION_STOP = "stop"
        const val EXTRA_WORKOUT_ID = "workoutId"
        private const val TAG = "ExecutionService"
        private const val TICK_MS = 100L

        /** The "3, 2, 1" on screen before the first exercise, so a workout never starts cold. */
        private const val LEAD_IN_SEC = 3

        /** The notification is rebuilt at most this often, independent of the 10Hz tick. */
        private const val NOTIFY_INTERVAL_MS = 1_000L

        /** Backstop only — far longer than any real workout; see [acquireWakeLock]. */
        private const val WAKE_LOCK_TIMEOUT_MS = 4 * 60 * 60 * 1_000L

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
        fun restart(context: Context) = command(context, ACTION_RESTART)
        fun toggleMute(context: Context) = command(context, ACTION_TOGGLE_MUTE)
        fun stop(context: Context) = command(context, ACTION_STOP)
    }
}
