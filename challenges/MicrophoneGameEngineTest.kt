package com.irlachievements.app.domain.challenges

import com.irlachievements.app.domain.model.Capability
import org.junit.Assert.*
import org.junit.Test

class MicrophoneGameEngineTest {
    private fun calibrated(id: String) = MicrophoneGameEngine(id).also { engine ->
        for (time in 0L..2000L step 20) engine.process(time, -60f)
    }
    @Test fun intensityHasThreeLevelsAndCannotWinWithSilence() {
        val engine = calibrated("voice_balance")
        var state = engine.initialState()
        for (time in 2020L..15000L step 20) state = engine.process(time, -44f)
        assertEquals(RunStatus.SUCCESS, state.status)
        assertEquals(3, state.step)
        assertFalse(state.microphoneActive)
        val silent = calibrated("voice_balance")
        assertEquals(0f, silent.process(20_000, -60f).progress, 0f)
    }
    @Test fun tooLoudDoesNotRewardShoutingAndCalibrationRejectsNoise() {
        val engine = calibrated("voice_balance")
        for (time in 2020L..20_000L step 20) engine.process(time, -4f)
        assertEquals(0, engine.initialState().step)
        val noisy = MicrophoneGameEngine("voice_balance")
        noisy.process(0, -10f)
        assertEquals(RunStatus.FAILED, noisy.process(2000, -10f).status)
    }
    @Test fun clapPatternsRequireThreeCorrectRhythms() {
        val engine = calibrated("clap_echo")
        var levelStart = 2000L
        var state = engine.initialState()
        repeat(3) { level ->
            val pattern = MicrophoneGameEngine.rhythm(level)
            val inputStart = levelStart + pattern.last() + 2000
            pattern.forEach { beat ->
                state = engine.process(inputStart + beat, -30f)
                engine.process(inputStart + beat + 40, -60f)
            }
            levelStart = inputStart + pattern.last()
        }
        assertEquals(RunStatus.SUCCESS, state.status)
    }
    @Test fun demonstrationCannotCountAsClaps() {
        val engine = calibrated("clap_echo")
        for (time in 2020L..4500L step 20) engine.process(time, if (time % 40 == 0L) -30f else -60f)
        assertEquals(0, engine.initialState().step)
    }
    @Test fun acousticShortAndLongPatternsCompleteThreeLevels() {
        val engine = calibrated("acoustic_key")
        var now = 2100L
        var state = engine.initialState()
        repeat(3) { level ->
            MicrophoneGameEngine.code(level).forEach { long ->
                engine.process(now, -30f)
                now += if (long) 900 else 300
                engine.process(now, -60f)
                now += 120
                state = engine.process(now, -60f)
                now += 200
            }
        }
        assertEquals(RunStatus.SUCCESS, state.status)
    }
    @Test fun silentPathRequiresFreshMotionAndCorrectSequence() {
        val noMotion = calibrated("silent_path")
        for (time in 2020L..20_000L step 20) noMotion.process(time, -60f, 4f, 0f, false)
        assertEquals(0, noMotion.initialState().step)
        val engine = calibrated("silent_path")
        var now = 2020L
        var state = engine.initialState()
        repeat(3) { level ->
            repeat(level + 1) { direction ->
                engine.process(now, -60f, 0f, 0f, true); now += 20
                repeat(35) {
                    state = engine.process(now, -60f, if (direction == 0) 4f else if (direction == 1) -4f else 0f,
                        if (direction == 2) -4f else 0f, true)
                    now += 20
                }
            }
        }
        assertEquals(RunStatus.SUCCESS, state.status)
    }
    @Test fun timeoutAndTerminalStatesNeverKeepMicrophoneActive() {
        val engine = calibrated("acoustic_key")
        val result = engine.process(47_001, -60f)
        assertEquals(RunStatus.FAILED, result.status)
        assertFalse(result.microphoneActive)
        assertEquals(result, engine.process(48_000, -30f))
    }
    @Test fun pcmLevelAndHardwareRequirementsAreExplicit() {
        assertEquals(-90f, MicrophoneGameEngine.levelDb(ShortArray(320), 320), .01f)
        assertEquals(-6.02f, MicrophoneGameEngine.levelDb(ShortArray(320) { 16384 }, 320), .02f)
        MicrophoneGameEngine.ids.forEach {
            assertTrue(Capability.MICROPHONE in ChallengeGameEngine.requiredCapabilities(it))
            assertFalse(ChallengeDiscovery.matches(ChallengeKind.SENSOR_FREE, it))
        }
        assertTrue(Capability.ACCELEROMETER in ChallengeGameEngine.requiredCapabilities("silent_path"))
    }
}
