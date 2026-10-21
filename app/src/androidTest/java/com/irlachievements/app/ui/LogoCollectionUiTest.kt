package com.irlachievements.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.irlachievements.app.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LogoCollectionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lockedSecretIsConcealedAndCannotBeEquipped() {
        val secret = LogoCollection.evaluate(emptyMap()).single { it.style == LogoStyle.GLITCH_RELIC }
        compose.setContent { LogoCollectionCard(secret, false) { error("Locked logo selected") } }
        compose.onNodeWithText("Bloqueado").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Logo secreto").assertIsDisplayed()
        compose.onNodeWithContentDescription("QUESTIFY · Glitch Relic").assertDoesNotExist()
    }

    @Test fun unlockedLogoCanBeSelected() {
        var selected: LogoStyle? = null
        val entry = LogoUnlockState(LogoStyle.SKY_PULSE, true, "Completa 5 retos diferentes", "5 / 5 retos", 1f)
        compose.setContent { LogoCollectionCard(entry, false) { selected = it } }
        compose.onNodeWithText("Equipar Sky Pulse").performClick()
        compose.runOnIdle { assertEquals(LogoStyle.SKY_PULSE, selected) }
    }

    @Test fun equippedLogoCannotBeEquippedTwice() {
        val entry = LogoCollection.evaluate(emptyMap()).first()
        compose.setContent { LogoCollectionCard(entry, true) { error("Already selected") } }
        compose.onNodeWithText("Equipado").assertIsNotEnabled()
    }
}
