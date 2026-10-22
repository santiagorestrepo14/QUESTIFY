package com.irlachievements.app.domain.challenges

import kotlin.random.Random

/** Three self-contained levels. Audio is untimed to avoid penalising Bluetooth latency. */
class PuzzleGameEngine(private val id: String, seed: Int) {
    companion object { val ids = setOf("ascending_path", "odd_symbol", "stereo_compass") }
    private val random = Random(seed)
    private var level = 0
    private var answers = 0
    private var expected = 1
    private var oddIndex = 0
    private var started = 0L
    private var lastTime = 0L
    private var audioToken = 0
    private var soundSequence = emptyList<Int>()
    private var state = ChallengeRunState(challengeId = id, status = RunStatus.RUNNING, totalSteps = 3)
    init { require(id in ids); prepareLevel() }

    private fun prepareLevel() {
        answers = 0
        expected = 1
        started = lastTime
        when (id) {
            "ascending_path" -> state = state.copy(touchPhase = TouchPhase.INPUT,
                touchChoices = (1..(level + 3)).shuffled(random), instruction = "Toca 1 → ${level + 3}",
                detail = "Nivel ${level + 1}/3 · En orden ascendente · 25 segundos")
            "odd_symbol" -> {
                val size = listOf(4, 6, 9)[level]
                oddIndex = random.nextInt(size)
                val symbol = random.nextInt(4)
                state = state.copy(touchPhase = TouchPhase.INPUT,
                    touchChoices = List(size) { if (it == oddIndex) (symbol + 1 + random.nextInt(3)) % 4 else symbol },
                    instruction = "Encuentra el símbolo diferente", detail = "Nivel ${level + 1}/3 · $size tarjetas · 20 segundos")
            }
            else -> {
                soundSequence = (when (level) {
                    0 -> listOf(0, 1)
                    1 -> listOf(0, 1, 2)
                    else -> listOf(0, 1, 2, random.nextInt(3))
                }).shuffled(random)
                nextSound()
            }
        }
    }

    private fun nextSound() {
        state = state.copy(touchPhase = TouchPhase.AUDIO_READY, audioSide = soundSequence[answers],
            instruction = if (level == 0) "¿Izquierda o derecha?" else "¿Izquierda, derecha o frente / centro?",
            detail = "Nivel ${level + 1}/3 · $answers / ${level + 2} sonidos acertados · Sin límite de tiempo")
    }

    fun tick(elapsed: Long): ChallengeRunState {
        if (state.status != RunStatus.RUNNING) return state
        lastTime = elapsed.coerceAtLeast(lastTime)
        val limit = if (id == "ascending_path") 25_000 else 20_000
        if (id != "stereo_compass" && lastTime - started > limit) {
            fail("Se acabó el tiempo del nivel", "Puedes volver a intentarlo sin perder XP.", "TOUCH_TIMEOUT")
        }
        return state
    }

    fun tap(tile: Int, elapsed: Long): ChallengeRunState {
        tick(elapsed)
        if (state.status != RunStatus.RUNNING) return state
        if (id == "stereo_compass") {
            when (tile) {
                10 -> if (state.touchPhase in setOf(TouchPhase.AUDIO_READY, TouchPhase.INPUT)) {
                    audioToken++
                    state = state.copy(touchPhase = TouchPhase.AUDIO_PLAYING, audioToken = audioToken)
                }
                11 -> if (state.touchPhase == TouchPhase.AUDIO_PLAYING) state = state.copy(touchPhase = TouchPhase.INPUT)
                12 -> state = state.copy(touchPhase = TouchPhase.AUDIO_READY)
                0, 1, 2 -> if (state.touchPhase == TouchPhase.INPUT) {
                    if (tile != state.audioSide) return fail("Era otra dirección", "Comprueba la posición L/R de los auriculares y repite la prueba estéreo. Frente / centro suena igual en ambos oídos.", "AUDIO_SIDE")
                    answers++
                    if (answers == level + 2) advance() else nextSound()
                }
            }
            return state
        }
        if (tile !in state.touchChoices.indices || state.touchChoices[tile] < 0) return state
        if (id == "ascending_path") {
            if (state.touchChoices[tile] != expected) return fail("Ese número todavía no", "El siguiente era $expected. Mira el orden antes de tocar.", "NUMBER_ORDER")
            expected++
            state = state.copy(touchChoices = state.touchChoices.mapIndexed { index, value -> if (index == tile) -1 else value })
            if (expected > level + 3) advance()
            else state = state.copy(instruction = "Ahora toca $expected")
        } else {
            if (tile != oddIndex) return fail("Ese símbolo se repite", "Busca la única tarjeta con una forma distinta, no un color distinto.", "ODD_SYMBOL")
            advance()
        }
        return state
    }

    private fun advance() {
        level++
        state = state.copy(step = level, progress = level / 3f)
        if (level == 3) {
            state = state.copy(status = RunStatus.SUCCESS, touchPhase = TouchPhase.NONE,
                instruction = "¡Tres niveles superados!", detail = "Reto completado. Puedes repetirlo; la XP del logro se concede una sola vez.")
        } else prepareLevel()
    }

    private fun fail(title: String, detail: String, code: String): ChallengeRunState {
        state = state.copy(status = RunStatus.FAILED, touchPhase = TouchPhase.NONE,
            instruction = title, detail = detail, failureCode = code)
        return state
    }
}
