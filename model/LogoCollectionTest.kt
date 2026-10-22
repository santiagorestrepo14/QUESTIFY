package com.irlachievements.app.domain.model

import com.irlachievements.app.domain.achievements.AchievementCatalog
import com.irlachievements.app.domain.challenges.ChallengeGameEngine
import org.junit.Assert.*
import org.junit.Test

class LogoCollectionTest {
    @Test fun nextRewardNeverSpoilsGlitchAndEndsWhenVisibleCollectionIsComplete() {
        assertEquals(LogoStyle.SUNSET_CHARGE, LogoCollection.nextReward(emptyMap())?.style)
        assertNull(LogoCollection.nextReward(earned(catalog)))
        assertNotEquals(LogoStyle.GLITCH_RELIC, LogoCollection.nextReward(earned(catalog.filterNot { it.hidden }))?.style)
    }

    @Test fun acknowledgedRewardsDoNotRepeatAndDefaultLogoNeverCelebrates() {
        assertTrue(LogoCollection.pendingRewards(emptyMap(), emptySet()).isEmpty())
        val all = earned(catalog)
        assertEquals(8, LogoCollection.pendingRewards(all, emptySet()).size)
        assertTrue(LogoCollection.pendingRewards(all, LogoStyle.entries.map { it.id }.toSet()).isEmpty())
        assertFalse(LogoCollection.pendingRewards(all, setOf(LogoStyle.SKY_PULSE.id)).contains(LogoStyle.SKY_PULSE))
    }
    private val catalog = AchievementCatalog.all
    private fun earned(items: List<AchievementDefinition>) = items.associate { it.id to AchievementState(it.id, unlocked = true) }
    private fun unlocked(style: LogoStyle, items: List<AchievementDefinition>) = LogoCollection.evaluate(earned(items)).single { it.style == style }.unlocked

    @Test fun freshPlayerOnlyHasNeonCore() {
        val collection = LogoCollection.evaluate(emptyMap())
        assertEquals(9, collection.size)
        assertEquals(listOf(LogoStyle.NEON_CORE), collection.filter { it.unlocked }.map { it.style })
    }

    @Test fun challengeAndRarityThresholdsAreExact() {
        val rules = listOf(
            Triple(LogoStyle.SKY_PULSE, 5, catalog.filter { it.id in ChallengeGameEngine.playableIds }),
            Triple(LogoStyle.VIOLET_RUSH, 8, catalog.filter { it.rarity == Rarity.EPIC }),
            Triple(LogoStyle.GOLD_RANK, 5, catalog.filter { it.rarity == Rarity.LEGENDARY }),
            Triple(LogoStyle.GLITCH_RELIC, 12, catalog.filter { it.hidden }),
        )
        rules.forEach { (style, count, candidates) ->
            assertTrue(candidates.size >= count)
            assertFalse(unlocked(style, candidates.take(count - 1)))
            assertTrue(unlocked(style, candidates.take(count)))
        }
    }

    @Test fun novaNeedsBothNormalCollectionAndDistinctChallenges() {
        val normal = catalog.filter { it.category == AchievementCategory.VISIBLE }.take(40)
        val challenges = catalog.filter { it.id in ChallengeGameEngine.playableIds }.take(30)
        assertFalse(unlocked(LogoStyle.NOVA_CROWN, normal + challenges.take(29)))
        assertFalse(unlocked(LogoStyle.NOVA_CROWN, normal.take(39) + challenges))
        assertFalse(unlocked(LogoStyle.NOVA_CROWN, catalog.filter { it.hidden } + challenges))
        assertTrue(unlocked(LogoStyle.NOVA_CROWN, normal + challenges))
    }

    @Test fun levelRewardsFollowExistingXpCurve() {
        val styles = mapOf(LogoStyle.SUNSET_CHARGE to 4, LogoStyle.EMERALD_NIGHT to 8, LogoStyle.FROST_ELITE to 12)
        for (size in 0..catalog.size) {
            val items = catalog.take(size)
            val level = XpProgression.from(items.sumOf { it.rarity.xp }).level
            styles.forEach { (style, required) -> assertEquals(level >= required, unlocked(style, items)) }
        }
    }

    @Test fun unknownIdsAndUnfinishedProgressDoNotUnlockRewards() {
        val states = catalog.associate { it.id to AchievementState(it.id, currentProgress = 99999.0) } +
            ("invented" to AchievementState("invented", unlocked = true))
        assertEquals(1, LogoCollection.evaluate(states).count { it.unlocked })
        assertEquals(LogoStyle.NEON_CORE, LogoCollection.resolveSelection("nova_crown", states))
        assertEquals(LogoStyle.NEON_CORE, LogoCollection.resolveSelection("invalid", earned(catalog)))
    }

    @Test fun existingProgressRetroactivelyUnlocksAllAndSelectionPersistsByStableId() {
        val states = earned(catalog)
        assertTrue(LogoCollection.evaluate(states).all { it.unlocked })
        LogoStyle.entries.forEach { assertEquals(it, LogoCollection.resolveSelection(it.id, states)) }
    }

    @Test fun lockedSecretDoesNotExposeThresholdOrProgress() {
        val secret = LogoCollection.evaluate(earned(catalog.filter { it.hidden }.take(11))).single { it.style == LogoStyle.GLITCH_RELIC }
        assertFalse(secret.requirement.contains("12"))
        assertEquals("Condición secreta", secret.progressText)
        assertEquals(0f, secret.progress, 0f)
    }
}
