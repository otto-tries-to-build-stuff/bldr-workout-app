package com.ottotech.workoutbuilder.ai

import com.anthropic.core.JsonValue
import com.anthropic.core.http.Headers
import com.anthropic.errors.AnthropicInvalidDataException
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.UnauthorizedException
import kotlin.test.Test
import kotlin.test.assertEquals

class CoachFailuresTest {

    private fun body(message: String) =
        JsonValue.from(mapOf("type" to "error", "error" to mapOf("type" to "invalid_request_error", "message" to message)))

    @Test
    fun `an unreadable response is a failure, not a crash`() {
        // e.g. a hotel Wi-Fi login page returned instead of JSON
        assertEquals(FailureKind.OTHER, CoachFailures.classify(AnthropicInvalidDataException("bad json")).kind)
    }

    @Test
    fun `a 400 carries the API's own message so the user and logcat can see why`() {
        val e = BadRequestException.builder()
            .headers(Headers.builder().build())
            .body(body("Your credit balance is too low to access the Anthropic API."))
            .build()
        val failed = CoachFailures.classify(e)
        assertEquals(FailureKind.REJECTED, failed.kind)
        assertEquals("Your credit balance is too low to access the Anthropic API.", failed.detail)
    }

    @Test
    fun `auth and network failures keep their own kinds`() {
        val unauthorized = UnauthorizedException.builder().headers(Headers.builder().build()).body(body("invalid x-api-key")).build()
        assertEquals(FailureKind.BAD_KEY, CoachFailures.classify(unauthorized).kind)
        assertEquals(FailureKind.NO_NETWORK, CoachFailures.classify(AnthropicIoException("offline")).kind)
    }
}
