package com.irlachievements.app.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.lifecycle.ViewModelProvider
import com.irlachievements.app.MainActivity
import com.irlachievements.app.domain.challenges.RunStatus
import com.irlachievements.app.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ChallengeNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun waitForHome() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Inicio").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun creditsReturnHomeWithButtonAndSystemBack() {
        lateinit var model: AppViewModel
        compose.runOnIdle {
            model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
            model.completeOnboarding()
        }
        compose.waitUntil(10_000) { model.uiState.value.onboardingComplete && model.uiState.value.latestUnlock != null }
        compose.runOnIdle { model.clearCelebration() }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Inicio").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("home_list").performScrollToNode(hasText("Créditos"))
        compose.onNodeWithText("Créditos").performClick()
        compose.onNodeWithText("Santiago Restrepo").assertIsDisplayed()
        compose.onNodeWithContentDescription("Volver al inicio").performClick()
        compose.onNodeWithTag("home_list").performScrollToNode(hasText("Créditos"))
        compose.onNodeWithText("Créditos").performClick()
        compose.onNodeWithText("Santiago Restrepo").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("home_list").performScrollToNode(hasText("Créditos"))
        compose.onNodeWithText("Créditos").assertIsDisplayed()
        compose.onNodeWithText("Santiago Restrepo").assertDoesNotExist()
    }

    @Test
    fun soundSwitchPersistsItsSetting() {
        lateinit var model: AppViewModel
        val preferences = PreferencesRepository(compose.activity.applicationContext)
        val original = runBlocking { preferences.soundEnabled.first() }
        compose.runOnIdle {
            model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
            model.completeOnboarding()
        }
        compose.waitUntil(10_000) { model.uiState.value.onboardingComplete && model.uiState.value.latestUnlock != null }
        compose.waitUntil(5_000) { model.uiState.value.soundEnabled == original }
        compose.runOnIdle { model.clearCelebration() }
        waitForHome()
        compose.onNodeWithText("Ajustes").performClick()
        compose.onNodeWithText("Developer Tools").assertDoesNotExist()
        try {
            compose.onNodeWithContentDescription("Sonidos del juego").performClick()
            compose.waitUntil(5_000) { model.uiState.value.soundEnabled != original }
            assertEquals(!original, runBlocking { preferences.soundEnabled.first() })
        } finally {
            runBlocking { preferences.saveSoundEnabled(original) }
        }
    }

    @Test
    fun navigatingHomeStopsTheActiveChallenge() {
        lateinit var model: AppViewModel
        compose.runOnIdle {
            model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
            model.completeOnboarding()
        }
        compose.waitUntil(10_000) { model.uiState.value.onboardingComplete }
        compose.waitUntil(10_000) { model.uiState.value.latestUnlock != null }
        compose.runOnIdle { model.clearCelebration() }
        waitForHome()
        compose.onNodeWithText("Retos").performClick()
        compose.onNodeWithText("FILTRAR POR SENSOR").assertIsDisplayed()
        compose.runOnIdle { model.startChallenge("full_circle") }
        compose.waitUntil(10_000) { model.uiState.value.challengeRun.status == RunStatus.RUNNING }
        compose.runOnIdle { model.clearCelebration() }
        compose.onNodeWithText("Inicio").performClick()
        compose.waitUntil(5_000) { model.uiState.value.challengeRun.status == RunStatus.IDLE }
        compose.runOnIdle { assertEquals(RunStatus.IDLE, model.uiState.value.challengeRun.status) }
    }
}
