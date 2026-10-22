package com.irlachievements.app.domain.achievements

import com.irlachievements.app.data.repository.AchievementRepository
import com.irlachievements.app.data.repository.InMemoryAchievementRepository
import com.irlachievements.app.domain.model.AchievementState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class AchievementConcurrencyTest {
    @Test
    fun `simultaneous background and foreground events unlock and award only once`() = runTest {
        val memory = InMemoryAchievementRepository()
        var awards = 0
        val delayed = object : AchievementRepository by memory {
            override suspend fun getState(id: String): AchievementState? {
                val snapshot = memory.getState(id)
                yield()
                return snapshot
            }
            override suspend fun saveState(state: AchievementState, xpGranted: Int?) {
                if (xpGranted != null) awards++
                memory.saveState(state, xpGranted)
            }
        }
        val definition = AchievementCatalog.all.first { it.id == "full_circle" }
        val engine = AchievementEngine(delayed, listOf(definition))
        val results = List(20) {
            async { engine.process(ForceUnlock(definition.id, LocalDateTime.of(2026, 9, 4, 12, 0))) }
        }.awaitAll()
        assertEquals(1, results.sumOf { it.newlyUnlocked.size })
        assertEquals(1, awards)
        assertEquals(definition.rarity.xp, engine.summary().totalXp)
    }
}
