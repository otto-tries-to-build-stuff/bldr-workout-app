package com.ottotech.workoutbuilder.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.ThinkingConfigAdaptive
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

sealed interface CoachReply {
    data class Json(val text: String) : CoachReply
    data class Refused(val message: String) : CoachReply
    /** [detail] is the API's own error message when it sent one — shown for [FailureKind.REJECTED], logged always. */
    data class Failed(val kind: FailureKind, val detail: String? = null) : CoachReply
}

/** [REJECTED] is a 4xx the API explained (bad request, no credit…): retrying the same request won't help. */
enum class FailureKind { NO_NETWORK, BAD_KEY, BUSY, REJECTED, OTHER }

/**
 * Sorts whatever the SDK threw into something the screen can say. Catches everything: an
 * uncaught exception here would escape the view model's coroutine and crash the app — e.g. a
 * captive-portal HTML page makes the SDK throw AnthropicInvalidDataException.
 */
object CoachFailures {
    fun classify(e: Exception): CoachReply.Failed = when (e) {
        is UnauthorizedException, is PermissionDeniedException -> CoachReply.Failed(FailureKind.BAD_KEY)
        is RateLimitException -> CoachReply.Failed(FailureKind.BUSY)
        is AnthropicServiceException ->
            if (e.statusCode() >= 500) CoachReply.Failed(FailureKind.BUSY)
            else CoachReply.Failed(FailureKind.REJECTED, apiMessage(e))
        is AnthropicIoException -> CoachReply.Failed(FailureKind.NO_NETWORK)
        else -> CoachReply.Failed(FailureKind.OTHER, e.javaClass.simpleName)
    }

    private fun apiMessage(e: AnthropicServiceException): String? = try {
        e.body().convert(JsonNode::class.java)?.path("error")?.path("message")?.asText()?.ifBlank { null }
    } catch (_: Exception) {
        null
    } ?: "HTTP ${e.statusCode()}"
}

/**
 * One Coach request per call (spec §9): Claude Opus 5, adaptive thinking, medium effort,
 * structured output, cached system prompt, server-side refusal fallback. The SDK retries
 * 429/5xx/connection errors twice with backoff before an exception reaches us.
 *
 * Building the client and the first request pay a ~4 s one-time warm-up on Android (class
 * loading), so the screen calls [warmUp] when it opens.
 */
class CoachClient(apiKey: String) {

    private val client: AnthropicClient = AnthropicOkHttpClient.builder().apiKey(apiKey).build()

    private val schema: Map<String, JsonValue> =
        ObjectMapper().readValue(CoachSchema.JSON, Map::class.java)
            .map { (k, v) -> k as String to JsonValue.from(v) }.toMap()

    /** Touches the classes the real call needs without a network round trip. */
    fun warmUp() {
        buildParams("warm-up", listOf(Turn(true, "warm-up")))
    }

    fun generate(system: String, turns: List<Turn>): CoachReply = try {
        val response = client.messages().create(buildParams(system, turns))
        if (response.stopReason().orElse(null) == StopReason.REFUSAL) {
            val why = response.stopDetails().flatMap { it.explanation() }.orElse(null)
            CoachReply.Refused(why ?: "The Coach declined that request.")
        } else {
            // Kotlin collection ops, not Java streams: Stream.toList() needs API 34; minSdk is 26.
            val text = response.content().mapNotNull { block -> block.text().map { it.text() }.orElse(null) }.joinToString("")
            if (text.isBlank()) CoachReply.Failed(FailureKind.OTHER) else CoachReply.Json(text)
        }
    } catch (e: Exception) {
        CoachFailures.classify(e)
    }

    private fun buildParams(system: String, turns: List<Turn>): MessageCreateParams {
        val builder = MessageCreateParams.builder()
            .model("claude-opus-5")
            .maxTokens(16000L)
            .thinking(ThinkingConfigAdaptive.builder().build())
            .outputConfig(
                OutputConfig.builder()
                    .effort(OutputConfig.Effort.MEDIUM)
                    .format(JsonOutputFormat.builder().schema(JsonOutputFormat.Schema.builder().putAllAdditionalProperties(schema).build()).build())
                    .build(),
            )
            .systemOfTextBlockParams(
                listOf(TextBlockParam.builder().text(system).cacheControl(CacheControlEphemeral.builder().build()).build()),
            )
            // Server-side refusal fallback (spec §9). Sent as a raw header/body field: the SDK's
            // MessageCreateParams builder (2.34.0) has no typed setter for it.
            .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
        turns.forEach { if (it.fromUser) builder.addUserMessage(it.text) else builder.addAssistantMessage(it.text) }
        return builder.build()
    }
}
