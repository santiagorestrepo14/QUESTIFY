package com.irlachievements.app.domain.challenges

import com.irlachievements.app.domain.model.Capability

data class EnvironmentalChallenge(
    val id: String,
    val title: String,
    val capability: Capability,
    val threshold: Float,
    val atMost: Boolean,
    val unit: String,
) {
    val targetLabel: String get() = "${if (atMost) "≤" else "≥"} ${threshold.toInt()} $unit"
    val description: String get() = "Mide $targetLabel durante 3 segundos con el sensor ambiental del teléfono."
    fun accepts(value: Float): Boolean = if (atMost) value <= threshold else value >= threshold
    fun isValid(value: Float): Boolean = value.isFinite() && when (capability) {
        Capability.HUMIDITY -> value in 0f..100f
        Capability.AMBIENT_TEMPERATURE -> value > -273.15f
        Capability.PRESSURE -> value > 0f
        else -> false
    }
}

object EnvironmentalChallenges {
    val all = listOf(
        EnvironmentalChallenge("dry_air", "Aire seco", Capability.HUMIDITY, 35f, true, "% HR"),
        EnvironmentalChallenge("humid_air", "Aire húmedo", Capability.HUMIDITY, 65f, false, "% HR"),
        EnvironmentalChallenge("cool_air", "Ambiente fresco", Capability.AMBIENT_TEMPERATURE, 18f, true, "°C"),
        EnvironmentalChallenge("warm_air", "Ambiente cálido", Capability.AMBIENT_TEMPERATURE, 27f, false, "°C"),
        EnvironmentalChallenge("low_pressure", "Bajo presión… o no", Capability.PRESSURE, 950f, true, "hPa"),
        EnvironmentalChallenge("high_pressure", "Peso del aire", Capability.PRESSURE, 1020f, false, "hPa"),
    )
    val byId = all.associateBy { it.id }
}
