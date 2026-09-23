package com.ottotech.workoutbuilder.domain

import kotlin.math.ceil

/** An audio cue the execution service should play. */
enum class Cue { COUNTDOWN, GO, FINISH }

/** A session transition: the new state, plus whatever should be heard at that instant. */
data class SessionTick(
    val session: WorkoutSession,
    val cues: List<Cue> = emptyList(),
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
     * Advances the clock. Returns the same session when nothing is due, so the caller can
     * compare instances cheaply and only re-publish state that changed.
     */
    fun tick(now: Long): SessionTick {
        if (finished || isPaused) return SessionTick(this)
        val remaining = remainingMs(now) ?: return SessionTick(this) // manual / reps wait for a tap
        if (remaining <= 0L) return advance(now, overflow = -remaining)

        val second = ceil(remaining / 1000.0).toInt()
        val due = second in 1..3 && (lastCountdown == null || second < lastCountdown)
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
        return if (next >= segments.size) {
            SessionTick(copy(index = segments.size, finished = true, lastCountdown = null), listOf(Cue.FINISH))
        } else {
            SessionTick(
                copy(
                    index = next,
                    startedAt = now - carry,
                    pausedAt = null,
                    pausedTotal = 0L,
                    lastCountdown = null,
                ),
                listOf(Cue.GO),
            )
        }
    }

    /**
     * Moves to the next segment on the person's instruction, whatever its mode and whatever
     * time is left. Unlike [tick]'s auto-advance there is no overflow to carry: the new
     * segment starts now.
     */
    fun skip(now: Long): SessionTick {
        if (finished) return SessionTick(this)
        val next = index + 1
        return if (next >= segments.size) {
            SessionTick(copy(index = segments.size, finished = true, lastCountdown = null), listOf(Cue.FINISH))
        } else {
            SessionTick(
                copy(
                    index = next,
                    startedAt = now,
                    pausedAt = null,
                    pausedTotal = 0L,
                    lastCountdown = null,
                ),
                listOf(Cue.GO),
            )
        }
    }

    /**
     * "Done" on a manual or reps segment. Mechanically identical to [skip] — named
     * separately because the intent differs, and the screen shows a different control.
     */
    fun complete(now: Long): SessionTick = skip(now)

    fun pause(now: Long): WorkoutSession =
        if (finished || isPaused) this else copy(pausedAt = now)

    /** Folds the pause into [pausedTotal], so remaining time resumes where it stopped. */
    fun resume(now: Long): WorkoutSession {
        val since = pausedAt ?: return this
        return copy(pausedAt = null, pausedTotal = pausedTotal + (now - since))
    }

    companion object {
        fun start(segments: List<Segment>, now: Long): SessionTick =
            if (segments.isEmpty()) {
                // Nothing to run. Finish silently — a finish tone for a workout that never
                // started would be a lie, and Home must not record it as performed.
                SessionTick(WorkoutSession(segments, finished = true))
            } else {
                SessionTick(WorkoutSession(segments, index = 0, startedAt = now), listOf(Cue.GO))
            }
    }
}
