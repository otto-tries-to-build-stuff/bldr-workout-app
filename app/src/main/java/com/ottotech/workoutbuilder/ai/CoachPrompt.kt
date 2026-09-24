package com.ottotech.workoutbuilder.ai

import com.ottotech.workoutbuilder.domain.Exercise
import com.ottotech.workoutbuilder.domain.Facet
import com.ottotech.workoutbuilder.domain.TagVocabulary

/**
 * The system prompt is the cached prefix: it must be byte-identical for the same library, so
 * exercises are sorted by id and tags by facet/value, and nothing time-dependent goes in.
 */
object CoachPrompt {

    fun system(library: List<Exercise>): String = buildString {
        appendLine("You are a strength and conditioning coach designing one workout for a single person.")
        appendLine("Build it from the exercise library below. Prefer existing exercises: set exerciseId to the library id.")
        appendLine("Only invent an exercise when nothing in the library fits; then set exerciseId to null, give it a clear")
        appendLine("common name, a primaryMuscle, and tags chosen only from the vocabulary below.")
        appendLine()
        appendLine("Structure: sections run in order; a section repeats its items for `rounds`. Use TIMED items with durationSec")
        appendLine("for intervals, REPS items with sets and reps for strength work, MANUAL for anything the person ends by tapping.")
        appendLine("Rests are whole seconds; use null for no rest. Put a warm-up first and keep the total near any time asked for.")
        appendLine("Use `note` for short form cues only.")
        appendLine()
        appendLine("Tag vocabulary (facet: values):")
        Facet.entries.forEach { appendLine("${it.name}: ${TagVocabulary.valuesFor(it).joinToString(", ")}") }
        appendLine()
        appendLine("Exercise library (id · name · primary muscle · tags):")
        library.sortedBy { it.id }.forEach { ex ->
            val tags = ex.tags.sortedWith(compareBy({ it.facet.ordinal }, { it.value }))
                .joinToString(" ") { "${it.facet.name}:${it.value}" }
            appendLine(listOf(ex.id, ex.name, ex.primaryMuscle.label, tags).filter { it.isNotEmpty() }.joinToString(" · "))
        }
    }

    fun request(goal: String, minutes: Int?, equipment: Set<String>): String = buildString {
        append("Goal: ").append(goal.trim())
        if (minutes != null) append("\nAbout $minutes minutes long.")
        if (equipment.isNotEmpty()) append("\nEquipment available: ").append(equipment.sorted().joinToString(", ")).append('.')
    }
}
