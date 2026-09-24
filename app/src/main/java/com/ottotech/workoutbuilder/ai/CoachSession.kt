package com.ottotech.workoutbuilder.ai

import com.ottotech.workoutbuilder.domain.Exercise
import com.ottotech.workoutbuilder.domain.WorkoutSummarizer
import com.ottotech.workoutbuilder.domain.WorkoutSummary
import java.time.Instant

sealed interface CoachPhase {
    data object Compose : CoachPhase
    data object Loading : CoachPhase

    /**
     * [exerciseNames] covers library and newly invented exercises, keyed by id, for the preview
     * list. [notice] is set when a Tweak or Regenerate failed: the draft stays, with the reason.
     */
    data class Preview(
        val result: CoachResult.Ok,
        val summary: WorkoutSummary,
        val exerciseNames: Map<String, String>,
        val notice: String? = null,
    ) : CoachPhase

    data class Problem(val message: String, val canRetry: Boolean, val keyProblem: Boolean) : CoachPhase
}

/**
 * The Coach's decisions, free of Android and of the network so they can be tested: which
 * conversation to send for generate / tweak / regenerate / retry, and what the screen shows for
 * each reply.
 *
 * Two rules the screen relies on:
 * - one request at a time — every start method returns null while one is in flight, so a double
 *   tap never sends (and bills) two;
 * - a failure while refining a draft keeps that draft on screen with a notice rather than
 *   replacing it with an error page.
 */
class CoachSession(private val newId: () -> String, private val now: () -> Instant) {

    var inFlight = false
        private set

    /** What the last request sent; Retry re-sends it. */
    private var sent: CoachConversation? = null
    private var fromPreview = false

    private var lastPreview: CoachPhase.Preview? = null
    private var lastPreviewConversation: CoachConversation? = null

    fun start(request: String): CoachConversation? {
        if (inFlight) return null
        lastPreview = null
        lastPreviewConversation = null
        return begin(CoachConversation.start(request), fromPreview = false)
    }

    fun regenerate(): CoachConversation? =
        lastPreviewConversation?.takeUnless { inFlight }?.let { begin(it.forRegenerate(), fromPreview = true) }

    fun tweak(instruction: String): CoachConversation? =
        lastPreviewConversation?.takeUnless { inFlight }?.let { begin(it.tweak(instruction), fromPreview = true) }

    fun retry(): CoachConversation? =
        sent?.takeUnless { inFlight }?.let { begin(it.forRegenerate(), fromPreview) }

    private fun begin(conversation: CoachConversation, fromPreview: Boolean): CoachConversation {
        inFlight = true
        sent = conversation
        this.fromPreview = fromPreview
        return conversation
    }

    fun onReply(sent: CoachConversation, reply: CoachReply, library: List<Exercise>): CoachPhase {
        inFlight = false
        val problem = when (reply) {
            is CoachReply.Json -> when (val r = CoachResponseMapper.map(reply.text, library, newId, now())) {
                is CoachResult.Ok -> return preview(sent.withReply(reply.text), r, library)
                is CoachResult.Invalid -> CoachPhase.Problem("Couldn't build that one.", canRetry = true, keyProblem = false)
            }
            is CoachReply.Refused -> CoachPhase.Problem(reply.message, canRetry = false, keyProblem = false)
            is CoachReply.Failed -> problemFor(reply)
        }
        val keep = lastPreview
        return if (fromPreview && keep != null) keep.copy(notice = problem.message) else problem
    }

    private fun preview(conversation: CoachConversation, result: CoachResult.Ok, library: List<Exercise>): CoachPhase.Preview {
        val all = (library + result.newExercises).associateBy { it.id }
        val p = CoachPhase.Preview(result, WorkoutSummarizer.summarize(result.workout, all), all.mapValues { it.value.name })
        lastPreview = p
        lastPreviewConversation = conversation
        return p
    }

    private fun problemFor(failed: CoachReply.Failed): CoachPhase.Problem = when (failed.kind) {
        FailureKind.NO_NETWORK -> CoachPhase.Problem("No connection. Check your internet and try again.", canRetry = true, keyProblem = false)
        // Retry too: after fixing the key in Settings the user comes straight back here.
        FailureKind.BAD_KEY -> CoachPhase.Problem("Anthropic didn't accept your API key.", canRetry = true, keyProblem = true)
        FailureKind.BUSY -> CoachPhase.Problem("The Coach is busy right now. Try again in a moment.", canRetry = true, keyProblem = false)
        FailureKind.REJECTED -> CoachPhase.Problem(
            "Anthropic couldn't take that request: ${failed.detail ?: "no reason given"}",
            canRetry = false,
            keyProblem = false,
        )
        FailureKind.OTHER -> CoachPhase.Problem("Something went wrong. Try again.", canRetry = true, keyProblem = false)
    }
}
