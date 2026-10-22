package com.irlachievements.app.domain.achievements

import java.security.SecureRandom

fun interface RandomSource {
    fun nextInt(bound: Int): Int
}

class SecureRandomSource : RandomSource {
    private val random = SecureRandom()
    override fun nextInt(bound: Int): Int = random.nextInt(bound)
}
