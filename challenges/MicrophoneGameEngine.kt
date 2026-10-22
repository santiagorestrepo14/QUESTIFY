package com.irlachievements.app.domain.challenges

import kotlin.math.*

/** Receives only aggregate levels, never speech or retained audio. Relative dBFS, not calibrated SPL. */
class MicrophoneGameEngine(private val id: String) {
    companion object {
        val ids = setOf("voice_balance", "clap_echo", "acoustic_key", "silent_path")
        fun levelDb(samples: ShortArray, count: Int): Float {
            require(count in 1..samples.size)
            val energy = (0 until count).sumOf { val v = samples[it] / 32768.0; v * v } / count
            return (10 * log10(energy.coerceAtLeast(1e-9))).toFloat()
        }
        fun rhythm(level: Int): List<Long> = when (level) {
            0 -> listOf(0, 600, 1200)
            1 -> listOf(0, 600, 1500, 2100)
            else -> listOf(0, 600, 1500, 2100, 3300)
        }
        fun code(level: Int): List<Boolean> = when (level) {
            0 -> listOf(false, true, false)
            1 -> listOf(true, false, false, true)
            else -> listOf(false, true, false, true, true)
        }
    }
    private var state = ChallengeRunState(challengeId = id, status = RunStatus.RUNNING, totalSteps = 3,
        instruction = "Calibrando el ambiente", detail = "Guarda silencio durante 2 segundos. No necesitas gritar.",
        microphoneActive = true)
    private var last = -1L
    private var baselineTotal = 0f
    private var baselineCount = 0
    private var baseline = -60f
    private var calibrated = false
    private var level = 0
    private var levelStarted = 0L
    private var hold = 0L
    private var pulseStart: Long? = null
    private var quietSince: Long? = null
    private var lastPulse = -1000L
    private var firstClap: Long? = null
    private var matched = 0
    private var neutralRequired = false
    init { require(id in ids) }
    fun initialState() = state

