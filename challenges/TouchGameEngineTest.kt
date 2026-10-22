package com.irlachievements.app.domain.challenges

import org.junit.Assert.*
import org.junit.Test

class TouchGameEngineTest {
    @Test fun memoryRequiresAllThreeRoundsAndIgnoresPreviewTaps() {
        val engine = TouchGameEngine("color_echo", 7)
        assertEquals(TouchPhase.SHOW, engine.tap(0, 100).touchPhase)
        var time = 100L
        var state = engine.tick(time)
        repeat(3) { round ->
            val shown = mutableListOf<Int>()
            var previous: Int? = null
            while (state.touchPhase == TouchPhase.SHOW) {
                time += 100
                state = engine.tick(time)
                state.highlightedTile?.let { if (it != previous) shown += it }
                previous = state.highlightedTile
                assertTrue(time < 50_000)
            }
            assertEquals(3 + round, shown.size)
            assertEquals(TouchPhase.INPUT, state.touchPhase)
            shown.forEach { time += 100; state = engine.tap(it, time) }
            assertEquals(if (round == 2) RunStatus.SUCCESS else RunStatus.RUNNING, state.status)
        }
        assertEquals(3, state.step)
        assertEquals(1f, state.progress, 0f)
        assertEquals(state, engine.tap(2, time + 1))
    }

    @Test fun memoryRejectsWrongSymbolAndTimesOutWithoutInput() {
        val engine = TouchGameEngine("color_echo", 9)
        val first = engine.tick(600).highlightedTile!!
        engine.tick(5_100)
        val failed = engine.tap((first + 1) % 4, 5_200)
        assertEquals(RunStatus.FAILED, failed.status)
        assertEquals("TOUCH_SEQUENCE", failed.failureCode)
        assertEquals(failed, engine.tick(99_000))
        assertEquals(RunStatus.FAILED, TouchGameEngine("color_echo", 9).tick(25_101).status)
    }

    @Test fun reactionRequiresThreeSignalsAndMeasuresMean() {
        val engine = TouchGameEngine("quiet_signal", 4)
        var time = 0L
        var state = engine.tick(time)
        repeat(3) { round ->
            while (state.touchPhase != TouchPhase.SIGNAL) {
                time += 25
                state = engine.tick(time)
                assertTrue(time < 30_000)
            }
            time += 250
            state = engine.tap(0, time)
            assertEquals(if (round == 2) RunStatus.SUCCESS else RunStatus.RUNNING, state.status)
            if (round < 2) assertEquals(state, engine.tap(0, time + 1))
        }
        assertEquals(250L, state.reactionMs)
    }

    @Test fun reactionRejectsEarlyUnseenSignalAndLateTouches() {
        val early = TouchGameEngine("quiet_signal", 1).tap(0, 100)
        assertEquals("EARLY", early.failureCode)
        assertEquals("EARLY", TouchGameEngine("quiet_signal", 1).tap(0, 5_000).failureCode)
        val late = TouchGameEngine("quiet_signal", 1)
        assertEquals(TouchPhase.SIGNAL, late.tick(5_000).touchPhase)
        assertEquals(RunStatus.FAILED, late.tap(0, 8_001).status)
    }

    @Test fun touchGamesHaveNoHardwareRequirementsAndRestartCleanly() {
        TouchGameEngine.ids.forEach {
            assertTrue(ChallengeGameEngine.requiredCapabilities(it).isEmpty())
            assertEquals(RunStatus.RUNNING, ChallengeGameEngine(it).initialState().status)
        }
        assertEquals(RunStatus.RUNNING, TouchGameEngine("quiet_signal", 1).tick(0).status)
    }
}
