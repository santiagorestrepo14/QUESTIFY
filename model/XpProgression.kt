package com.irlachievements.app.domain.model

import kotlin.math.pow

data class XpLevel(
    val level: Int,
    val title: String,
    val reward: String,
    val currentThreshold: Int,
    val nextThreshold: Int,
    val totalXp: Int,
    val nextRankTitle: String?,
    val nextRankLevel: Int?,
    val xpToNextRank: Int?,
) {
    val progress: Float
        get() = ((totalXp - currentThreshold).toFloat() / (nextThreshold - currentThreshold))
            .coerceIn(0f, 1f)

    val xpToNextLevel: Int
        get() = (nextThreshold - totalXp).coerceAtLeast(0)
}

object XpProgression {
    data class RankMilestone(val level: Int, val title: String, val reward: String)

    val rankMilestones = listOf(
        RankMilestone(1, "Novato", "Marco base"),
        RankMilestone(4, "Explorador", "Marco verde"),
        RankMilestone(7, "Aventurero", "Marco cian"),
        RankMilestone(10, "Cazador", "Marco violeta"),
        RankMilestone(13, "Especialista", "Marco de élite"),
        RankMilestone(16, "Maestro", "Marco maestro"),
        RankMilestone(19, "Leyenda", "Marco legendario"),
        RankMilestone(22, "Mito", "Marco dorado"),
    )

    fun from(totalXp: Int): XpLevel {
        val safeXp = totalXp.coerceAtLeast(0)
        var level = 1
        while (thresholdFor(level + 1) <= safeXp) level++
        val currentRank = rankMilestones.last { it.level <= level }
        val nextRank = rankMilestones.firstOrNull { it.level > level }
        return XpLevel(
            level = level,
            title = currentRank.title,
            reward = currentRank.reward,
            currentThreshold = thresholdFor(level),
            nextThreshold = thresholdFor(level + 1),
            totalXp = safeXp,
            nextRankTitle = nextRank?.title,
            nextRankLevel = nextRank?.level,
            xpToNextRank = nextRank?.let { (thresholdFor(it.level) - safeXp).coerceAtLeast(0) },
        )
    }

    fun thresholdFor(level: Int): Int = (40.0 * (level.coerceAtLeast(1) - 1).toDouble().pow(2)).toInt()

}
