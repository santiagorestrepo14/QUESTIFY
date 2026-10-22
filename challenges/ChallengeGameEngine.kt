package com.irlachievements.app.domain.challenges

import com.irlachievements.app.domain.model.Capability
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

enum class RunStatus { IDLE, RUNNING, SUCCESS, FAILED, UNSUPPORTED }

data class SensorFrame(
    val elapsedMs: Long,
    val pitch: Float,
    val roll: Float,
    val gravityX: Float,
    val gravityY: Float,
    val gravityZ: Float,
    val linearAcceleration: Float,
    val yaw: Float = 0f,
    val gyroX: Float = 0f,
    val gyroY: Float = 0f,
    val gyroZ: Float = 0f,
    val lightLux: Float = 0f,
    val accelerationNoiseFloor: Float = .08f,
    val gyroscopeNoiseFloor: Float = .35f,
    val headingAccuracyDegrees: Float? = null,
    val headingReliable: Boolean = true,
    val environmentalValue: Float? = null,
)

data class ChallengeRunState(
    val challengeId: String? = null,
    val status: RunStatus = RunStatus.IDLE,
    val instruction: String = "Elige un reto",
    val detail: String = "Los sensores están apagados",
    val progress: Float = 0f,
    val step: Int = 0,
    val totalSteps: Int = 1,
    val reactionMs: Long? = null,
    val angularError: Float? = null,
    val extraMovements: Int = 0,
    val timingDeviationPercent: Float? = null,
    val failureCode: String? = null,
    val ballX: Float = -0.84f,
    val ballY: Float = 0.84f,
    val touchPhase: TouchPhase = TouchPhase.NONE,
    val highlightedTile: Int? = null,
    val touchChoices: List<Int> = emptyList(),
    val audioSide: Int = 0,
    val audioToken: Int = 0,
    val boardLabels: List<String> = emptyList(),
    val referenceLabels: List<String> = emptyList(),
    val audioNotes: List<Int> = emptyList(),
    val audioAnswerReady: Boolean = false,
    val microphoneActive: Boolean = false,
    val microphoneLevel: Float = 0f,
    val microphoneTargetLow: Float = 0f,
    val microphoneTargetHigh: Float = 0f,
    val microphoneBeat: Int = -1,
)

data class NormalizedRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(x: Float, y: Float, radius: Float = 0.055f): Boolean =
        x + radius > left && x - radius < right && y + radius > top && y - radius < bottom
}

