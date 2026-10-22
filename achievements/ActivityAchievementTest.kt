package com.irlachievements.app.domain.achievements

import android.app.PendingIntent
import com.irlachievements.app.data.local.ActivityTransitionEntity
import com.irlachievements.app.data.repository.GoogleActivityRepository
import com.irlachievements.app.data.repository.InMemoryAchievementRepository
import com.irlachievements.app.domain.model.AchievementCategory
import com.irlachievements.app.domain.model.AchievementDefinition
import com.irlachievements.app.domain.model.Rarity
import com.irlachievements.app.domain.model.RuleType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ActivityAchievementTest {
    private val now = LocalDateTime.of(2026, 8, 30, 14, 0)

    @Test
    fun `still to walking unlocks Back On Your Feet`() = runTest {
        val definition = visible("back_on_your_feet", RuleType.ACTIVITY_TRANSITION)
        val repository = InMemoryAchievementRepository()
        val engine = AchievementEngine(repository, listOf(definition))

        engine.process(ActivityTransitionObserved(ActivityKind.WALKING, TransitionKind.ENTER, previousActivity = ActivityKind.STILL, occurredAt = now))

        assertTrue(repository.getState(definition.id)!!.unlocked)
    }

    @Test
    fun `activity sequence only unlocks in its exact order`() = runTest {
        val definition = hidden("gear_change")
        val repository = InMemoryAchievementRepository()
        val engine = AchievementEngine(repository, listOf(definition))

        engine.process(ActivitySequenceObserved(listOf(ActivityKind.RUNNING, ActivityKind.WALKING, ActivityKind.RUNNING), occurredAt = now))
        assertFalse(repository.getState(definition.id)?.unlocked == true)

        engine.process(ActivitySequenceObserved(listOf(ActivityKind.STILL, ActivityKind.WALKING, ActivityKind.RUNNING, ActivityKind.WALKING), occurredAt = now.plusMinutes(1)))
        assertTrue(repository.getState(definition.id)!!.unlocked)
    }

    @Test
    fun `reverse detector requires a completed earlier sequence`() {
        assertFalse(GoogleActivityRepository.containsCompletedReverse(listOf(ActivityKind.WALKING, ActivityKind.RUNNING, ActivityKind.WALKING)))
        assertTrue(
            GoogleActivityRepository.containsCompletedReverse(
                listOf(
                    ActivityKind.WALKING, ActivityKind.RUNNING, ActivityKind.ON_BICYCLE,
                    ActivityKind.ON_BICYCLE, ActivityKind.RUNNING, ActivityKind.WALKING,
                ),
            ),
        )
    }

    @Test
    fun `activity sequences cannot join transitions separated by hours`() {
        val minute = 60_000L
        val history = listOf(
            entered(ActivityKind.STILL, 0),
            entered(ActivityKind.WALKING, 10 * minute),
            entered(ActivityKind.RUNNING, 3 * 60 * minute),
            entered(ActivityKind.WALKING, 3 * 60 * minute + 10 * minute),
        )

        val contiguous = GoogleActivityRepository.contiguousSequenceHistory(history)

        assertTrue(contiguous == listOf(ActivityKind.RUNNING, ActivityKind.WALKING))
    }

    @Test
    fun `activity sequence keeps a realistic continuous session`() {
        val minute = 60_000L
        val history = listOf(
            entered(ActivityKind.STILL, 0),
            entered(ActivityKind.WALKING, 30 * minute),
            entered(ActivityKind.RUNNING, 90 * minute),
            entered(ActivityKind.ON_BICYCLE, 120 * minute),
        )

        val contiguous = GoogleActivityRepository.contiguousSequenceHistory(history)

        assertTrue(
            contiguous == listOf(
                ActivityKind.STILL,
                ActivityKind.WALKING,
                ActivityKind.RUNNING,
                ActivityKind.ON_BICYCLE,
            ),
        )
    }

    @Test
    fun `activity transition callback is mutable only where Android requires it`() {
        val androidNineFlags = GoogleActivityRepository.activityTransitionPendingIntentFlags(28)
        val androidTwelveFlags = GoogleActivityRepository.activityTransitionPendingIntentFlags(31)

        assertTrue(androidNineFlags and PendingIntent.FLAG_UPDATE_CURRENT != 0)
        assertFalse(androidNineFlags and PendingIntent.FLAG_MUTABLE != 0)
        assertTrue(androidTwelveFlags and PendingIntent.FLAG_UPDATE_CURRENT != 0)
        assertTrue(androidTwelveFlags and PendingIntent.FLAG_MUTABLE != 0)
        assertFalse(androidTwelveFlags and PendingIntent.FLAG_IMMUTABLE != 0)
    }

    @Test
    fun `new terrain and cooldown sequences require their exact order`() = runTest {
        val threeTerrains = hidden("three_terrains")
        val coolDown = hidden("cool_down")
        val repository = InMemoryAchievementRepository()
        val engine = AchievementEngine(repository, listOf(threeTerrains, coolDown))

        engine.process(
            ActivitySequenceObserved(
                listOf(ActivityKind.WALKING, ActivityKind.RUNNING, ActivityKind.WALKING),
                occurredAt = now,
            ),
        )
        assertFalse(repository.getState("three_terrains")?.unlocked == true)

        engine.process(
            ActivitySequenceObserved(
                listOf(ActivityKind.STILL, ActivityKind.WALKING, ActivityKind.RUNNING, ActivityKind.ON_BICYCLE),
                occurredAt = now.plusMinutes(1),
            ),
        )
        assertTrue(repository.getState("three_terrains")!!.unlocked)
        assertFalse(repository.getState("cool_down")?.unlocked == true)

        engine.process(
            ActivitySequenceObserved(
                listOf(ActivityKind.ON_BICYCLE, ActivityKind.RUNNING, ActivityKind.WALKING, ActivityKind.STILL),
                occurredAt = now.plusMinutes(2),
            ),
        )
        assertTrue(repository.getState("cool_down")!!.unlocked)
    }

    private fun visible(id: String, rule: RuleType) =
        AchievementDefinition(id, id, id, AchievementCategory.VISIBLE, false, Rarity.COMMON, rule)

    private fun hidden(id: String) =
        AchievementDefinition(id, id, id, AchievementCategory.HIDDEN, true, Rarity.RARE, RuleType.ACTIVITY_SEQUENCE)

    private fun entered(activity: ActivityKind, timestamp: Long) = ActivityTransitionEntity(
        timestamp = timestamp,
        activityType = activity.name,
        transitionType = TransitionKind.ENTER.name,
    )
}
