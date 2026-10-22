package com.irlachievements.app.domain.challenges

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.min

object StereoCue {
    const val sampleRate = 24_000
    const val durationMs = 360
    /** 0 left, 1 right, 2 equal-power centre (not a claim of spatial front positioning). */
    fun samples(side: Int): ShortArray {
        require(side in 0..2)
        val frames = sampleRate * durationMs / 1_000
        val fade = sampleRate * 40 / 1_000
        return ShortArray(frames * 2).also { pcm ->
            repeat(frames) { frame ->
                val envelope = min(1.0, min(frame.toDouble() / fade, (frames - 1 - frame).toDouble() / fade))
                val value = (sin(2 * PI * 440 * frame / sampleRate) * 32767 * .18 * envelope *
                    if (side == 2) .707 else 1.0).toInt().toShort()
                pcm[frame * 2] = if (side == 1) 0 else value
                pcm[frame * 2 + 1] = if (side == 0) 0 else value
            }
        }
    }
}
