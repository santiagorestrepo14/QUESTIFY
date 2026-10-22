package com.irlachievements.app.domain.model

enum class AchievementCategory { VISIBLE, HIDDEN, CHALLENGE }

enum class Rarity(val xp: Int) {
    COMMON(10), UNCOMMON(25), RARE(50), EPIC(100), LEGENDARY(250),
}

enum class Capability {
    NONE, ACTIVITY_RECOGNITION, USAGE_ACCESS, ACCELEROMETER, GYROSCOPE,
    ROTATION_VECTOR, GRAVITY, MAGNETOMETER, LIGHT, HUMIDITY, AMBIENT_TEMPERATURE, PRESSURE, MICROPHONE,
}

enum class RuleType {
    DAILY_STEPS, TOTAL_STEPS, STEP_STREAK, DAILY_DISTANCE, TOTAL_DISTANCE,
    FIRST_ACTIVITY, ACTIVITY_COMBINATION, ACTIVITY_TRANSITION,
    ACTIVITY_SEQUENCE,
    DAILY_UNLOCKS, HOURS_WITHOUT_UNLOCK, MAX_DAILY_UNLOCKS, DAILY_USAGE_PATTERN,
    APP_OPEN, DAYS_SINCE_INSTALL, DISTINCT_APP_DAYS, RETURN_AFTER_DAYS,
    BATTERY_AT_MOST, BATTERY_CYCLE, TIME_WINDOW, CALENDAR_DATE, SPECIAL_DATE, CONTEXTUAL_OPEN, POWER_SOURCE_COLLECTION,
    CHALLENGE_COMPLETED, CHALLENGE_META, CHALLENGE_COUNT, XP_TOTAL, COLLECTION_META,
    SECRET_COUNT, UNLOCK_ORDINAL, ALL_VISIBLE, RANDOM_ROLL,
}

enum class ChallengeAvailability { READY }

data class AchievementDefinition(
    val id: String,
    val title: String,
    val description: String,
    val category: AchievementCategory,
    val hidden: Boolean = category == AchievementCategory.HIDDEN,
    val rarity: Rarity,
    val ruleType: RuleType,
    val target: Double = 1.0,
    val requiredCapability: Capability = Capability.NONE,
    val availability: ChallengeAvailability = ChallengeAvailability.READY,
)

data class AchievementState(
    val achievementId: String,
    val currentProgress: Double = 0.0,
    val unlocked: Boolean = false,
    val unlockedAt: Long? = null,
)

data class PlayerSummary(
    val totalXp: Int,
    val achievementsUnlocked: Int,
    val totalAchievements: Int,
    val visibleUnlocked: Int,
    val hiddenUnlocked: Int,
    val challengesCompleted: Int,
) {
    val completionPercentage: Int
        get() = if (totalAchievements == 0) 0 else (achievementsUnlocked * 100 / totalAchievements)
}

data class UnlockResult(
    val newlyUnlocked: List<AchievementDefinition>,
    val updatedStates: List<AchievementState>,
)
