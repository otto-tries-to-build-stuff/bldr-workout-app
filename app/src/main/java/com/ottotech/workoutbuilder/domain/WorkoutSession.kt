package com.ottotech.workoutbuilder.domain

import kotlin.math.ceil

/** An audio cue the execution service should play. */
enum class Cue { COUNTDOWN, GO, FINISH }

/** A session transition: the new state, plus whatever should be heard at that instant. */
data class SessionTick(
    val session: WorkoutSession,
    val cues: List<Cue> = emptyList(),
    /**
     * A line for the voice to say at this transition, or null. Emitted whether or not voice is
     * switched on — the service decides whether to speak it.
     */
    val speech: String? = null,
)

/**
 * The running state of a workout, as a pure function of a clock reading.
 *
 * Deliberately Android-free and clock-free: every method takes `now` (a
 * `SystemClock.elapsedRealtime()` reading) as a parameter rather than reading a clock
 * itself. That is what makes "does it beep at 3, 2, 1 and advance at zero" a JVM test
 * measured in milliseconds instead of a stopwatch-and-phone exercise.
 *
 * Timing is wall-clock arithmetic, never tick accumulation (spec §7): a segment's remaining
 * time is its duration minus elapsed-since-start minus time spent paused. Scheduling delay
 * and device sleep therefore cannot make a 30-second interval drift.
 */
