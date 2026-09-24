package com.ottotech.workoutbuilder.ai

/**
 * Pasting a key from a browser on a phone routinely drags in a trailing newline or a space
 * mid-string, so the key is normalised before it is stored. The check is a sanity check
 * against pasting the wrong thing, not validation — only the API can say a key is good.
 */
object ApiKeyFormat {
    private const val PREFIX = "sk-ant-"

    fun normalise(raw: String): String = raw.filterNot { it.isWhitespace() }

    fun looksValid(key: String): Boolean = key.startsWith(PREFIX) && key.length > PREFIX.length
}
