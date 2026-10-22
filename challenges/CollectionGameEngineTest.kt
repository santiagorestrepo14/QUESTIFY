package com.irlachievements.app.domain.challenges

import org.junit.Assert.*
import org.junit.Test

class CollectionGameEngineTest {
    @Test fun notesHaveSoftEdgesAndLimitedAmplitude() {
        val tones = (0..5).map(CollectionTone::samples)
        tones.forEach { samples ->
            assertEquals(8400, samples.size)
            assertEquals(0, samples.first().toInt())
            assertEquals(0, samples.last().toInt())
            assertTrue(samples.any { it.toInt() != 0 })
            assertTrue(samples.all { kotlin.math.abs(it.toInt()) <= 4000 })
        }
        tones.zipWithNext().forEach { (a, b) -> assertFalse(a.contentEquals(b)) }
    }
    @Test fun confirmationTracksSuccessfulPlaybackAndResetsOnError() {
        val e = CollectionGameEngine("sound_pairs", 1)
        assertFalse(e.tick(0).audioAnswerReady)
        assertFalse(e.tap(0, 0).audioAnswerReady)
        assertTrue(e.tap(CollectionGameEngine.HEARD, 0).audioAnswerReady)
        val first = e.tap(CollectionGameEngine.CONFIRM, 0)
        assertFalse(first.audioAnswerReady)
        assertNull(first.highlightedTile)
        e.tap(1, 0)
        assertFalse(e.tap(CollectionGameEngine.AUDIO_ERROR, 0).audioAnswerReady)
        assertEquals(0, e.tap(CollectionGameEngine.CONFIRM, 0).step)
    }
    @Test fun allRegisteredWithoutSensorsAndDiscardedAbsent() {
        CollectionGameEngine.ids.forEach {
            assertTrue(ChallengeGameEngine.playableIds.contains(it))
            assertTrue(ChallengeGameEngine.requiredCapabilities(it).isEmpty())
        }
        listOf("bridges", "selective_focus", "audio_filter", "sound_count").forEach {
            assertFalse(ChallengeGameEngine.playableIds.contains(it))
        }
    }
    @Test fun routeIsContinuousAndCompletesAcrossSeeds() {
        repeat(40) { seed ->
            val e = CollectionGameEngine("fleeting_route", seed)
            var time = 0L
            var s = e.tick(time)
            repeat(3) { level ->
                val n = listOf(4, 6, 8)[level]
                val path = (0 until n).map { e.tick(time + it * 900).highlightedTile!! }
                assertEquals(n, path.toSet().size)
                path.zipWithNext().forEach { (a, b) -> assertEquals(1, kotlin.math.abs(a/3-b/3)+kotlin.math.abs(a%3-b%3)) }
                time += n * 900
                e.tick(time)
                path.forEach { s = e.tap(it, time) }
            }
            assertEquals(RunStatus.SUCCESS, s.status)
            assertEquals(s, e.tap(0, time + 10000))
        }
    }
    @Test fun observationHasBlankIntervalAndExactlyOneChange() {
        val e = CollectionGameEngine("subtle_change", 7)
        var time = 0L
        var s = e.tick(time)
        repeat(3) {
            val before = s.boardLabels
            assertEquals(TouchPhase.SHOW, e.tap(0, time).touchPhase)
            assertEquals(TouchPhase.BREAK, e.tick(time + 3500).touchPhase)
            s = e.tick(time + 4200)
            val changed = before.indices.filter { before[it] != s.boardLabels[it] }
            assertEquals(1, changed.size)
            time += 4200; s = e.tap(changed.single(), time)
        }
        assertEquals(RunStatus.SUCCESS, s.status)
    }
    @Test fun inverseUsesThreeRules() {
        val e = CollectionGameEngine("inverse_rule", 4)
        var s = e.tick(0)
        repeat(3) { level ->
            repeat(level + 3) {
                var answer = listOf("↑", "→", "↓", "←").indexOf(s.instruction.takeLast(1))
                if (s.instruction.startsWith("OPUESTO")) answer = (answer + 2) % 4
                s = e.tap(answer, 0)
            }
        }
        assertEquals(RunStatus.SUCCESS, s.status)
    }
    @Test fun piecesRequireCorrectPlacementAndThreeLevels() {
        val e = CollectionGameEngine("in_place", 4)
        var s = e.tick(0)
        repeat(3) {
            val reference = s.referenceLabels
            reference.forEachIndexed { target, symbol ->
                e.tap(s.boardLabels.take(reference.size).indexOf(symbol), 0)
                s = e.tap(reference.size + target, 0)
            }
        }
        assertEquals(RunStatus.SUCCESS, s.status)
    }
    @Test fun detectiveRequiresSuccessfulAudioThenFindsChange() {
        val e = CollectionGameEngine("sound_detective", 5)
        var s = e.tick(0)
        repeat(3) { level ->
            assertEquals(s, e.tap(0, 0))
            e.tap(CollectionGameEngine.PLAY, 0)
            e.tap(CollectionGameEngine.AUDIO_ERROR, 0)
            assertEquals(level, e.tap(0, 0).step)
            e.tap(CollectionGameEngine.PLAY, 0)
            s = e.tap(CollectionGameEngine.HEARD, 0)
            val n = level + 3
            val target = (0 until n).single { s.audioNotes[it] != s.audioNotes[n + 2 + it] }
            s = e.tap(target, 0)
        }
        assertEquals(RunStatus.SUCCESS, s.status)
    }
    @Test fun tonalOrderAndPairsCompleteOnlyAfterListening() {
        for (id in listOf("tone_ladder", "sound_pairs")) {
            val e = CollectionGameEngine(id, 12)
            var s = e.tick(0)
            repeat(3) {
                val notes = s.boardLabels.indices.associateWith { index ->
                    s = e.tap(index, 0)
                    val note = s.audioNotes.single()
                    s = e.tap(CollectionGameEngine.HEARD, 0)
                    note
                }
                val order = notes.entries.sortedBy { it.value }.map { it.key }
                order.forEach { index ->
                    e.tap(index, 0)
                    val pending = e.tap(CollectionGameEngine.CONFIRM, 0)
                    assertEquals(TouchPhase.AUDIO_PLAYING, pending.touchPhase)
                    e.tap(CollectionGameEngine.HEARD, 0)
                    s = e.tap(CollectionGameEngine.CONFIRM, 0)
                }
            }
            assertEquals(id, RunStatus.SUCCESS, s.status)
        }
    }
    @Test fun invalidInputsAndWrongAnswersAreSafe() {
        for (id in CollectionGameEngine.ids) {
            val e = CollectionGameEngine(id, 2)
            val s = e.tick(0)
            assertEquals(s, e.tap(-5, 0))
            assertEquals(s, e.tap(999, 0))
        }
        val e = CollectionGameEngine("inverse_rule", 2)
        val s = e.tick(0)
        val correct = listOf("↑", "→", "↓", "←").indexOf(s.instruction.takeLast(1))
        assertEquals(RunStatus.FAILED, e.tap((correct + 1) % 4, 0).status)
    }
}
