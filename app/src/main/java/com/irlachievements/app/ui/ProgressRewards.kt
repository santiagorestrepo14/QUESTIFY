package com.irlachievements.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.irlachievements.app.domain.challenges.*
import com.irlachievements.app.domain.model.*
import com.irlachievements.app.ui.theme.*

@Composable
internal fun ChallengeGuideDialog(definition: AchievementDefinition, unavailable: Set<Capability>,
    onDismiss: () -> Unit, onStart: () -> Unit, permissionNote: String? = null) {
    val kind = ChallengeDiscovery.kind(definition.id)
    val tactile = definition.id in TouchGameEngine.ids
    val microphone = definition.id in MicrophoneGameEngine.ids
    val initial = remember(definition.id) { if (microphone) MicrophoneGameEngine(definition.id).initialState() else ChallengeGameEngine(definition.id).initialState() }
    var entered by remember(definition.id) { mutableStateOf(false) }
    LaunchedEffect(definition.id) { entered = true }
    val tilt by animateFloatAsState(if (entered && kind != ChallengeKind.ENVIRONMENT) 18f else 0f, tween(650), label = "guideTilt")
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(definition.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!tactile) Icon(Icons.Default.ScreenRotation, null, tint = Cyan, modifier = Modifier.size(54.dp).align(Alignment.CenterHorizontally).rotate(tilt))
                Text(if (tactile) "Solo toques · sin sensores · colores suaves" else "Vista previa ilustrativa · sensores apagados", color = Cyan)
                Text(definition.description, color = Color.White)
                if (microphone) {
                    Text("MICRÓFONO SOLO DURANTE ESTE RETO", color = Gold)
                    Text("Pide permiso al iniciar. Se mide intensidad en memoria: no se reconocen palabras, no se guardan grabaciones y no se envía audio. Se detiene al terminar, salir o poner la app en segundo plano.")
                    Text("Guarda silencio durante la calibración inicial. Usa sonidos suaves. Puedes cancelar o revocar el permiso en Android.")
                    permissionNote?.let { Text(it, color = Gold) }
                }
                Text("AL EMPEZAR", fontWeight = FontWeight.Bold)
                Text(when (definition.id) {
                    "simon_says", "simon_says_ii" -> "Memoriza la secuencia que aparecerá al iniciar. Cambia en cada intento."
                    "target_practice" -> "Sigue los cinco ángulos que aparecerán durante la partida. Se eligen al iniciar."
                    "final_boss" -> "Memoriza IZQ · ARRIBA · DER, espera la señal y sigue el ángulo indicado en la partida."
                    else -> initial.instruction + "\n" + initial.detail
                })
                Text(when (definition.id) {
                    "color_echo" -> "Tres niveles: 3, 4 y 5 símbolos. Observa la flecha y repite en Tu turno. Hay 20 segundos para responder en cada nivel."
                    "quiet_signal" -> "Espera AHORA antes de tocar. Tres niveles con plazos de 3, 2,5 y 2 segundos. Tocar antes termina el intento."
                    "sound_pairs" -> "Toca una tarjeta para escucharla y confírmala. Escucha otra y confirma su pareja. Las parejas acertadas quedan marcadas; las distintas se ocultan de nuevo. Tres niveles: 3, 4 y 6 parejas."
                    "tone_ladder" -> "Puedes escuchar todas las tarjetas cuantas veces quieras. Confirma primero la más grave y continúa hacia la más aguda. Tres niveles: 3, 4 y 5 notas."
                    "sound_detective" -> "Pulsa Escuchar: sonará la secuencia A, una pausa larga y la secuencia B. Solo cambia una posición; tócala. Tres niveles: 3, 4 y 5 notas."
                    "fleeting_route" -> "Observa las casillas marcadas en orden. Cuando termine la demostración, repite la ruta con toques. Tres niveles de 4, 6 y 8 casillas contiguas."
                    "inverse_rule" -> "Nivel 1: toca la misma dirección. Nivel 2: la opuesta. Nivel 3: alterna las reglas leyendo IGUAL u OPUESTO."
                    "subtle_change" -> "Observa durante 3,5 segundos. Tras una pausa sin destellos, toca la única forma que cambió. Tres niveles con 4, 6 y 9 tarjetas."
                    "in_place" -> "Reproduce el mosaico de referencia en los huecos. Arrastra cada pieza o toca primero la pieza y después su hueco. Tres niveles con 3, 5 y 7 piezas."
                    "ascending_path" -> "Tres niveles con 3, 4 y 5 números mezclados. Tócalos de menor a mayor. Hay 25 segundos por nivel."
                    "odd_symbol" -> "Encuentra la única forma distinta entre 4, 6 y 9 tarjetas. Hay 20 segundos por nivel."
                    "stereo_compass" -> "Usa auriculares estéreo y prueba las direcciones. Nivel 1: dos señales izquierda/derecha. Nivel 2: tres señales incluyendo frente / centro. Nivel 3: cuatro señales mezcladas. Frente / centro suena en ambos oídos; no simula una posición física delante de ti. Responde con toques, sin límite de tiempo."
                    "voice_balance" -> "Mantén el indicador en la franja marcada durante 3, 4 y 5 segundos. Cada nivel estrecha la franja; no exige más volumen."
                    "clap_echo" -> "Escucha y observa el patrón, y repítelo con palmadas suaves cuando diga Tu turno. Tres niveles de 3, 4 y 5 golpes. Con Sonidos desactivado, la referencia es visual."
                    "acoustic_key" -> "Corto: 0,18–0,55 segundos; largo: 0,7–1,4 segundos. Deja pausas entre sonidos. Tres niveles de 3, 4 y 5 señales."
                    "silent_path" -> "Empieza cómodo y quieto, con espacio libre. Inclina a izquierda, derecha y arriba según se indique y vuelve al centro entre gestos. Cada postura requiere 0,7 segundos sin ruido. No camines mientras miras la pantalla."
                    else -> kind.guide
                })
                Text(if (tactile) "No se activa ningún sensor. No se modifica el brillo de la pantalla."
                    else "Necesita: " + ChallengeGameEngine.requiredCapabilities(definition.id)
                        .joinToString(", ", transform = ChallengeDiscovery::sensorName))
                if (definition.id in CollectionGameEngine.audioIds)
                    Text("Activa Sonidos del juego. Puedes usar el altavoz o auriculares a volumen cómodo. No se usa el micrófono ni hay límite de tiempo.")
                Text("Puedes salir cuando quieras. Repetir no vuelve a conceder la XP del logro.")
            }
        },
        confirmButton = {
            Button(onClick = onStart, enabled = ChallengeDiscovery.compatible(definition.id, unavailable)) { Text("Iniciar reto") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Ahora no") } },
        containerColor = Panel)
}

