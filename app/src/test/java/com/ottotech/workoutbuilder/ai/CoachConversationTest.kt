package com.ottotech.workoutbuilder.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CoachConversationTest {
    @Test
    fun `tweak appends the reply and the instruction`() {
        val c = CoachConversation.start("goal").withReply("{json}").tweak("shorter")
        assertEquals(listOf(Turn(true, "goal"), Turn(false, "{json}"), Turn(true, "shorter")), c.turns)
    }

    @Test
    fun `regenerate drops only the trailing reply`() {
        val c = CoachConversation.start("goal").withReply("{a}").tweak("more core").withReply("{b}")
        assertEquals(3, c.forRegenerate().turns.size)
        assertEquals(Turn(true, "more core"), c.forRegenerate().turns.last())
    }

    @Test
    fun `tweak without a reply is a programming error`() {
        assertFailsWith<IllegalStateException> { CoachConversation.start("goal").tweak("x") }
    }
}
