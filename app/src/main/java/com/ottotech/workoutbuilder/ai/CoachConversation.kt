package com.ottotech.workoutbuilder.ai

data class Turn(val fromUser: Boolean, val text: String)

/**
 * The message history sent to Claude. Tweak continues the conversation — the previous JSON
 * goes back as the assistant's turn, then the refinement — so Claude edits its own draft
 * rather than starting over. Regenerate re-asks the last user turn.
 */
data class CoachConversation(val turns: List<Turn>) {

    fun withReply(json: String) = CoachConversation(turns + Turn(fromUser = false, text = json))

    fun tweak(instruction: String): CoachConversation {
        check(turns.lastOrNull()?.fromUser == false) { "Tweak needs a reply to refine" }
        return CoachConversation(turns + Turn(fromUser = true, text = instruction.trim()))
    }

    fun forRegenerate(): CoachConversation =
        if (turns.lastOrNull()?.fromUser == false) CoachConversation(turns.dropLast(1)) else this

    companion object {
        fun start(request: String) = CoachConversation(listOf(Turn(fromUser = true, text = request)))
    }
}
