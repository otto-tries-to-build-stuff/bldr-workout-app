package com.ottotech.workoutbuilder.ui.help

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HelpScriptTest {

    private enum class K { A, B, C, D }

    // Step 0: A (100) B (200). Step 1: C (300). Step 2: D (400). Total 1000.
    private val sample = script {
        step(K.A to 100, K.B to 200)
        step(K.C to 300)
        step(K.D to 400)
    }

    @Test
    fun `steps start and end where their beats do`() {
        assertEquals(1000, sample.totalMs)
        assertEquals(3, sample.stepCount)
        assertEquals(0, sample.startOf(0))
        assertEquals(300, sample.endOf(0))
        assertEquals(300, sample.startOf(1))
        assertEquals(600, sample.startOf(2))
        assertEquals(1000, sample.endOf(2))
    }

    @Test
    fun `position names the beat, its step and how far through it`() {
        val p = sample.positionAt(400)
        assertEquals(K.C, p.key)
        assertEquals(1, p.step)
        assertEquals(1f / 3f, p.progress, 0.001f)
        assertEquals(K.A, sample.positionAt(0).key)
        assertEquals(K.D, sample.positionAt(999).key)
    }

    @Test
    fun `time past the end loops back to the start`() {
        assertEquals(K.A, sample.positionAt(1000).key)
        assertEquals(K.C, sample.positionAt(2400).key)
    }

    @Test
    fun `reached, progressOf and during describe a key relative to now`() {
        val p = sample.positionAt(450) // halfway through C
        assertTrue(p.reached(K.B))
        assertTrue(p.reached(K.C))
        assertFalse(p.reached(K.D))
        assertEquals(1f, p.progressOf(K.A))
        assertEquals(0.5f, p.progressOf(K.C), 0.001f)
        assertEquals(0f, p.progressOf(K.D))
        assertEquals(0.5f, p.during(K.C), 0.001f)
        assertEquals(0f, p.during(K.A))
    }

    @Test
    fun `a script refuses repeated keys and missing durations`() {
        assertFailsWith<IllegalArgumentException> { script { step(K.A to 100, K.A to 100) } }
        assertFailsWith<IllegalArgumentException> { script { step(K.A to 0) } }
        assertFailsWith<IllegalArgumentException> { script { } }
    }

    @Test
    fun `every topic's animation has exactly one stretch per step`() {
        HelpTopic.entries.forEach { topic ->
            assertEquals(topic.steps.size, topic.script.stepCount, "${topic.name} steps vs animation")
        }
    }

    @Test
    fun `tour cards play as a single step`() {
        WelcomeCard.entries.mapNotNull { it.script }.forEach { assertEquals(1, it.stepCount) }
    }

    @Test
    fun `bold markers split into runs, unpaired markers stay as text`() {
        assertEquals(
            listOf("Tap " to false, "SAVE" to true, " now." to false),
            boldRuns("Tap **SAVE** now."),
        )
        assertEquals(listOf("Use **this" to false), boldRuns("Use **this"))
        assertEquals(listOf("SAVE" to true), boldRuns("**SAVE**"))
    }

    @Test
    fun `every step's bold markers are paired`() {
        HelpTopic.entries.flatMap { it.steps }.forEach { step ->
            assertEquals(0, step.split("**").size % 2 - 1, "Unpaired ** in: $step")
        }
    }
}
