package com.irlachievements.app.domain.achievements

import com.irlachievements.app.data.repository.InMemoryAchievementRepository
import com.irlachievements.app.domain.model.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class AchievementExpansionTest {
    private val now = LocalDateTime.of(2026, 8, 31, 13, 37)

    @Test
    fun `seven new activity sequences and five modes use exact sensor evidence`() = runTest {
        val idsToSequence = mapOf(
            "relay_race" to listOf(ActivityKind.WALKING, ActivityKind.RUNNING, ActivityKind.WALKING, ActivityKind.RUNNING),
            "park_and_run" to listOf(ActivityKind.IN_VEHICLE, ActivityKind.WALKING, ActivityKind.RUNNING),
            "human_power" to listOf(ActivityKind.IN_VEHICLE, ActivityKind.ON_BICYCLE, ActivityKind.WALKING),
            "pit_stop" to listOf(ActivityKind.IN_VEHICLE, ActivityKind.STILL, ActivityKind.IN_VEHICLE),
            "switchback" to listOf(ActivityKind.WALKING, ActivityKind.ON_BICYCLE, ActivityKind.WALKING),
            "sudden_sprint" to listOf(ActivityKind.STILL, ActivityKind.RUNNING, ActivityKind.STILL),
            "home_stretch" to listOf(ActivityKind.IN_VEHICLE, ActivityKind.WALKING, ActivityKind.STILL),
        )
        idsToSequence.forEach { (id, sequence) ->
            val definition = catalog(id)
            val repository = InMemoryAchievementRepository()
            val engine = AchievementEngine(repository, listOf(definition))
            engine.process(ActivitySequenceObserved(sequence.reversed(), occurredAt = now))
            if (sequence != sequence.reversed()) assertFalse(repository.getState(id)?.unlocked == true)
            engine.process(ActivitySequenceObserved(sequence, occurredAt = now.plusMinutes(1)))
            assertTrue("$id should unlock", repository.getState(id)!!.unlocked)
        }

        val fiveModes = catalog("five_modes")
        val repository = InMemoryAchievementRepository()
        val engine = AchievementEngine(repository, listOf(fiveModes))
        engine.process(ActivityTransitionObserved(ActivityKind.RUNNING, TransitionKind.ENTER, ActivityKind.entries.toSet() - ActivityKind.ON_FOOT, occurredAt = now))
        assertTrue(repository.getState("five_modes")!!.unlocked)
    }

    @Test
    fun `daily usage patterns evaluate the whole completed day`() = runTest {
        val cases = mapOf(
            "single_visit" to DayCompleted(1, now),
            "late_start" to DayCompleted(2, now, firstUnlockMinuteOfDay = 12 * 60),
            "morning_only" to DayCompleted(2, now, lastUnlockMinuteOfDay = 11 * 60 + 59),
            "office_hours" to DayCompleted(5, now, firstUnlockMinuteOfDay = 8 * 60, lastUnlockMinuteOfDay = 17 * 60 + 59),
            "night_shift" to DayCompleted(5, now, nightUnlocks = 5),
            "hourglass" to DayCompleted(12, now, activeUnlockHours = 12),
            "rapid_fire" to DayCompleted(5, now, maxUnlocksInFiveMinutes = 5),
            "deliberate" to DayCompleted(4, now, minimumGapMinutes = 60.0),
        )
        cases.forEach { (id, event) ->
            val repository = InMemoryAchievementRepository()
            AchievementEngine(repository, listOf(catalog(id))).process(event)
            assertTrue("$id should unlock", repository.getState(id)!!.unlocked)
        }
    }

    @Test
    fun `new calendar contexts and power collection are strict`() = runTest {
        val contextIds = listOf("prime_time", "calendar_echo", "date_stamp", "weekend_wireless", "low_power_return", "dock_worker", "anniversary_charge")
        val contextCatalog = contextIds.map(::catalog)
        val repository = InMemoryAchievementRepository()
        val engine = AchievementEngine(repository, contextCatalog)
        val matching = AppOpened(
            1, 1, 365, daysSinceLastOpen = 30, batteryPercent = 10, isCharging = true,
            powerSource = PowerSource.WIRELESS, occurredAt = LocalDateTime.of(2026, 2, 2, 2, 2),
        )
        engine.process(matching)
        assertEquals(setOf("prime_time", "calendar_echo", "date_stamp", "low_power_return", "anniversary_charge"), repository.getAllStates().filter { it.unlocked }.map { it.achievementId }.toSet())

        val power = catalog("power_trinity")
        val powerRepository = InMemoryAchievementRepository()
        val powerEngine = AchievementEngine(powerRepository, listOf(power))
        listOf(PowerSource.DOCK, PowerSource.USB, PowerSource.AC).forEachIndexed { index, source ->
            powerEngine.process(AppOpened(index + 1, 1, 0, isCharging = true, powerSource = source, occurredAt = now.plusMinutes(index.toLong())))
        }
        assertFalse(powerRepository.getState("power_trinity")!!.unlocked)
        powerEngine.process(AppOpened(4, 1, 0, isCharging = true, powerSource = PowerSource.WIRELESS, occurredAt = now.plusMinutes(4)))
        assertTrue(powerRepository.getState("power_trinity")!!.unlocked)
    }

    @Test
    fun `new precision metas require their own challenge telemetry`() = runTest {
        val definitions = listOf("bullseye", "photo_finish", "perfect_landing", "final_form").map(::catalog)
        val repository = InMemoryAchievementRepository()
        val engine = AchievementEngine(repository, definitions)
        engine.process(ChallengeCompleted("full_circle", 1, 0, reactionMs = 200, angularError = 0f, occurredAt = now))
        assertTrue(repository.getAllStates().none { it.unlocked })
        engine.process(ChallengeCompleted("target_practice", 1, 0, angularError = 1f, occurredAt = now))
        engine.process(ChallengeCompleted("duel", 1, 0, reactionMs = 250, angularError = 5f, occurredAt = now))
        engine.process(ChallengeCompleted("clone", 1, 0, angularError = 1f, occurredAt = now))
        engine.process(ChallengeCompleted("perfect_run", 1, 0, angularError = .5f, occurredAt = now))
        assertEquals(definitions.map { it.id }.toSet(), repository.getAllStates().filter { it.unlocked }.map { it.achievementId }.toSet())
    }

    @Test
    fun `collection meta persists partial progress and unlocks on final member`() = runTest {
        val members = listOf("traffic_light", "duel").map {
            AchievementDefinition(it, it, it, AchievementCategory.CHALLENGE, false, Rarity.COMMON, RuleType.CHALLENGE_COMPLETED)
        }
        val meta = catalog("reaction_master")
        val repository = InMemoryAchievementRepository()
        val engine = AchievementEngine(repository, members + meta)
        engine.process(ForceUnlock("traffic_light", now))
        assertEquals(1.0, repository.getState("reaction_master")!!.currentProgress, 0.0)
        assertFalse(repository.getState("reaction_master")!!.unlocked)
        engine.process(ForceUnlock("duel", now.plusMinutes(1)))
        assertTrue(repository.getState("reaction_master")!!.unlocked)
    }

    @Test
    fun `challenge count and XP progression update from actual unlocked definitions`() = runTest {
        val challenges = listOf("full_circle", "duel").map {
            AchievementDefinition(it, it, it, AchievementCategory.CHALLENGE, false, Rarity.COMMON, RuleType.CHALLENGE_COMPLETED)
        }
        val rookie = AchievementDefinition("challenge_rookie", "Rookie", "", AchievementCategory.VISIBLE, false, Rarity.UNCOMMON, RuleType.CHALLENGE_COUNT, 2.0)
        val xp = AchievementDefinition("xp_hunter", "XP", "", AchievementCategory.VISIBLE, false, Rarity.EPIC, RuleType.XP_TOTAL, 20.0)
        val repository = InMemoryAchievementRepository()
        val engine = AchievementEngine(repository, challenges + rookie + xp)
        engine.process(ForceUnlock("full_circle", now))
        assertEquals(1.0, repository.getState("challenge_rookie")!!.currentProgress, 0.0)
        engine.process(ForceUnlock("duel", now.plusMinutes(1)))
        assertTrue(repository.getState("challenge_rookie")!!.unlocked)
        assertTrue(repository.getState("xp_hunter")!!.unlocked)
    }

    private fun catalog(id: String): AchievementDefinition = AchievementCatalog.byId.getValue(id)
}
