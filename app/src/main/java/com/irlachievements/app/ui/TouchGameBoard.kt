package com.irlachievements.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.irlachievements.app.domain.challenges.ChallengeRunState
import com.irlachievements.app.domain.challenges.TouchPhase
import com.irlachievements.app.ui.theme.Muted
import com.irlachievements.app.ui.theme.Panel

/** Only small, muted tiles change. No screen flashes, looping effects or brightness control. */
@Composable
internal fun TouchGameBoard(run: ChallengeRunState, soundEnabled: Boolean = true, onTouch: (Int) -> Unit) {
    if (run.challengeId in com.irlachievements.app.domain.challenges.CollectionGameEngine.ids) {
        CollectionGameBoard(run, soundEnabled, onTouch)
    } else if (run.challengeId == "stereo_compass") {
        StereoGameBoard(run, soundEnabled, onTouch)
    } else if (run.challengeId in setOf("ascending_path", "odd_symbol")) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Nivel ${run.step + 1} / 3 · Solo toques", color = Muted)
            run.touchChoices.chunked(3).forEachIndexed { row, choices ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    choices.forEachIndexed { column, value ->
                        val label = if (value < 0) "✓" else if (run.challengeId == "ascending_path") value.toString()
                            else listOf("●", "◆", "▲", "■")[value]
                        OutlinedButton(onClick = { onTouch(row * 3 + column) }, enabled = value >= 0,
                            modifier = Modifier.weight(1f).heightIn(min = 80.dp),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF9CACC9).copy(alpha = .1f),
                                contentColor = Color(0xFFBAC7DD))) { Text(label) }
                    }
                }
            }
            Text(if (run.challengeId == "ascending_path") "Los números acertados quedan marcados."
                else "Solo una forma es distinta. El color es igual para todas.", color = Muted)
        }
    } else if (run.challengeId == "color_echo") {
        val labels = listOf("● Círculo", "◆ Rombo", "▲ Triángulo", "■ Cuadrado")
        val colors = listOf(Color(0xFF99BCA9), Color(0xFF9CACC9), Color(0xFFC5AF91), Color(0xFFB6A1C0))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Nivel ${run.step + 1} / 3 · Memoria táctil", color = Muted)
            repeat(2) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(2) { column ->
                        val index = row * 2 + column
                        val active = run.highlightedTile == index
                        val tint = colors[index]
                        OutlinedButton(onClick = { onTouch(index) },
                            enabled = run.touchPhase == TouchPhase.INPUT,
                            modifier = Modifier.weight(1f).heightIn(min = 96.dp).semantics { selected = active },
                            border = BorderStroke(if (active) 3.dp else 1.dp, if (active) tint else tint.copy(alpha = .3f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = tint.copy(alpha = .12f), contentColor = tint,
                                disabledContainerColor = tint.copy(alpha = if (active) .22f else .08f),
                                disabledContentColor = if (active) Color.White else tint.copy(alpha = .7f))) {
                            Text((if (active) "→ " else "") + labels[index])
                        }
                    }
                }
            }
            Text(if (run.touchPhase == TouchPhase.INPUT) "Toca los símbolos en el mismo orden."
                else "Observa la tarjeta marcada con →. Los botones esperan a Tu turno.", color = Muted)
        }
    } else {
        val signal = run.touchPhase == TouchPhase.SIGNAL
        val resting = run.touchPhase == TouchPhase.BREAK
        val accent = Color(0xFFA3BEAD)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Nivel ${run.step + 1} / 3 · Reacción táctil", color = Muted)
            OutlinedButton(onClick = { onTouch(0) }, enabled = !resting,
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                border = BorderStroke(2.dp, if (signal) accent else Muted.copy(alpha = .35f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (signal) accent.copy(alpha = .16f) else Panel,
                    contentColor = if (signal) Color.White else Muted)) {
                Text(if (signal) "● AHORA · toca aquí" else if (resting) "✓ Preparando siguiente señal" else "◌ Espera la señal")
            }
            Text("El texto y el símbolo indican cuándo tocar. Sin destellos ni cambios de brillo.", color = Muted)
        }
    }
}
