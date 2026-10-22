package com.irlachievements.app.domain.challenges

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/** Mono notes with a 40 ms envelope at both ends to avoid abrupt clicks. */
object CollectionTone {
    const val sampleRate = 24000
    fun samples(note: Int): ShortArray {
        require(note in 0..5)
        return ShortArray(8400) { i ->
            val envelope = minOf(1.0, i / 960.0, (8399 - i) / 960.0).coerceAtLeast(0.0)
            (sin(2 * PI * (220.0 * 2.0.pow(note / 4.0)) * i / sampleRate) * envelope * 4000).toInt().toShort()
        }
    }
}
