package com.irlachievements.app.domain.challenges

import kotlin.random.Random

enum class TouchPhase { NONE, SHOW, INPUT, WAIT, SIGNAL, BREAK, AUDIO_READY, AUDIO_PLAYING }

/** Monotonic elapsed time and touch events only. No Android APIs or sensor frames required. */
class TouchGameEngine(private val id: String, seed: Int) {
    companion object { val ids = setOf("color_echo", "quiet_signal") + PuzzleGameEngine.ids + CollectionGameEngine.ids }
    private val collection = if (id in CollectionGameEngine.ids) CollectionGameEngine(id, seed) else null
    private val puzzle = if (id in PuzzleGameEngine.ids) PuzzleGameEngine(id, seed) else null
    private val random = Random(seed)
    private val sequence = List(5) { random.nextInt(4) }
    private var round = 0
    private var input = 0
    private var roundStarted = 0L
    private var lastTime = 0L
    private var cueAt = random.nextLong(2_000, 4_001)
    private var signalStarted = 0L
    private var breakUntil = 0L
    private val reactions = mutableListOf<Long>()
    private var state = ChallengeRunState(challengeId = id, status = RunStatus.RUNNING, totalSteps = 3,
        touchPhase = if (id == "color_echo") TouchPhase.SHOW else TouchPhase.WAIT,
        instruction = if (id == "color_echo") "Observa la secuencia" else "Espera la señal",
        detail = if (id == "color_echo") "Ronda 1/3 · Después repite los símbolos" else "Toca solo cuando aparezca AHORA. No hace falta tocar fuerte.")

    init { require(id in ids) }

    fun tick(elapsed: Long): ChallengeRunState {
        collection?.let { return it.tick(elapsed) }
        puzzle?.let { return it.tick(elapsed) }
        if (state.status != RunStatus.RUNNING) return state
        val now = elapsed.coerceAtLeast(lastTime)
        lastTime = now
        if (id == "color_echo") {
            val length = 3 + round
            val inputAt = roundStarted + 600 + length * 1_500
            if (now < inputAt) {
                val offset = now - roundStarted - 600
                val index = (offset / 1_500).toInt()
                val active = if (offset >= 0 && offset % 1_500 < 900) sequence[index] else null
                state = state.copy(touchPhase = TouchPhase.SHOW, highlightedTile = active,
                    instruction = "Observa la secuencia", detail = "Ronda ${round + 1}/3 · $length símbolos")
            } else if (now - inputAt > 20_000) {
                fail("Tiempo para repetir agotado", "Tienes 20 segundos para repetir cada secuencia. Puedes descansar y volver a intentarlo.", "TOUCH_TIMEOUT")
            } else {
                state = state.copy(touchPhase = TouchPhase.INPUT, highlightedTile = null,
                    instruction = "Tu turno", detail = "Ronda ${round + 1}/3 · $input / $length símbolos repetidos")
            }
        } else {
            if (state.touchPhase == TouchPhase.BREAK && now >= breakUntil) {
                cueAt = now + random.nextLong(2_000, 4_001)
                state = state.copy(touchPhase = TouchPhase.WAIT, instruction = "Espera la señal", detail = "Ronda ${round + 1}/3 · No te adelantes")
            }
            if (state.touchPhase == TouchPhase.WAIT && now >= cueAt) {
                signalStarted = now
                state = state.copy(touchPhase = TouchPhase.SIGNAL, instruction = "AHORA · toca", detail = "Nivel ${round + 1}/3 · Tienes ${(3_000 - round * 500) / 1_000f} segundos")
            }
            if (state.touchPhase == TouchPhase.SIGNAL && now - signalStarted > 3_000 - round * 500) {
                fail("La señal terminó", "El plazo de este nivel era ${(3_000 - round * 500) / 1_000f} segundos. Mantén la vista en AHORA.", "TOUCH_TIMEOUT")
            }
        }
        return state
    }

    fun tap(tile: Int, elapsed: Long): ChallengeRunState {
        collection?.let { return it.tap(tile, elapsed) }
        puzzle?.let { return it.tap(tile, elapsed) }
        val phaseBefore = state.touchPhase
        tick(elapsed)
        if (state.status != RunStatus.RUNNING || tile !in 0..3) return state
        if (id == "color_echo") {
            if (phaseBefore != TouchPhase.INPUT || state.touchPhase != TouchPhase.INPUT) return state
            if (tile != sequence[input]) return fail("Símbolo distinto", "Acertaste $input de ${3 + round} símbolos en la ronda ${round + 1}. Observa el orden, no solo el color.", "TOUCH_SEQUENCE")
            input++
            if (input == 3 + round) {
                round++
                if (round == 3) return success("¡Eco completo!", "Superaste secuencias de 3, 4 y 5 símbolos.")
                input = 0
                roundStarted = lastTime
                state = state.copy(step = round, progress = round / 3f)
            }
            return tick(lastTime)
        }
        if (tile != 0 || phaseBefore == TouchPhase.BREAK) return state
        if (phaseBefore == TouchPhase.WAIT) return fail("Te adelantaste", "Tocaste antes de AHORA. Espera el cambio de palabra y de símbolo.", "EARLY")
        if (state.touchPhase == TouchPhase.SIGNAL) {
            reactions += lastTime - signalStarted
            round++
            if (round == 3) {
                state = state.copy(reactionMs = reactions.average().toLong())
                return success("¡Tres señales acertadas!", "Tiempo medio aproximado: ${state.reactionMs} ms. Sin salidas falsas.")
            }
            breakUntil = lastTime + 1_200
            state = state.copy(step = round, progress = round / 3f, touchPhase = TouchPhase.BREAK,
                instruction = "Bien · descansa un instante", detail = "$round / 3 señales acertadas")
        }
        return state
    }

    private fun success(title: String, detail: String): ChallengeRunState {
        state = state.copy(status = RunStatus.SUCCESS, instruction = title, detail = detail,
            progress = 1f, step = 3, touchPhase = TouchPhase.NONE, highlightedTile = null)
        return state
    }

    private fun fail(title: String, detail: String, code: String): ChallengeRunState {
        state = state.copy(status = RunStatus.FAILED, instruction = title, detail = detail,
            failureCode = code, touchPhase = TouchPhase.NONE, highlightedTile = null)
        return state
    }
}
