package com.irlachievements.app.domain.challenges

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeGameEngineTest {
    @Test
    fun `all accepted challenges have a running engine`() {
        assertEquals(64, ChallengeGameEngine.playableIds.size)
        ChallengeGameEngine.playableIds.forEach { id ->
            val state = if (id in MicrophoneGameEngine.ids) MicrophoneGameEngine(id).initialState()
                else ChallengeGameEngine(id, seed = 7).process(frame(0))
            assertEquals("$id must be playable", RunStatus.RUNNING, state.status)
        }
    }

    @Test
    fun `secret code enforces order and neutral separation`() {
        val engine = ChallengeGameEngine("secret_code", seed = 1)
        engine.process(frame(0))
        engine.process(frame(100, z = .9f))
        engine.process(frame(200))
        engine.process(frame(300, x = -.9f))
        engine.process(frame(400))
        engine.process(frame(500, z = -.9f))
        engine.process(frame(600))
        val result = engine.process(frame(700, x = .9f))

        assertEquals(RunStatus.SUCCESS, result.status)
    }

    @Test
    fun `initial portrait posture calibrates as center instead of a wrong move`() {
        val engine = ChallengeGameEngine("secret_code", seed = 1)

        val result = engine.process(frame(0, y = .95f))

        assertEquals(RunStatus.RUNNING, result.status)
        assertEquals(0, result.step)
    }

    @Test
    fun `traffic light rejects early motion and records valid reaction`() {
        val early = ChallengeGameEngine("traffic_light", seed = 2)
        early.process(frame(0))
        assertEquals(RunStatus.FAILED, early.process(frame(500, acceleration = 3f)).status)

        val valid = ChallengeGameEngine("traffic_light", seed = 2)
        valid.process(frame(0))
        var time = 50L
        while (valid.process(frame(time)).instruction != "¡VERDE!") time += 50
        val result = valid.process(frame(time + 50, acceleration = 3f))
        assertEquals(RunStatus.SUCCESS, result.status)
        assertTrue(result.reactionMs != null)
    }

    @Test
    fun `human metronome accepts uniform physical intervals`() {
        val engine = ChallengeGameEngine("human_metronome", seed = 3)
        var result = engine.initialState()
        repeat(6) { index ->
            engine.process(frame(index * 500L, acceleration = 0f))
            result = engine.process(frame(index * 500L + 10, acceleration = 3f))
        }

        assertEquals(RunStatus.SUCCESS, result.status)
    }

    @Test
    fun `rhythm validates fast fast pause fast pattern`() {
        val engine = ChallengeGameEngine("rhythm", seed = 4)
        var result = engine.process(frame(0, acceleration = 3f))
        listOf(450L, 900L, 1_800L, 2_250L).forEach { time ->
            engine.process(frame(time - 100, acceleration = 0f))
            result = engine.process(frame(time, acceleration = 3f))
        }

        assertEquals(RunStatus.SUCCESS, result.status)
    }

    @Test
    fun `false start requires opposite finishing side`() {
        val engine = ChallengeGameEngine("false_start", seed = 5)
        engine.process(frame(0, roll = 40f))
        engine.process(frame(200, roll = 0f))
        val result = engine.process(frame(400, roll = -40f))

        assertEquals(RunStatus.SUCCESS, result.status)
    }

    @Test
    fun `full circle integrates real gyroscope rates`() {
        val engine = ChallengeGameEngine("full_circle", seed = 6)
        var result = engine.process(frame(0, gyroZ = 90f))
        repeat(40) { index -> result = engine.process(frame((index + 1) * 100L, gyroZ = 90f)) }

        assertEquals(RunStatus.SUCCESS, result.status)
    }

    @Test
    fun `backwards rotation rejects the wrong physical direction`() {
        val engine = ChallengeGameEngine("do_it_backwards", seed = 7)

        val result = engine.process(frame(0, gyroZ = -90f))

        assertEquals(RunStatus.FAILED, result.status)
    }

    @Test
    fun `north star requires a continuous verified heading`() {
        val engine = ChallengeGameEngine("north_star", seed = 8)
        engine.process(frame(0, yaw = 0f))

        val result = engine.process(frame(10_000, yaw = 0f))

        assertEquals(RunStatus.SUCCESS, result.status)
    }

    @Test
    fun `light challenges use measured lux and hold time`() {
        val vampire = ChallengeGameEngine("vampire", seed = 9)
        vampire.process(frame(0, lightLux = 30f))
        assertEquals(RunStatus.SUCCESS, vampire.process(frame(3_000, lightLux = 30f)).status)

        val supernova = ChallengeGameEngine("supernova", seed = 10)
        supernova.process(frame(0, lightLux = 300f))
        assertEquals(RunStatus.SUCCESS, supernova.process(frame(3_000, lightLux = 300f)).status)
    }


    @Test
    fun `light challenges require three continuous seconds at inclusive thresholds`() {
        listOf(Triple("vampire", 30f, 31f), Triple("supernova", 300f, 299f)).forEach { (id, valid, invalid) ->
            val engine = ChallengeGameEngine(id, seed = 9)
            engine.process(frame(0, lightLux = valid))
            assertEquals(RunStatus.RUNNING, engine.process(frame(2_999, lightLux = valid)).status)
            assertEquals(0f, engine.process(frame(3_000, lightLux = invalid)).progress)
            engine.process(frame(3_100, lightLux = valid))
            assertEquals(RunStatus.RUNNING, engine.process(frame(6_099, lightLux = valid)).status)
            assertEquals(RunStatus.SUCCESS, engine.process(frame(6_100, lightLux = valid)).status)
        }
    }

    @Test
    fun `invalid light measurements cannot unlock challenges`() {
        listOf("vampire", "supernova").forEach { id ->
            listOf(-1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { invalid ->
                val engine = ChallengeGameEngine(id, seed = 9)
                engine.process(frame(0, lightLux = invalid))
                val result = engine.process(frame(3_000, lightLux = invalid))
                assertEquals(RunStatus.RUNNING, result.status)
                assertEquals(0f, result.progress)
            }
        }
    }

    @Test
    fun `calibrated acceleration noise raises the shake threshold`() {
        val engine = ChallengeGameEngine("shake_it", seed = 1)

        val noisyButStill = engine.process(frame(1_000, acceleration = 2.5f, accelerationNoise = .35f))
        val deliberateShake = engine.process(frame(1_400, acceleration = 3f, accelerationNoise = .35f))

        assertEquals(0, noisyButStill.step)
        assertEquals(1, deliberateShake.step)
    }

    @Test
    fun `gyroscope deadband prevents stationary drift from accumulating turns`() {
        val engine = ChallengeGameEngine("full_circle", seed = 1)
        var result = engine.process(frame(0, gyroZ = 1f, gyroscopeNoise = .35f))

        repeat(100) { index ->
            result = engine.process(frame((index + 1) * 100L, gyroZ = 1f, gyroscopeNoise = .35f))
        }

        assertEquals(0f, result.progress, .001f)
    }

    @Test
    fun `poor compass precision cannot unlock an off-target heading`() {
        val engine = ChallengeGameEngine("north_star", seed = 1)

        engine.process(frame(0, yaw = 20f, headingAccuracy = 25f))
        val result = engine.process(frame(10_000, yaw = 20f, headingAccuracy = 25f))

        assertEquals(RunStatus.RUNNING, result.status)
        assertEquals(0f, result.progress, .001f)
    }

    @Test
    fun `one sustained acceleration peak cannot count as repeated shakes`() {
        val engine = ChallengeGameEngine("shake_it")
        var result = engine.process(frame(0, acceleration = 3f))
        repeat(30) { result = engine.process(frame((it + 1) * 400L, acceleration = 3f)) }
        assertEquals(1, result.step)
        engine.process(frame(12_100, acceleration = 0f))
        result = engine.process(frame(12_500, acceleration = 3f))
        assertEquals(2, result.step)
    }

    @Test
    fun `slow motion accepts a full turn at eighteen degrees per second`() {
        val engine = ChallengeGameEngine("slow_motion")
        var result = engine.process(frame(0, gyroZ = 18f))
        repeat(201) { result = engine.process(frame((it + 1) * 100L, gyroZ = 18f)) }
        assertEquals(RunStatus.SUCCESS, result.status)
    }

    private fun frame(
        elapsedMs: Long,
        pitch: Float = 0f,
        roll: Float = 0f,
        x: Float = 0f,
        y: Float = 0f,
        z: Float = 0f,
        acceleration: Float = 0f,
        yaw: Float = 0f,
        gyroZ: Float = 0f,
        lightLux: Float = 0f,
        accelerationNoise: Float = .08f,
        gyroscopeNoise: Float = .35f,
        headingAccuracy: Float? = null,
    ) = SensorFrame(
        elapsedMs,
        pitch,
        roll,
        x,
        y,
        z,
        acceleration,
        yaw = yaw,
        gyroZ = gyroZ,
        lightLux = lightLux,
        accelerationNoiseFloor = accelerationNoise,
        gyroscopeNoiseFloor = gyroscopeNoise,
        headingAccuracyDegrees = headingAccuracy,
    )
}
