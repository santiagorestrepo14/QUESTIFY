package com.irlachievements.app.domain.model

import com.irlachievements.app.domain.achievements.AchievementCatalog
import com.irlachievements.app.domain.challenges.ChallengeGameEngine

enum class LogoStyle(val id: String, val title: String) {
    NEON_CORE("neon_core", "Neon Core"),
    SKY_PULSE("sky_pulse", "Sky Pulse"),
    VIOLET_RUSH("violet_rush", "Violet Rush"),
    SUNSET_CHARGE("sunset_charge", "Sunset Charge"),
    EMERALD_NIGHT("emerald_night", "Emerald Night"),
    FROST_ELITE("frost_elite", "Frost Elite"),
    GOLD_RANK("gold_rank", "Gold Rank"),
    NOVA_CROWN("nova_crown", "Nova Crown"),
    GLITCH_RELIC("glitch_relic", "Glitch Relic");

    companion object {
        fun fromId(id: String?): LogoStyle = entries.firstOrNull { it.id == id } ?: NEON_CORE
    }
}

data class LogoUnlockState(
    val style: LogoStyle,
    val unlocked: Boolean,
    val requirement: String,
    val progressText: String,
    val progress: Float,
)

/** Cosmetics derive from saved achievements. Replays and unknown IDs never grant extra progress. */
object LogoCollection {
    fun nextReward(states: Map<String, AchievementState>): LogoUnlockState? =
        evaluate(states).filter { !it.unlocked && it.style != LogoStyle.GLITCH_RELIC }.maxByOrNull { it.progress }

    fun pendingRewards(states: Map<String, AchievementState>, seen: Set<String>): List<LogoStyle> =
        evaluate(states).filter { it.unlocked && it.style != LogoStyle.NEON_CORE && it.style.id !in seen }.map { it.style }

    fun evaluate(states: Map<String, AchievementState>): List<LogoUnlockState> {
        val earned = AchievementCatalog.all.filter { states[it.id]?.unlocked == true }
        val level = XpProgression.from(earned.sumOf { it.rarity.xp }).level
        val normal = earned.count { it.category == AchievementCategory.VISIBLE }
        val challenges = earned.count { it.id in ChallengeGameEngine.playableIds }
        val secrets = earned.count { it.hidden }
        val epic = earned.count { it.rarity == Rarity.EPIC }
        val legendary = earned.count { it.rarity == Rarity.LEGENDARY }
        fun rule(style: LogoStyle, value: Int, target: Int, requirement: String, unit: String) =
            LogoUnlockState(style, value >= target, requirement,
                "${value.coerceAtMost(target)} / $target $unit", (value.toFloat() / target).coerceIn(0f, 1f))
        return LogoStyle.entries.map { style ->
            when (style) {
                LogoStyle.NEON_CORE -> LogoUnlockState(style, true, "Disponible desde el nivel 1", "Tu primera insignia", 1f)
                LogoStyle.SUNSET_CHARGE -> rule(style, level, 4, "Alcanza el nivel 4", "niveles")
                LogoStyle.EMERALD_NIGHT -> rule(style, level, 8, "Alcanza el nivel 8", "niveles")
                LogoStyle.FROST_ELITE -> rule(style, level, 12, "Alcanza el nivel 12", "niveles")
                LogoStyle.SKY_PULSE -> rule(style, challenges, 5, "Completa 5 retos diferentes", "retos")
                LogoStyle.VIOLET_RUSH -> rule(style, epic, 8, "Consigue 8 logros épicos", "épicos")
                LogoStyle.GOLD_RANK -> rule(style, legendary, 5, "Consigue 5 logros legendarios", "legendarios")
                LogoStyle.GLITCH_RELIC -> if (secrets >= 12) {
                    rule(style, secrets, 12, "Descubre 12 logros secretos", "secretos")
                } else LogoUnlockState(style, false, "Lo que no se ve también deja huellas.", "Condición secreta", 0f)
                LogoStyle.NOVA_CROWN -> LogoUnlockState(style, normal >= 40 && challenges >= 30,
                    "Consigue 40 logros normales y completa 30 retos diferentes",
                    "${normal.coerceAtMost(40)} / 40 normales · ${challenges.coerceAtMost(30)} / 30 retos",
                    minOf(normal / 40f, challenges / 30f).coerceIn(0f, 1f))
            }
        }
    }

    fun resolveSelection(id: String?, states: Map<String, AchievementState>): LogoStyle {
        val requested = LogoStyle.fromId(id)
        return evaluate(states).firstOrNull { it.style == requested && it.unlocked }?.style ?: LogoStyle.NEON_CORE
    }
}
