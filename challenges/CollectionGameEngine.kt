package com.irlachievements.app.domain.challenges

import kotlin.random.Random

/** Seven three-level games. Audio acknowledgement is required before answers count. */
class CollectionGameEngine(private val id: String, seed: Int) {
    companion object {
        val audioIds = setOf("sound_pairs", "tone_ladder", "sound_detective")
        val ids = audioIds + setOf("fleeting_route", "inverse_rule", "subtle_change", "in_place")
        const val PLAY = 100
        const val HEARD = 101
        const val AUDIO_ERROR = 102
        const val CONFIRM = 103
    }
    private val random = Random(seed)
    private val shapes = listOf("●", "◆", "▲", "■", "★", "✚", "⬟")
    private var level = 0
    private var time = 0L
    private var since = 0L
    private var input = 0
    private var target = 0
    private var selected = -1
    private var first = -1
    private var heard = false
        set(value) {
            field = value
            state = state.copy(audioAnswerReady = value)
        }
    private var values = emptyList<Int>()
    private var sequence = emptyList<Int>()
    private val solved = mutableSetOf<Int>()
    private var state = ChallengeRunState(challengeId = id, status = RunStatus.RUNNING, totalSteps = 3)
    init { require(id in ids); prepare() }

    private fun prepare() {
        since = time; input = 0; selected = -1; first = -1; heard = false; solved.clear()
        state = state.copy(touchPhase = TouchPhase.INPUT, highlightedTile = null,
            boardLabels = emptyList(), referenceLabels = emptyList(), audioNotes = emptyList(),
            detail = "Nivel ${level + 1}/3 · Sin penalización de XP")
        when (id) {
            "sound_pairs" -> {
                val n = listOf(3, 4, 6)[level]
                values = ((0 until n).toList() + (0 until n).toList()).shuffled(random)
                state = state.copy(instruction = "Escucha una tarjeta y confirma para formar una pareja",
                    boardLabels = values.indices.map { "${it + 1} · ♪" })
            }
            "tone_ladder" -> {
                values = (0 until level + 3).shuffled(random)
                state = state.copy(instruction = "Escucha las notas y confírmalas de grave a aguda",
                    boardLabels = values.indices.map { "Nota ${it + 1}" })
            }
            "sound_detective" -> {
                sequence = List(level + 3) { random.nextInt(6) }; target = random.nextInt(sequence.size)
                val changed = sequence.mapIndexed { i, v -> if (i == target) (v + 2) % 6 else v }
                state = state.copy(instruction = "Escucha A y B: ¿qué posición cambió?",
                    audioNotes = sequence + listOf(-1, -1) + changed,
                    boardLabels = sequence.indices.map { "Posición ${it + 1}" })
            }
            "fleeting_route" -> {
                // A continuous path on a 3x3 board, no repeated cell.
                val path = mutableListOf(random.nextInt(9))
                val length = listOf(4, 6, 8)[level]
                fun extend(): Boolean {
                    if (path.size == length) return true
                    val v = path.last()
                    val neighbors = (0..8).filter { it !in path &&
                        kotlin.math.abs(it / 3 - v / 3) + kotlin.math.abs(it % 3 - v % 3) == 1 }.shuffled(random)
                    for (n in neighbors) { path += n; if (extend()) return true; path.removeAt(path.lastIndex) }
                    return false
                }
                extend(); sequence = path.toList()
                state = state.copy(touchPhase = TouchPhase.SHOW, instruction = "Memoriza la ruta en orden",
                    boardLabels = List(9) { "·" })
            }
            "inverse_rule" -> { sequence = List(level + 3) { random.nextInt(4) }; direction() }
            "subtle_change" -> {
                values = List(listOf(4, 6, 9)[level]) { random.nextInt(4) }; target = random.nextInt(values.size)
                state = state.copy(touchPhase = TouchPhase.SHOW, instruction = "Observa las formas",
                    boardLabels = values.map { shapes[it] })
            }
            "in_place" -> {
                values = (0 until listOf(3, 5, 7)[level]).shuffled(random)
                sequence = values.shuffled(random)
                state = state.copy(instruction = "Reconstruye la figura: elige pieza y toca su hueco",
                    referenceLabels = sequence.map { shapes[it] },
                    boardLabels = values.map { shapes[it] } + List(values.size) { "□" })
            }
        }
    }
    private fun direction() {
        val inverse = level == 1 || (level == 2 && input % 2 == 1)
        val arrow = sequence[input]
        target = if (inverse) (arrow + 2) % 4 else arrow
        state = state.copy(instruction = "${if (inverse) "OPUESTO" else "IGUAL"} · ${listOf("↑", "→", "↓", "←")[arrow]}",
            boardLabels = listOf("↑", "→", "↓", "←"), detail = "Nivel ${level + 1}/3 · ${input + 1}/${sequence.size}")
    }
    fun tick(elapsed: Long): ChallengeRunState {
        if (state.status != RunStatus.RUNNING) return state
        time = maxOf(time, elapsed)
        if (id == "fleeting_route" && state.touchPhase == TouchPhase.SHOW) {
            val index = ((time - since) / 900).toInt()
            state = if (index < sequence.size) state.copy(highlightedTile = sequence[index])
            else state.copy(touchPhase = TouchPhase.INPUT, highlightedTile = null, instruction = "Repite la ruta")
        }
        if (id == "subtle_change") {
            if (state.touchPhase == TouchPhase.SHOW && time - since >= 3500)
                state = state.copy(touchPhase = TouchPhase.BREAK, boardLabels = List(values.size) { "·" }, instruction = "Un instante…")
            if (state.touchPhase == TouchPhase.BREAK && time - since >= 4200)
                state = state.copy(touchPhase = TouchPhase.INPUT, instruction = "¿Qué forma cambió?",
                    boardLabels = values.mapIndexed { i, v -> shapes[if (i == target) (v + 1) % 4 else v] })
        }
        return state
    }
    fun tap(action: Int, elapsed: Long): ChallengeRunState {
        val phase = state.touchPhase
        tick(elapsed)
        if (state.status != RunStatus.RUNNING) return state
        if (id in audioIds) {
            if (action == AUDIO_ERROR) {
                heard = false; state = state.copy(touchPhase = TouchPhase.INPUT); return state
            }
            if (action == HEARD && phase == TouchPhase.AUDIO_PLAYING) {
                heard = true; state = state.copy(touchPhase = TouchPhase.INPUT); return state
            }
            if (phase == TouchPhase.AUDIO_PLAYING) return state
            if (action == PLAY && state.audioNotes.isNotEmpty()) { play(); return state }
            if (id != "sound_detective" && action in values.indices && action !in solved) {
                selected = action; heard = false
                state = state.copy(highlightedTile = action, audioNotes = listOf(values[action]))
                play(); return state
            }
            if (!heard) return state
            if (id == "sound_detective" && action in sequence.indices) {
                if (action == target) advance() else fail()
            } else if (action == CONFIRM && selected >= 0 && selected !in solved) {
                if (id == "tone_ladder") {
                    if (values[selected] != input) return fail()
                    solved += selected; input++; mark()
                    if (input == values.size) advance()
                } else if (first == -1) {
                    first = selected; heard = false
                    state = state.copy(instruction = "Busca la pareja de la tarjeta ${first + 1}", highlightedTile = null)
                } else if (first != selected) {
                    val matched = values[first] == values[selected]
                    if (matched) {
                        solved += first; solved += selected; mark()
                    }
                    first = -1; heard = false
                    state = state.copy(instruction = if (matched) "✓ Pareja encontrada · sigue así"
                        else "Sonidos distintos · prueba otra pareja", highlightedTile = null,
                        detail = "Nivel ${level + 1}/3 · ${solved.size / 2}/${values.size / 2} parejas")
                    if (solved.size == values.size) advance()
                }
            }
            return state
        }
        if (phase != TouchPhase.INPUT || state.touchPhase != TouchPhase.INPUT || action !in state.boardLabels.indices) return state
        when (id) {
            "fleeting_route" -> if (action != sequence[input]) fail() else {
                input++; state = state.copy(highlightedTile = action)
                if (input == sequence.size) advance()
            }
            "inverse_rule" -> if (action != target) fail() else { input++; if (input == sequence.size) advance() else direction() }
            "subtle_change" -> if (action == target) advance() else fail()
            "in_place" -> {
                val n = values.size
                if (action < n && action !in solved) { selected = action; state = state.copy(highlightedTile = action) }
                else if (action >= n && selected >= 0 && selected !in solved) {
                    if (solved.any { values[it] == sequence[action - n] }) return state
                    if (values[selected] != sequence[action - n]) return fail()
                    solved += selected
                    state = state.copy(boardLabels = state.boardLabels.mapIndexed { i, s ->
                        when (i) { selected -> "✓"; action -> shapes[values[selected]]; else -> s }
                    }, highlightedTile = null)
                    selected = -1
                    if (solved.size == n) advance()
                }
            }
        }
        return state
    }
    private fun play() { heard = false; state = state.copy(touchPhase = TouchPhase.AUDIO_PLAYING, audioToken = state.audioToken + 1) }
    private fun mark() { heard = false; state = state.copy(highlightedTile = null,
        boardLabels = state.boardLabels.mapIndexed { i, s -> if (i in solved) "✓" else s }) }
    private fun advance() {
        level++; state = state.copy(step = level, progress = level / 3f)
        if (level < 3) prepare() else state = state.copy(status = RunStatus.SUCCESS, touchPhase = TouchPhase.NONE,
            instruction = "¡Tres niveles superados!", detail = "La XP se concede una sola vez.", audioNotes = emptyList(), highlightedTile = null)
    }
    private fun fail(): ChallengeRunState {
        state = state.copy(status = RunStatus.FAILED, touchPhase = TouchPhase.NONE,
            instruction = "No era esa opción", detail = "Puedes intentarlo otra vez sin perder XP.", failureCode = "COLLECTION_ANSWER")
        return state
    }
}
