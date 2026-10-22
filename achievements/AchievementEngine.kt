package com.irlachievements.app.domain.achievements

import com.irlachievements.app.data.repository.AchievementRepository
import com.irlachievements.app.domain.challenges.ChallengeGameEngine
import com.irlachievements.app.domain.model.*
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AchievementEngine(
    private val repository: AchievementRepository,
    private val catalog: List<AchievementDefinition> = AchievementCatalog.all,
    private val randomSource: RandomSource = SecureRandomSource(),
) {
    private val byId = catalog.associateBy { it.id }
    private val processingMutex = Mutex()

    suspend fun process(event: AchievementEvent): UnlockResult = processingMutex.withLock {
        val updated = mutableListOf<AchievementState>()
        val unlocked = mutableListOf<AchievementDefinition>()

        candidatesFor(event).forEach { definition ->
            val existing = repository.getState(definition.id)
                ?: AchievementState(achievementId = definition.id)
            if (existing.unlocked) return@forEach

            val progress = if (definition.ruleType == RuleType.RANDOM_ROLL) {
                existing.currentProgress + 1.0
            } else {
                progressFor(definition, event, existing).coerceAtLeast(existing.currentProgress)
            }
            val shouldUnlock = qualifies(definition, event, progress)
            val next = existing.copy(
                currentProgress = if (shouldUnlock) definition.target else progress.coerceAtMost(definition.target),
                unlocked = shouldUnlock,
                unlockedAt = if (shouldUnlock) event.occurredAt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() else null,
            )
            if (next != existing) {
                repository.saveState(next, if (shouldUnlock) definition.rarity.xp else null)
                updated += next
            }
            if (shouldUnlock) unlocked += definition
        }

        evaluateMeta(event, updated, unlocked)
        UnlockResult(unlocked.distinctBy { it.id }, updated.distinctBy { it.achievementId })
    }

    suspend fun summary(): PlayerSummary {
        val unlockedIds = repository.getAllStates().filter { it.unlocked }.mapTo(hashSetOf()) { it.achievementId }
        val unlockedDefinitions = catalog.filter { it.id in unlockedIds }
        return PlayerSummary(
            totalXp = unlockedDefinitions.sumOf { it.rarity.xp },
            achievementsUnlocked = unlockedDefinitions.size,
            totalAchievements = catalog.size,
            visibleUnlocked = unlockedDefinitions.count { it.category == AchievementCategory.VISIBLE },
            hiddenUnlocked = unlockedDefinitions.count { it.category == AchievementCategory.HIDDEN },
            challengesCompleted = unlockedDefinitions.count { it.category == AchievementCategory.CHALLENGE },
        )
    }

    private fun candidatesFor(event: AchievementEvent): List<AchievementDefinition> = when (event) {
        is StepsUpdated -> catalog.filter { it.ruleType in setOf(RuleType.DAILY_STEPS, RuleType.TOTAL_STEPS, RuleType.STEP_STREAK, RuleType.DAILY_DISTANCE, RuleType.TOTAL_DISTANCE) }
        is ActivityTransitionObserved -> catalog.filter { it.ruleType in setOf(RuleType.FIRST_ACTIVITY, RuleType.ACTIVITY_COMBINATION, RuleType.ACTIVITY_TRANSITION) }
        is ActivitySequenceObserved -> catalog.filter { it.ruleType == RuleType.ACTIVITY_SEQUENCE }
        is UnlockDetected -> catalog.filter { it.ruleType in setOf(RuleType.DAILY_UNLOCKS, RuleType.HOURS_WITHOUT_UNLOCK) }
        is DayCompleted -> catalog.filter { it.ruleType in setOf(RuleType.MAX_DAILY_UNLOCKS, RuleType.DAILY_USAGE_PATTERN) }
        is AppOpened -> catalog.filter { it.ruleType in setOf(RuleType.APP_OPEN, RuleType.DAYS_SINCE_INSTALL, RuleType.DISTINCT_APP_DAYS, RuleType.RETURN_AFTER_DAYS, RuleType.TIME_WINDOW, RuleType.CALENDAR_DATE, RuleType.SPECIAL_DATE, RuleType.CONTEXTUAL_OPEN, RuleType.POWER_SOURCE_COLLECTION, RuleType.RANDOM_ROLL) }
        is BatteryObserved -> catalog.filter { it.ruleType in setOf(RuleType.BATTERY_AT_MOST, RuleType.BATTERY_CYCLE) }
        is ChallengeCompleted -> catalog.filter { it.id == event.challengeId || it.ruleType == RuleType.CHALLENGE_META }
        is ChallengeFailed -> catalog.filter { it.id == "definition_of_insanity" || it.ruleType == RuleType.CHALLENGE_META }
        is ForceUnlock -> listOfNotNull(byId[event.achievementId])
        is DayChanged -> emptyList()
    }

    private fun progressFor(
        definition: AchievementDefinition,
        event: AchievementEvent,
        existing: AchievementState,
    ): Double = when (event) {
        is StepsUpdated -> when (definition.ruleType) {
            RuleType.DAILY_STEPS -> when (definition.id) {
                "night_walker" -> event.stepsBetweenMidnightAnd5.toDouble()
                "before_breakfast" -> event.stepsBefore8.toDouble()
                "overachiever" -> event.stepsBefore12.toDouble()
                else -> event.dailySteps.toDouble()
            }
            RuleType.TOTAL_STEPS -> event.totalSteps.toDouble()
            RuleType.STEP_STREAK -> if (definition.id == "consistency") event.consecutiveDaysAbove5k.toDouble() else event.consecutiveDaysAbove10k.toDouble()
            RuleType.DAILY_DISTANCE -> event.dailyDistanceMeters
            RuleType.TOTAL_DISTANCE -> event.totalDistanceMeters
            else -> 0.0
        }
        is ActivityTransitionObserved -> when (definition.ruleType) {
            RuleType.ACTIVITY_COMBINATION -> if (definition.id == "five_modes") {
                event.activitiesToday.intersect(setOf(ActivityKind.STILL, ActivityKind.WALKING, ActivityKind.RUNNING, ActivityKind.ON_BICYCLE, ActivityKind.IN_VEHICLE)).size.toDouble()
            } else {
                event.activitiesToday.intersect(setOf(ActivityKind.WALKING, ActivityKind.RUNNING, ActivityKind.ON_BICYCLE)).size.toDouble()
            }
            else -> 1.0
        }
        is ActivitySequenceObserved -> if (matchesSequence(definition.id, event)) definition.target else 0.0
        is UnlockDetected -> if (definition.ruleType == RuleType.DAILY_UNLOCKS) event.unlocksToday.toDouble() else event.hoursSincePreviousUnlock
        is DayCompleted -> if (definition.ruleType == RuleType.DAILY_USAGE_PATTERN) {
            if (matchesDailyUsage(definition.id, event)) definition.target else 0.0
        } else {
            event.unlocks.toDouble()
        }
        is AppOpened -> when (definition.ruleType) {
            RuleType.DAYS_SINCE_INSTALL -> event.daysSinceInstall.toDouble()
            RuleType.DISTINCT_APP_DAYS -> event.distinctUsageDays.toDouble()
            RuleType.RETURN_AFTER_DAYS -> event.daysSinceLastOpen.toDouble()
            RuleType.CONTEXTUAL_OPEN -> if (definition.id == "clock_deja_vu") event.sameClockMinuteDistinctDates.toDouble() else 1.0
            RuleType.POWER_SOURCE_COLLECTION -> (existing.currentProgress.toInt() or event.powerSource.collectionBit()).toDouble()
            RuleType.RANDOM_ROLL -> 1.0
            else -> 1.0
        }
        is BatteryObserved -> when (definition.ruleType) {
            RuleType.BATTERY_CYCLE -> when {
                event.percent <= 5 -> existing.currentProgress.coerceAtLeast(1.0)
                event.percent >= 95 && existing.currentProgress >= 1.0 -> 2.0
                else -> existing.currentProgress
            }
            else -> event.percent.toDouble()
        }
        is ChallengeCompleted -> when (definition.id) {
            "persistent", "definition_of_insanity" -> event.previousFailures.toDouble()
            "deja_vu" -> event.distinctCompletionDates.toDouble()
            else -> 1.0
        }
        is ChallengeFailed -> event.totalFailures.toDouble()
        is ForceUnlock -> definition.target
        is DayChanged -> 0.0
    }

    private fun qualifies(definition: AchievementDefinition, event: AchievementEvent, progress: Double): Boolean = when {
        event is ForceUnlock -> event.achievementId == definition.id
        definition.ruleType == RuleType.RANDOM_ROLL -> randomSource.nextInt(definition.target.toInt()) == 0
        definition.ruleType == RuleType.TIME_WINDOW && event is AppOpened -> matchesTime(definition.id, event.occurredAt.toLocalTime())
        definition.ruleType == RuleType.CALENDAR_DATE && event is AppOpened -> matchesDate(definition.id, event)
        definition.ruleType == RuleType.SPECIAL_DATE && event is AppOpened -> event.occurredAt.dayOfMonth == 13 && event.occurredAt.dayOfWeek == DayOfWeek.FRIDAY
        definition.ruleType == RuleType.CONTEXTUAL_OPEN && event is AppOpened -> matchesContext(definition.id, event)
        definition.ruleType == RuleType.POWER_SOURCE_COLLECTION && event is AppOpened -> (progress.toInt() and 7) == 7
        definition.ruleType == RuleType.DAILY_USAGE_PATTERN && event is DayCompleted -> matchesDailyUsage(definition.id, event)
        definition.ruleType == RuleType.FIRST_ACTIVITY && event is ActivityTransitionObserved -> matchesActivity(definition.id, event) && event.transition == TransitionKind.ENTER
        definition.ruleType == RuleType.ACTIVITY_TRANSITION && event is ActivityTransitionObserved -> event.previousActivity == ActivityKind.STILL && event.activity == ActivityKind.WALKING && event.transition == TransitionKind.ENTER
        definition.ruleType == RuleType.ACTIVITY_SEQUENCE && event is ActivitySequenceObserved -> matchesSequence(definition.id, event)
        definition.ruleType == RuleType.MAX_DAILY_UNLOCKS && event is DayCompleted -> event.unlocks <= definition.target
        definition.ruleType == RuleType.BATTERY_AT_MOST && event is BatteryObserved -> event.percent <= definition.target
        definition.ruleType == RuleType.BATTERY_CYCLE && event is BatteryObserved -> progress >= definition.target
        definition.id == "weekend_warrior" && event is StepsUpdated -> event.occurredAt.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) && progress >= definition.target
        definition.id == "the_trinity" && event is ActivityTransitionObserved -> progress >= definition.target
        definition.id == "against_all_odds" && event is ChallengeCompleted -> event.batteryPercent <= 5
        definition.id == "one_shot" && event is ChallengeCompleted -> event.attemptNumber == 1 && event.previousFailures == 0
        definition.id == "persistent" && event is ChallengeCompleted -> event.previousFailures >= 5
        definition.id == "definition_of_insanity" && event is ChallengeFailed -> event.totalFailures >= 10
        definition.id == "deja_vu" && event is ChallengeCompleted -> event.distinctCompletionDates >= 3
        definition.id == "three_am_gamer" && event is ChallengeCompleted -> event.occurredAt.hour == 3
        definition.id == "zero_point_zero" && event is ChallengeCompleted -> event.angularError != null && event.angularError <= .25f
        definition.id == "are_you_a_robot" && event is ChallengeCompleted -> event.challengeId == "human_metronome" && event.timingDeviationPercent != null && event.timingDeviationPercent <= 3f
        definition.id == "premature" && event is ChallengeFailed -> event.failureCode == "EARLY" && event.repeatedFailureCount >= 3
        definition.id == "photographic_memory" && event is ChallengeCompleted -> event.challengeId == "simon_says_ii" && event.attemptNumber == 1 && event.extraMovements == 0
        definition.id == "frame_perfect" && event is ChallengeCompleted -> event.reactionMs != null && event.reactionMs in 100L..180L
        definition.id == "redemption" && event is ChallengeCompleted -> event.previousFailures > 0 && event.isExceptionalExecution()
        definition.id == "so_close" && event is ChallengeFailed -> event.totalSteps >= 4 && event.completedSteps == event.totalSteps - 1
        definition.id == "perfect_combo" && event is ChallengeCompleted ->
            event.challengeId in setOf("final_boss", "perfect_run") && event.angularError != null && event.angularError <= 1f && event.extraMovements == 0
        definition.id == "cold_blooded" && event is ChallengeCompleted ->
            event.challengeId in setOf("traffic_light", "duel") && event.reactionMs != null && event.reactionMs in 350L..800L && event.extraMovements == 0
        definition.id == "bullseye" && event is ChallengeCompleted ->
            event.challengeId == "target_practice" && event.angularError != null && event.angularError <= 1f
        definition.id == "photo_finish" && event is ChallengeCompleted ->
            event.challengeId == "duel" && event.reactionMs != null && event.reactionMs in 181L..300L && event.angularError != null && event.angularError <= 5f
        definition.id == "perfect_landing" && event is ChallengeCompleted ->
            event.challengeId == "clone" && event.angularError != null && event.angularError <= 1f
        definition.id == "final_form" && event is ChallengeCompleted ->
            event.challengeId == "perfect_run" && event.angularError != null && event.angularError <= .5f
        definition.ruleType == RuleType.CHALLENGE_META -> false
        definition.ruleType == RuleType.CHALLENGE_COMPLETED && event is ChallengeCompleted -> definition.id == event.challengeId
        definition.ruleType == RuleType.APP_OPEN -> true
        else -> progress >= definition.target
    }

    private fun matchesActivity(id: String, event: ActivityTransitionObserved): Boolean = when (id) {
        "walk_it_off" -> event.activity == ActivityKind.WALKING
        "run" -> event.activity == ActivityKind.RUNNING
        "two_wheels" -> event.activity == ActivityKind.ON_BICYCLE
        "passenger" -> event.activity == ActivityKind.IN_VEHICLE
        else -> false
    }

    private fun matchesSequence(id: String, event: ActivitySequenceObserved): Boolean {
        val target = when (id) {
            "gear_change" -> listOf(ActivityKind.WALKING, ActivityKind.RUNNING, ActivityKind.WALKING)
            "accelerating" -> listOf(ActivityKind.STILL, ActivityKind.WALKING, ActivityKind.RUNNING, ActivityKind.ON_BICYCLE)
            "decelerating" -> listOf(ActivityKind.ON_BICYCLE, ActivityKind.RUNNING, ActivityKind.WALKING, ActivityKind.STILL)
            "traveler" -> listOf(ActivityKind.WALKING, ActivityKind.IN_VEHICLE, ActivityKind.WALKING)
            "door_to_door" -> listOf(ActivityKind.STILL, ActivityKind.WALKING, ActivityKind.IN_VEHICLE, ActivityKind.WALKING, ActivityKind.STILL)
            "without_engine" -> listOf(ActivityKind.IN_VEHICLE, ActivityKind.WALKING, ActivityKind.ON_BICYCLE)
            "three_terrains" -> listOf(ActivityKind.WALKING, ActivityKind.RUNNING, ActivityKind.ON_BICYCLE)
            "cool_down" -> listOf(ActivityKind.RUNNING, ActivityKind.WALKING, ActivityKind.STILL)
            "relay_race" -> listOf(ActivityKind.WALKING, ActivityKind.RUNNING, ActivityKind.WALKING, ActivityKind.RUNNING)
            "park_and_run" -> listOf(ActivityKind.IN_VEHICLE, ActivityKind.WALKING, ActivityKind.RUNNING)
            "human_power" -> listOf(ActivityKind.IN_VEHICLE, ActivityKind.ON_BICYCLE, ActivityKind.WALKING)
            "pit_stop" -> listOf(ActivityKind.IN_VEHICLE, ActivityKind.STILL, ActivityKind.IN_VEHICLE)
            "switchback" -> listOf(ActivityKind.WALKING, ActivityKind.ON_BICYCLE, ActivityKind.WALKING)
            "sudden_sprint" -> listOf(ActivityKind.STILL, ActivityKind.RUNNING, ActivityKind.STILL)
            "home_stretch" -> listOf(ActivityKind.IN_VEHICLE, ActivityKind.WALKING, ActivityKind.STILL)
            "reverse_gear" -> return event.reverseSequenceCompleted
            else -> return false
        }
        return event.recentEntries.endsWith(target)
    }

    private fun matchesTime(id: String, time: LocalTime): Boolean = when (id) {
        "witching_hour" -> time.hour == 3 && time.minute == 33
        "nice" -> time.hour == 4 && time.minute == 20
        "leet" -> time.hour == 13 && time.minute == 37
        "twelve_thirty_four" -> time.hour == 12 && time.minute == 34
        "not_found" -> time.hour == 4 && time.minute == 4
        "midnight_club" -> time.hour == 0 && time.minute == 0
        "early_bird" -> time >= LocalTime.of(5, 0) && time <= LocalTime.of(5, 30)
        "night_owl" -> time >= LocalTime.of(3, 0) && time < LocalTime.of(4, 0)
        else -> false
    }

    private fun matchesDate(id: String, event: AppOpened): Boolean = when (id) {
        "new_year_same_phone" -> event.occurredAt.monthValue == 1 && event.occurredAt.dayOfMonth == 1
        "leap_of_faith" -> event.occurredAt.monthValue == 2 && event.occurredAt.dayOfMonth == 29
        else -> false
    }

    private fun matchesContext(id: String, event: AppOpened): Boolean {
        val time = event.occurredAt.toLocalTime()
        val battery = event.batteryPercent
        return when (id) {
            "life_support" -> battery != null && battery <= 3 && event.isCharging
            "overcharged" -> battery == 100 && event.isCharging
            "mirror_time" -> "%02d%02d".format(time.hour, time.minute).let { it == it.reversed() }
            "battery_synchronized" -> battery != null && battery == time.minute
            "day_synchronized" -> battery != null && battery == event.occurredAt.dayOfMonth
            "perfect_count" -> battery != null && event.achievementsUnlocked > 0 && battery == event.achievementsUnlocked
            "checksum" -> battery != null && battery == time.hour + time.minute
            "triple_match" -> battery != null && event.achievementsUnlocked > 0 &&
                battery == time.minute && battery == event.achievementsUnlocked
            "clock_deja_vu" -> event.sameClockMinuteDistinctDates >= 3
            "wireless" -> event.isCharging && event.powerSource == PowerSource.WIRELESS
            "prime_time" -> time.hour.isPrime() && time.minute.isPrime()
            "calendar_echo" -> time.hour == event.occurredAt.dayOfMonth
            "date_stamp" -> time.hour == event.occurredAt.monthValue && time.minute == event.occurredAt.dayOfMonth
            "weekend_wireless" -> event.occurredAt.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) &&
                event.isCharging && event.powerSource == PowerSource.WIRELESS
            "low_power_return" -> event.daysSinceLastOpen >= 30 && battery != null && battery <= 10
            "dock_worker" -> event.isCharging && event.powerSource == PowerSource.DOCK
            "anniversary_charge" -> event.daysSinceInstall >= 365 && event.isCharging
            else -> false
        }
    }

    private fun matchesDailyUsage(id: String, event: DayCompleted): Boolean = when (id) {
        "single_visit" -> event.unlocks == 1
        "late_start" -> event.unlocks >= 1 && (event.firstUnlockMinuteOfDay ?: -1) >= 12 * 60
        "morning_only" -> event.unlocks >= 2 && (event.lastUnlockMinuteOfDay ?: Int.MAX_VALUE) < 12 * 60
        "office_hours" -> event.unlocks >= 5 && (event.firstUnlockMinuteOfDay ?: -1) >= 8 * 60 &&
            (event.lastUnlockMinuteOfDay ?: Int.MAX_VALUE) < 18 * 60
        "night_shift" -> event.nightUnlocks >= 5
        "hourglass" -> event.activeUnlockHours >= 12
        "rapid_fire" -> event.maxUnlocksInFiveMinutes >= 5
        "deliberate" -> event.unlocks in 2..12 && (event.minimumGapMinutes ?: 0.0) >= 60.0
        else -> false
    }

    private suspend fun evaluateMeta(
        event: AchievementEvent,
        updated: MutableList<AchievementState>,
        unlocked: MutableList<AchievementDefinition>,
    ) {
        val states = repository.getAllStates()
        val unlockedIds = states.filter { it.unlocked }.mapTo(hashSetOf()) { it.achievementId }

        suspend fun unlockMeta(id: String, progress: Double) {
            val definition = byId[id] ?: return
            if (id in unlockedIds) return
            val state = AchievementState(
                achievementId = id,
                currentProgress = definition.target,
                unlocked = true,
                unlockedAt = event.occurredAt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
            )
            repository.saveState(state, definition.rarity.xp)
            updated += state
            unlocked += definition
            unlockedIds += id
        }

        suspend fun updateMeta(id: String, progress: Double) {
            val definition = byId[id] ?: return
            val existing = repository.getState(id) ?: AchievementState(achievementId = id)
            if (existing.unlocked) return
            val bounded = progress.coerceAtMost(definition.target)
            if (progress >= definition.target) {
                unlockMeta(id, progress)
            } else if (bounded > existing.currentProgress) {
                val state = existing.copy(currentProgress = bounded)
                repository.saveState(state, null)
                updated += state
            }
        }

        val hiddenCount = catalog.count { it.category == AchievementCategory.HIDDEN && it.id in unlockedIds }
        if (hiddenCount >= 10) unlockMeta("secret_hunter", hiddenCount.toDouble())
        if (hiddenCount >= 20) unlockMeta("treasure_hunter", hiddenCount.toDouble())
        if (unlockedIds.size >= 42) unlockMeta("the_answer", 42.0)

        val visibleIds = catalog.filter { it.category == AchievementCategory.VISIBLE }.mapTo(hashSetOf()) { it.id }
        if (visibleIds.isNotEmpty() && unlockedIds.containsAll(visibleIds)) unlockMeta("completionist", visibleIds.size.toDouble())

        val movementChallengeIds = ChallengeGameEngine.originalPlayableIds
        if (movementChallengeIds.isNotEmpty() && unlockedIds.containsAll(movementChallengeIds)) {
            unlockMeta("gyroscope_master", movementChallengeIds.size.toDouble())
        }

        val masteryFamilies = listOf(
            setOf("simon_says", "simon_says_ii", "clone", "final_boss"),
            setOf("traffic_light", "duel", "perfect_run"),
            setOf("target_practice", "perfectionist", "final_boss"),
            setOf("steady_hand", "rock_solid", "the_bomb", "dont_move"),
        )
        if (masteryFamilies.all { family -> family.any { it in unlockedIds } }) unlockMeta("all_rounder", 4.0)

        val physicalFamilies = setOf(
            Capability.ACCELEROMETER,
            Capability.ROTATION_VECTOR,
            Capability.GYROSCOPE,
            Capability.GRAVITY,
            Capability.MAGNETOMETER,
            Capability.LIGHT,
        )
        val masteredPhysicalFamilies = catalog
            .filter { it.category == AchievementCategory.CHALLENGE && it.id in ChallengeGameEngine.playableIds && it.id in unlockedIds }
            .mapTo(hashSetOf()) { it.requiredCapability }
        if (masteredPhysicalFamilies.containsAll(physicalFamilies)) unlockMeta("device_master", physicalFamilies.size.toDouble())

        val challengeCollections = mapOf(
            "spin_doctor" to setOf("full_circle", "do_it_backwards", "double_trouble", "beyblade", "let_it_rip"),
            "axis_architect" to setOf("triple_axis", "xyz", "flat_spin"),
            "stillness_master" to setOf("steady_hand", "rock_solid", "spirit_level", "perfectionist", "dont_move"),
            "shake_therapy" to setOf("shake_it", "shake_it_off", "are_you_okay"),
            "human_compass" to setOf("north_star", "four_winds", "wrong_way_around"),
            "memory_palace" to setOf("simon_says", "simon_says_ii", "clone"),
            "reaction_master" to setOf("traffic_light", "duel"),
            "rhythm_section" to setOf("human_metronome", "rhythm"),
            "light_and_shadow" to setOf("vampire", "supernova"),
            "boss_rush" to setOf("final_boss", "perfect_run"),
            "sequence_breaker" to setOf("secret_code", "false_start", "final_boss"),
            "orientation_master" to setOf("standing_tall", "upside_down", "flip_flop"),
        )
        challengeCollections.forEach { (id, required) ->
            updateMeta(id, required.count { it in unlockedIds }.toDouble())
        }

        val unlockedDefinitions = catalog.filter { it.id in unlockedIds }
        val completedPlayableChallenges = ChallengeGameEngine.playableIds.count { it in unlockedIds }.toDouble()
        updateMeta("challenge_rookie", completedPlayableChallenges)
        updateMeta("challenge_regular", completedPlayableChallenges)
        updateMeta("challenge_elite", completedPlayableChallenges)
        updateMeta("xp_hunter", unlockedDefinitions.sumOf { it.rarity.xp }.toDouble())

        val unlockedRarities = unlockedDefinitions.mapTo(hashSetOf()) { it.rarity }
        updateMeta("rarity_rainbow", unlockedRarities.size.toDouble())
        val categoryCounts = AchievementCategory.entries.associateWith { category ->
            unlockedDefinitions.count { it.category == category }
        }
        updateMeta("balanced_build", categoryCounts.values.minOrNull()?.toDouble() ?: 0.0)
        updateMeta("legendary_collection", unlockedDefinitions.count { it.rarity == Rarity.LEGENDARY }.toDouble())

        val categoriesByDate = repository.getAllStates().asSequence()
            .filter { it.unlocked && it.unlockedAt != null }
            .mapNotNull { state -> byId[state.achievementId]?.category?.let { category ->
                Instant.ofEpochMilli(state.unlockedAt!!).atZone(ZoneId.systemDefault()).toLocalDate() to category
            } }
            .groupBy({ it.first }, { it.second })
        val hasTripleCrown = categoriesByDate.values.any { it.toSet().containsAll(AchievementCategory.entries) }
        updateMeta("triple_crown", if (hasTripleCrown) 1.0 else 0.0)
    }
}

private fun Int.isPrime(): Boolean = this >= 2 && (2..kotlin.math.sqrt(toDouble()).toInt()).none { this % it == 0 }

private fun PowerSource.collectionBit(): Int = when (this) {
    PowerSource.USB -> 1
    PowerSource.AC -> 2
    PowerSource.WIRELESS -> 4
    PowerSource.DOCK -> 8
    else -> 0
}

private fun ChallengeCompleted.isExceptionalExecution(): Boolean =
    angularError?.let { it <= .5f } == true ||
        timingDeviationPercent?.let { it <= 3f } == true ||
        reactionMs?.let { it in 100L..220L } == true

private fun <T> List<T>.endsWith(suffix: List<T>): Boolean =
    size >= suffix.size && subList(size - suffix.size, size) == suffix
