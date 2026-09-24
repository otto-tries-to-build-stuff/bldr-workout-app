package com.ottotech.workoutbuilder.domain

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutDraftTest {

    private fun blank() = WorkoutDraft.newWorkout("w", Instant.EPOCH)

    /** A workout with one section "s" holding one default (timed 30s) item "i". */
    private fun oneItem(): Workout =
        WorkoutDraft.addItem(WorkoutDraft.addSection(blank(), "s"), "s", "i", "push-up")

    // ---- newWorkout ----

    @Test
    fun `a new workout is empty with the given id and no sections`() {
        val w = WorkoutDraft.newWorkout("abc", Instant.ofEpochSecond(5))
        assertEquals("abc", w.id)
        assertEquals("", w.name)
        assertEquals(TrainingType.STRENGTH, w.type)
        assertEquals(Instant.ofEpochSecond(5), w.createdAt)
        assertNull(w.lastPerformedAt)
        assertTrue(w.sections.isEmpty())
    }

    // ---- rename / setType ----

    @Test
    fun `rename sets the name`() {
        assertEquals("Leg Day", WorkoutDraft.rename(blank(), "Leg Day").name)
    }

    @Test
    fun `setType sets the type`() {
        assertEquals(TrainingType.HIIT, WorkoutDraft.setType(blank(), TrainingType.HIIT).type)
    }

    // ---- addSection ----

    @Test
    fun `addSection appends a section named for its position`() {
        val w = WorkoutDraft.addSection(blank(), "s1")
        assertEquals(1, w.sections.size)
        assertEquals("s1", w.sections[0].id)
        assertEquals("Section 1", w.sections[0].name)
        assertEquals(1, w.sections[0].rounds)
    }

    @Test
    fun `addSection numbers by the current count`() {
        val w = WorkoutDraft.addSection(WorkoutDraft.addSection(blank(), "s1"), "s2")
        assertEquals(listOf("Section 1", "Section 2"), w.sections.map { it.name })
    }

    // ---- removeSection ----

    @Test
    fun `removeSection drops the section`() {
        val w = WorkoutDraft.addSection(WorkoutDraft.addSection(blank(), "s1"), "s2")
        assertEquals(listOf("s2"), WorkoutDraft.removeSection(w, "s1").sections.map { it.id })
    }

    @Test
    fun `removeSection of an unknown id is a no-op`() {
        val w = WorkoutDraft.addSection(blank(), "s1")
        assertEquals(1, WorkoutDraft.removeSection(w, "nope").sections.size)
    }

    // ---- moveSection ----

    @Test
    fun `moveSection UP swaps with the previous section`() {
        val w = WorkoutDraft.addSection(WorkoutDraft.addSection(blank(), "s1"), "s2")
        val out = WorkoutDraft.moveSection(w, "s2", MoveDirection.UP)
        assertEquals(listOf("s2", "s1"), out.sections.map { it.id })
    }

    @Test
    fun `moveSection UP at the top is a no-op`() {
        val w = WorkoutDraft.addSection(WorkoutDraft.addSection(blank(), "s1"), "s2")
        val out = WorkoutDraft.moveSection(w, "s1", MoveDirection.UP)
        assertEquals(listOf("s1", "s2"), out.sections.map { it.id })
    }

    @Test
    fun `moveSection DOWN at the bottom is a no-op`() {
        val w = WorkoutDraft.addSection(WorkoutDraft.addSection(blank(), "s1"), "s2")
        val out = WorkoutDraft.moveSection(w, "s2", MoveDirection.DOWN)
        assertEquals(listOf("s1", "s2"), out.sections.map { it.id })
    }

    // ---- renameSection / setRounds ----

    @Test
    fun `renameSection changes only the target`() {
        val w = WorkoutDraft.addSection(WorkoutDraft.addSection(blank(), "s1"), "s2")
        val out = WorkoutDraft.renameSection(w, "s2", "Sprints")
        assertEquals(listOf("Section 1", "Sprints"), out.sections.map { it.name })
    }

    @Test
    fun `setRounds sets the count`() {
        val w = WorkoutDraft.addSection(blank(), "s1")
        assertEquals(3, WorkoutDraft.setRounds(w, "s1", 3).sections[0].rounds)
    }

    @Test
    fun `setRounds clamps below one to one`() {
        val w = WorkoutDraft.addSection(blank(), "s1")
        assertEquals(1, WorkoutDraft.setRounds(w, "s1", 0).sections[0].rounds)
    }

    // ---- setRest ----

    @Test
    fun `setRest sets a timed rest in the requested slot`() {
        val w = WorkoutDraft.addSection(blank(), "s1")
        val out = WorkoutDraft.setRest(w, "s1", RestSlot.BETWEEN_EXERCISES, Rest.Timed(15))
        assertEquals(Rest.Timed(15), out.sections[0].restBetweenExercises)
    }

    @Test
    fun `setRest null clears the slot`() {
        val w = WorkoutDraft.setRest(
            WorkoutDraft.addSection(blank(), "s1"), "s1", RestSlot.BETWEEN_ROUNDS, Rest.Manual,
        )
        val out = WorkoutDraft.setRest(w, "s1", RestSlot.BETWEEN_ROUNDS, null)
        assertNull(out.sections[0].restBetweenRounds)
    }

    @Test
    fun `setRest touches only its own slot`() {
        val w = WorkoutDraft.addSection(blank(), "s1")
        val out = WorkoutDraft.setRest(w, "s1", RestSlot.AFTER_SECTION, Rest.Timed(60))
        assertEquals(Rest.Timed(60), out.sections[0].restAfterSection)
        assertNull(out.sections[0].restBetweenExercises)
        assertNull(out.sections[0].restBetweenRounds)
    }

    // ---- addItem ----

    @Test
    fun `addItem appends a timed item defaulting to thirty seconds`() {
        val item = oneItem().sections[0].items.single()
        assertEquals("i", item.id)
        assertEquals("push-up", item.exerciseId)
        assertEquals(ItemMode.TIMED, item.mode)
        assertEquals(30, item.durationSec)
    }

    // ---- removeItem / moveItem ----

    @Test
    fun `removeItem drops the item`() {
        val w = WorkoutDraft.addItem(oneItem(), "s", "i2", "air-squat")
        assertEquals(listOf("i2"), WorkoutDraft.removeItem(w, "s", "i").sections[0].items.map { it.id })
    }

    @Test
    fun `moveItem DOWN swaps with the next item`() {
        val w = WorkoutDraft.addItem(oneItem(), "s", "i2", "air-squat")
        val out = WorkoutDraft.moveItem(w, "s", "i", MoveDirection.DOWN)
        assertEquals(listOf("i2", "i"), out.sections[0].items.map { it.id })
    }

    @Test
    fun `moveItem UP at the top is a no-op`() {
        val w = WorkoutDraft.addItem(oneItem(), "s", "i2", "air-squat")
        val out = WorkoutDraft.moveItem(w, "s", "i", MoveDirection.UP)
        assertEquals(listOf("i", "i2"), out.sections[0].items.map { it.id })
    }

    // ---- setItemMode ----

    @Test
    fun `switching to REPS sets default sets and reps`() {
        val item = WorkoutDraft.setItemMode(oneItem(), "s", "i", ItemMode.REPS).sections[0].items.single()
        assertEquals(ItemMode.REPS, item.mode)
        assertEquals(3, item.sets)
        assertEquals(10, item.reps)
    }

    @Test
    fun `switching to TIMED restores a default duration`() {
        val manual = WorkoutDraft.setItemMode(oneItem(), "s", "i", ItemMode.MANUAL)
        val timed = WorkoutDraft.setItemMode(manual, "s", "i", ItemMode.TIMED).sections[0].items.single()
        assertEquals(ItemMode.TIMED, timed.mode)
        assertEquals(30, timed.durationSec)
    }

    @Test
    fun `switching to MANUAL keeps the workout saveable`() {
        val w = WorkoutDraft.rename(
            WorkoutDraft.setItemMode(oneItem(), "s", "i", ItemMode.MANUAL), "M",
        )
        assertTrue(WorkoutDraft.canSave(w))
    }

    // ---- field setters ----

    @Test
    fun `setDuration updates the item`() {
        assertEquals(45, WorkoutDraft.setDuration(oneItem(), "s", "i", 45).sections[0].items.single().durationSec)
    }

    @Test
    fun `setSetsReps updates both`() {
        val item = WorkoutDraft.setSetsReps(oneItem(), "s", "i", 5, 8).sections[0].items.single()
        assertEquals(5, item.sets)
        assertEquals(8, item.reps)
    }

    @Test
    fun `setWeight sets and clears`() {
        val set = WorkoutDraft.setWeight(oneItem(), "s", "i", 20f)
        assertEquals(20f, set.sections[0].items.single().weightKg)
        assertNull(WorkoutDraft.setWeight(set, "s", "i", null).sections[0].items.single().weightKg)
    }

    @Test
    fun `setNote sets and blank clears`() {
        val set = WorkoutDraft.setNote(oneItem(), "s", "i", "touch the line")
        assertEquals("touch the line", set.sections[0].items.single().note)
        assertNull(WorkoutDraft.setNote(set, "s", "i", "   ").sections[0].items.single().note)
    }

    // ---- validation ----

    @Test
    fun `a blank name blocks saving`() {
        val w = oneItem() // name is still ""
        assertTrue("Name your workout" in WorkoutDraft.saveBlockers(w))
        assertFalse(WorkoutDraft.canSave(w))
    }

    @Test
    fun `a workout with no items blocks saving`() {
        val w = WorkoutDraft.rename(WorkoutDraft.addSection(blank(), "s"), "Empty")
        assertTrue("Add at least one exercise" in WorkoutDraft.saveBlockers(w))
    }

    @Test
    fun `a timed item with no duration blocks saving`() {
        val w = WorkoutDraft.setDuration(WorkoutDraft.rename(oneItem(), "T"), "s", "i", 0)
        assertTrue("Set a duration for every timed exercise" in WorkoutDraft.saveBlockers(w))
    }

    @Test
    fun `a reps item missing reps blocks saving`() {
        val reps = WorkoutDraft.setItemMode(WorkoutDraft.rename(oneItem(), "R"), "s", "i", ItemMode.REPS)
        val bad = WorkoutDraft.setSetsReps(reps, "s", "i", 3, 0)
        assertTrue("Set sets and reps for every rep exercise" in WorkoutDraft.saveBlockers(bad))
    }

    @Test
    fun `a named workout with one valid item can save`() {
        val w = WorkoutDraft.rename(oneItem(), "Court Speed")
        assertEquals(emptyList(), WorkoutDraft.saveBlockers(w))
        assertTrue(WorkoutDraft.canSave(w))
    }

    // ---- hasContent ----

    @Test
    fun `a brand-new draft with its seeded empty section has no content`() {
        // BuilderViewModel seeds one empty section, so "untouched" is not "no sections".
        val fresh = WorkoutDraft.addSection(
            WorkoutDraft.newWorkout("w", Instant.EPOCH),
            "s1",
        )
        assertFalse(WorkoutDraft.hasContent(fresh))
    }

    @Test
    fun `naming the workout counts as content`() {
        val named = WorkoutDraft.rename(
            WorkoutDraft.addSection(WorkoutDraft.newWorkout("w", Instant.EPOCH), "s1"),
            "Leg Day",
        )
        assertTrue(WorkoutDraft.hasContent(named))
    }

    @Test
    fun `adding an exercise counts as content`() {
        val withItem = WorkoutDraft.addItem(
            WorkoutDraft.addSection(WorkoutDraft.newWorkout("w", Instant.EPOCH), "s1"),
            "s1", "i1", "push-up",
        )
        assertTrue(WorkoutDraft.hasContent(withItem))
    }

    // ---- hasUnsavedChanges ----

    @Test
    fun `a new draft falls back to hasContent`() {
        val fresh = WorkoutDraft.addSection(blank(), "s1")
        assertFalse(WorkoutDraft.hasUnsavedChanges(original = null, draft = fresh))
        assertTrue(WorkoutDraft.hasUnsavedChanges(original = null, draft = WorkoutDraft.rename(fresh, "Leg Day")))
    }

    @Test
    fun `an edited workout that is unchanged has no unsaved changes`() {
        val saved = WorkoutDraft.rename(oneItem(), "Leg Day")
        assertFalse(WorkoutDraft.hasUnsavedChanges(original = saved, draft = saved))
    }

    @Test
    fun `any edit to a saved workout counts, including structure-only ones`() {
        // Unlike a new draft, adding an empty section to a saved workout is a real change to it.
        val saved = WorkoutDraft.rename(oneItem(), "Leg Day")
        assertTrue(WorkoutDraft.hasUnsavedChanges(saved, WorkoutDraft.addSection(saved, "s2")))
        assertTrue(WorkoutDraft.hasUnsavedChanges(saved, WorkoutDraft.setDuration(saved, "s", "i", 45)))
    }
}
