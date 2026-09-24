package com.ottotech.workoutbuilder.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.ottotech.workoutbuilder.domain.Exercise
import com.ottotech.workoutbuilder.domain.Facet
import com.ottotech.workoutbuilder.domain.ItemMode
import com.ottotech.workoutbuilder.domain.Muscle
import com.ottotech.workoutbuilder.domain.Rest
import com.ottotech.workoutbuilder.domain.Section
import com.ottotech.workoutbuilder.domain.Tag
import com.ottotech.workoutbuilder.domain.TagVocabulary
import com.ottotech.workoutbuilder.domain.TrainingType
import com.ottotech.workoutbuilder.domain.Workout
import com.ottotech.workoutbuilder.domain.WorkoutItem
import java.time.Instant

sealed interface CoachResult {
    data class Ok(val workout: Workout, val newExercises: List<Exercise>) : CoachResult
    data class Invalid(val reasons: List<String>) : CoachResult
}

/**
 * Turns the Coach's structured-output JSON into domain objects (spec §9).
 *
 * Structured outputs guarantee the shape, not the numbers, so the numeric rules are checked
 * here and every failure is collected rather than stopping at the first. Exercises resolve in
 * order: a real library id → a library exercise with the same name → an invented custom
 * exercise, whose id is derived from its name so saving twice upserts instead of duplicating.
 */
object CoachResponseMapper {

    private val mapper = ObjectMapper()

    fun customIdFor(name: String): String =
        "custom-" + name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    fun map(json: String, library: List<Exercise>, newId: () -> String, now: Instant): CoachResult {
        val root = try { mapper.readTree(json) } catch (e: Exception) { null }
            ?: return CoachResult.Invalid(listOf("The Coach's reply wasn't readable"))
        val sectionsNode = root.path("sections")
        val typeLabel = root.path("type").asText()
        val type = TrainingType.entries.firstOrNull { it.label == typeLabel }
        if (!sectionsNode.isArray || type == null || !root.hasNonNull("name")) {
            return CoachResult.Invalid(listOf("The Coach's reply was missing parts"))
        }

        val byId = library.associateBy { it.id }
        val byName = library.associateBy { it.name.normalisedName() }
        val invented = LinkedHashMap<String, Exercise>()
        val reasons = mutableListOf<String>()

        fun resolve(item: JsonNode, name: String): String {
            val id = item.path("exerciseId").textOrNull()
            if (id != null && id in byId) return id
            byName[name.normalisedName()]?.let { return it.id }
            val customId = customIdFor(name)
            invented.getOrPut(customId) { invent(customId, name, item) }
            return customId
        }

        val sections = sectionsNode.mapIndexedNotNull { sIndex, s ->
            val label = "Section ${sIndex + 1}"
            val rounds = s.path("rounds").asInt(0)
            if (rounds < 1) reasons += "$label: rounds must be at least 1"
            val items = s.path("items").mapIndexed { iIndex, item ->
                val where = "$label, exercise ${iIndex + 1}"
                val mode = ItemMode.entries.firstOrNull { it.name == item.path("mode").asText() } ?: ItemMode.MANUAL
                val duration = item.path("durationSec").intOrNull()
                val sets = item.path("sets").intOrNull()
                val reps = item.path("reps").intOrNull()
                val name = item.path("exerciseName").asText().trim()
                if (name.isEmpty()) reasons += "$where: missing a name"
                if (mode == ItemMode.TIMED && (duration == null || duration <= 0)) reasons += "$where: needs a duration"
                if (mode == ItemMode.REPS && (sets == null || sets <= 0 || reps == null || reps <= 0)) {
                    reasons += "$where: needs sets and reps"
                }
                WorkoutItem(
                    id = newId(),
                    exerciseId = if (name.isEmpty()) "" else resolve(item, name),
                    mode = mode,
                    durationSec = duration.takeIf { mode == ItemMode.TIMED },
                    sets = sets.takeIf { mode == ItemMode.REPS },
                    reps = reps.takeIf { mode == ItemMode.REPS },
                    note = item.path("note").textOrNull(),
                )
            }
            if (items.isEmpty()) return@mapIndexedNotNull null
            Section(
                id = newId(),
                name = s.path("name").asText().ifBlank { label },
                rounds = rounds.coerceAtLeast(1),
                restBetweenExercises = s.path("restBetweenExercisesSec").restOrNull(),
                restBetweenRounds = s.path("restBetweenRoundsSec").restOrNull(),
                items = items,
            )
        }
        if (sections.isEmpty()) reasons += "The workout has no exercises"
        if (reasons.isNotEmpty()) return CoachResult.Invalid(reasons)

        val workout = Workout(
            id = newId(),
            name = root.path("name").asText().ifBlank { "Coach workout" },
            type = type,
            createdAt = now,
            sections = sections,
        )
        return CoachResult.Ok(workout, invented.values.toList())
    }

    private fun invent(id: String, name: String, item: JsonNode): Exercise {
        val primary = Muscle.fromLabel(item.path("primaryMuscle").asText()) ?: Muscle.FULL_BODY
        val tags = item.path("tags").mapNotNull { t ->
            val facet = Facet.entries.firstOrNull { it.name == t.path("facet").asText() } ?: return@mapNotNull null
            Tag(facet, t.path("value").asText())
        }.filter { TagVocabulary.isValid(it) && !(it.facet == Facet.MUSCLE && it.value == primary.label) }
        return Exercise(
            id = id,
            name = name,
            primaryMuscle = primary,
            tags = tags.toSet(),
            demoQuery = "${name.lowercase()} proper form",
            isCustom = true,
        )
    }

    private fun String.normalisedName() = lowercase().replace(Regex("[^a-z0-9]"), "")
    private fun JsonNode.textOrNull(): String? = if (isNull || isMissingNode) null else asText().ifBlank { null }
    private fun JsonNode.intOrNull(): Int? = if (isNull || isMissingNode) null else asInt()
    private fun JsonNode.restOrNull(): Rest? = intOrNull()?.takeIf { it > 0 }?.let { Rest.Timed(it) }
}
