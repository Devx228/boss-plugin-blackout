package ai.rever.boss.plugin.dynamic.blackout.engine

import kotlinx.serialization.Serializable
import kotlin.random.Random

/** Original, seeded escape-room content. No clock, UI or provider dependencies. */
@Serializable data class EscapeScenario(
    val seed: Long,
    val symbols: List<String>,
    val manualOrder: List<String>,
    val drawAmps: Int,
    val supplyVolts: Int,
    val releaseSeal: String,
    val channelOrder: List<String>,
    val startupOrder: List<String>,
    val cipher: String,
    val shift: Int,
    val password: String,
    val fragments: List<StoryFragment>,
    val storyOrder: List<String>
)
@Serializable data class StoryFragment(val id: String, val text: String)

object EscapeRoom {
    const val RULES_VERSION = "escape-1"
    const val SECONDS = 600
    const val INTRO = "The lights went out at 02:14. You woke in a sealed maintenance room. " +
        "Your companion is an AI on the emergency terminal. You can see the room; it can read the records. " +
        "The air reserve lasts ten minutes. Find out what happened. Get both of you out."

    fun generate(seed: Long): EscapeScenario {
        val rng = Random(seed)
        val all = listOf("SUN", "WAVE", "LEAF", "MOON", "EYE", "STAR")
        val symbols = all.shuffled(rng).take(3)
        val manualOrder = all.shuffled(rng)
        val startup = symbols.sortedBy { manualOrder.indexOf(it) }
        val password = listOf("LIGHT", "HAVEN", "ORBIT", "DAWN", "HOME", "ALIVE").random(rng)
        val shift = rng.nextInt(1, 6)
        val cipher = password.map { 'A' + ((it - 'A' + shift) % 26) }.joinToString("")
        val labels = listOf("A", "B", "C").shuffled(rng)
        val fragments = listOf(
            StoryFragment(labels[0], "MARA: The coolant alarm is real. I shut down the main bus before it could ignite."),
            StoryFragment(labels[1], "IVO: Power is gone. I sealed maintenance to keep the smoke out. There is someone still inside."),
            StoryFragment(labels[2], "MARA: I heard them knocking. I left the companion online. The manual release needs both of them.")
        )
        return EscapeScenario(seed, symbols, manualOrder, listOf(4,5,8,10,14,16).random(rng), listOf(24,48).random(rng), all.random(rng), all.shuffled(rng),
            startup, cipher, shift, password, fragments.shuffled(rng), labels)
    }
}
