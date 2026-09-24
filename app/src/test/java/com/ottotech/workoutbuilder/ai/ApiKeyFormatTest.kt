package com.ottotech.workoutbuilder.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApiKeyFormatTest {
    @Test
    fun `normalise strips whitespace and line breaks from a pasted key`() {
        assertEquals("sk-ant-abc123", ApiKeyFormat.normalise("  sk-ant-abc\n123 \t"))
    }

    @Test
    fun `a key must start with sk-ant- and have a body`() {
        assertTrue(ApiKeyFormat.looksValid("sk-ant-api03-xyz"))
        assertFalse(ApiKeyFormat.looksValid("sk-ant-"))
        assertFalse(ApiKeyFormat.looksValid("sk-proj-xyz"))
        assertFalse(ApiKeyFormat.looksValid(""))
    }
}
