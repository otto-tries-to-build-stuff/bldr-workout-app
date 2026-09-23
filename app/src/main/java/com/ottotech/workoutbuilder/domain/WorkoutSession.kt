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
