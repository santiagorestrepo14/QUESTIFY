package com.irlachievements.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.irlachievements.app.domain.challenges.ChallengeRunState
import com.irlachievements.app.domain.challenges.TouchPhase
import com.irlachievements.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
internal fun StereoGameBoard(run: ChallengeRunState, soundEnabled: Boolean, onTouch: (Int) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val player = remember(context) { StereoCuePlayer(context) }
    val scope = rememberCoroutineScope()
    var tested by remember { mutableIntStateOf(0) }
    var confirmed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { player.stop(); confirmed = false; tested = 0 }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer); player.stop() }
    }
    LaunchedEffect(run.audioToken, run.touchPhase, soundEnabled) {
        if (run.touchPhase == TouchPhase.AUDIO_PLAYING) {
            if (!soundEnabled || !confirmed) { onTouch(12); return@LaunchedEffect }
            busy = true
            try {
                val error = player.play(run.audioSide)
                message = error
                if (error == null) onTouch(11)
                else { confirmed = false; tested = 0; onTouch(12) }
            } finally { busy = false }
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Nivel ${run.step + 1} / 3 · Auriculares estéreo", color = Cyan)
        Text("Frente / centro = el mismo sonido en ambos oídos. No simula una fuente físicamente delante de ti.", color = Muted)
        if (!soundEnabled) Text("Activa Sonidos del juego en Ajustes para jugar este reto.", color = Gold)
        if (!confirmed) {
            Text("Prueba las tres direcciones a un volumen cómodo. No subiremos el volumen del teléfono.", color = Muted)
            listOf("Probar izquierda", "Probar derecha", "Probar frente / centro").forEachIndexed { side, label ->
                OutlinedButton(enabled = soundEnabled && !busy, modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                val error = player.play(side)
                                message = error
                                if (error == null) tested = tested or (1 shl side)
                            } finally { busy = false }
                        }
                    }) { Text(label) }
            }
            Button(onClick = { confirmed = true; message = null }, enabled = tested == 7 && !busy && soundEnabled,
                modifier = Modifier.fillMaxWidth()) { Text("Distingo las tres direcciones") }
            Text("Si se oyen iguales, revisa audio mono, balance y colocación L/R. No necesitas mover el teléfono.", color = Muted)
        } else {
            Button(onClick = { onTouch(10) }, enabled = soundEnabled && !busy &&
                run.touchPhase in setOf(TouchPhase.AUDIO_READY, TouchPhase.INPUT),
                modifier = Modifier.fillMaxWidth()) {
                Text(if (run.touchPhase == TouchPhase.INPUT) "Escuchar otra vez" else if (busy) "Escuchando…" else "Escuchar señal")
            }
            val answerEnabled = soundEnabled && !busy && run.touchPhase == TouchPhase.INPUT
            OutlinedButton(onClick = { onTouch(2) }, enabled = answerEnabled && run.step > 0,
                modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 64.dp)) { Text("↑ Frente / centro") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { onTouch(0) }, enabled = answerEnabled,
                    modifier = Modifier.weight(1f).heightIn(min = 64.dp)) { Text("← Izquierda") }
                OutlinedButton(onClick = { onTouch(1) }, enabled = answerEnabled,
                    modifier = Modifier.weight(1f).heightIn(min = 64.dp)) { Text("Derecha →") }
            }
            TextButton(onClick = { confirmed = false; tested = 0; player.stop(); onTouch(12) }, enabled = !busy) {
                Text("Repetir prueba de auriculares")
            }
        }
        message?.let { Text(it, color = Gold) }
    }
}
