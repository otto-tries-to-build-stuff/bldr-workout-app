package com.ottotech.workoutbuilder.ai

import com.ottotech.workoutbuilder.domain.Exercise
import com.ottotech.workoutbuilder.domain.Facet
import com.ottotech.workoutbuilder.domain.Muscle
import com.ottotech.workoutbuilder.domain.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoachPromptTest {
    private val a = Exercise("b-row", "Bent Row", Muscle.BACK, setOf(Tag(Facet.EQUIPMENT, "Barbell")), "row")
    private val b = Exercise("a-squat", "Air Squat", Muscle.LEGS, demoQuery = "squat")

    @Test
    fun `system prompt lists every exercise as id, name, muscle, tags`() {
        val s = CoachPrompt.system(listOf(a, b))
        assertTrue("b-row · Bent Row · Back · EQUIPMENT:Barbell" in s)
        assertTrue("a-squat · Air Squat · Legs" in s)
    }

    @Test
    fun `system prompt is identical regardless of library order, so it caches`() {
        assertEquals(CoachPrompt.system(listOf(a, b)), CoachPrompt.system(listOf(b, a)))
    }

    @Test
    fun `system prompt carries the tag vocabulary`() {
        val s = CoachPrompt.system(listOf(a))
        assertTrue("Pull-up bar" in s)
        assertTrue("Plyometric" in s)
    }

    @Test
    fun `request includes goal and only the chips that were chosen`() {
        val r = CoachPrompt.request("legs and core", minutes = 30, equipment = setOf("Dumbbell"))
        assertTrue("legs and core" in r)
        assertTrue("30 minutes" in r)
        assertTrue("Dumbbell" in r)
        val bare = CoachPrompt.request("anything", minutes = null, equipment = emptySet())
        assertFalse("minutes" in bare)
        assertFalse("Equipment" in bare)
    }
}
