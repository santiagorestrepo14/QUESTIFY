package com.irlachievements.app.domain.achievements

import java.time.LocalDate
import java.time.LocalDateTime

sealed interface AchievementEvent {
    val occurredAt: LocalDateTime
}

data class StepsUpdated(
    val dailySteps: Long,
    val totalSteps: Long,
    val dailyDistanceMeters: Double = 0.0,
    val totalDistanceMeters: Double = 0.0,
    val consecutiveDaysAbove5k: Int = 0,
    val consecutiveDaysAbove10k: Int = 0,
    val stepsBetweenMidnightAnd5: Long = 0,
    val stepsBefore8: Long = 0,
    val stepsBefore12: Long = 0,
    override val occurredAt: LocalDateTime,
) : AchievementEvent

data class ActivityTransitionObserved(
    val activity: ActivityKind,
    val transition: TransitionKind,
    val activitiesToday: Set<ActivityKind> = emptySet(),
    val previousActivity: ActivityKind? = null,
    override val occurredAt: LocalDateTime,
) : AchievementEvent

data class ActivitySequenceObserved(
    val recentEntries: List<ActivityKind>,
    val reverseSequenceCompleted: Boolean = false,
    override val occurredAt: LocalDateTime,
) : AchievementEvent

enum class ActivityKind { STILL, WALKING, RUNNING, ON_BICYCLE, IN_VEHICLE, ON_FOOT }
enum class TransitionKind { ENTER, EXIT }

data class UnlockDetected(
    val unlocksToday: Int,
    val hoursSincePreviousUnlock: Double,
    override val occurredAt: LocalDateTime,
) : AchievementEvent

data class DayCompleted(
    val unlocks: Int,
    override val occurredAt: LocalDateTime,
    val firstUnlockMinuteOfDay: Int? = null,
    val lastUnlockMinuteOfDay: Int? = null,
    val nightUnlocks: Int = 0,
    val activeUnlockHours: Int = 0,
    val maxUnlocksInFiveMinutes: Int = 0,
    val minimumGapMinutes: Double? = null,
) : AchievementEvent

data class AppOpened(
    val openCountToday: Int,
    val distinctUsageDays: Int,
    val daysSinceInstall: Int,
    val daysSinceLastOpen: Int = 0,
    val batteryPercent: Int? = null,
    val isCharging: Boolean = false,
    val powerSource: PowerSource = PowerSource.NONE,
    val achievementsUnlocked: Int = 0,
    val sameClockMinuteDistinctDates: Int = 1,
    override val occurredAt: LocalDateTime,
) : AchievementEvent

enum class PowerSource { NONE, USB, AC, WIRELESS, DOCK, UNKNOWN }

data class BatteryObserved(
    val percent: Int,
    override val occurredAt: LocalDateTime,
) : AchievementEvent

data class ChallengeCompleted(
    val challengeId: String,
    val attemptNumber: Int,
    val previousFailures: Int,
    val batteryPercent: Int = 100,
    val distinctCompletionDates: Int = 1,
    val reactionMs: Long? = null,
    val angularError: Float? = null,
    val timingDeviationPercent: Float? = null,
    val extraMovements: Int = 0,
    override val occurredAt: LocalDateTime,
) : AchievementEvent

data class ChallengeFailed(
    val challengeId: String,
    val totalFailures: Int,
    val failureCode: String? = null,
    val repeatedFailureCount: Int = 0,
    val completedSteps: Int = 0,
    val totalSteps: Int = 0,
    override val occurredAt: LocalDateTime,
) : AchievementEvent

data class DayChanged(
    val date: LocalDate,
    override val occurredAt: LocalDateTime = date.atStartOfDay(),
) : AchievementEvent

data class ForceUnlock(
    val achievementId: String,
    override val occurredAt: LocalDateTime,
) : AchievementEvent
