package com.irlachievements.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.irlachievements.app.domain.achievements.AchievementCatalog
import com.irlachievements.app.domain.challenges.ChallengeRunState
import com.irlachievements.app.domain.challenges.RunStatus
import com.irlachievements.app.domain.model.AchievementState
import com.irlachievements.app.ui.theme.IrlAchievementsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChallengeUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun missingEnvironmentalSensorDisablesChallengeButton() {
        var started = false
        compose.setContent {
            IrlAchievementsTheme {
                ChallengeCard(AchievementCatalog.byId.getValue("humid_air"), null,
                    setOf(com.irlachievements.app.domain.model.Capability.HUMIDITY), { started = true })
            }
        }
        compose.onNodeWithText("No compatible con este dispositivo").assertIsNotEnabled()
        org.junit.Assert.assertFalse(started)
    }

    @Test
    fun completedChallengeCanBeReplayed() {
        var selected: String? = null
        compose.setContent {
            IrlAchievementsTheme {
                ChallengeCard(AchievementCatalog.byId.getValue("full_circle"),
                    AchievementState("full_circle", unlocked = true), emptySet(), { selected = it })
            }
        }
        compose.onNodeWithText("Volver a jugar · XP ya obtenida").assertIsEnabled().performClick()
        assertEquals("full_circle", selected)
    }

    @Test
    fun cancelRemainsReachableOnSmallScreenWithLargeText() {
        var closed = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                IrlAchievementsTheme {
                    Box(Modifier.height(400.dp)) {
                        ChallengePlayScreen(ChallengeRunState("full_circle", RunStatus.RUNNING,
                            "Calibrando sensores…", "Déjalo quieto mientras ajustamos la lectura"), {}, { closed = true })
                    }
                }
            }
        }
        compose.onNodeWithText("Cancelar intento").performScrollTo().performClick()
        assertTrue(closed)
    }
}
