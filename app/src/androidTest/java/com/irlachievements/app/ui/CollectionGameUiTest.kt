package com.irlachievements.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.irlachievements.app.domain.challenges.*
import com.irlachievements.app.ui.theme.IrlAchievementsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CollectionGameUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun routeObservationDisablesTiles() {
        val state = CollectionGameEngine("fleeting_route", 1).tick(0)
        compose.setContent { IrlAchievementsTheme {
            CollectionGameBoard(state, true) { error("No input during observation") }
        } }
        compose.onNodeWithContentDescription("Tarjeta 1: ·").assertIsNotEnabled()
        compose.onNodeWithText("Memoriza la ruta en orden").assertIsDisplayed()
    }

    @Test fun disabledAudioDoesNotStartPlayback() {
        compose.setContent { IrlAchievementsTheme {
            CollectionGameBoard(CollectionGameEngine("sound_detective", 2).tick(0), false) { error("Audio disabled") }
        } }
        compose.onNodeWithText("Escuchar de nuevo").assertIsNotEnabled()
        compose.onNodeWithText("Activa Sonidos del juego en Ajustes.").assertIsDisplayed()
    }

    @Test fun unheardCardCannotBeConfirmed() {
        val engine = CollectionGameEngine("sound_pairs", 2)
        engine.tap(0, 0)
        val state = engine.tap(CollectionGameEngine.AUDIO_ERROR, 0)
        compose.setContent { IrlAchievementsTheme {
            CollectionGameBoard(state, true) { error("Unheard card must remain disabled") }
        } }
        compose.onNodeWithText("Confirmar tarjeta escuchada").assertIsNotEnabled()
    }

    @Test fun piecesCanBePlacedWithAccessibleTaps() {
        val actions = mutableListOf<Int>()
        val state = CollectionGameEngine("in_place", 3).tick(0)
        compose.setContent { IrlAchievementsTheme { CollectionGameBoard(state, true) { actions += it } } }
        compose.onNodeWithContentDescription("Tarjeta 1: ${state.boardLabels[0]}").performClick()
        compose.onNodeWithContentDescription("Hueco 1: □").performClick()
        compose.runOnIdle { assertEquals(listOf(0, 3), actions) }
    }
}
