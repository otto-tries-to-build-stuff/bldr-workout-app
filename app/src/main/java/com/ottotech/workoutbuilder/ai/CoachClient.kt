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
import com.fasterxml.jackson.databind.ObjectMapper

sealed interface CoachReply {
    data class Json(val text: String) : CoachReply
    data class Refused(val message: String) : CoachReply
    data class Failed(val kind: FailureKind) : CoachReply
}

enum class FailureKind { NO_NETWORK, BAD_KEY, BUSY, OTHER }

/**
 * One Coach request per call (spec §9): Claude Opus 5, adaptive thinking, medium effort,
 * structured output, cached system prompt, server-side refusal fallback. The SDK retries
 * 429/5xx/connection errors twice with backoff before an exception reaches us.
 *
 * Building the client and the first request pay a ~4 s one-time warm-up on Android (class
 * loading — see STATUS.md), so the screen calls [warmUp] when it opens.
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
            // Not Stream.toList(): that is Java 16 / Android API 34, and minSdk is 26.
            val text = response.content().mapNotNull { block -> block.text().map { it.text() }.orElse(null) }.joinToString("")
            if (text.isBlank()) CoachReply.Failed(FailureKind.OTHER) else CoachReply.Json(text)
        }
    } catch (e: UnauthorizedException) {
        CoachReply.Failed(FailureKind.BAD_KEY)
    } catch (e: PermissionDeniedException) {
        CoachReply.Failed(FailureKind.BAD_KEY)
    } catch (e: RateLimitException) {
        CoachReply.Failed(FailureKind.BUSY)
    } catch (e: AnthropicServiceException) {
        CoachReply.Failed(if (e.statusCode() >= 500) FailureKind.BUSY else FailureKind.OTHER)
    } catch (e: AnthropicIoException) {
        CoachReply.Failed(FailureKind.NO_NETWORK)
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
            // Server-side refusal fallback (spec §9). Sent as raw header/body because the
            // typed builder may not expose it yet.
            .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
        turns.forEach { if (it.fromUser) builder.addUserMessage(it.text) else builder.addAssistantMessage(it.text) }
        return builder.build()
    }
}
