package com.irlachievements.app.domain.challenges

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class PuzzleGameEngineTest {
    @Test fun ascendingRequiresAllNumbersInAllThreeLevels() {
        val engine = TouchGameEngine("ascending_path", 5)
        var state = engine.tick(0)
        var time = 0L
        repeat(3) { level ->
            assertEquals(level + 3, state.touchChoices.size)
            for (number in 1..(level + 3)) {
                state = engine.tap(state.touchChoices.indexOf(number), ++time)
            }
        }
        assertEquals(RunStatus.SUCCESS, state.status)
        assertEquals(state, engine.tap(0, 50_000))
    }

    @Test fun ascendingWrongOrderAndTimeoutFail() {
        val engine = TouchGameEngine("ascending_path", 5)
        val state = engine.tick(0)
        assertEquals(RunStatus.FAILED, engine.tap(state.touchChoices.indexOf(2), 1).status)
        assertEquals(RunStatus.FAILED, TouchGameEngine("ascending_path", 5).tick(25_001).status)
    }

    @Test fun intruderHasExactlyOneDifferentSymbolAtEveryLevel() {
        for (seed in 0..30) {
            val engine = TouchGameEngine("odd_symbol", seed)
            var state = engine.tick(0)
            repeat(3) { level ->
                assertEquals(listOf(4, 6, 9)[level], state.touchChoices.size)
                val counts = state.touchChoices.groupingBy { it }.eachCount()
                assertEquals(2, counts.size)
                val odd = counts.entries.single { it.value == 1 }.key
                state = engine.tap(state.touchChoices.indexOf(odd), level + 1L)
            }
            assertEquals(RunStatus.SUCCESS, state.status)
        }
    }

    @Test fun stereoRequiresPlaybackBeforeAnswerAndThreeLevels() {
        val engine = TouchGameEngine("stereo_compass", 7)
        var state = engine.tick(0)
        assertEquals(state, engine.tap(state.audioSide, 1))
        var time = 1L
        repeat(3) { level ->
            val sides = mutableSetOf<Int>()
            repeat(level + 2) {
                sides += state.audioSide
                state = engine.tap(10, ++time)
                assertEquals(TouchPhase.AUDIO_PLAYING, state.touchPhase)
                assertEquals(state, engine.tap(state.audioSide, ++time))
                state = engine.tap(11, ++time)
                state = engine.tap(state.audioSide, ++time)
            }
            assertEquals(if (level == 0) setOf(0, 1) else setOf(0, 1, 2), sides)
        }
        assertEquals(RunStatus.SUCCESS, state.status)
    }

    @Test fun audioErrorsAllowReplayButWrongDirectionsFail() {
        val engine = TouchGameEngine("stereo_compass", 7)
        engine.tap(10, 1)
        assertEquals(TouchPhase.AUDIO_READY, engine.tap(12, 2).touchPhase)
        assertEquals(RunStatus.RUNNING, engine.tick(1_000_000).status)
        engine.tap(10, 1_000_001)
        val state = engine.tap(11, 1_000_002)
        assertEquals(RunStatus.FAILED, engine.tap((state.audioSide + 1) % 3, 1_000_003).status)
    }

    @Test fun stereoPcmSeparatesSidesAndCentresWithoutClippingOrAbruptEdges() {
        for (side in 0..2) {
            val pcm = StereoCue.samples(side)
            assertEquals(StereoCue.sampleRate * StereoCue.durationMs / 1000 * 2, pcm.size)
            assertTrue(pcm.any { it.toInt() != 0 })
            assertTrue(pcm.all { abs(it.toInt()) <= 5900 })
            for (frame in 0 until pcm.size / 2) {
                if (side == 0) assertEquals(0, pcm[frame * 2 + 1].toInt())
                if (side == 1) assertEquals(0, pcm[frame * 2].toInt())
                if (side == 2) assertEquals(pcm[frame * 2], pcm[frame * 2 + 1])
            }
            assertEquals(0, pcm.first().toInt())
            assertEquals(0, pcm.last().toInt())
        }
    }
}
