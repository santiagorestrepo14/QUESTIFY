package com.irlachievements.app.domain.achievements

import com.irlachievements.app.domain.challenges.ChallengeGameEngine
import com.irlachievements.app.domain.model.AchievementCategory
import com.irlachievements.app.domain.model.ChallengeAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementCatalogTest {
    @Test
    fun `catalog ids are unique and category totals stay auditable`() {
        val catalog = AchievementCatalog.all

        assertEquals(222, catalog.size)
        assertEquals(catalog.size, catalog.map { it.id }.distinct().size)
        assertEquals(50, catalog.count { it.category == AchievementCategory.VISIBLE })
        assertEquals(107, catalog.count { it.category == AchievementCategory.HIDDEN })
        assertEquals(65, catalog.count { it.category == AchievementCategory.CHALLENGE })
        assertTrue(catalog.filter { it.category == AchievementCategory.HIDDEN }.all { it.hidden })
    }

    @Test
    fun `no challenge remains planned or without an implementation`() {
        val challenges = AchievementCatalog.all.filter { it.category == AchievementCategory.CHALLENGE }
        val playableCatalogIds = challenges.map { it.id }.toSet() - "gyroscope_master"

        assertTrue(challenges.all { it.availability == ChallengeAvailability.READY })
        assertEquals(playableCatalogIds, ChallengeGameEngine.playableIds)
    }
}
