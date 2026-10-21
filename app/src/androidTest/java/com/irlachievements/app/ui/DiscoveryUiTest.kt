package com.irlachievements.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.irlachievements.app.domain.achievements.AchievementCatalog
import com.irlachievements.app.domain.model.Capability
import com.irlachievements.app.domain.model.LogoStyle
import com.irlachievements.app.ui.theme.IrlAchievementsTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DiscoveryUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sensorFreeCategoryShowsTouchGames() {
        compose.setContent { IrlAchievementsTheme {
            ChallengesScreen(AppUiState(), {}, {}, {})
        } }
        compose.onNodeWithText("Sin sensores · 5").performClick()
        compose.onNodeWithTag("challenge_list").performScrollToNode(hasText("Eco de colores"))
        compose.onNodeWithText("Eco de colores").assertIsDisplayed()
        compose.onNodeWithTag("challenge_list").performScrollToNode(hasText("Señal tranquila"))
        compose.onNodeWithText("Señal tranquila").assertIsDisplayed()
        compose.onNodeWithText("Full Circle").assertDoesNotExist()
    }

    @Test fun guideDoesNotStartUntilConfirmed() {
        var started = false
        compose.setContent { IrlAchievementsTheme {
            ChallengeGuideDialog(AchievementCatalog.byId.getValue("full_circle"), emptySet(), {}, { started = true })
        } }
        compose.runOnIdle { assertFalse(started) }
        compose.onNodeWithText("Iniciar reto").performClick()
        compose.runOnIdle { assertTrue(started) }
    }

    @Test fun microphoneGuideExplainsPrivacyWithoutStartingCapture() {
        var started = false
        compose.setContent { IrlAchievementsTheme {
            ChallengeGuideDialog(AchievementCatalog.byId.getValue("voice_balance"), emptySet(), {}, { started = true })
        } }
        compose.runOnIdle { assertFalse(started) }
        compose.onNodeWithText("MICRÓFONO SOLO DURANTE ESTE RETO").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ahora no").assertIsEnabled()
    }

    @Test fun guideCannotStartWithoutRequiredHardware() {
        compose.setContent { IrlAchievementsTheme {
            ChallengeGuideDialog(AchievementCatalog.byId.getValue("full_circle"),
                setOf(Capability.GYROSCOPE), {}, { error("Unavailable hardware") })
        } }
        compose.onNodeWithText("Iniciar reto").assertIsNotEnabled()
    }

    @Test fun rewardOffersEquipAndDefer() {
        var deferred = false
        compose.setContent { IrlAchievementsTheme {
            LogoRewardDialog(LogoStyle.SKY_PULSE, {}, { deferred = true })
        } }
        compose.onNodeWithText("Equipar ahora").assertIsEnabled()
        compose.onNodeWithText("Más tarde").performClick()
        compose.runOnIdle { assertTrue(deferred) }
    }
}
