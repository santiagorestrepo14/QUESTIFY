package com.irlachievements.app.domain.achievements

import com.irlachievements.app.data.repository.InMemoryAchievementRepository
import com.irlachievements.app.data.repository.AndroidUsageRepository
import com.irlachievements.app.domain.model.AchievementCategory
import com.irlachievements.app.domain.model.AchievementDefinition
import com.irlachievements.app.domain.model.Rarity
import com.irlachievements.app.domain.model.RuleType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class UsageAchievementTest {
    private val now = LocalDateTime.of(2026, 8, 30, 20, 0)

    @Test
    fun `reconciled unlock count activates daily threshold`() = runTest {
        val definition = definition("hello_again", RuleType.DAILY_UNLOCKS, 10.0)
        val repository = InMemoryAchievementRepository()
        val engine = AchievementEngine(repository, listOf(definition))

        engine.process(UnlockDetected(10, .5, now))

        assertTrue(repository.getState(definition.id)!!.unlocked)
    }

    @Test
    fun `verified gap between unlocks activates detox threshold`() = runTest {
        val definition = definition("digital_detox", RuleType.HOURS_WITHOUT_UNLOCK, 8.0)
        val repository = InMemoryAchievementRepository()
        val engine = AchievementEngine(repository, listOf(definition))

        engine.process(UnlockDetected(2, 8.0, now))

        assertTrue(repository.getState(definition.id)!!.unlocked)
    }

    @Test
    fun `completed day at or below maximum activates Controlled`() = runTest {
        val definition = definition("controlled", RuleType.MAX_DAILY_UNLOCKS, 20.0)
        val repository = InMemoryAchievementRepository()
        val engine = AchievementEngine(repository, listOf(definition))

        engine.process(DayCompleted(20, now))

        assertTrue(repository.getState(definition.id)!!.unlocked)
    }

    @Test
    fun `Untouched requires a completed day with exactly zero unlocks`() = runTest {
        val definition = definition("untouched", RuleType.MAX_DAILY_UNLOCKS, 0.0)
        val repository = InMemoryAchievementRepository()
        val engine = AchievementEngine(repository, listOf(definition))

        engine.process(DayCompleted(1, now))
        assertFalse(repository.getState(definition.id)?.unlocked == true)

        engine.process(DayCompleted(0, now.plusDays(1)))
        assertTrue(repository.getState(definition.id)!!.unlocked)
    }

    @Test
    fun `a zero count is not treated as a complete day without independent screen evidence`() {
        assertFalse(AndroidUsageRepository.hasReliableDayEvidence(0, 0, 0))
        assertTrue(AndroidUsageRepository.hasReliableDayEvidence(1, 0, 0))
        assertTrue(AndroidUsageRepository.hasReliableDayEvidence(0, 1, 0))
        assertTrue(AndroidUsageRepository.hasReliableDayEvidence(0, 0, 1))
    }

    private fun definition(id: String, rule: RuleType, target: Double) =
        AchievementDefinition(id, id, id, AchievementCategory.VISIBLE, false, Rarity.RARE, rule, target)
}
