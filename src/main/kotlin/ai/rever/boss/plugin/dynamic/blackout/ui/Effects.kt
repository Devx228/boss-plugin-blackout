package ai.rever.boss.plugin.dynamic.blackout.ui

import ai.rever.boss.plugin.dynamic.blackout.application.RoomEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.math.PI
import kotlin.math.sin

/** Tiny generated cues avoid shipping media assets and keep sound entirely local. */
@Composable fun EffectSound(effect: RoomEffect?, muted: Boolean) {
    LaunchedEffect(effect?.id) {
        val current = effect ?: return@LaunchedEffect
        if (muted) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                when (current.kind) {
                    "BREAKER_TRIP", "KEYPAD_FAIL", "RECORDER_FAIL", "DECODER_FAIL", "ARM_FAIL", "VENT_FAIL" -> tone(145, 105)
                    "POWER_ON" -> { tone(280, 80); tone(420, 130) }
                    "CABINET_OPEN", "RECORDER_SYNC", "RECORDER_LOCK" -> { tone(440, 65); tone(620, 90) }
                    "ARMED" -> { tone(520, 55); tone(700, 55); tone(880, 90) }
                    "ESCAPE" -> { tone(440, 90); tone(660, 90); tone(880, 180) }
                    "TIMEOUT" -> tone(95, 350)
                    else -> tone(360, 45)
                }
            }
        }
    }
}

/**
 * Sound for the ending sequences: a warm chord each time the escape story advances, and a slow
 * heartbeat under the trapped sequence until the debrief appears.
 */
@Composable fun CinematicSound(cinematic: Cinematic, phase: Int, heartbeat: Boolean, muted: Boolean) {
    LaunchedEffect(cinematic, phase, muted) {
        if (muted || cinematic == Cinematic.NONE) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                if (cinematic == Cinematic.ESCAPE) when (phase) {
                    0 -> { tone(330, 140); tone(440, 140); tone(660, 260) }
                    1 -> tone(523, 120)
                    2 -> { tone(392, 120); tone(523, 120); tone(784, 300) }
                    else -> tone(659, 90)
                } else when (phase) {
                    0 -> { tone(110, 260); tone(82, 420) }
                    2 -> tone(70, 600)
                    else -> {}
                }
            }
        }
    }
    LaunchedEffect(cinematic, heartbeat, muted) {
        if (muted || cinematic != Cinematic.TRAPPED || !heartbeat) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            while (isActive) {
                runCatching { tone(62, 90); Thread.sleep(110); tone(55, 130) }
                delay(1100)
            }
        }
    }
}

private fun tone(frequency: Int, milliseconds: Int) {
    val sampleRate = 8_000f
    val count = (sampleRate * milliseconds / 1000).toInt()
    val bytes = ByteArray(count)
    for (i in bytes.indices) {
        val envelope = minOf(1.0, i / 80.0, (bytes.size - i) / 120.0).coerceAtLeast(0.0)
        bytes[i] = (sin(2.0 * PI * i * frequency / sampleRate) * 44.0 * envelope).toInt().toByte()
    }
    val line = AudioSystem.getSourceDataLine(AudioFormat(sampleRate, 8, 1, true, false))
    line.open(); line.start(); line.write(bytes, 0, bytes.size); line.drain(); line.close()
}
