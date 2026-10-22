package com.irlachievements.app.domain.challenges

import com.irlachievements.app.domain.model.Capability

enum class ChallengeKind(val label: String, val guide: String) {
    ALL("Todos", "Elige una forma de jugar."),
    SENSOR_FREE("Sin sensores", "Estos retos no necesitan sensores físicos."),
    MOVEMENT("Movimiento", "Sujeta el teléfono con firmeza. Haz el gesto indicado con espacio libre; nunca lo lances ni lo sueltes."),
    PRECISION("Precisión", "Parte de una postura cómoda. Usa movimientos pequeños y controlados; no fuerces las muñecas."),
    MEMORY("Memoria", "Observa el orden de las señales y repítelo con los movimientos indicados. Vuelve al centro entre gestos."),
    RHYTHM("Reacción y ritmo", "Lee la señal antes de moverte. Prioriza el momento del gesto, no su fuerza."),
    LOGIC("Observación y lógica", "Observa las tarjetas antes de tocar. Los números y símbolos importan más que el color."),
    AUDIO("Sonido", "Escucha a un volumen cómodo y responde con toques. Solo Brújula sonora necesita auriculares estéreo."),
    MICROPHONE("Micrófono", "Se analiza el sonido ambiente solo durante la partida, sin guardar ni enviar grabaciones. No necesitas gritar."),
    ENVIRONMENT("Entorno", "Busca una condición ambiental segura. No calientes, enfríes ni mojes el teléfono y nunca mires al sol."),
    META("Colección", "Se obtiene completando otros retos; no se inicia como un minijuego.");
}

object ChallengeDiscovery {
    fun matches(filter: ChallengeKind, id: String): Boolean = when (filter) {
        ChallengeKind.ALL -> true
        ChallengeKind.SENSOR_FREE -> id in ChallengeGameEngine.playableIds && ChallengeGameEngine.requiredCapabilities(id).isEmpty()
        else -> kind(id) == filter
    }
    private val memory = setOf("secret_code", "simon_says", "simon_says_ii", "clone")
    private val precision = setOf("steady_hand", "rock_solid", "spirit_level", "perfectionist",
        "upside_down", "standing_tall", "target_practice", "dont_move", "tilt_labyrinth",
        "north_star", "four_winds", "wrong_way_around", "flat_spin", "slow_motion")
    private val rhythm = setOf("false_start", "the_bomb", "traffic_light", "duel",
        "human_metronome", "rhythm", "final_boss", "perfect_run", "speedrun")

    fun kind(id: String): ChallengeKind = when {
        id !in ChallengeGameEngine.playableIds -> ChallengeKind.META
        id in MicrophoneGameEngine.ids -> ChallengeKind.MICROPHONE
        id in CollectionGameEngine.audioIds -> ChallengeKind.AUDIO
        id == "fleeting_route" -> ChallengeKind.MEMORY
        id in setOf("inverse_rule", "subtle_change", "in_place") -> ChallengeKind.LOGIC
        id == "color_echo" -> ChallengeKind.MEMORY
        id == "quiet_signal" -> ChallengeKind.RHYTHM
        id in setOf("ascending_path", "odd_symbol") -> ChallengeKind.LOGIC
        id == "stereo_compass" -> ChallengeKind.AUDIO
        id in EnvironmentalChallenges.byId || id in setOf("vampire", "supernova") -> ChallengeKind.ENVIRONMENT
        id in memory -> ChallengeKind.MEMORY
        id in precision -> ChallengeKind.PRECISION
        id in rhythm -> ChallengeKind.RHYTHM
        else -> ChallengeKind.MOVEMENT
    }

    fun missing(id: String, unavailable: Set<Capability>): Set<Capability> =
        if (id in ChallengeGameEngine.playableIds) ChallengeGameEngine.requiredCapabilities(id).intersect(unavailable) else emptySet()

    fun compatible(id: String, unavailable: Set<Capability>) =
        id in ChallengeGameEngine.playableIds && missing(id, unavailable).isEmpty()

    fun sensorName(capability: Capability): String = when (capability) {
        Capability.MICROPHONE -> "micrófono"
        Capability.ACCELEROMETER -> "acelerómetro"
        Capability.GYROSCOPE -> "giroscopio"
        Capability.ROTATION_VECTOR -> "orientación"
        Capability.MAGNETOMETER -> "brújula"
        Capability.LIGHT -> "luz"
        Capability.PRESSURE -> "presión"
        Capability.HUMIDITY -> "humedad"
        Capability.AMBIENT_TEMPERATURE -> "temperatura ambiente"
        Capability.GRAVITY -> "gravedad"
        else -> capability.name.lowercase().replace('_', ' ')
    }

    fun retryTip(run: ChallengeRunState): String = when {
        run.challengeId in MicrophoneGameEngine.ids -> "Busca un entorno tranquilo y vuelve a calibrar. No aumentes la fuerza de la voz o las palmadas."
        run.challengeId == "color_echo" -> "Mira los símbolos en orden. Espera Tu turno y repítelos tocando las cuatro tarjetas."
        run.challengeId == "quiet_signal" -> "Espera la palabra AHORA antes de tocar. Cada espera tiene una duración distinta."
        run.challengeId in setOf("ascending_path", "odd_symbol") -> "Mira todas las tarjetas antes de tocar. Puedes repetir sin perder XP."
        run.challengeId == "stereo_compass" -> "Repite la prueba izquierda/derecha con auriculares. Comprueba si tienes audio mono activado."
        run.failureCode == "EARLY" -> "Espera la señal de salida con el teléfono quieto; después realiza el gesto."
        run.challengeId == "tilt_labyrinth" -> "Reduce la inclinación cerca de las paredes y avanza con pequeños ajustes."
        run.challengeId == "slow_motion" -> "Reparte el giro de forma continua entre 15 y 25 segundos."
        run.challengeId == "are_you_okay" -> "Mantén un ritmo cómodo. Si notas fatiga, descansa; no aumentes la fuerza."
        else -> kind(run.challengeId.orEmpty()).guide
    }
}
