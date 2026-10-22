package com.irlachievements.app.domain.challenges

/** Input traces for the same parameterized registry used by physical-sensor games. */
internal object InteractiveCompletionTrace {
    fun touch(id: String, seed: Int): ChallengeRunState {
        val engine = TouchGameEngine(id, seed)
        var now = 0L
        var state = engine.tick(now)
        fun tick(time: Long) { now = time; state = engine.tick(now) }
        fun tap(value: Int) { state = engine.tap(value, now) }
        repeat(3) { level ->
            when (id) {
                "color_echo", "fleeting_route" -> {
                    val count = if (id == "color_echo") level + 3 else listOf(4, 6, 8)[level]
                    val start = now
                    val path = (0 until count).map {
                        tick(start + if (id == "color_echo") 600L + it * 1500 else it * 900L)
                        requireNotNull(state.highlightedTile)
                    }
                    tick(start + if (id == "color_echo") 600L + count * 1500 else count * 900L)
                    path.forEach { tap(it) }
                }
                "quiet_signal" -> {
                    var attempts = 0
                    while (state.touchPhase != TouchPhase.SIGNAL && attempts++ < 400) tick(now + 25)
                    check(state.touchPhase == TouchPhase.SIGNAL)
                    tick(now + 100); tap(0)
                }
                "ascending_path" -> for (number in 1..level + 3) tap(state.touchChoices.indexOf(number))
                "odd_symbol" -> {
                    val odd = state.touchChoices.groupingBy { it }.eachCount().entries.single { it.value == 1 }.key
                    tap(state.touchChoices.indexOf(odd))
                }
                "stereo_compass" -> repeat(level + 2) {
                    tap(10); tap(11); tap(state.audioSide)
                }
                "sound_pairs", "tone_ladder" -> {
                    val cards = state.boardLabels.indices.associateWith { index ->
                        tap(index)
                        val note = state.audioNotes.single()
                        tap(CollectionGameEngine.HEARD)
                        note
                    }
                    cards.entries.sortedBy { it.value }.forEach {
                        tap(it.key); tap(CollectionGameEngine.HEARD); tap(CollectionGameEngine.CONFIRM)
                    }
                }
                "sound_detective" -> {
                    tap(CollectionGameEngine.PLAY); tap(CollectionGameEngine.HEARD)
                    val n = level + 3
                    tap((0 until n).single { state.audioNotes[it] != state.audioNotes[n + 2 + it] })
                }
                "inverse_rule" -> repeat(level + 3) {
                    val arrow = listOf("↑", "→", "↓", "←").indexOf(state.instruction.takeLast(1))
                    tap(if (state.instruction.startsWith("OPUESTO")) (arrow + 2) % 4 else arrow)
                }
                "subtle_change" -> {
                    val before = state.boardLabels
                    tick(now + 4200)
                    tap(before.indices.single { before[it] != state.boardLabels[it] })
                }
                "in_place" -> {
                    val reference = state.referenceLabels
                    reference.forEachIndexed { target, shape ->
                        tap(state.boardLabels.take(reference.size).indexOf(shape))
                        tap(reference.size + target)
                    }
                }
                else -> error("Missing interactive trace for $id")
            }
            check(state.status in setOf(RunStatus.RUNNING, RunStatus.SUCCESS)) { "$id: $state" }
        }
        return state
    }

    fun microphone(id: String): ChallengeRunState {
        val engine = MicrophoneGameEngine(id)
        for (t in 0L..2000L step 20) engine.process(t, -60f)
        var state = engine.initialState()
        when (id) {
            "voice_balance" -> for (t in 2020L..15000L step 20) state = engine.process(t, -44f)
            "clap_echo" -> {
                var start = 2000L
                repeat(3) { level ->
                    val pattern = MicrophoneGameEngine.rhythm(level)
                    val inputStart = start + pattern.last() + 2000
                    pattern.forEach { beat ->
                        state = engine.process(inputStart + beat, -30f)
                        engine.process(inputStart + beat + 40, -60f)
                    }
                    start = inputStart + pattern.last()
                }
            }
            "acoustic_key" -> {
                var now = 2100L
                repeat(3) { level ->
                    MicrophoneGameEngine.code(level).forEach { long ->
                        engine.process(now, -30f)
                        now += if (long) 900 else 300
                        engine.process(now, -60f); now += 120
                        state = engine.process(now, -60f); now += 200
                    }
                }
            }
            "silent_path" -> {
                var now = 2020L
                repeat(3) { level -> repeat(level + 1) { direction ->
                    engine.process(now, -60f, 0f, 0f, true); now += 20
                    repeat(35) {
                        state = engine.process(now, -60f,
                            if (direction == 0) 4f else if (direction == 1) -4f else 0f,
                            if (direction == 2) -4f else 0f, true)
                        now += 20
                    }
                } }
            }
            else -> error("Missing microphone trace for $id")
        }
        return state
    }
}
