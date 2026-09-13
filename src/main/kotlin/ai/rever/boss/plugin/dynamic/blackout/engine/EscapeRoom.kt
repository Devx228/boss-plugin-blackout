package ai.rever.boss.plugin.dynamic.blackout.engine

import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable data class IncidentPack(
    val id: String,
    val alarm: String,
    val shelter: String,
    val rescue: String,
    val ending: String,
    val ultravioletDiscovery: String,
    val ventilationDiscovery: String,
    val safeVentilation: String,
    val passwords: List<String>,
    val escapeEpilogue: List<String> = emptyList(),
    val trappedEpilogue: List<String> = emptyList()
)

/** Seeded room content. It contains the solution and is never serialized to an agent view. */
@Serializable data class EscapeScenario(
    val seed: Long,
    val incident: IncidentPack,
    val symbols: List<String>,
    val manualOrder: List<String>,
    val drawAmps: Int,
    val supplyVolts: Int,
    val releaseSeal: String,
    val channelOrder: List<String>,
    val recorderWaveform: String,
    val recorderChannels: List<String>,
    val startupOrder: List<String>,
    val cipher: String,
    val shift: Int,
    val password: String,
    val fragments: List<StoryFragment>,
    val storyOrder: List<String>
)

@Serializable data class StoryFragment(val id: String, val text: String)

object EscapeRoom {
    const val RULES_VERSION = "escape-2"
    const val INTRO = "The lights failed at 02:14. You woke in a sealed maintenance room. " +
        "You have eyes and hands; your AI companion has records and remote control. Neither of you can open the exit alone."

    val incidents = listOf(
        IncidentPack(
            "COOLANT",
            "MARA: The coolant alarm is real. I shut down the main bus before it could ignite.",
            "IVO: Power is gone. I sealed maintenance to keep the smoke out. There is someone still inside.",
            "MARA: I heard them knocking. I left the companion online. The manual release needs both of them.",
            "Mara stopped a coolant fire. Ivo sealed the room against smoke, and together they left a way out for two minds.",
            "UV paint: MARA — MAIN BUS CUT WAS DELIBERATE.",
            "As the smoke clears, an old handprint appears beside the companion terminal.",
            "EXHAUST", listOf("LIGHT", "HAVEN", "ALIVE"),
            escapeEpilogue = listOf(
                "Cold air rushes in. It tastes like rain.",
                "Down the corridor the coolant pipes are frosted but whole. Mara's shutdown held.",
                "Ivo's seal is still warm where the smoke pressed against it.",
                "On the companion terminal a new line blinks: THANK YOU FOR LISTENING.",
                "Two operators went in. Two came out."
            ),
            trappedEpilogue = listOf(
                "The fan coughs once, then stops.",
                "The last amber light shrinks to a red point. Then nothing.",
                "Your breathing is the loudest thing in the room.",
                "On the other side of the door, something knocks twice.",
                "The terminal types by itself: ARE YOU STILL THERE?"
            )
        ),
        IncidentPack(
            "FLOOD",
            "SENA: The lower conduit ruptured. I killed the pumps before the live bus touched the water.",
            "ORIN: The pumps stopped. I sealed maintenance above the flood line. Someone is trapped beyond the hatch.",
            "SENA: I heard their signal. The companion still has the dry-side controls. The release must be shared.",
            "Sena prevented an electrical flood. Orin held the water below the room, leaving the dry-side controls online for your escape.",
            "UV inspection ink: WATERLINE STABLE — DO NOT RESTART PUMPS.",
            "Dry air clears the window; tally marks show that someone waited here and kept counting.",
            "INTAKE", listOf("SHORE", "ABOVE", "DRY"),
            escapeEpilogue = listOf(
                "The hatch opens onto dry steel and the smell of salt.",
                "Below the grating, black water lies still. Sena's pumps never restarted.",
                "Orin's tally marks end exactly where your footprints begin.",
                "The dry-side lamps power down one by one, as if saying goodbye.",
                "The flood stayed below you. You both stayed above it."
            ),
            trappedEpilogue = listOf(
                "Water ticks against the hatch. Patient.",
                "The dry-side lamps go out in order, like someone walking away.",
                "In the dark, the tally marks feel closer than before.",
                "A slow drip lands on your shoulder. Then another.",
                "The water is rising. No one is counting now."
            )
        ),
        IncidentPack(
            "SPORE",
            "TALI: The greenhouse sensor found airborne spores. I isolated circulation before they reached the crew deck.",
            "REN: Airflow is gone. I put maintenance on hold pressure and sealed the inner door with someone inside.",
            "TALI: They answered the wall phone. I left the companion on the clean circuit. Two operators can release the seal.",
            "Tali contained the spores. Ren held the room at safe pressure, and the clean companion circuit carried you both through.",
            "Under UV, harmless spores trace an arrow toward the sealed wall phone.",
            "At hold pressure the rattling stops, revealing a faint voice preserved in the duct recorder.",
            "HOLD", listOf("CLEAN", "BLOOM", "BREATH"),
            escapeEpilogue = listOf(
                "The inner door exhales. The air is clean.",
                "In the greenhouse, the spores sleep behind Tali's sealed vents.",
                "Ren's pressure gauge rests exactly where it needed to be.",
                "The wall phone rings once. On the line, someone laughs with relief.",
                "The clean circuit carried two voices out of the dark."
            ),
            trappedEpilogue = listOf(
                "The pressure hiss fades to a whisper, then to nothing.",
                "Under your torch, pale threads drift across the glass.",
                "The wall phone rings. When you answer, you only hear breathing.",
                "It is not yours.",
                "The room holds its breath with you."
            )
        )
    )

    fun generate(seed: Long): EscapeScenario {
        val rng = Random(seed)
        val all = listOf("SUN", "WAVE", "LEAF", "MOON", "EYE", "STAR")
        val symbols = all.shuffled(rng).take(3)
        val manualOrder = all.shuffled(rng)
        val incident = incidents.random(rng)
        val startup = symbols.sortedBy { manualOrder.indexOf(it) }
        val password = incident.passwords.random(rng)
        val shift = rng.nextInt(1, 6)
        val cipher = password.map { 'A' + ((it - 'A' + shift) % 26) }.joinToString("")
        val labels = listOf("A", "B", "C").shuffled(rng)
        val fragments = listOf(
            StoryFragment(labels[0], incident.alarm),
            StoryFragment(labels[1], incident.shelter),
            StoryFragment(labels[2], incident.rescue)
        )
        return EscapeScenario(seed, incident, symbols, manualOrder,
            listOf(4, 5, 8, 10, 14, 16).random(rng), listOf(24, 48).random(rng),
            all.random(rng), all.shuffled(rng), all.random(rng), all.shuffled(rng), startup,
            cipher, shift, password, fragments.shuffled(rng), labels)
    }
}
