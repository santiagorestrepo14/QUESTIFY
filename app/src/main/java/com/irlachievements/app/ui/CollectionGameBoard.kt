package com.irlachievements.app.ui

import android.content.Context
import android.media.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.irlachievements.app.domain.challenges.*
import com.irlachievements.app.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Quiet, locally synthesized notes; no recording, files or volume changes. */
private class CollectionAudio(context: Context) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private var track: AudioTrack? = null
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val focus = GameAudioFocus(manager) { closeTrack() }
    private val notesCache by lazy { Array(6) { CollectionTone.samples(it) } }
    private fun closeTrack() { val old = track; track = null; if (old != null) { runCatching { old.stop() }; old.release() } }
    fun stop() { focus.release(); closeTrack() }
    suspend fun play(notes: List<Int>): String? {
        val audio = manager ?: return "Salida de audio no disponible."
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL || audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0)
            return "Activa el sonido y usa un volumen cómodo."
        if (audio.isMusicActive) return "Pausa el audio de otras aplicaciones."
        try {
            if (!focus.acquire(attributes)) return "Android no permite reproducir ahora. Inténtalo al volver a la app."
            for (note in notes) {
                if (focus.interrupted) return "Audio interrumpido. Vuelve a escucharlo."
                if (note < 0) { delay(450); continue }
                val samples = notesCache[note]
                val next = AudioTrack.Builder().setAudioAttributes(attributes)
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(CollectionTone.sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(samples.size * 2).build()
                track = next
                if (next.state != AudioTrack.STATE_INITIALIZED || next.write(samples, 0, samples.size) != samples.size)
                    return "No se pudo preparar el sonido."
                next.setVolume(.4f); next.play(); delay(430)
                if (focus.interrupted || track !== next || audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0 ||
                    audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return "Audio interrumpido. Vuelve a escucharlo."
                closeTrack(); delay(120)
            }
            return if (focus.interrupted) "Audio interrumpido. Vuelve a escucharlo." else null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return "No se pudo reproducir. Comprueba la salida de audio." }
        finally { stop() }
    }
}

@Composable
internal fun CollectionGameBoard(run: ChallengeRunState, soundEnabled: Boolean, onTouch: (Int) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val player = remember { CollectionAudio(context) }
    val callback by rememberUpdatedState(onTouch)
    val playing by rememberUpdatedState(run.touchPhase == TouchPhase.AUDIO_PLAYING)
    var error by remember(run.challengeId) { mutableStateOf<String?>(null) }
    val audio = run.challengeId in CollectionGameEngine.audioIds
    DisposableEffect(lifecycle, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                player.stop()
                if (playing) callback(CollectionGameEngine.AUDIO_ERROR)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); player.stop() }
    }
    LaunchedEffect(run.audioToken, run.touchPhase, soundEnabled) {
        if (run.touchPhase == TouchPhase.AUDIO_PLAYING) {
            error = if (!soundEnabled) "Activa Sonidos del juego en Ajustes para jugar."
                else if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) "Partida pausada."
                else player.play(run.audioNotes)
            callback(if (error == null) CollectionGameEngine.HEARD else CollectionGameEngine.AUDIO_ERROR)
        }
    }
    val rectangles = remember(run.challengeId, run.step) { mutableMapOf<Int, Rect>() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Nivel ${run.step + 1}/3 · ${if (audio) "Escucha y toca" else "Solo pantalla"}", color = Muted)
        Text(run.instruction, style = MaterialTheme.typography.titleMedium)
        if (audio) {
            Text("Sin micrófono · volumen cómodo · puedes volver a escuchar", color = Muted)
            if (run.challengeId == "sound_detective")
                Text("Oirás A, una pausa y B. Elige la posición que cambió.", color = Muted)
            Button(onClick = { onTouch(CollectionGameEngine.PLAY) },
                enabled = soundEnabled && run.audioNotes.isNotEmpty() && run.touchPhase != TouchPhase.AUDIO_PLAYING) {
                Text(if (run.touchPhase == TouchPhase.AUDIO_PLAYING) "Escuchando…" else "Escuchar de nuevo")
            }
            if (!soundEnabled) Text("Activa Sonidos del juego en Ajustes.", color = Gold)
            error?.let { Text(it, color = Gold) }
        }
        if (run.referenceLabels.isNotEmpty()) {
            Text("REFERENCIA · reproduce este mosaico", color = Cyan)
            run.referenceLabels.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { symbol ->
                        Surface(Modifier.weight(1f), color = PanelRaised, shape = MaterialTheme.shapes.medium) {
                            Text(symbol, Modifier.padding(12.dp), color = Muted,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Text("Arrastra una pieza a su hueco, o toca pieza y hueco.", color = Muted)
        }
        val pieces = if (run.challengeId == "in_place") run.referenceLabels.size else 0
        val groups = if (pieces > 0) listOf(0 until pieces, pieces until pieces * 2)
            else listOf(run.boardLabels.indices)
        groups.forEachIndexed { group, indices ->
            if (pieces > 0) Text(if (group == 0) "PIEZAS" else "HUECOS", color = Muted)
            indices.toList().chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { index ->
                        val active = run.highlightedTile == index
                        val completed = run.boardLabels[index] == "✓"
                        val fill by animateColorAsState(if (active || completed) PanelRaised else Panel, label = "tileFill")
                        val enabled = run.touchPhase == TouchPhase.INPUT && !completed && (!audio || soundEnabled) &&
                            (run.challengeId != "sound_detective" || run.audioAnswerReady) &&
                            !(pieces > 0 && index >= pieces && run.boardLabels[index] != "□")
                        var tileModifier = Modifier.weight(1f).heightIn(min = 64.dp)
                            .semantics {
                                selected = active
                                contentDescription = "${if (pieces > 0 && index >= pieces) "Hueco ${index - pieces + 1}" else "Tarjeta ${index + 1}"}: ${run.boardLabels[index]}"
                            }
                            .onGloballyPositioned { rectangles[index] = it.boundsInRoot() }
                        if (pieces > 0 && index < pieces && enabled) tileModifier = tileModifier.pointerInput(run.step, index, enabled) {
                            var position = Offset.Zero
                            detectDragGestures(onDragStart = { start ->
                                position = (rectangles[index]?.topLeft ?: Offset.Zero) + start
                                callback(index)
                            }, onDrag = { change, amount -> change.consume(); position += amount },
                                onDragEnd = {
                                    rectangles.entries.firstOrNull { it.key >= pieces && it.value.contains(position) }
                                        ?.let { callback(it.key) }
                                })
                        }
                        OutlinedButton(onClick = { onTouch(index) }, enabled = enabled, modifier = tileModifier,
                            border = BorderStroke(if (active) 2.dp else 1.dp, if (active) Cyan else Muted.copy(alpha = .3f)),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = fill,
                                contentColor = Cyan, disabledContainerColor = fill,
                                disabledContentColor = if (completed) Acid else if (active) Cyan else Muted)) {
                            Text(if (active && run.challengeId == "fleeting_route") "●" else run.boardLabels[index])
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        if (run.challengeId in setOf("sound_pairs", "tone_ladder"))
            Button(onClick = { onTouch(CollectionGameEngine.CONFIRM) },
                enabled = soundEnabled && run.audioAnswerReady && run.highlightedTile != null && run.touchPhase == TouchPhase.INPUT) {
                Text("Confirmar tarjeta escuchada")
            }
    }
}
