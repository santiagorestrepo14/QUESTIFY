package com.irlachievements.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.irlachievements.app.domain.challenges.ChallengeRunState
import com.irlachievements.app.domain.challenges.RunStatus
import com.irlachievements.app.domain.challenges.TouchPhase
import com.irlachievements.app.ui.theme.IrlAchievementsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TouchGameUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun observationHasASymbolAndDoesNotAcceptTaps() {
        compose.setContent { IrlAchievementsTheme {
            TouchGameBoard(ChallengeRunState("color_echo", RunStatus.RUNNING,
                touchPhase = TouchPhase.SHOW, highlightedTile = 0)) { error("Observation must not accept input") }
        } }
        compose.onNodeWithText("→ ● Círculo").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test fun memorySendsTheCorrectTileWhenItIsThePlayersTurn() {
        var tile = -1
        compose.setContent { IrlAchievementsTheme {
            TouchGameBoard(ChallengeRunState("color_echo", RunStatus.RUNNING,
                touchPhase = TouchPhase.INPUT)) { tile = it }
        } }
        compose.onNodeWithText("◆ Rombo").performClick()
        compose.runOnIdle { assertEquals(1, tile) }
    }

    @Test fun reactionSignalHasTextAndAWorkingTouchTarget() {
        var touches = 0
        compose.setContent { IrlAchievementsTheme {
            TouchGameBoard(ChallengeRunState("quiet_signal", RunStatus.RUNNING,
                touchPhase = TouchPhase.SIGNAL)) { touches++ }
        } }
        compose.onNodeWithText("● AHORA · toca aquí").performClick()
        compose.runOnIdle { assertEquals(1, touches) }
    }
}
