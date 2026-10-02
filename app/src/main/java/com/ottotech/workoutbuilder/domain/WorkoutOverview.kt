package com.ottotech.workoutbuilder.domain

/** The workout overview screen's wording, kept pure so it is unit-tested rather than eyeballed. */
object WorkoutOverview {

    /** `45s`, `1 min 30s`, `3 × 10 · 60 kg` or `Until done`. */
    fun itemDetail(item: WorkoutItem): String = when (item.mode) {
        ItemMode.TIMED -> duration(item.durationSec ?: 0)
        ItemMode.REPS -> buildString {
            append("${item.sets ?: 1} × ${item.reps ?: 1}")
            item.weightKg?.let { append(" · ${it.toCleanString()} kg") }
        }
        ItemMode.MANUAL -> "Until done"
    }

    /**
     * Every rest the section has, in the order they happen, or null when it has none.
     * Between-rounds rest is dropped from a single-round section — the compiler never runs it.
     */
    fun restLine(section: Section): String? {
        val rests = listOfNotNull(
            section.restBetweenExercises?.let { it to "between exercises" },
            section.restBetweenRounds?.takeIf { section.rounds > 1 }?.let { it to "between rounds" },
            section.restAfterSection?.let { it to "after" },
        )
        if (rests.isEmpty()) return null
        // Only the first phrase says "rest"; repeating it in every part reads as a list of commands.
        return rests.mapIndexed { i, (rest, where) -> "${restPhrase(rest, first = i == 0)} $where" }
            .joinToString(" · ")
    }

    private fun restPhrase(rest: Rest, first: Boolean): String = when (rest) {
        is Rest.Timed -> if (first) "${duration(rest.seconds)} rest" else duration(rest.seconds)
        Rest.Manual -> if (first) "Rest until ready" else "until ready"
    }

    private fun duration(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return when {
            m == 0 -> "${s}s"
            s == 0 -> "$m min"
            else -> "$m min ${s}s"
        }
    }
}
