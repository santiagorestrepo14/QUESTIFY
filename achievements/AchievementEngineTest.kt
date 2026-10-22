package com.irlachievements.app.domain.achievements

import com.irlachievements.app.data.repository.InMemoryAchievementRepository
import com.irlachievements.app.domain.model.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class AchievementEngineTest {
    private val now = LocalDateTime.of(2026, 8, 30, 12, 0)

    @Test
    fun `simple daily threshold unlocks at target`() = runTest {
        val definition = definition("daily", RuleType.DAILY_STEPS, 1_000.0)
        val (engine, repository) = engine(listOf(definition))

        engine.process(StepsUpdated(999, 999, occurredAt = now))
        assertFalse(repository.getState("daily")!!.unlocked)

        val result = engine.process(StepsUpdated(1_000, 1_000, occurredAt = now))
        assertEquals(listOf("daily"), result.newlyUnlocked.map { it.id })
    }

    @Test
    fun `accumulative target uses total steps`() = runTest {
        val definition = definition("total", RuleType.TOTAL_STEPS, 10_000.0)
        val (engine, repository) = engine(listOf(definition))

        engine.process(StepsUpdated(200, 9_999, occurredAt = now))
        assertFalse(repository.getState("total")!!.unlocked)
        engine.process(StepsUpdated(10, 10_000, occurredAt = now.plusDays(1)))
        assertTrue(repository.getState("total")!!.unlocked)
    }

    @Test
    fun `daily progress does not use accumulated total`() = runTest {
        val definition = definition("daily", RuleType.DAILY_STEPS, 5_000.0)
        val (engine, repository) = engine(listOf(definition))

        engine.process(StepsUpdated(400, 500_000, occurredAt = now))

        assertEquals(400.0, repository.getState("daily")!!.currentProgress, 0.0)
        assertFalse(repository.getState("daily")!!.unlocked)
    }

    @Test
    fun `streak unlocks on consecutive day target`() = runTest {
        val definition = definition("streak", RuleType.STEP_STREAK, 3.0)
        val (engine, repository) = engine(listOf(definition))

        engine.process(StepsUpdated(5_000, 5_000, consecutiveDaysAbove5k = 2, consecutiveDaysAbove10k = 2, occurredAt = now))
        engine.process(StepsUpdated(5_000, 10_000, consecutiveDaysAbove5k = 3, consecutiveDaysAbove10k = 3, occurredAt = now.plusDays(1)))

        assertTrue(repository.getState("streak")!!.unlocked)
    }

    @Test
    fun `hidden achievement remains typed hidden when unlocked`() = runTest {
        val secret = AchievementDefinition("secret", "Secret", "Hidden condition", AchievementCategory.HIDDEN, true, Rarity.RARE, RuleType.DAILY_STEPS, 10.0)
        val (engine, repository) = engine(listOf(secret))

        engine.process(StepsUpdated(10, 10, occurredAt = now))

        assertTrue(repository.getState("secret")!!.unlocked)
        assertTrue(secret.hidden)
    }

    @Test
    fun `unlock is idempotent and XP is granted once`() = runTest {
        val definition = definition("once", RuleType.DAILY_STEPS, 100.0, Rarity.EPIC)
        val (engine, repository) = engine(listOf(definition))

        assertEquals(1, engine.process(StepsUpdated(100, 100, occurredAt = now)).newlyUnlocked.size)
        assertTrue(engine.process(StepsUpdated(200, 200, occurredAt = now.plusMinutes(1))).newlyUnlocked.isEmpty())
        assertEquals(100, engine.summary().totalXp)
        assertNotNull(repository.getState("once")!!.unlockedAt)
    }

    @Test
    fun `achievement number 42 unlocks The Answer`() = runTest {
        val existing = (1..41).map { AchievementState("filler_$it", 1.0, true, it.toLong()) }
        val trigger = definition("trigger", RuleType.DAILY_STEPS, 1.0)
        val answer = AchievementDefinition("the_answer", "The Answer", "42", AchievementCategory.HIDDEN, true, Rarity.LEGENDARY, RuleType.UNLOCK_ORDINAL, 42.0)
        val fillers = (1..41).map { definition("filler_$it", RuleType.APP_OPEN, 1.0) }
        val repository = InMemoryAchievementRepository(existing)
        val engine = AchievementEngine(repository, fillers + trigger + answer, RandomSource { 1 })

        val result = engine.process(StepsUpdated(1, 1, occurredAt = now))

        assertTrue(result.newlyUnlocked.any { it.id == "the_answer" })
        assertTrue(repository.getState("the_answer")!!.unlocked)
    }

    @Test
    fun `The Answer is not skipped when one event crosses achievement 42`() = runTest {
        val existing = (1..40).map { AchievementState("filler_$it", 1.0, true, it.toLong()) }
        val triggers = (1..3).map { definition("trigger_$it", RuleType.DAILY_STEPS, it.toDouble()) }
        val answer = AchievementDefinition("the_answer", "The Answer", "42", AchievementCategory.HIDDEN, true, Rarity.LEGENDARY, RuleType.UNLOCK_ORDINAL, 42.0)
        val fillers = (1..40).map { definition("filler_$it", RuleType.APP_OPEN, 1.0) }
        val repository = InMemoryAchievementRepository(existing)
        val engine = AchievementEngine(repository, fillers + triggers + answer, RandomSource { 1 })

        val result = engine.process(StepsUpdated(3, 3, occurredAt = now))

        assertEquals(43, repository.getAllStates().count { it.unlocked && it.achievementId != "the_answer" })
        assertTrue(result.newlyUnlocked.any { it.id == "the_answer" })
    }

    @Test
    fun `multiple related achievements unlock from one event`() = runTest {
        val definitions = listOf(
            definition("one", RuleType.DAILY_STEPS, 1_000.0),
            definition("five", RuleType.DAILY_STEPS, 5_000.0),
            definition("ten", RuleType.DAILY_STEPS, 10_000.0),
        )
        val (engine, _) = engine(definitions)

        val result = engine.process(StepsUpdated(10_000, 10_000, occurredAt = now))

        assertEquals(setOf("one", "five", "ten"), result.newlyUnlocked.map { it.id }.toSet())
    }

    @Test
    fun `progress persists between events`() = runTest {
        val definition = definition("progress", RuleType.DAILY_STEPS, 1_000.0)
        val repository = InMemoryAchievementRepository()
        val firstEngine = AchievementEngine(repository, listOf(definition), RandomSource { 1 })
        firstEngine.process(StepsUpdated(420, 420, occurredAt = now))

        val restoredEngine = AchievementEngine(repository, listOf(definition), RandomSource { 1 })
        restoredEngine.process(StepsUpdated(600, 600, occurredAt = now.plusMinutes(1)))

        assertEquals(600.0, repository.getState("progress")!!.currentProgress, 0.0)
    }

    @Test
    fun `challenge completed on first attempt unlocks challenge and One Shot`() = runTest {
        val challenge = AchievementDefinition("full_circle", "Full Circle", "360", AchievementCategory.CHALLENGE, false, Rarity.COMMON, RuleType.CHALLENGE_COMPLETED)
        val oneShot = AchievementDefinition("one_shot", "One Shot", "First try", AchievementCategory.HIDDEN, true, Rarity.RARE, RuleType.CHALLENGE_META)
        val (engine, repository) = engine(listOf(challenge, oneShot))

        engine.process(ChallengeCompleted("full_circle", 1, 0, occurredAt = now))

        assertTrue(repository.getState("full_circle")!!.unlocked)
        assertTrue(repository.getState("one_shot")!!.unlocked)
    }

    @Test
    fun `five failures followed by completion unlocks Persistent`() = runTest {
        val challenge = AchievementDefinition("full_circle", "Full Circle", "360", AchievementCategory.CHALLENGE, false, Rarity.COMMON, RuleType.CHALLENGE_COMPLETED)
        val persistent = AchievementDefinition("persistent", "Persistent", "Keep going", AchievementCategory.HIDDEN, true, Rarity.EPIC, RuleType.CHALLENGE_META, 5.0)
        val (engine, repository) = engine(listOf(challenge, persistent))

        repeat(5) { failure -> engine.process(ChallengeFailed("full_circle", failure + 1, occurredAt = now.plusMinutes(failure.toLong()))) }
        engine.process(ChallengeCompleted("full_circle", 6, 5, occurredAt = now.plusHours(1)))

        assertTrue(repository.getState("persistent")!!.unlocked)
    }

    @Test
    fun `random achievement can be tested deterministically`() = runTest {
        val random = AchievementDefinition("glitch", "Glitch", "Random", AchievementCategory.HIDDEN, true, Rarity.LEGENDARY, RuleType.RANDOM_ROLL, 24.0)
        val (engine, repository) = engine(listOf(random), RandomSource { 0 })

        engine.process(AppOpened(1, 1, 0, occurredAt = now))

        assertTrue(repository.getState("glitch")!!.unlocked)
    }

    @Test
    fun `compound open contexts only unlock from matching observed data`() = runTest {
        val definitions = listOf(
            hiddenContext("life_support"),
            hiddenContext("overcharged"),
            hiddenContext("wireless"),
        )
        val (engine, repository) = engine(definitions)

        engine.process(
            AppOpened(
                1, 1, 0,
                batteryPercent = 3,
                isCharging = false,
                powerSource = PowerSource.NONE,
                occurredAt = now,
            ),
        )
        assertFalse(repository.getState("life_support")!!.unlocked)

        val result = engine.process(
            AppOpened(
                2, 1, 0,
                batteryPercent = 3,
                isCharging = true,
                powerSource = PowerSource.WIRELESS,
                occurredAt = now.plusMinutes(1),
            ),
        )
        assertEquals(setOf("life_support", "wireless"), result.newlyUnlocked.map { it.id }.toSet())
        assertFalse(repository.getState("overcharged")!!.unlocked)
    }

    @Test
    fun `clock battery and internal progress combinations are evaluated exactly`() = runTest {
        val definitions = listOf(
            hiddenContext("mirror_time"),
            hiddenContext("battery_synchronized"),
            hiddenContext("day_synchronized"),
            hiddenContext("perfect_count"),
            hiddenContext("checksum"),
            hiddenContext("triple_match"),
        )
        val (engine, _) = engine(definitions)
        val event = AppOpened(
            1, 1, 0,
            batteryPercent = 22,
            achievementsUnlocked = 22,
            occurredAt = LocalDateTime.of(2026, 8, 22, 22, 22),
        )

        val result = engine.process(event)

        assertEquals(
            setOf("mirror_time", "battery_synchronized", "day_synchronized", "perfect_count", "triple_match"),
            result.newlyUnlocked.map { it.id }.toSet(),
        )
        assertFalse(result.newlyUnlocked.any { it.id == "checksum" })
    }

    @Test
    fun `clock deja vu requires the same minute on three distinct dates`() = runTest {
        val dejaVu = hiddenContext("clock_deja_vu", 3.0)
        val (engine, repository) = engine(listOf(dejaVu))

        engine.process(AppOpened(1, 1, 0, sameClockMinuteDistinctDates = 2, occurredAt = now))
        assertFalse(repository.getState("clock_deja_vu")!!.unlocked)
        engine.process(AppOpened(1, 3, 2, sameClockMinuteDistinctDates = 3, occurredAt = now.plusDays(2)))

        assertTrue(repository.getState("clock_deja_vu")!!.unlocked)
    }

    @Test
    fun `derived challenge achievements require real telemetry`() = runTest {
        val definitions = listOf(
            AchievementDefinition("zero_point_zero", "0.00", "", AchievementCategory.HIDDEN, true, Rarity.LEGENDARY, RuleType.CHALLENGE_META),
            AchievementDefinition("are_you_a_robot", "Robot", "", AchievementCategory.HIDDEN, true, Rarity.EPIC, RuleType.CHALLENGE_META),
            AchievementDefinition("frame_perfect", "Frame", "", AchievementCategory.HIDDEN, true, Rarity.LEGENDARY, RuleType.CHALLENGE_META),
        )
        val (engine, repository) = engine(definitions)

        engine.process(ChallengeCompleted("target_practice", 1, 0, angularError = .8f, occurredAt = now))
        assertFalse(repository.getState("zero_point_zero")?.unlocked == true)
        engine.process(ChallengeCompleted("target_practice", 2, 0, angularError = .2f, occurredAt = now.plusMinutes(1)))
        engine.process(ChallengeCompleted("human_metronome", 1, 0, timingDeviationPercent = 2.5f, occurredAt = now.plusMinutes(2)))
        engine.process(ChallengeCompleted("duel", 1, 0, reactionMs = 145, occurredAt = now.plusMinutes(3)))

        assertTrue(repository.getState("zero_point_zero")!!.unlocked)
        assertTrue(repository.getState("are_you_a_robot")!!.unlocked)
        assertTrue(repository.getState("frame_perfect")!!.unlocked)
    }

    @Test
    fun `premature needs three persisted failures with the same cause`() = runTest {
        val premature = AchievementDefinition("premature", "Prematuro", "", AchievementCategory.HIDDEN, true, Rarity.RARE, RuleType.CHALLENGE_META, 3.0)
        val (engine, repository) = engine(listOf(premature))

        engine.process(ChallengeFailed("duel", 3, failureCode = "EARLY", repeatedFailureCount = 2, occurredAt = now))
        assertFalse(repository.getState("premature")?.unlocked == true)
        engine.process(ChallengeFailed("duel", 4, failureCode = "EARLY", repeatedFailureCount = 3, occurredAt = now.plusMinutes(1)))

        assertTrue(repository.getState("premature")!!.unlocked)
    }

    @Test
    fun `gyroscope master unlocks only after every original movement challenge`() = runTest {
        val movement = com.irlachievements.app.domain.challenges.ChallengeGameEngine.originalPlayableIds.map {
            AchievementDefinition(it, it, "", AchievementCategory.CHALLENGE, false, Rarity.COMMON, RuleType.CHALLENGE_COMPLETED)
        }
        val master = AchievementDefinition("gyroscope_master", "Master", "", AchievementCategory.CHALLENGE, false, Rarity.LEGENDARY, RuleType.CHALLENGE_COMPLETED)
        val (engine, repository) = engine(movement + master)

        movement.forEach { engine.process(ForceUnlock(it.id, now)) }

        assertTrue(repository.getState("gyroscope_master")!!.unlocked)
    }

    @Test
    fun `full battery cycle requires low observation before high observation`() = runTest {
        val cycle = AchievementDefinition("full_cycle", "Cycle", "", AchievementCategory.HIDDEN, true, Rarity.EPIC, RuleType.BATTERY_CYCLE, 2.0)
        val (engine, repository) = engine(listOf(cycle))

        engine.process(BatteryObserved(100, now))
        assertFalse(repository.getState("full_cycle")?.unlocked == true)

        engine.process(BatteryObserved(5, now.plusHours(1)))
        assertEquals(1.0, repository.getState("full_cycle")!!.currentProgress, 0.0)
        assertFalse(repository.getState("full_cycle")!!.unlocked)

        engine.process(BatteryObserved(95, now.plusHours(2)))
        assertTrue(repository.getState("full_cycle")!!.unlocked)
    }

    @Test
    fun `new skill achievements reject ordinary results and accept their exact telemetry`() = runTest {
        val definitions = listOf(
            AchievementDefinition("so_close", "Close", "", AchievementCategory.HIDDEN, true, Rarity.RARE, RuleType.CHALLENGE_META),
            AchievementDefinition("perfect_combo", "Combo", "", AchievementCategory.HIDDEN, true, Rarity.LEGENDARY, RuleType.CHALLENGE_META),
            AchievementDefinition("cold_blooded", "Cold", "", AchievementCategory.HIDDEN, true, Rarity.EPIC, RuleType.CHALLENGE_META),
        )
        val (engine, repository) = engine(definitions)

        engine.process(ChallengeFailed("simon_says_ii", 1, completedSteps = 6, totalSteps = 8, occurredAt = now))
        engine.process(ChallengeCompleted("final_boss", 1, 0, angularError = 1.5f, occurredAt = now.plusMinutes(1)))
        engine.process(ChallengeCompleted("traffic_light", 1, 0, reactionMs = 250, occurredAt = now.plusMinutes(2)))
        assertFalse(repository.getState("so_close")?.unlocked == true)
        assertFalse(repository.getState("perfect_combo")?.unlocked == true)
        assertFalse(repository.getState("cold_blooded")?.unlocked == true)

        engine.process(ChallengeFailed("simon_says_ii", 2, completedSteps = 7, totalSteps = 8, occurredAt = now.plusMinutes(3)))
        engine.process(ChallengeCompleted("final_boss", 2, 0, angularError = .8f, occurredAt = now.plusMinutes(4)))
        engine.process(ChallengeCompleted("traffic_light", 2, 0, reactionMs = 500, occurredAt = now.plusMinutes(5)))
        assertTrue(repository.getState("so_close")!!.unlocked)
        assertTrue(repository.getState("perfect_combo")!!.unlocked)
        assertTrue(repository.getState("cold_blooded")!!.unlocked)
    }

    @Test
    fun `unrelated challenge events cannot unlock any specialized meta achievement`() = runTest {
        val meta = AchievementCatalog.all.filter { it.ruleType == RuleType.CHALLENGE_META }
        val (engine, repository) = engine(meta)

        val failed = engine.process(
            ChallengeFailed(
                challengeId = "full_circle",
                totalFailures = 1,
                failureCode = "ORDINARY_FAILURE",
                completedSteps = 1,
                totalSteps = 4,
                occurredAt = now,
            ),
        )
        val completed = engine.process(
            ChallengeCompleted(
                challengeId = "full_circle",
                attemptNumber = 2,
                previousFailures = 0,
                batteryPercent = 50,
                occurredAt = now.plusMinutes(1),
            ),
        )

        assertTrue(failed.newlyUnlocked.isEmpty())
        assertTrue(completed.newlyUnlocked.isEmpty())
        assertTrue(repository.getAllStates().none { it.unlocked })
    }

    @Test
    fun `device master needs one completed challenge from every physical sensor family`() = runTest {
        val familyChallenges = listOf(
            challengeDefinition("shake_it", Capability.ACCELEROMETER),
            challengeDefinition("steady_hand", Capability.ROTATION_VECTOR),
            challengeDefinition("full_circle", Capability.GYROSCOPE),
            challengeDefinition("spirit_level", Capability.GRAVITY),
            challengeDefinition("north_star", Capability.MAGNETOMETER),
            challengeDefinition("vampire", Capability.LIGHT),
        )
        val master = AchievementDefinition("device_master", "Master", "", AchievementCategory.HIDDEN, true, Rarity.LEGENDARY, RuleType.CHALLENGE_META, 6.0)
        val (engine, repository) = engine(familyChallenges + master)

        familyChallenges.dropLast(1).forEach { engine.process(ForceUnlock(it.id, now)) }
        assertFalse(repository.getState("device_master")?.unlocked == true)

        engine.process(ForceUnlock(familyChallenges.last().id, now.plusMinutes(1)))
        assertTrue(repository.getState("device_master")!!.unlocked)
    }

    private fun definition(id: String, rule: RuleType, target: Double, rarity: Rarity = Rarity.COMMON) =
        AchievementDefinition(id, id, id, AchievementCategory.VISIBLE, false, rarity, rule, target)

    private fun hiddenContext(id: String, target: Double = 1.0) =
        AchievementDefinition(id, id, id, AchievementCategory.HIDDEN, true, Rarity.RARE, RuleType.CONTEXTUAL_OPEN, target)

    private fun challengeDefinition(id: String, capability: Capability) =
        AchievementDefinition(id, id, id, AchievementCategory.CHALLENGE, false, Rarity.COMMON, RuleType.CHALLENGE_COMPLETED, requiredCapability = capability)

    private fun engine(definitions: List<AchievementDefinition>, random: RandomSource = RandomSource { 1 }): Pair<AchievementEngine, InMemoryAchievementRepository> {
        val repository = InMemoryAchievementRepository()
        return AchievementEngine(repository, definitions, random) to repository
    }
}