    fun process(now: Long, db: Float, tiltX: Float = 0f, tiltY: Float = 0f, motionFresh: Boolean = false): ChallengeRunState {
        if (state.status != RunStatus.RUNNING || now <= last || !db.isFinite()) return state
        val delta = if (last < 0) 0 else (now - last).coerceAtMost(100)
        last = now
        if (!calibrated) {
            baselineTotal += db
            baselineCount++
            if (now < 2000) return state
            baseline = baselineTotal / baselineCount
            if (baseline > -22f) return fail("Ambiente demasiado intenso", "Busca un entorno más tranquilo y vuelve a calibrar.", "MIC_ENVIRONMENT")
            baseline = baseline.coerceIn(-75f, -35f)
            calibrated = true
            levelStarted = now
        }
        val relative = db - baseline
        state = state.copy(microphoneLevel = ((relative + 2) / 32).coerceIn(0f, 1f), microphoneBeat = -1)
        if (now - levelStarted > 45_000) return fail("Tiempo del nivel agotado", "Descansa y vuelve a intentarlo. No aumentes la fuerza de la voz ni de las palmadas.", "MIC_TIMEOUT")
        when (id) {
            "voice_balance" -> {
                val tolerance = listOf(8f, 6f, 4f)[level]
                val within = abs(relative - 16f) <= tolerance / 2
                hold = if (within) hold + delta else 0
                state = state.copy(instruction = when { within -> "Mantén ese sonido suave"; relative < 16 - tolerance / 2 -> "Acerca un sonido suave"; else -> "Baja la intensidad" },
                    detail = "Nivel ${level + 1}/3 · Mantén la franja ${3 + level} s · ${hold / 1000f} s",
                    microphoneTargetLow = (18 - tolerance / 2) / 32, microphoneTargetHigh = (18 + tolerance / 2) / 32)
                if (hold >= (3 + level) * 1000) advance(now)
            }
            "silent_path" -> {
                val directions = listOf("IZQUIERDA", "DERECHA", "ARRIBA").take(level + 1)
                val quiet = relative < 8
                val centred = abs(tiltX) < 1.2f && abs(tiltY) < 1.2f
                if (centred) neutralRequired = false
                val target = when (matched) { 0 -> tiltX > 2.5f; 1 -> tiltX < -2.5f; else -> tiltY < -2.5f }
                hold = if (motionFresh && quiet && target && !neutralRequired) hold + delta else 0
                state = state.copy(instruction = if (!motionFresh) "Esperando movimiento" else if (!quiet) "Haz una pausa: se detectó ruido"
                    else if (neutralRequired) "Vuelve al centro" else "Inclina: ${directions[matched]}",
                    detail = "Nivel ${level + 1}/3 · $matched / ${directions.size} gestos · Mantén cada postura 0,7 s")
                if (hold >= 700) {
                    matched++; hold = 0; neutralRequired = true
                    if (matched == directions.size) advance(now)
                }
            }
            "clap_echo" -> {
                val pattern = rhythm(level)
                val elapsed = now - levelStarted
                val listenAt = pattern.last() + 1800
                if (elapsed < listenAt) {
                    val beat = pattern.indices.firstOrNull { elapsed - (600 + pattern[it]) in 0..160 }
                    state = state.copy(instruction = "Escucha y observa el ritmo", detail = "Nivel ${level + 1}/3 · Después repite con palmadas suaves",
                        microphoneBeat = beat ?: -1)
                    return state
                }
                state = state.copy(instruction = "Tu turno: palmadas suaves", detail = "$matched / ${pattern.size} golpes · Patrón en ms: ${pattern.zipWithNext { a, b -> b - a }.joinToString(" · ")}")
                if (relative > 12 && pulseStart == null && now - lastPulse > 180) {
                    pulseStart = now
                    lastPulse = now
                    if (firstClap == null) firstClap = now
                    val expected = pattern[matched]
                    if (abs((now - firstClap!!) - expected) > 250) return fail("El ritmo cambió", "Repite los intervalos de la demostración, sin dar palmadas más fuertes.", "MIC_RHYTHM")
                    matched++
                    if (matched == pattern.size) advance(now)
                }
                if (relative < 6) pulseStart = null
            }
            "acoustic_key" -> {
                val pattern = code(level)
                state = state.copy(instruction = "Código: " + pattern.joinToString(" ") { if (it) "LARGO" else "corto" },
                    detail = "Nivel ${level + 1}/3 · $matched / ${pattern.size} · Corto: 0,18–0,55 s; largo: 0,7–1,4 s. Deja silencio entre sonidos.")
                if (relative > 10) {
                    quietSince = null
                    if (pulseStart == null) pulseStart = now
                    if (now - pulseStart!! > 1600) return fail("Sonido demasiado largo", "Usa un sonido cómodo y una pausa clara.", "MIC_DURATION")
                } else if (relative < 6 && pulseStart != null) {
                    if (quietSince == null) quietSince = now
                    if (now - quietSince!! >= 100) {
                        val duration = quietSince!! - pulseStart!!
                        val valid = if (pattern[matched]) duration in 700..1400 else duration in 180..550
                        pulseStart = null; quietSince = null
                        if (!valid) return fail("Duración distinta", "El sonido duró $duration ms. Revisa si correspondía corto o largo.", "MIC_DURATION")
                        matched++
                        if (matched == pattern.size) advance(now)
                    }
                }
            }
        }
        return state
    }
    private fun advance(now: Long) {
        level++; levelStarted = now; matched = 0; hold = 0; pulseStart = null; firstClap = null; quietSince = null
        neutralRequired = id == "silent_path"
        state = state.copy(step = level, progress = level / 3f, microphoneBeat = -1)
        if (level == 3) state = state.copy(status = RunStatus.SUCCESS, instruction = "¡Tres niveles superados!",
            detail = "Reto completado. Micrófono detenido; no se ha guardado audio.", microphoneActive = false)
    }
    private fun fail(title: String, detail: String, code: String): ChallengeRunState {
        state = state.copy(status = RunStatus.FAILED, instruction = title, detail = detail, failureCode = code, microphoneActive = false)
        return state
    }
}
