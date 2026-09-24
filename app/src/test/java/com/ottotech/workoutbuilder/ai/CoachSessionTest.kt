package com.ottotech.workoutbuilder.ai

import com.ottotech.workoutbuilder.domain.Exercise
import com.ottotech.workoutbuilder.domain.Muscle
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoachSessionTest {

    private val library = listOf(Exercise("push-up", "Push-up", Muscle.CHEST, demoQuery = "push-up form"))
    private var n = 0
    private val session = CoachSession(newId = { "id${n++}" }, now = { Instant.EPOCH })

    private fun json(name: String) = """{"name":"$name","type":"HIIT","sections":[{"name":"A","rounds":1,
        "restBetweenExercisesSec":null,"restBetweenRoundsSec":null,"items":[{"exerciseId":"push-up",
        "exerciseName":"Push-up","primaryMuscle":"Chest","tags":[],"mode":"TIMED","durationSec":30,
        "sets":null,"reps":null,"note":null}]}]}"""

    private fun previewOf(name: String): CoachPhase.Preview {
        assertNotNull(session.start("goal"))
        return assertIs<CoachPhase.Preview>(session.onReply(CoachReply.Json(json(name)), library))
    }

    @Test
    fun `a second request while one is in flight is refused, so a double tap bills once`() {
        assertNotNull(session.start("goal"))
        assertNull(session.start("goal"))
        assertNull(session.retry())
    }

    @Test
    fun `a failed tweak keeps the last good draft on screen with a notice`() {
        val good = previewOf("Leg Day")
        assertNotNull(session.tweak("more core"))
        val after = assertIs<CoachPhase.Preview>(session.onReply(CoachReply.Refused("declined"), library))
        assertEquals(good.result, after.result)
        assertEquals("declined", after.notice)
    }

    @Test
    fun `a failed regenerate also keeps the last good draft`() {
        val good = previewOf("Leg Day")
        assertNotNull(session.regenerate())
        val after = assertIs<CoachPhase.Preview>(session.onReply(CoachReply.Failed(FailureKind.BUSY), library))
        assertEquals(good.result, after.result)
        assertNotNull(after.notice)
    }

    @Test
    fun `after a failed tweak the next tweak still refines the last good draft`() {
        previewOf("Leg Day")
        assertNotNull(session.tweak("more core"))
        session.onReply(CoachReply.Failed(FailureKind.NO_NETWORK), library)
        val again = assertNotNull(session.tweak("more core"))
        assertEquals(3, again.turns.size)
        assertTrue(again.turns.last().fromUser)
    }

    @Test
    fun `a rejected key offers both retry and settings`() {
        assertNotNull(session.start("goal"))
        val problem = assertIs<CoachPhase.Problem>(session.onReply(CoachReply.Failed(FailureKind.BAD_KEY), library))
        assertTrue(problem.canRetry)
        assertTrue(problem.keyProblem)
    }

    @Test
    fun `a request the API rejects shows its reason and does not offer a pointless retry`() {
        assertNotNull(session.start("goal"))
        val problem = assertIs<CoachPhase.Problem>(
            session.onReply(CoachReply.Failed(FailureKind.REJECTED, "Your credit balance is too low."), library),
        )
        assertTrue("credit balance" in problem.message)
        assertEquals(false, problem.canRetry)
    }
}
