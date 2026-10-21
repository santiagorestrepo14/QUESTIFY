package com.irlachievements.app.ui

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.irlachievements.app.domain.challenges.ChallengeRunState
import com.irlachievements.app.ui.theme.*

@Composable
internal fun MicrophoneGameBoard(run: ChallengeRunState, soundEnabled: Boolean) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val audio = remember(context) { context.getSystemService(AudioManager::class.java) }
    val tone = remember(soundEnabled, run.challengeId) {
        if (soundEnabled && run.challengeId == "clap_echo")
            runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 18) }.getOrNull() else null
    }
    DisposableEffect(tone, owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) tone?.stopTone() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); tone?.release() }
    }
    LaunchedEffect(run.microphoneBeat, run.step) {
        if (run.microphoneBeat >= 0 && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
            audio?.ringerMode == AudioManager.RINGER_MODE_NORMAL &&
            audio.getStreamVolume(AudioManager.STREAM_MUSIC) > 0 && !audio.isMusicActive) {
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 90)
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("MICRÓFONO ACTIVO · SOLO ESTE RETO", color = Gold)
        Text("Nivel ${run.step + 1}/3 · Sin grabaciones guardadas ni envíos", color = Muted)
        val bandDescription = when {
            run.microphoneTargetHigh <= run.microphoneTargetLow -> "Escuchando el ambiente"
            run.microphoneLevel < run.microphoneTargetLow -> "Por debajo de la franja"
            run.microphoneLevel > run.microphoneTargetHigh -> "Por encima de la franja"
            else -> "Dentro de la franja"
        }
        Canvas(Modifier.fillMaxWidth().height(32.dp).semantics {
            contentDescription = "Indicador de intensidad"; stateDescription = bandDescription
        }) {
            drawRect(PanelRaised)
            if (run.microphoneTargetHigh > run.microphoneTargetLow) {
                drawRect(Acid.copy(alpha = .28f), Offset(size.width * run.microphoneTargetLow, 0f),
                    Size(size.width * (run.microphoneTargetHigh - run.microphoneTargetLow), size.height))
            }
            val needle = size.width * run.microphoneLevel
            drawLine(Color.White, Offset(needle, 0f), Offset(needle, size.height), strokeWidth = 3.dp.toPx())
        }
        Text("Intensidad relativa al ambiente; no es un sonómetro. No necesitas gritar.", color = Muted)
        if (run.microphoneTargetHigh > run.microphoneTargetLow) Text(bandDescription, color = Cyan)
        if (run.challengeId == "clap_echo") {
            Text(if (run.microphoneBeat >= 0) "● GOLPE ${run.microphoneBeat + 1}" else "·",
                color = Cyan)
        }
    }
}
