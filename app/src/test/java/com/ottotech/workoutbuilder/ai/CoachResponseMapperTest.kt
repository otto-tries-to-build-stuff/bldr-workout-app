package com.ottotech.workoutbuilder.ai

import com.ottotech.workoutbuilder.domain.Exercise
import com.ottotech.workoutbuilder.domain.Facet
import com.ottotech.workoutbuilder.domain.ItemMode
import com.ottotech.workoutbuilder.domain.Muscle
import com.ottotech.workoutbuilder.domain.Rest
import com.ottotech.workoutbuilder.domain.Tag
import com.ottotech.workoutbuilder.domain.TrainingType
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoachResponseMapperTest {

    private val library = listOf(
        Exercise("push-up", "Push-up", Muscle.CHEST, demoQuery = "push-up form"),
        Exercise("goblet-squat", "Goblet Squat", Muscle.LEGS, demoQuery = "goblet squat form"),
    )
    private var n = 0
    private fun map(json: String) = CoachResponseMapper.map(json, library, { "id${n++}" }, Instant.EPOCH)

    private fun item(
        id: String? = "push-up", name: String = "Push-up", muscle: String = "Chest",
        tags: String = "[]", mode: String = "TIMED", duration: Int? = 30,
        sets: Int? = null, reps: Int? = null, note: String? = null,
    ) = """{"exerciseId":${id?.let { "\"$it\"" }},"exerciseName":"$name","primaryMuscle":"$muscle",
        "tags":$tags,"mode":"$mode","durationSec":$duration,"sets":$sets,"reps":$reps,
        "note":${note?.let { "\"$it\"" }}}"""

    private fun workout(vararg sections: String, type: String = "HIIT") =
        """{"name":"Leg Burner","type":"$type","sections":[${sections.joinToString(",")}]}"""

    private fun section(vararg items: String, rounds: Int = 1, restEx: Int? = null, restRound: Int? = null) =
        """{"name":"Block A","rounds":$rounds,"restBetweenExercisesSec":$restEx,
            "restBetweenRoundsSec":$restRound,"items":[${items.joinToString(",")}]}"""

    @Test
    fun `a valid response maps to a workout with library exercises`() {
        val result = assertIs<CoachResult.Ok>(map(workout(section(item(), rounds = 3, restEx = 15, restRound = 60))))
        val w = result.workout
        assertEquals("Leg Burner", w.name)
        assertEquals(TrainingType.HIIT, w.type)
        assertEquals(Instant.EPOCH, w.createdAt)
        val s = w.sections.single()
        assertEquals(3, s.rounds)
        assertEquals(Rest.Timed(15), s.restBetweenExercises)
        assertEquals(Rest.Timed(60), s.restBetweenRounds)
        assertNull(s.restAfterSection)
        val i = s.items.single()
        assertEquals("push-up", i.exerciseId)
        assertEquals(ItemMode.TIMED, i.mode)
        assertEquals(30, i.durationSec)
        assertTrue(result.newExercises.isEmpty())
    }

    @Test
    fun `reps items carry sets and reps, and the note passes through`() {
        val ok = assertIs<CoachResult.Ok>(map(workout(section(item(mode = "REPS", duration = null, sets = 3, reps = 12, note = "slow down")))))
        val i = ok.workout.sections.single().items.single()
        assertEquals(3, i.sets); assertEquals(12, i.reps); assertEquals("slow down", i.note)
    }

    @Test
    fun `zero or null rests mean no rest`() {
        val ok = assertIs<CoachResult.Ok>(map(workout(section(item(), restEx = 0, restRound = null))))
        assertNull(ok.workout.sections.single().restBetweenExercises)
        assertNull(ok.workout.sections.single().restBetweenRounds)
    }

    @Test
    fun `a null exerciseId invents a custom exercise with valid tags only`() {
        val tags = """[{"facet":"EQUIPMENT","value":"Kettlebell"},{"facet":"EQUIPMENT","value":"Sandbag"},
            {"facet":"MUSCLE","value":"Legs"},{"facet":"MUSCLE","value":"Core"}]"""
        val ok = assertIs<CoachResult.Ok>(map(workout(section(item(id = null, name = "Kettlebell Halo", muscle = "Legs", tags = tags)))))
        val ex = ok.newExercises.single()
        assertEquals("custom-kettlebell-halo", ex.id)
        assertEquals("Kettlebell Halo", ex.name)
        assertEquals(Muscle.LEGS, ex.primaryMuscle)
        assertTrue(ex.isCustom)
        assertEquals("kettlebell halo proper form", ex.demoQuery)
        // Sandbag is not in the vocabulary; MUSCLE:Legs repeats the primary. Both dropped.
        assertEquals(setOf(Tag(Facet.EQUIPMENT, "Kettlebell"), Tag(Facet.MUSCLE, "Core")), ex.tags)
        assertEquals(ex.id, ok.workout.sections.single().items.single().exerciseId)
    }

    @Test
    fun `an exerciseId not in the library is treated as invented by name`() {
        val ok = assertIs<CoachResult.Ok>(map(workout(section(item(id = "made-up-id", name = "Bear Crawl", muscle = "Full body")))))
        assertEquals("custom-bear-crawl", ok.newExercises.single().id)
    }

    @Test
    fun `an invented name matching a library exercise reuses it`() {
        val ok = assertIs<CoachResult.Ok>(map(workout(section(item(id = null, name = "  goblet squat ")))))
        assertTrue(ok.newExercises.isEmpty())
        assertEquals("goblet-squat", ok.workout.sections.single().items.single().exerciseId)
    }

    @Test
    fun `the same invented name twice creates one exercise`() {
        val a = item(id = null, name = "Bear Crawl", muscle = "Full body")
        val ok = assertIs<CoachResult.Ok>(map(workout(section(a, a))))
        assertEquals(1, ok.newExercises.size)
    }

    @Test
    fun `invented ids are stable across calls so re-saving does not duplicate`() {
        assertEquals(CoachResponseMapper.customIdFor("Bear Crawl!"), CoachResponseMapper.customIdFor("bear  crawl"))
    }

    @Test
    fun `empty sections are dropped`() {
        val ok = assertIs<CoachResult.Ok>(map(workout(section(), section(item()))))
        assertEquals(1, ok.workout.sections.size)
    }

    @Test
    fun `validation failures are all reported`() {
        val bad = workout(
            section(
                item(duration = 0),
                item(mode = "REPS", duration = null, sets = 0, reps = null),
                item(name = " "),
                rounds = 0,
            ),
        )
        val invalid = assertIs<CoachResult.Invalid>(map(bad))
        assertTrue(invalid.reasons.any { "rounds" in it })
        assertTrue(invalid.reasons.any { "duration" in it })
        assertTrue(invalid.reasons.any { "sets" in it })
        assertTrue(invalid.reasons.any { "name" in it })
    }

    @Test
    fun `no exercises at all is invalid`() {
        assertIs<CoachResult.Invalid>(map(workout(section())))
        assertIs<CoachResult.Invalid>(map(workout()))
    }

    @Test
    fun `malformed JSON is invalid rather than a crash`() {
        assertIs<CoachResult.Invalid>(map("{not json"))
        assertIs<CoachResult.Invalid>(map("""{"name":"x"}"""))
    }
}