class ChallengeGameEngine(
    private val challengeId: String,
    seed: Int = challengeId.hashCode(),
) {
    private val touchEngine = if (challengeId in TouchGameEngine.ids) TouchGameEngine(challengeId, seed) else null
    fun tickTouch(elapsedMs: Long): ChallengeRunState = requireNotNull(touchEngine).tick(elapsedMs)
    fun tap(tile: Int, elapsedMs: Long): ChallengeRunState = requireNotNull(touchEngine).tap(tile, elapsedMs)
    private val random = Random(seed)
    private var state = ChallengeRunState(challengeId, RunStatus.RUNNING)
    private var baseline: SensorFrame? = null
    private var phase = 0
    private var phaseStarted = 0L
    private var stableSince: Long? = null
    private var lastShake = -1_000L
    private var shakeArmed = true
    private val shakeTimes = mutableListOf<Long>()
    private var lastZone = Zone.NEUTRAL
    private var firstSide = 0
    private val greenAt = random.nextLong(2_600L, 4_801L)
    private val bombDuration = random.nextLong(9_000L, 14_001L)
    private val targetAngle = random.nextInt(-55, 56).let { if (abs(it) < 20) if (it < 0) -30 else 30 else it }.toFloat()
    private var lastFrameAt = 0L
    private var hasFrame = false
    private var rotationAxis = -1
    private var rotationDirection = 0
    private var accumulatedRotation = 0f
    private val axisRotations = FloatArray(3)
    private val axisDirections = IntArray(3)
    private var motionStartedAt: Long? = null
    private var actionCount = 0
    private var expectedSign = 1
    private var previousFace = 0
    private val simonTargets = List(if (challengeId == "simon_says_ii") 8 else 4) {
        listOf(Zone.LEFT, Zone.RIGHT, Zone.UP, Zone.DOWN)[random.nextInt(4)]
    }
    private val angleTargets = List(5) { random.nextInt(-60, 61).toFloat() }

    fun initialState(): ChallengeRunState = touchEngine?.tick(0) ?: describe(null)

    fun process(frame: SensorFrame): ChallengeRunState {
        touchEngine?.let { return it.tick(frame.elapsedMs) }
        if (state.status != RunStatus.RUNNING) return state
        if (hasFrame && frame.elapsedMs <= lastFrameAt) return state
        if (baseline == null) baseline = frame
        state = EnvironmentalChallenges.byId[challengeId]?.let { environmentalChallenge(frame, it) } ?: when (challengeId) {
            "secret_code" -> sequence(frame, listOf(Zone.FACE_UP, Zone.RIGHT, Zone.FACE_DOWN, Zone.LEFT), "Código")
            "false_start" -> falseStart(frame)
            "clone" -> clone(frame)
            "the_bomb" -> bomb(frame)
            "traffic_light" -> trafficLight(frame)
            "duel" -> duel(frame)
            "human_metronome" -> metronome(frame)
            "rhythm" -> rhythm(frame)
            "tilt_labyrinth" -> labyrinth(frame)
            "final_boss" -> finalBoss(frame)
            "perfect_run" -> perfectRun(frame)
            "full_circle" -> rotationChallenge(frame, 1)
            "do_it_backwards" -> rotationChallenge(frame, 1, requiredDirection = 1, requiredAxis = 2)
            "double_trouble" -> rotationChallenge(frame, 2)
            "beyblade" -> rotationChallenge(frame, 5)
            "let_it_rip" -> rotationChallenge(frame, 10)
            "triple_axis" -> tripleAxis(frame, ordered = false)
            "xyz" -> tripleAxis(frame, ordered = true)
            "steady_hand" -> holdOrientation(frame, tolerance = 2f, durationMs = 20_000)
            "rock_solid" -> holdOrientation(frame, tolerance = 1f, durationMs = 10_000)
            "spirit_level" -> holdLevel(frame, tolerance = 3f, durationMs = 15_000)
            "perfectionist" -> holdLevel(frame, tolerance = 1f, durationMs = 5_000)
            "upside_down" -> holdPosture(frame, durationMs = 30_000, upsideDown = true)
            "standing_tall" -> holdPosture(frame, durationMs = 30_000, upsideDown = false)
            "shake_it" -> shakeCount(frame, 20)
            "shake_it_off" -> shakeCount(frame, 50)
            "are_you_okay" -> shakeCount(frame, 100, 60_000)
            "left_right_left" -> tiltCount(frame, 20, lateral = true)
            "nod_yes" -> tiltCount(frame, 20, lateral = false)
            "flip_flop" -> flipCount(frame, 10)
            "slow_motion" -> rotationChallenge(frame, 1, minDurationMs = 15_000, maxDurationMs = 25_000)
            "speedrun" -> rotationChallenge(frame, 1, maxDurationMs = 2_000)
            "north_star" -> compass(frame, listOf(0f), holdMs = 10_000)
            "four_winds" -> compass(frame, listOf(0f, 90f, 180f, -90f), holdMs = 1_000)
            "wrong_way_around" -> compass(frame, listOf(-90f, 180f, 90f, 0f), holdMs = 1_000)
            "flat_spin" -> rotationChallenge(frame, 1, requireFlat = true)
            "simon_says", "simon_says_ii" -> simon(frame)
            "target_practice" -> targetPractice(frame)
            "dont_move" -> holdOrientation(frame, tolerance = 2f, durationMs = 60_000, maxAcceleration = .45f)
            "vampire" -> lightChallenge(frame, maximumLux = 30f)
            "supernova" -> lightChallenge(frame, minimumLux = 300f)
            else -> state.copy(status = RunStatus.UNSUPPORTED, instruction = "Reto no disponible", detail = "Esta mecánica aún no tiene motor")
        }
        lastFrameAt = frame.elapsedMs
        hasFrame = true
        return state
    }

    private fun describe(frame: SensorFrame?): ChallengeRunState = when (challengeId) {
        "secret_code" -> state.copy(instruction = "Boca arriba", detail = "Después: derecha · boca abajo · izquierda", totalSteps = 4)
        "false_start" -> state.copy(instruction = "Amaga hacia cualquier lado", detail = "Vuelve al centro y termina al lado contrario", totalSteps = 3)
        "clone" -> state.copy(instruction = "Mantén tu postura inicial", detail = "La memorizaré durante 1,5 segundos", totalSteps = 3)
        "the_bomb" -> state.copy(instruction = "No te muevas", detail = "Estabilizando detonador…", totalSteps = 1)
        "traffic_light" -> state.copy(instruction = "ROJO · quieto", detail = "Sacude únicamente cuando cambie a verde", totalSteps = 1)
        "duel" -> state.copy(instruction = "Espera…", detail = "Al ver ¡YA!, gira el móvil 90°", totalSteps = 1)
        "human_metronome" -> state.copy(instruction = "Haz 6 sacudidas regulares", detail = "Tú eliges el tempo; mantén intervalos iguales", totalSteps = 6)
        "rhythm" -> state.copy(instruction = "Repite: rápido · rápido · pausa · rápido", detail = "Empieza con una sacudida", totalSteps = 5)
        "tilt_labyrinth" -> state.copy(instruction = "Lleva la bola a la meta", detail = "Inclina suavemente y no toques paredes", totalSteps = 1)
        "final_boss" -> state.copy(instruction = "Memoriza: IZQ · ARRIBA · DER", detail = "Después espera la señal y termina en ${targetAngle.toInt()}°", totalSteps = 5)
        "perfect_run" -> state.copy(instruction = "RONDA 1/4 · Reacción", detail = "Quieto hasta la señal; después sacude", totalSteps = 4)
        "full_circle" -> rotationDescription("Completa una vuelta", 1)
        "do_it_backwards" -> rotationDescription("Gira en sentido antihorario", 1)
        "double_trouble" -> rotationDescription("Completa dos vueltas", 2)
        "beyblade" -> rotationDescription("Completa cinco vueltas", 5)
        "let_it_rip" -> rotationDescription("Completa diez vueltas", 10)
        "triple_axis" -> state.copy(instruction = "Gira 360° en cada eje", detail = "X · Y · Z en cualquier orden", totalSteps = 3)
        "xyz" -> state.copy(instruction = "EJE X", detail = "Después Y y Z", totalSteps = 3)
        "steady_hand" -> holdDescription("Mantén la orientación", 20)
        "rock_solid" -> holdDescription("No salgas de ±1°", 10)
        "spirit_level" -> holdDescription("Colócalo horizontal", 15)
        "perfectionist" -> holdDescription("Horizontal dentro de ±1°", 5)
        "upside_down" -> holdDescription("Ponlo boca abajo", 30)
        "standing_tall" -> holdDescription("Ponlo completamente vertical", 30)
        "shake_it" -> state.copy(instruction = "0 / 20", detail = "Sacudidas controladas", totalSteps = 20)
        "shake_it_off" -> state.copy(instruction = "0 / 50", detail = "Sacudidas controladas", totalSteps = 50)
        "are_you_okay" -> state.copy(instruction = "0 / 100", detail = "Tienes 60 segundos", totalSteps = 100)
        "left_right_left" -> state.copy(instruction = "Inclina izquierda y derecha", detail = "20 cambios alternados", totalSteps = 20)
        "nod_yes" -> state.copy(instruction = "Inclina arriba y abajo", detail = "20 cambios alternados", totalSteps = 20)
        "flip_flop" -> state.copy(instruction = "Boca arriba ↔ boca abajo", detail = "10 alternancias", totalSteps = 10)
        "slow_motion" -> rotationDescription("Una vuelta muy lenta", 1).copy(detail = "Entre 15 y 25 segundos")
        "speedrun" -> rotationDescription("Una vuelta", 1).copy(detail = "Menos de 2 segundos")
        "north_star" -> state.copy(instruction = "Busca el norte", detail = "Mantén ±10° durante 10 segundos")
        "four_winds" -> state.copy(instruction = "NORTE", detail = "Después este, sur y oeste", totalSteps = 4)
        "wrong_way_around" -> state.copy(instruction = "OESTE", detail = "Después sur, este y norte", totalSteps = 4)
        "flat_spin" -> rotationDescription("Gira manteniendo horizontal", 1)
        "simon_says", "simon_says_ii" -> state.copy(instruction = "MEMORIZA", detail = simonTargets.joinToString(" · ") { it.label }, totalSteps = simonTargets.size)
        "target_practice" -> state.copy(instruction = "OBJETIVO ${angleTargets.first().toInt()}°", detail = "Cinco ángulos aleatorios", totalSteps = 5)
        "dont_move" -> holdDescription("No te muevas", 60)
        "vampire" -> state.copy(instruction = "Busca un lugar con poca luz", detail = "Mantén 30 lux o menos durante 3 segundos. Puedes cubrir suavemente el sensor superior.")
        "supernova" -> state.copy(instruction = "Busca un lugar bien iluminado", detail = "Mantén 300 lux o más durante 3 segundos. Acerca el teléfono a una lámpara sin tocarla.")
        else -> EnvironmentalChallenges.byId[challengeId]?.let {
            state.copy(instruction = "Esperando lectura real", detail = "Objetivo ${it.targetLabel} · 3 segundos")
        } ?: state
    }

    private fun rotationDescription(instruction: String, turns: Int) = state.copy(instruction = instruction, detail = "El giroscopio medirá el giro continuo", totalSteps = turns)
    private fun holdDescription(instruction: String, seconds: Int) = state.copy(instruction = instruction, detail = "$seconds segundos continuos")

    private fun sequence(frame: SensorFrame, targets: List<Zone>, label: String): ChallengeRunState {
        val current = zone(frame, targets[phase])
        if (current != Zone.NEUTRAL && (lastZone == Zone.NEUTRAL ||
                (current.isFace && current != lastZone))) {
            if (current == targets[phase]) {
                phase++
                if (phase == targets.size) return success("$label completado", "Secuencia limpia: ${targets.joinToString(" · ") { it.label }}")
            } else {
                return fail("Movimiento incorrecto", "Esperaba ${targets[phase].label}; detecté ${current.label}")
            }
        }
        lastZone = current
        return state.copy(
            instruction = targets[phase].label,
            detail = "Paso ${phase + 1} de ${targets.size} · vuelve a la postura inicial entre inclinaciones",
            step = phase,
            totalSteps = targets.size,
            progress = phase.toFloat() / targets.size,
        )
    }

    private fun falseStart(frame: SensorFrame): ChallengeRunState {
        when (phase) {
            0 -> if (abs(frame.roll) >= 35f) { firstSide = if (frame.roll > 0) 1 else -1; phase = 1 }
            1 -> if (abs(frame.roll) <= 8f) phase = 2
            2 -> if (frame.roll * firstSide <= -35f) return success("Engaño perfecto", "Amago, centro y giro contrario detectados")
        }
        val prompts = listOf("Amaga a un lado", "Regresa al centro", "Termina al lado contrario")
        return state.copy(instruction = prompts[phase], detail = "Giro lateral controlado", step = phase, totalSteps = 3, progress = phase / 3f)
    }

    private fun clone(frame: SensorFrame): ChallengeRunState {
        val base = baseline ?: frame
        val error = orientationError(base, frame)
        when (phase) {
            0 -> if (frame.elapsedMs >= 1_500) { phase = 1; phaseStarted = frame.elapsedMs }
            1 -> if (error >= 25f) { phase = 2; stableSince = null }
            2 -> {
                if (error <= 5f && frame.isStable(.45f)) {
                    val since = stableSince ?: frame.elapsedMs.also { stableSince = it }
                    if (frame.elapsedMs - since >= 1_200) return success("Orientación clonada", "Error final ${"%.1f".format(error)}°", error)
                } else stableSince = null
            }
        }
        val prompt = listOf("Memorizando postura…", "Muévelo al menos 25°", "Regresa a la postura original")[phase]
        return state.copy(instruction = prompt, detail = "Error actual ${"%.1f".format(error)}°", step = phase, totalSteps = 3, progress = when (phase) { 0 -> (frame.elapsedMs / 1_500f).coerceAtMost(1f); 1 -> (error / 25f).coerceAtMost(1f); else -> (1f - error / 25f).coerceIn(0f, 1f) }, angularError = error)
    }

    private fun bomb(frame: SensorFrame): ChallengeRunState {
        val base = baseline ?: frame
        if (frame.elapsedMs > 1_200 && (!frame.isStable(.9f) || orientationError(base, frame) > 4.5f)) {
            return fail("BOOM", "Movimiento fuera del margen seguro")
        }
        if (frame.elapsedMs >= bombDuration) return success("Bomba desactivada", "${"%.1f".format(bombDuration / 1_000f)} s sin romper la estabilidad")
        val remaining = ((bombDuration - frame.elapsedMs).coerceAtLeast(0) / 1_000f)
        return state.copy(instruction = "${"%.1f".format(remaining)}", detail = "No te muevas · tolerancia angular 4,5°", progress = frame.elapsedMs.toFloat() / bombDuration)
    }

    private fun trafficLight(frame: SensorFrame): ChallengeRunState {
        val shake = isNewShake(frame)
        if (frame.elapsedMs < greenAt) {
            if (shake) return fail("Salida falsa", "Te moviste ${greenAt - frame.elapsedMs} ms antes de la señal", "EARLY")
            return state.copy(instruction = "ROJO", detail = "Quieto…", progress = frame.elapsedMs.toFloat() / greenAt)
        }
        if (frame.elapsedMs > greenAt + 3_000) return fail("Demasiado tarde", "No hubo reacción dentro de 3 segundos")
        if (shake) return success("VERDE", "Reacción en ${frame.elapsedMs - greenAt} ms", reaction = frame.elapsedMs - greenAt)
        return state.copy(instruction = "¡VERDE!", detail = "¡Sacude ahora!", progress = 1f)
    }

    private fun duel(frame: SensorFrame): ChallengeRunState {
        val base = baseline ?: frame
        val delta = angularDifference(frame.roll, base.roll)
        if (frame.elapsedMs < greenAt) {
            if (delta > 18f) return fail("Desenfundaste antes", "Salida falsa detectada", "EARLY")
            return state.copy(instruction = "ESPERA…", detail = "No gires antes de la señal", progress = frame.elapsedMs.toFloat() / greenAt)
        }
        if (frame.elapsedMs > greenAt + 2_000) return fail("Duelo perdido", "No alcanzaste 90° a tiempo")
        if (delta in 78f..102f) return success("¡Impacto!", "Giro de ${delta.toInt()}° en ${frame.elapsedMs - greenAt} ms", error = abs(90f - delta), reaction = frame.elapsedMs - greenAt)
        return state.copy(instruction = "¡YA!", detail = "Gira 90° · actual ${delta.toInt()}°", progress = (delta / 90f).coerceAtMost(1f), angularError = abs(90f - delta))
    }

    private fun metronome(frame: SensorFrame): ChallengeRunState {
        if (isNewShake(frame)) shakeTimes += frame.elapsedMs
        if (shakeTimes.size >= 6) {
            val intervals = shakeTimes.zipWithNext { a, b -> b - a }
            val average = intervals.average()
            val maxError = intervals.maxOf { abs(it - average) } / average
            return if (maxError <= .16) success("Tempo humano perfecto", "Desviación máxima ${"%.1f".format(maxError * 100)}%").copy(timingDeviationPercent = (maxError * 100).toFloat())
            else fail("Tempo irregular", "Desviación ${"%.1f".format(maxError * 100)}%; objetivo ≤16%")
        }
        return state.copy(instruction = "${shakeTimes.size} / 6", detail = if (shakeTimes.isEmpty()) "Marca tu propio tempo" else "Mantén exactamente el intervalo", step = shakeTimes.size, totalSteps = 6, progress = shakeTimes.size / 6f)
    }

    private fun rhythm(frame: SensorFrame): ChallengeRunState {
        val expected = listOf(450L, 450L, 900L, 450L)
        if (isNewShake(frame)) {
            shakeTimes += frame.elapsedMs
            if (shakeTimes.size >= 2) {
                val index = shakeTimes.size - 2
                val actual = shakeTimes[index + 1] - shakeTimes[index]
                if (abs(actual - expected[index]) > 220L) return fail("Ritmo roto", "Intervalo ${actual} ms; objetivo ${expected[index]} ms")
            }
        }
        if (shakeTimes.size == 5) return success("Patrón clavado", "Rápido · rápido · pausa · rápido")
        val labels = listOf("EMPIEZA", "RÁPIDO", "RÁPIDO", "PAUSA", "RÁPIDO")
        return state.copy(instruction = labels[shakeTimes.size], detail = "Tolerancia ±220 ms", step = shakeTimes.size, totalSteps = 5, progress = shakeTimes.size / 5f)
    }

    private fun labyrinth(frame: SensorFrame): ChallengeRunState {
        val dt = if (!hasFrame) 0f else ((frame.elapsedMs - lastFrameAt).coerceIn(0L, 50L) / 1_000f)
        val nextX = (state.ballX + frame.roll.coerceIn(-30f, 30f) / 30f * dt * .72f).coerceIn(-.94f, .94f)
        val nextY = (state.ballY - frame.pitch.coerceIn(-30f, 30f) / 30f * dt * .72f).coerceIn(-.94f, .94f)
        if ((nextX != state.ballX || nextY != state.ballY) &&
            (abs(nextX) >= .935f || abs(nextY) >= .935f || walls.any { it.contains(nextX, nextY) })) {
            return fail("Tocaste una pared", "La ruta exige movimientos suaves")
        }
        val distance = hypot(nextX - .82f, nextY + .82f)
        if (distance < .12f) return success("Laberinto limpio", "Llegaste sin colisiones")
        return state.copy(ballX = nextX, ballY = nextY, instruction = "META ↗", detail = "Sin colisiones · distancia ${"%.2f".format(distance)}", progress = (1f - distance / 2.35f).coerceIn(0f, 1f))
    }

    private fun finalBoss(frame: SensorFrame): ChallengeRunState {
        val sequence = listOf(Zone.LEFT, Zone.UP, Zone.RIGHT)
        if (phase == 0 && frame.elapsedMs >= 3_000) { phase = 1; phaseStarted = frame.elapsedMs }
        if (phase == 0) return state.copy(instruction = "MEMORIZA", detail = "IZQ · ARRIBA · DER", progress = frame.elapsedMs / 3_000f, totalSteps = 5)
        if (phase == 1) {
            if (frame.elapsedMs < phaseStarted + greenAt) {
                if (zone(frame) != Zone.NEUTRAL || !frame.isStable(1.1f)) return fail("Salida falsa", "El jefe detectó movimiento antes de la señal")
                return state.copy(instruction = "ESPERA…", detail = "La secuencia ya está oculta", progress = .2f, step = 1, totalSteps = 5)
            }
            phase = 2; lastZone = Zone.NEUTRAL
        }
        if (phase in 2..4) {
            val index = phase - 2
            val current = zone(frame, sequence[index])
            if (current != Zone.NEUTRAL && (lastZone == Zone.NEUTRAL ||
                    (current.isFace && current != lastZone))) {
                if (current != sequence[index]) return fail("Combo roto", "Movimiento ${index + 1} incorrecto")
                phase++
            }
            lastZone = current
            return state.copy(instruction = "EJECUTA DE MEMORIA", detail = "Movimiento ${index + 1} de 3", step = phase - 1, totalSteps = 5, progress = (phase - 1) / 5f)
        }
        val error = abs(frame.roll - targetAngle)
        if (error <= 4f && frame.isStable(.6f)) {
            val since = stableSince ?: frame.elapsedMs.also { stableSince = it }
            if (frame.elapsedMs - since >= 800) return success("Jefe derrotado", "Final a ${"%.1f".format(frame.roll)}° · error ${"%.1f".format(error)}°", error)
        } else stableSince = null
        return state.copy(instruction = "FINAL: ${targetAngle.toInt()}°", detail = "Actual ${frame.roll.toInt()}° · mantén 0,8 s", step = 4, totalSteps = 5, progress = .8f + .2f * (1f - error / 90f).coerceIn(0f, 1f), angularError = error)
    }

    private fun perfectRun(frame: SensorFrame): ChallengeRunState {
        val sequence = listOf(Zone.LEFT, Zone.RIGHT, Zone.FACE_UP)
        when (phase) {
            0 -> {
                val shake = isNewShake(frame)
                if (frame.elapsedMs < greenAt && shake) return fail("Perfect Run perdida", "Salida falsa en reacción")
                if (frame.elapsedMs >= greenAt && shake) { phase = 1; phaseStarted = frame.elapsedMs; lastZone = Zone.NEUTRAL }
                return state.copy(instruction = if (frame.elapsedMs < greenAt) "1/4 · ESPERA" else "1/4 · ¡SACUDE!", detail = "Prueba de reacción", step = 0, totalSteps = 4, progress = 0f)
            }
            1 -> {
                if (frame.elapsedMs - phaseStarted < 2_200) return state.copy(instruction = "2/4 · MEMORIZA", detail = "IZQ · DER · BOCA ARRIBA", step = 1, totalSteps = 4, progress = .25f)
                phase = 2
            }
        }
        if (phase in 2..4) {
            val index = phase - 2
            val current = zone(frame, sequence[index])
            if (current != Zone.NEUTRAL && (lastZone == Zone.NEUTRAL ||
                    (current.isFace && current != lastZone))) {
                if (current != sequence[index]) return fail("Perfect Run perdida", "Error en memoria")
                phase++
                if (phase == 5) phaseStarted = frame.elapsedMs
            }
            lastZone = current
            return state.copy(instruction = "2/4 · REPRODUCE", detail = "Paso ${index + 1} de 3", step = 1, totalSteps = 4, progress = .25f + index / 12f)
        }
        if (phase == 5) {
            if (!frame.isStable(.65f) || abs(frame.roll) > 5f || abs(frame.pitch) > 5f) phaseStarted = frame.elapsedMs
            if (frame.elapsedMs - phaseStarted >= 3_000) { phase = 6; stableSince = null }
            return state.copy(instruction = "3/4 · INMÓVIL", detail = "3 segundos dentro de ±5°", step = 2, totalSteps = 4, progress = .5f + .25f * ((frame.elapsedMs - phaseStarted) / 3_000f).coerceIn(0f, 1f))
        }
        val error = abs(frame.roll - 45f)
        if (error <= 3f && frame.isStable(.55f)) {
            val since = stableSince ?: frame.elapsedMs.also { stableSince = it }
            if (frame.elapsedMs - since >= 900) return success("PERFECT RUN", "Las cuatro familias superadas sin fallar", error)
        } else stableSince = null
        return state.copy(instruction = "4/4 · 45°", detail = "Actual ${frame.roll.toInt()}° · tolerancia ±3°", step = 3, totalSteps = 4, progress = .75f + .25f * (1f - error / 90f).coerceIn(0f, 1f), angularError = error)
    }

    private fun rotationChallenge(
        frame: SensorFrame,
        turns: Int,
        requiredDirection: Int? = null,
        requiredAxis: Int? = null,
        minDurationMs: Long = 0,
        maxDurationMs: Long? = null,
        requireFlat: Boolean = false,
    ): ChallengeRunState {
        val rates = calibratedGyroscopeRates(frame)
        if (rotationAxis < 0) {
            val candidate = requiredAxis ?: rates.indices.maxByOrNull { abs(rates[it]) } ?: 0
            val startThreshold = max(if (minDurationMs > 0) 8f else 20f, frame.gyroscopeNoiseFloor * 4f).coerceAtMost(20f)
            if (abs(rates[candidate]) >= startThreshold) {
                rotationAxis = candidate
                rotationDirection = if (rates[candidate] >= 0f) 1 else -1
                motionStartedAt = frame.elapsedMs
                if (requiredDirection != null && rotationDirection != requiredDirection) return fail("Sentido incorrecto", "Gira en el sentido indicado")
            }
        }
        val start = motionStartedAt
        if (rotationAxis >= 0 && start != null) {
            if (requireFlat && (abs(frame.pitch) > 9f || abs(frame.roll) > 9f)) return fail("Perdiste el plano", "Mantén pitch y roll dentro de ±9°")
            val dt = ((frame.elapsedMs - lastFrameAt).coerceIn(0L, 100L) / 1_000f)
            val signed = rates[rotationAxis] * rotationDirection
            accumulatedRotation = (accumulatedRotation + signed * dt).coerceAtLeast(0f)
            val duration = frame.elapsedMs - start
            if (maxDurationMs != null && duration > maxDurationMs && accumulatedRotation < turns * 360f) return fail("Tiempo agotado", "No completaste el giro dentro del límite")
            if (accumulatedRotation >= turns * 360f) {
                if (duration < minDurationMs) return fail("Demasiado rápido", "Duración ${"%.1f".format(duration / 1_000f)} s; mínimo ${minDurationMs / 1_000} s")
                if (maxDurationMs != null && duration > maxDurationMs) return fail("Demasiado lento", "Duración ${"%.1f".format(duration / 1_000f)} s")
                return success("Rotación completada", "${"%.0f".format(accumulatedRotation)}° en ${"%.1f".format(duration / 1_000f)} s")
            }
        }
        val target = turns * 360f
        return state.copy(
            instruction = if (rotationAxis < 0) "Empieza a girar" else "${accumulatedRotation.toInt()}° / ${target.toInt()}°",
            detail = when { requireFlat -> "Mantén el teléfono horizontal"; requiredDirection != null -> "Pantalla hacia ti · giro antihorario sobre el eje Z"; else -> "No inviertas el sentido" },
            progress = (accumulatedRotation / target).coerceIn(0f, 1f),
            step = (accumulatedRotation / 360f).toInt().coerceAtMost(turns),
            totalSteps = turns,
        )
    }

    private fun tripleAxis(frame: SensorFrame, ordered: Boolean): ChallengeRunState {
        val rates = calibratedGyroscopeRates(frame)
        val dt = ((frame.elapsedMs - lastFrameAt).coerceIn(0L, 100L) / 1_000f)
        if (ordered) {
            accumulateAxis(phase, rates[phase], dt)
            if (axisRotations[phase] >= 360f) {
                phase++
                if (phase == 3) return success("XYZ completado", "Tres ejes en el orden correcto")
            }
        } else {
            rates.indices.forEach { axis -> accumulateAxis(axis, rates[axis], dt) }
            if (axisRotations.all { it >= 360f }) return success("Triple Axis completado", "X, Y y Z superaron 360°")
        }
        val names = listOf("X", "Y", "Z")
        val completed = axisRotations.count { it >= 360f }
        return state.copy(
            instruction = if (ordered) "EJE ${names[phase]} · ${axisRotations[phase].toInt()}°" else names.indices.joinToString(" · ") { "${names[it]} ${axisRotations[it].toInt().coerceAtMost(360)}°" },
            detail = if (ordered) "Completa X, después Y y después Z" else "Gira cada eje 360°",
            progress = (axisRotations.sumOf { it.coerceAtMost(360f).toDouble() } / 1_080.0).toFloat(),
            step = completed,
            totalSteps = 3,
        )
    }

    private fun accumulateAxis(axis: Int, rate: Float, dt: Float) {
        if (axisRotations[axis] >= 360f) return
        if (axisDirections[axis] == 0 && abs(rate) >= 20f) axisDirections[axis] = if (rate > 0) 1 else -1
        axisRotations[axis] = (axisRotations[axis] + rate * axisDirections[axis] * dt).coerceAtLeast(0f)
    }

    private fun holdOrientation(frame: SensorFrame, tolerance: Float, durationMs: Long, maxAcceleration: Float = .7f): ChallengeRunState {
        val base = baseline ?: frame
        val error = orientationError(base, frame)
        val valid = error <= tolerance && frame.isStable(maxAcceleration)
        if (valid) {
            val since = stableSince ?: frame.elapsedMs.also { stableSince = it }
            if (frame.elapsedMs - since >= durationMs) return success("Estabilidad conseguida", "${durationMs / 1_000} s · error final ${"%.1f".format(error)}°", error)
        } else stableSince = null
        val held = stableSince?.let { frame.elapsedMs - it } ?: 0L
        return state.copy(instruction = "${"%.1f".format(held / 1_000f)} / ${durationMs / 1_000} s", detail = "Error ${"%.1f".format(error)}° · tolerancia ±$tolerance°", progress = held.toFloat() / durationMs, angularError = error)
    }

    private fun holdLevel(frame: SensorFrame, tolerance: Float, durationMs: Long): ChallengeRunState {
        val error = max(abs(frame.pitch), abs(frame.roll))
        if (error <= tolerance && frame.gravityZ > .75f && frame.isStable(.65f)) {
            val since = stableSince ?: frame.elapsedMs.also { stableSince = it }
            if (frame.elapsedMs - since >= durationMs) return success("Nivel perfecto", "${durationMs / 1_000} s dentro de ±$tolerance°", error)
        } else stableSince = null
        val held = stableSince?.let { frame.elapsedMs - it } ?: 0L
        return state.copy(instruction = "Horizontal · ${"%.1f".format(held / 1_000f)} s", detail = "Pitch ${frame.pitch.toInt()}° · roll ${frame.roll.toInt()}°", progress = held.toFloat() / durationMs, angularError = error)
    }

    private fun holdPosture(frame: SensorFrame, durationMs: Long, upsideDown: Boolean): ChallengeRunState {
        val valid = if (upsideDown) frame.gravityZ < -.82f else abs(frame.gravityZ) < .12f
        if (valid && frame.isStable(.7f)) {
            val since = stableSince ?: frame.elapsedMs.also { stableSince = it }
            if (frame.elapsedMs - since >= durationMs) return success(if (upsideDown) "Boca abajo completado" else "Vertical completado", "${durationMs / 1_000} segundos continuos")
        } else stableSince = null
        val held = stableSince?.let { frame.elapsedMs - it } ?: 0L
        return state.copy(instruction = if (upsideDown) "Boca abajo" else "Vertical", detail = "${"%.1f".format(held / 1_000f)} / ${durationMs / 1_000} s", progress = held.toFloat() / durationMs)
    }

    private fun shakeCount(frame: SensorFrame, target: Int, timeLimitMs: Long? = null): ChallengeRunState {
        if (timeLimitMs != null && frame.elapsedMs > timeLimitMs) return fail("Tiempo agotado", "$actionCount de $target sacudidas")
        if (isNewShake(frame)) actionCount++
        if (actionCount >= target) return success("Sacudidas completadas", "$target movimientos controlados")
        return state.copy(instruction = "$actionCount / $target", detail = timeLimitMs?.let { "Quedan ${((it - frame.elapsedMs).coerceAtLeast(0) / 1_000)} s" } ?: "Sacudidas separadas y controladas", step = actionCount, totalSteps = target, progress = actionCount / target.toFloat())
    }

    private fun tiltCount(frame: SensorFrame, target: Int, lateral: Boolean): ChallengeRunState {
        val base = baseline ?: frame
        val delta = if (lateral) signedAngularDifference(frame.roll, base.roll) else signedAngularDifference(frame.pitch, base.pitch)
        if (delta * expectedSign >= 28f) {
            actionCount++
            expectedSign *= -1
        }
        if (actionCount >= target) return success("Serie completada", "$target inclinaciones alternadas")
        return state.copy(instruction = "$actionCount / $target · ${if (expectedSign > 0) if (lateral) "DERECHA" else "ARRIBA" else if (lateral) "IZQUIERDA" else "ABAJO"}", detail = "Cruza 28° en cada dirección", step = actionCount, totalSteps = target, progress = actionCount / target.toFloat())
    }

    private fun flipCount(frame: SensorFrame, target: Int): ChallengeRunState {
        val sign = when { frame.gravityZ > .78f -> 1; frame.gravityZ < -.78f -> -1; else -> 0 }
        if (sign != 0 && sign != previousFace) {
            if (previousFace != 0) actionCount++
            previousFace = sign
        }
        if (actionCount >= target) return success("Flip Flop completado", "$target alternancias")
        return state.copy(instruction = "$actionCount / $target · ${if (previousFace > 0) "BOCA ABAJO" else "BOCA ARRIBA"}", detail = "Alterna las dos caras; la postura inicial no cuenta", step = actionCount, totalSteps = target, progress = actionCount / target.toFloat())
    }

    private fun compass(frame: SensorFrame, targets: List<Float>, holdMs: Long): ChallengeRunState {
        val target = targets[phase]
        val error = angularDifference(frame.yaw, target)
        if (!frame.headingReliable || (frame.headingAccuracyDegrees ?: 0f) > 10f) {
            stableSince = null
            return state.copy(instruction = "Ajusta la brújula", detail = "Aléjate de imanes y metal; mueve el móvil suavemente en forma de ocho", progress = phase.toFloat() / targets.size)
        }
        if (error <= 10f && frame.isStable(.7f)) {
            val since = stableSince ?: frame.elapsedMs.also { stableSince = it }
            if (frame.elapsedMs - since >= holdMs) {
                phase++
                stableSince = null
                if (phase == targets.size) return success("Rumbo completado", targets.joinToString(" · ") { compassName(it) })
            }
        } else stableSince = null
        val held = stableSince?.let { frame.elapsedMs - it } ?: 0L
        val accuracy = frame.headingAccuracyDegrees?.let {
            if (it > 30f) " · precisión baja (±${it.toInt()}°)" else " · precisión ±${it.toInt()}°"
        }.orEmpty()
        return state.copy(instruction = compassName(target), detail = "Rumbo ${frame.yaw.toInt()}° · error ${error.toInt()}°$accuracy", step = phase, totalSteps = targets.size, progress = (phase + held.toFloat() / holdMs) / targets.size, angularError = error)
    }

    private fun simon(frame: SensorFrame): ChallengeRunState {
        val memorizeMs = if (simonTargets.size > 4) 5_000L else 3_000L
        if (phase == 0) {
            if (frame.elapsedMs < memorizeMs) return state.copy(instruction = "MEMORIZA", detail = simonTargets.joinToString(" · ") { it.label }, progress = frame.elapsedMs.toFloat() / memorizeMs / 4f, totalSteps = simonTargets.size)
            phase = 1
            lastZone = Zone.NEUTRAL
        }
        val index = phase - 1
        val current = zone(frame)
        if (current != Zone.NEUTRAL && lastZone == Zone.NEUTRAL) {
            if (current != simonTargets[index]) return fail("Simon dice: no", "Movimiento ${index + 1} incorrecto")
            phase++
            if (phase > simonTargets.size) return success("Simon completado", "${simonTargets.size} orientaciones de memoria")
        }
        lastZone = current
        return state.copy(instruction = "REPITE DE MEMORIA", detail = "Movimiento ${index + 1} de ${simonTargets.size}", step = index, totalSteps = simonTargets.size, progress = index / simonTargets.size.toFloat())
    }

    private fun targetPractice(frame: SensorFrame): ChallengeRunState {
        val target = angleTargets[phase]
        val error = abs(frame.roll - target)
        if (error <= 4f && frame.isStable(.65f)) {
            val since = stableSince ?: frame.elapsedMs.also { stableSince = it }
            if (frame.elapsedMs - since >= 600) {
                phase++
                stableSince = null
                if (phase == angleTargets.size) return success("Cinco impactos", "Todos los objetivos dentro de ±4°", error)
            }
        } else stableSince = null
        return state.copy(instruction = "OBJETIVO ${target.toInt()}°", detail = "Actual ${frame.roll.toInt()}° · error ${error.toInt()}°", step = phase, totalSteps = angleTargets.size, progress = phase / angleTargets.size.toFloat(), angularError = error)
    }

    private fun lightChallenge(frame: SensorFrame, maximumLux: Float? = null, minimumLux: Float? = null): ChallengeRunState {
        val valid = frame.lightLux.isFinite() && frame.lightLux >= 0f &&
            (maximumLux?.let { frame.lightLux <= it } ?: (frame.lightLux >= (minimumLux ?: Float.MAX_VALUE)))
        if (valid) {
            val since = stableSince ?: frame.elapsedMs.also { stableSince = it }
            if (frame.elapsedMs - since >= 3_000L) {
                return success(
                    if (maximumLux != null) "Modo vampiro" else "Supernova detectada",
                    "${"%.1f".format(frame.lightLux)} lux medidos por el sensor",
                )
            }
        } else stableSince = null
        val held = stableSince?.let { frame.elapsedMs - it } ?: 0L
        return state.copy(
            instruction = "${"%.1f".format(frame.lightLux)} lux",
            detail = if (maximumLux != null) "Objetivo ≤ ${maximumLux.toInt()} lux" else "Objetivo ≥ ${minimumLux?.toInt()} lux",
            progress = (held / 3_000f).coerceIn(0f, 1f),
        )
    }

    private fun environmentalChallenge(frame: SensorFrame, challenge: EnvironmentalChallenge): ChallengeRunState {
        val value = frame.environmentalValue
        if (value == null || !challenge.isValid(value)) {
            stableSince = null
            return state.copy(instruction = "Esperando lectura válida", detail = "Objetivo ${challenge.targetLabel}", progress = 0f)
        }
        if (challenge.accepts(value)) {
            val since = stableSince ?: frame.elapsedMs.also { stableSince = it }
            if (frame.elapsedMs - since >= 3_000) return success(challenge.title, "${"%.1f".format(value)} ${challenge.unit} · 3 segundos verificados")
        } else stableSince = null
        val held = stableSince?.let { frame.elapsedMs - it } ?: 0L
        return state.copy(instruction = "${"%.1f".format(value)} ${challenge.unit}",
            detail = "Objetivo ${challenge.targetLabel} · ${"%.1f".format(held / 1_000f)} / 3 s · no expongas el teléfono a condiciones extremas",
            progress = (held / 3_000f).coerceIn(0f, 1f))
    }

    private fun signedAngularDifference(a: Float, b: Float): Float = ((a - b + 540f) % 360f) - 180f
    private fun angularDifference(a: Float, b: Float): Float = abs(signedAngularDifference(a, b))
    private fun compassName(angle: Float): String = when (angle.toInt()) { 0 -> "NORTE"; 90 -> "ESTE"; 180, -180 -> "SUR"; -90, 270 -> "OESTE"; else -> "${angle.toInt()}°" }

    private fun isNewShake(frame: SensorFrame): Boolean {
        val threshold = max(2.35f, frame.accelerationNoiseFloor * 8f).coerceAtMost(3.2f)
        if (frame.linearAcceleration < threshold * .5f) shakeArmed = true
        if (!shakeArmed || frame.linearAcceleration < threshold || frame.elapsedMs - lastShake < 320L) return false
        shakeArmed = false
        lastShake = frame.elapsedMs
        return true
    }

    private fun SensorFrame.isStable(baseThreshold: Float): Boolean =
        linearAcceleration <= max(baseThreshold, accelerationNoiseFloor * 4f).coerceAtMost(1.2f)

    private fun calibratedGyroscopeRates(frame: SensorFrame): FloatArray {
        val deadband = max(1.5f, frame.gyroscopeNoiseFloor * 3f).coerceAtMost(9f)
        return floatArrayOf(frame.gyroX, frame.gyroY, frame.gyroZ).mapToFloatArray { rate ->
            when {
                rate > deadband -> rate
                rate < -deadband -> rate
                else -> 0f
            }
        }
    }

    private inline fun FloatArray.mapToFloatArray(transform: (Float) -> Float): FloatArray =
        FloatArray(size) { index -> transform(this[index]) }

    private fun zone(frame: SensorFrame, expected: Zone? = null): Zone {
        // Absolute face targets can coincide with the initial neutral pose. Other games
        // only classify directional tilts, so a shallow tilt is never a spurious face-up.
        if (expected == Zone.FACE_UP && frame.gravityZ > .78f) return Zone.FACE_UP
        if (expected == Zone.FACE_DOWN && frame.gravityZ < -.78f) return Zone.FACE_DOWN
        val base = baseline ?: frame
        if (orientationError(base, frame) < 14f && gravityDistance(base, frame) < .24f) return Zone.NEUTRAL
        val lateral = signedAngularDifference(frame.roll, base.roll)
        val longitudinal = signedAngularDifference(frame.pitch, base.pitch)
        return when {
            abs(lateral) > 42f && abs(lateral) >= abs(longitudinal) -> if (lateral > 0) Zone.RIGHT else Zone.LEFT
            abs(longitudinal) > 42f -> if (longitudinal > 0) Zone.UP else Zone.DOWN
            frame.gravityX - base.gravityX > .72f -> Zone.LEFT
            frame.gravityX - base.gravityX < -.72f -> Zone.RIGHT
            // Hysteresis: a partial return must not arm the same movement again.
            else -> lastZone
        }
    }

    private fun orientationError(a: SensorFrame, b: SensorFrame): Float =
        hypot(angularDifference(a.pitch, b.pitch), angularDifference(a.roll, b.roll))

    private fun gravityDistance(a: SensorFrame, b: SensorFrame): Float = sqrt(
        (a.gravityX - b.gravityX) * (a.gravityX - b.gravityX) +
            (a.gravityY - b.gravityY) * (a.gravityY - b.gravityY) +
            (a.gravityZ - b.gravityZ) * (a.gravityZ - b.gravityZ),
    )

    private fun success(title: String, detail: String, error: Float? = null, reaction: Long? = null) =
        state.copy(status = RunStatus.SUCCESS, instruction = title, detail = detail, progress = 1f, angularError = error, reactionMs = reaction)

    private fun fail(title: String, detail: String, code: String? = null) =
        state.copy(status = RunStatus.FAILED, instruction = title, detail = detail, failureCode = code ?: title.uppercase().replace(' ', '_'))

    private enum class Zone(val label: String) {
        NEUTRAL("Centro"), FACE_UP("Boca arriba"), FACE_DOWN("Boca abajo"), LEFT("Izquierda"), RIGHT("Derecha"), UP("Arriba"), DOWN("Abajo");

        val isFace: Boolean get() = this == FACE_UP || this == FACE_DOWN
    }

    companion object {
        val originalPlayableIds = setOf(
            "full_circle", "do_it_backwards", "double_trouble", "beyblade", "let_it_rip", "triple_axis", "xyz",
            "steady_hand", "rock_solid", "spirit_level", "perfectionist", "upside_down", "standing_tall",
            "shake_it", "shake_it_off", "are_you_okay", "left_right_left", "nod_yes", "flip_flop", "slow_motion",
            "speedrun", "north_star", "four_winds", "wrong_way_around", "flat_spin", "simon_says", "simon_says_ii",
            "target_practice", "dont_move",
        )
        private val addedPlayableIds = setOf("secret_code", "false_start", "clone", "the_bomb", "traffic_light", "duel", "human_metronome", "rhythm", "tilt_labyrinth", "final_boss", "perfect_run", "vampire", "supernova")
        val playableIds = originalPlayableIds + addedPlayableIds + EnvironmentalChallenges.byId.keys + TouchGameEngine.ids + MicrophoneGameEngine.ids

        fun requiredCapabilities(challengeId: String): Set<Capability> = EnvironmentalChallenges.byId[challengeId]?.let { setOf(it.capability) } ?: when (challengeId) {
            "silent_path" -> setOf(Capability.MICROPHONE, Capability.ACCELEROMETER)
            in MicrophoneGameEngine.ids -> setOf(Capability.MICROPHONE)
            in TouchGameEngine.ids -> emptySet()
            "shake_it", "shake_it_off", "are_you_okay", "the_bomb", "traffic_light", "human_metronome", "rhythm" -> setOf(Capability.ACCELEROMETER)
            "vampire", "supernova" -> setOf(Capability.LIGHT)
            "north_star", "four_winds", "wrong_way_around" -> setOf(Capability.ACCELEROMETER, Capability.ROTATION_VECTOR, Capability.MAGNETOMETER)
            "full_circle", "do_it_backwards", "double_trouble", "beyblade", "let_it_rip", "triple_axis", "xyz", "slow_motion", "speedrun", "flat_spin" -> setOf(Capability.ACCELEROMETER, Capability.ROTATION_VECTOR, Capability.GYROSCOPE)
            else -> setOf(Capability.ACCELEROMETER, Capability.ROTATION_VECTOR)
        }
        val walls = listOf(
            NormalizedRect(-.62f, -.72f, -.48f, .94f),
            NormalizedRect(-.48f, -.72f, .22f, -.58f),
            NormalizedRect(.08f, -.58f, .22f, .56f),
            NormalizedRect(.22f, .42f, .68f, .56f),
        )
    }
}