@Composable
internal fun NextRewardCard(state: AppUiState, onCollection: () -> Unit) {
    val next = remember(state.states) { LogoCollection.nextReward(state.states) }
    val level = XpProgression.from(state.summary.totalXp)
    ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = PanelRaised)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("TU PRÓXIMA RECOMPENSA", color = Acid, fontWeight = FontWeight.Bold)
            if (next != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QuestifyLogo(Modifier.size(54.dp), next.style)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(next.style.title, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(next.requirement, color = Muted)
                        Text(next.progressText, color = Cyan)
                    }
                }
                LinearProgressIndicator(progress = { next.progress }, modifier = Modifier.fillMaxWidth(), color = Acid)
            } else {
                Text("¡Tienes todos los logos de progresión visible!", color = Color.White)
            }
            level.nextRankLevel?.let {
                Text("Siguiente marco: nivel $it · ${level.xpToNextRank} XP restantes", color = Muted)
            }
            TextButton(onClick = onCollection) { Text("Ver colección de logos") }
        }
    }
}

@Composable
internal fun LogoRewardDialog(style: LogoStyle, onEquip: () -> Unit, onDismiss: () -> Unit, error: String? = null) {
    var entered by remember(style) { mutableStateOf(false) }
    LaunchedEffect(style) { entered = true }
    val scale by animateFloatAsState(if (entered) 1f else .65f, tween(420), label = "logoUnlock")
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("¡Nuevo logo desbloqueado!") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                QuestifyLogo(Modifier.size(130.dp).scale(scale), style)
                Text(style.title, color = Acid, fontWeight = FontWeight.Black)
                Text("Ya es tuyo. Puedes usarlo en tu perfil y en el escritorio de Android. No consume XP.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(onClick = onEquip) { Text("Equipar ahora") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Más tarde") } },
        containerColor = Panel)
}
