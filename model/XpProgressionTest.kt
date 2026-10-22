package com.irlachievements.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XpProgressionTest {
    @Test
    fun `levels use a stable quadratic curve`() {
        assertEquals(1, XpProgression.from(0).level)
        assertEquals(1, XpProgression.from(39).level)
        assertEquals(2, XpProgression.from(40).level)
        assertEquals(3, XpProgression.from(160).level)
        assertEquals(6, XpProgression.from(1_000).level)
    }

    @Test
    fun `progress and rank describe the next useful milestone`() {
        val level = XpProgression.from(100)

        assertEquals(2, level.level)
        assertEquals("Novato", level.title)
        assertEquals(40, level.currentThreshold)
        assertEquals(160, level.nextThreshold)
        assertEquals(60, level.xpToNextLevel)
        assertTrue(level.progress in 0f..1f)
        assertEquals("Explorador", XpProgression.from(XpProgression.thresholdFor(4)).title)
        assertEquals("Marco base", level.reward)
        assertEquals("Explorador", level.nextRankTitle)
        assertEquals(4, level.nextRankLevel)
        assertEquals(XpProgression.thresholdFor(4) - 100, level.xpToNextRank)
    }

    @Test
    fun `maximum rank has no imaginary next reward`() {
        val myth = XpProgression.from(XpProgression.thresholdFor(22))

        assertEquals("Mito", myth.title)
        assertEquals("Marco dorado", myth.reward)
        assertEquals(null, myth.nextRankTitle)
        assertEquals(null, myth.xpToNextRank)
    }
}
