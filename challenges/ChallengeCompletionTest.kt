package com.irlachievements.app.domain.challenges

import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Synthetic successful traces, not a substitute for physical sensor/device QA. */
@RunWith(Parameterized::class)
class ChallengeCompletionTest(private val id: String, private val seed: Int) {
    @Test
    fun completesTheEntireMechanic() {
        if (id in TouchGameEngine.ids || id in MicrophoneGameEngine.ids) {
            val state = if (id in TouchGameEngine.ids) InteractiveCompletionTrace.touch(id, seed)
                else InteractiveCompletionTrace.microphone(id)
            assertEquals("$id seed=$seed: $state", RunStatus.SUCCESS, state.status)
            assertEquals(1f, state.progress, 0f)
            return
        }
        val run = Trace(id, seed)
        when (id) {
            "full_circle", "do_it_backwards", "double_trouble", "beyblade", "let_it_rip", "flat_spin", "speedrun", "slow_motion" -> {
                val turns = when (id) { "double_trouble" -> 2; "beyblade" -> 5; "let_it_rip" -> 10; else -> 1 }
                val rate = when (id) { "slow_motion" -> 18f; "speedrun" -> 360f; else -> 180f }
                run.hold((turns * 360_000 / rate).toLong() + 100) { it.copy(gyroZ = rate) }
            }
            "triple_axis", "xyz" -> {
                for (axis in 0..2) run.hold(2_000) { it.copy(gyroX = if (axis == 0) 180f else 0f,
                    gyroY = if (axis == 1) 180f else 0f, gyroZ = if (axis == 2) 180f else 0f) }
            }
            "steady_hand" -> run.hold(20_000)
            "rock_solid" -> run.hold(10_000)
            "spirit_level" -> run.hold(15_000)
            "perfectionist" -> run.hold(5_000)
            "dont_move" -> run.hold(60_000)
            "upside_down" -> run.hold(30_100) { pose(it.elapsedMs, roll = 180f) }
            "standing_tall" -> run.hold(30_100) { pose(it.elapsedMs, pitch = -90f) }
            "shake_it", "shake_it_off", "are_you_okay" -> {
                val count = when (id) { "shake_it" -> 20; "shake_it_off" -> 50; else -> 100 }
                repeat(count) { run.hold(350); run.step { it.copy(linearAcceleration = 3f) } }
            }
            "left_right_left", "nod_yes" -> repeat(20) { index ->
                val angle = if (index % 2 == 0) 40f else -40f
                run.step { pose(it.elapsedMs, roll = if (id == "left_right_left") angle else 0f,
                    pitch = if (id == "nod_yes") angle else 0f) }
            }
            "flip_flop" -> repeat(10) { index -> run.step { pose(it.elapsedMs, roll = if (index % 2 == 0) 180f else 0f) } }
            "north_star", "four_winds", "wrong_way_around" -> {
                val targets = when (id) { "north_star" -> listOf(0f); "four_winds" -> listOf(0f, 90f, 180f, -90f); else -> listOf(-90f, 180f, 90f, 0f) }
                targets.forEach { heading -> run.hold(if (id == "north_star") 10_000 else 1_100) { it.copy(yaw = heading) } }
            }
            "simon_says", "simon_says_ii" -> {
                val targets = run.state.detail.split(" · ")
                run.hold(if (id == "simon_says") 3_000 else 5_000)
                targets.forEach(run::gesture)
            }
            "target_practice" -> repeat(5) {
                val angle = Regex("-?\\d+").find(run.state.instruction)!!.value.toFloat()
                run.hold(700) { pose(it.elapsedMs, roll = angle) }
            }
            "secret_code" -> {
                run.hold(100)
                run.gesture("Derecha")
                run.step { pose(it.elapsedMs, roll = 180f) }
                run.hold(100)
                run.gesture("Izquierda")
            }
            "false_start" -> { run.step { pose(it.elapsedMs, roll = 40f) }; run.step(); run.step { pose(it.elapsedMs, roll = -40f) } }
            "clone" -> { run.hold(1_600); run.step { pose(it.elapsedMs, roll = 30f) }; run.hold(1_400) }
            "the_bomb" -> run.hold(14_100)
            "traffic_light", "duel" -> {
                run.until { it.instruction == if (id == "duel") "¡YA!" else "¡VERDE!" }
                run.step { if (id == "duel") pose(it.elapsedMs, roll = 90f) else it.copy(linearAcceleration = 3f) }
            }
            "human_metronome" -> repeat(6) { run.hold(450); run.step { it.copy(linearAcceleration = 3f) } }
            "rhythm" -> {
                run.step { it.copy(linearAcceleration = 3f) }
                listOf(450L, 450L, 900L, 450L).forEach { interval ->
                    run.hold(interval - 50); run.step { it.copy(linearAcceleration = 3f) }
                }
            }
            "tilt_labyrinth" -> {
                // Up the left corridor, then right above the walls to the visible goal.
                repeat(47) { run.step { pose(it.elapsedMs, pitch = 30f) } }
                repeat(47) { run.step { pose(it.elapsedMs, roll = 30f) } }
            }
            "final_boss" -> {
                run.until { it.instruction == "EJECUTA DE MEMORIA" }
                listOf("Izquierda", "Arriba", "Derecha").forEach(run::gesture)
                run.step()
                val angle = Regex("-?\\d+").find(run.state.instruction)!!.value.toFloat()
                run.hold(950) { pose(it.elapsedMs, roll = angle) }
            }
            "perfect_run" -> {
                run.until { it.instruction.contains("¡SACUDE!") }
                run.step { it.copy(linearAcceleration = 3f) }
                run.hold(2_200)
                run.gesture("Izquierda")
                run.gesture("Derecha")
                run.hold(3_200)
                run.hold(1_100) { pose(it.elapsedMs, roll = 45f) }
            }
            "vampire" -> run.hold(3_000) { it.copy(lightLux = 1f) }
            "supernova" -> run.hold(3_100) { it.copy(lightLux = 25_000f) }
            else -> {
                val definition = EnvironmentalChallenges.byId[id] ?: error("Missing success trace for $id")
                run.hold(3_100) { it.copy(environmentalValue = definition.threshold) }
            }
        }
        assertEquals("$id seed=$seed: ${run.state}", RunStatus.SUCCESS, run.state.status)
        assertEquals(1f, run.state.progress, 0f)
    }

