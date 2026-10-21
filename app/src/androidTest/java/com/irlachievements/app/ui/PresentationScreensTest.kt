package com.irlachievements.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.irlachievements.app.ui.theme.IrlAchievementsTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PresentationScreensTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun splashShowsBrandAfterEntryAnimation() {
        compose.setContent { IrlAchievementsTheme { BrandSplashScreen() } }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("QUESTIFY").assertIsDisplayed()
    }

    @Test
    fun creditsNameAndReturnRemainAccessibleWithLargeText() {
        var returned = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                IrlAchievementsTheme {
                    Box(Modifier.height(320.dp)) { CreditsScreen { returned = true } }
                }
            }
        }
        compose.onNodeWithText("Santiago Restrepo").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Josue Pino").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sebastian Cruz").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Volver al inicio").performScrollTo().performClick()
        assertTrue(returned)
    }
}
