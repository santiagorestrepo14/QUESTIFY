package com.irlachievements.app.domain.challenges

import com.irlachievements.app.domain.model.Capability
import org.junit.Assert.*
import org.junit.Test

class ChallengeDiscoveryTest {
    @Test fun everyPlayableChallengeHasAnActualCategory() {
        ChallengeGameEngine.playableIds.forEach { id ->
            assertNotEquals(ChallengeKind.ALL, ChallengeDiscovery.kind(id))
            assertNotEquals(ChallengeKind.META, ChallengeDiscovery.kind(id))
            assertEquals(ChallengeGameEngine.requiredCapabilities(id).isEmpty(),
                ChallengeDiscovery.matches(ChallengeKind.SENSOR_FREE, id))
        }
    }

    @Test fun memoryDoesNotPretendToBeSensorFree() {
        assertEquals(ChallengeKind.MEMORY, ChallengeDiscovery.kind("simon_says"))
        assertFalse(ChallengeDiscovery.compatible("simon_says", setOf(Capability.ACCELEROMETER)))
        assertTrue(ChallengeDiscovery.matches(ChallengeKind.SENSOR_FREE, "color_echo"))
        assertTrue(ChallengeDiscovery.matches(ChallengeKind.MEMORY, "color_echo"))
        assertTrue(ChallengeDiscovery.matches(ChallengeKind.RHYTHM, "quiet_signal"))
        assertTrue(ChallengeDiscovery.compatible("quiet_signal", Capability.entries.toSet()))
    }

    @Test fun compatibilityUsesAllRequirementsAndExcludesMeta() {
        assertFalse(ChallengeDiscovery.compatible("gyroscope_master", emptySet()))
        assertFalse(ChallengeDiscovery.compatible("unknown", emptySet()))
        assertTrue(ChallengeDiscovery.compatible("full_circle", setOf(Capability.HUMIDITY)))
        assertEquals(setOf(Capability.GYROSCOPE),
            ChallengeDiscovery.missing("full_circle", setOf(Capability.GYROSCOPE, Capability.HUMIDITY)))
    }

    @Test fun knownTypesAndUsefulRetryHints() {
        assertEquals(ChallengeKind.ENVIRONMENT, ChallengeDiscovery.kind("humid_air"))
        assertEquals(ChallengeKind.PRECISION, ChallengeDiscovery.kind("tilt_labyrinth"))
        assertEquals(ChallengeKind.RHYTHM, ChallengeDiscovery.kind("traffic_light"))
        assertTrue(ChallengeDiscovery.retryTip(ChallengeRunState(challengeId = "duel", failureCode = "EARLY")).contains("Espera"))
    }
}
