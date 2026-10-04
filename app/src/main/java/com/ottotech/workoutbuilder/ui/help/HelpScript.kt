package com.ottotech.workoutbuilder.ui.help

/**
 * The timeline of one how-to or welcome animation, kept free of Compose so it is testable on
 * the JVM. A script is a list of beats; each beat names what the scene shows ([Beat.key]) for
 * how long, and which numbered step it illustrates. Scenes draw from a [ScenePosition], so the
 * picture and the lit step always come from the same clock.
 */
class Beat(val key: Enum<*>, val step: Int, val ms: Int)

class Script(val beats: List<Beat>) {

    init {
        require(beats.isNotEmpty()) { "A script needs at least one beat" }
        require(beats.first().step == 0) { "Steps start at 0" }
        require(beats.zipWithNext().all { (a, b) -> b.step == a.step || b.step == a.step + 1 }) {
            "Steps must run in order with none skipped"
        }
        require(beats.all { it.ms > 0 }) { "Every beat needs a duration" }
        require(beats.map { it.key }.toSet().size == beats.size) { "Each key appears once" }
    }

    private val starts: IntArray = beats.runningFold(0) { acc, beat -> acc + beat.ms }.dropLast(1).toIntArray()

    val totalMs: Int = beats.sumOf { it.ms }
    val stepCount: Int = beats.last().step + 1

    /** Time at which [step]'s first beat begins. */
    fun startOf(step: Int): Int = starts[beats.indexOfFirst { it.step == step }]

    /** Time at which [step]'s last beat ends. */
    fun endOf(step: Int): Int {
        val last = beats.indexOfLast { it.step == step }
        return starts[last] + beats[last].ms
    }

    /** Where the animation is at [timeMs], wrapping round so it loops. */
    fun positionAt(timeMs: Long): ScenePosition {
        val t = Math.floorMod(timeMs, totalMs.toLong()).toInt()
        val i = starts.indexOfLast { it <= t }
        return ScenePosition(this, i, (t - starts[i]).toFloat() / beats[i].ms)
    }

    internal fun indexOf(key: Enum<*>): Int =
        beats.indexOfFirst { it.key == key }.also { require(it >= 0) { "$key is not in this script" } }
}

/** One moment of a script: the current beat and how far through it (0..1) the animation is. */
class ScenePosition internal constructor(private val script: Script, val index: Int, val progress: Float) {

    val key: Enum<*> get() = script.beats[index].key
    val step: Int get() = script.beats[index].step

    /** True from the start of [key] until the loop restarts. */
    fun reached(key: Enum<*>): Boolean = index >= script.indexOf(key)

    /** 0 before [key], its progress while it plays, 1 after: for changes that stay made. */
    fun progressOf(key: Enum<*>): Float {
        val i = script.indexOf(key)
        return when {
            index < i -> 0f
            index > i -> 1f
            else -> progress
        }
    }

    /** [key]'s progress while it plays, 0 otherwise: for passing effects such as a tap. */
    fun during(key: Enum<*>): Float = if (index == script.indexOf(key)) progress else 0f
}

/**
 * Builds a script one numbered step at a time:
 * `script { step(A to 600, B to 700); step(C to 1400) }`.
 */
fun script(build: ScriptBuilder.() -> Unit): Script = Script(ScriptBuilder().apply(build).beats)

class ScriptBuilder internal constructor() {
    internal val beats = mutableListOf<Beat>()
    private var step = -1

    fun step(vararg moments: Pair<Enum<*>, Int>) {
        step++
        moments.forEach { (key, ms) -> beats += Beat(key, step, ms) }
    }
}