    private class Trace(id: String, seed: Int) {
        private val engine = ChallengeGameEngine(id, seed)
        private var time = 0L
        var state = engine.process(pose(0))
            private set

        fun step(transform: (SensorFrame) -> SensorFrame = { it }) {
            time += 50
            state = engine.process(transform(pose(time)))
            assertTrue("Unexpected failure at $time: $state", state.status == RunStatus.RUNNING || state.status == RunStatus.SUCCESS)
        }

        fun hold(ms: Long, transform: (SensorFrame) -> SensorFrame = { it }) = repeat((ms / 50).toInt()) { step(transform) }

        fun until(predicate: (ChallengeRunState) -> Boolean) {
            repeat(300) { if (predicate(state)) return; step() }
            error("Never reached cue: $state")
        }

        fun gesture(label: String) {
            for (angle in 10..60 step 10) step { tilt(it.elapsedMs, label, angle.toFloat()) }
            for (angle in 50 downTo 0 step 10) step { tilt(it.elapsedMs, label, angle.toFloat()) }
        }

        private fun tilt(time: Long, label: String, angle: Float) = when (label) {
            "Izquierda" -> pose(time, roll = -angle)
            "Derecha" -> pose(time, roll = angle)
            "Arriba" -> pose(time, pitch = angle)
            "Abajo" -> pose(time, pitch = -angle)
            else -> error("Unexpected gesture $label")
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}, seed={1}")
        fun cases(): List<Array<Any>> = ChallengeGameEngine.playableIds.flatMap { id ->
            listOf(1, 7, 42).map { seed -> arrayOf<Any>(id, seed) }
        }

        // Gravity and angles agree with SensorManager.getOrientation's pitch/roll axes.
        private fun pose(time: Long, pitch: Float = 0f, roll: Float = 0f): SensorFrame {
            val p = Math.toRadians(pitch.toDouble())
            val r = Math.toRadians(roll.toDouble())
            return SensorFrame(time, pitch, roll, (-sin(r) * cos(p)).toFloat(),
                (-sin(p)).toFloat(), (cos(r) * cos(p)).toFloat(), 0f)
        }
    }
}