data class WorkoutSession(
    val segments: List<Segment>,
    val index: Int = 0,
    /** Clock reading at which the current segment began, back-dated by any carried overflow. */
    val startedAt: Long = 0L,
    /** Non-null while paused: the reading at which the pause began. */
    val pausedAt: Long? = null,
    /** Milliseconds spent paused during the current segment. */
    val pausedTotal: Long = 0L,
    /** The countdown second (3, 2 or 1) already beeped on this segment, so it fires once. */
    val lastCountdown: Int? = null,
    val finished: Boolean = false,
) {

    val current: Segment? get() = segments.getOrNull(index)

    val isPaused: Boolean get() = pausedAt != null

    /** Number of WORK segments — the progress bar's denominator; rests are not steps. */
    val workTotal: Int get() = segments.count { it.kind == SegmentKind.WORK }

    /**
     * 1-based count of WORK segments reached so far. A rest reports the exercise that
     * preceded it, so the header does not flicker backwards between exercises.
     */
    val workIndex: Int
        get() = segments.take((index + 1).coerceAtMost(segments.size))
            .count { it.kind == SegmentKind.WORK }
            .coerceAtLeast(1)

    /** Milliseconds actually spent on the current segment, excluding paused time. */
    fun elapsedMs(now: Long): Long {
        val end = pausedAt ?: now
        return (end - startedAt - pausedTotal).coerceAtLeast(0L)
    }

    /** Milliseconds left on the current segment, or null when it has no duration. */
    fun remainingMs(now: Long): Long? {
        val duration = current?.durationSec ?: return null
        return duration * 1000L - elapsedMs(now)
    }

    /**
     * Seconds to show on the ring. Rounds **up**, so a segment shows its full duration the
     * instant it starts and shows "1" through the whole final second. Rounding down would
     * display 29 immediately and hold 0 on screen for a full second before advancing.
     */
    fun remainingSecDisplay(now: Long): Int? {
        val ms = remainingMs(now) ?: return null
        return ceil(ms / 1000.0).toInt().coerceAtLeast(0)
    }

    /**
     * A rest that hands over to a get-ready step stays silent: that step's own 3-2-1 is the
     * countdown, and beeping here too would be beeps, voice, beeps. With voice off a rest is
     * never followed by a lead-in, so this is always true there.
     */
    private val countsDown: Boolean
        get() = !(current?.kind == SegmentKind.REST && segments.getOrNull(index + 1)?.kind == SegmentKind.LEAD_IN)

    /**
     * Advances the clock. Returns the same session when nothing is due, so the caller can
     * compare instances cheaply and only re-publish state that changed.
     */
    fun tick(now: Long): SessionTick {
        if (finished || isPaused) return SessionTick(this)
        val remaining = remainingMs(now) ?: return SessionTick(this) // manual / reps wait for a tap
        if (remaining <= 0L) return advance(now, overflow = -remaining)

        val second = ceil(remaining / 1000.0).toInt()
        val due = second in 1..3 && (lastCountdown == null || second < lastCountdown) && countsDown
        return if (due) {
            SessionTick(copy(lastCountdown = second), listOf(Cue.COUNTDOWN))
        } else {
            SessionTick(this)
        }
    }

    /**
     * Moves past the current segment, carrying [overflow] — the milliseconds by which the
     * clock has already run past its end — into the next one, so a late tick does not
     * silently lengthen the workout.
     *
     * A long gap (device asleep, service descheduled) can span several segments. Those are
     * consumed by arithmetic and land on the right one with a single go cue, rather than
     * replaying each intermediate segment's cues in a burst. A segment with no duration
     * stops the catch-up: it waits for a person, and cannot be slept through.
     */
    private fun advance(now: Long, overflow: Long): SessionTick {
        var next = index + 1
        var carry = overflow
        while (next < segments.size) {
            val durationMs = (segments[next].durationSec ?: break) * 1000L
            if (carry < durationMs) break
            carry -= durationMs
            next++
        }
        return if (next >= segments.size) finish() else entered(beginSegment(next, now - carry), next)
    }

    /** Segment [at] from its start — the one place a segment's per-run state is reset. */
    private fun beginSegment(at: Int, startedAt: Long, paused: Boolean = false) = copy(
        index = at,
        startedAt = startedAt,
        pausedAt = if (paused) startedAt else null,
        pausedTotal = 0L,
        lastCountdown = null,
    )

    /**
     * The tick for having just begun segment [at] of [session]. A get-ready step opens with the
     * voice and no tone — "go" is kept for the exercise itself.
     */
    private fun entered(session: WorkoutSession, at: Int): SessionTick {
        val cues = if (session.segments[at].kind == SegmentKind.LEAD_IN) emptyList() else listOf(Cue.GO)
        return SessionTick(session, cues, speechFor(at))
    }

    /** Shown and said with a lead-in at [at]: the workout's first, or one of the get-ready steps after it. */
    fun leadInCaption(at: Int = index): String = if (at == 0) "First up" else "Next up"

    /** What the voice says as segment [at] begins — see [SessionTick.speech]. */
    private fun speechFor(at: Int): String? {
        val segment = segments[at]
        return when (segment.kind) {
            SegmentKind.LEAD_IN -> "${leadInCaption(at)}: ${segment.exerciseName}"
            SegmentKind.REST -> "Rest"
            SegmentKind.WORK -> null // its get-ready step already named it
        }
    }

    private fun finish() = SessionTick(
        copy(index = segments.size, finished = true, lastCountdown = null),
        listOf(Cue.FINISH),
        "Workout complete",
    )

    /**
     * Moves to the next segment on the person's instruction, whatever its mode and whatever
     * time is left. Unlike [tick]'s auto-advance there is no overflow to carry: the new
     * segment starts now.
     */
    fun skip(now: Long): SessionTick {
        if (finished) return SessionTick(this)
        val next = index + 1
        return if (next >= segments.size) finish() else entered(beginSegment(next, now), next)
    }

    /**
     * "Done" on a manual or reps segment. Mechanically identical to [skip] — named
     * separately because the intent differs, and the screen shows a different control.
     */
    fun complete(now: Long): SessionTick = skip(now)

    /**
     * Puts the current segment back to its start: full time on a timed one, zero elapsed on a
     * manual one. A paused session stays paused — with the pause re-anchored to [now], so no
     * time is lost before Resume — and plays no go cue, since nothing has started yet.
     */
    fun restart(now: Long): SessionTick {
        if (finished) return SessionTick(this)
        return if (isPaused) SessionTick(beginSegment(index, now, paused = true)) else entered(beginSegment(index, now), index)
    }

    fun pause(now: Long): WorkoutSession =
        if (finished || isPaused) this else copy(pausedAt = now)

    /** Folds the pause into [pausedTotal], so remaining time resumes where it stopped. */
    fun resume(now: Long): WorkoutSession {
        val since = pausedAt ?: return this
        return copy(pausedAt = null, pausedTotal = pausedTotal + (now - since))
    }

    companion object {
        /**
         * [leadInSec] puts a "3, 2, 1" in front of the first segment. It is an ordinary timed
         * [SegmentKind.LEAD_IN] segment, so [tick]'s last-three-seconds beeps are the count,
         * advancing past it plays go, and pause / skip / restart need no special case.
         *
         * With [voice] on, a lead-in — a get-ready step — goes in front of **every** exercise,
         * not just the first, giving the voice room to name it before the 3-2-1.
         */
        fun start(segments: List<Segment>, now: Long, leadInSec: Int = 0, voice: Boolean = false): SessionTick {
            // Nothing to run. Finish silently — a finish tone for a workout that never
            // started would be a lie, and Home must not record it as performed.
            if (segments.isEmpty()) return SessionTick(WorkoutSession(segments, finished = true))
            if (leadInSec <= 0) return SessionTick(WorkoutSession(segments, startedAt = now), listOf(Cue.GO))
            // A copy of the segment it precedes, so a lead-in can name what's coming.
            fun leadIn(before: Segment) =
                before.copy(kind = SegmentKind.LEAD_IN, mode = ItemMode.TIMED, durationSec = leadInSec)
            val withLeadIns = if (voice) {
                // A compiled workout always opens on an exercise (SegmentCompiler only puts
                // rests after items), so this also covers the first segment.
                segments.flatMap { if (it.kind == SegmentKind.WORK) listOf(leadIn(it), it) else listOf(it) }
            } else {
                listOf(leadIn(segments.first())) + segments
            }
            val session = WorkoutSession(withLeadIns, startedAt = now)
            return session.tick(now).copy(speech = session.speechFor(0))
        }
    }
}
