package com.irlachievements.app.ui

import com.irlachievements.app.domain.challenges.RunStatus
import org.junit.Assert.*
import org.junit.Test

class GameFeedbackTest {
    @Test fun cuesOnlyForNewResultsOrUnlocks() {
        assertEquals(FeedbackCue.SUCCESS, feedbackCue(RunStatus.RUNNING, RunStatus.SUCCESS, false))
        assertEquals(FeedbackCue.FAILURE, feedbackCue(RunStatus.RUNNING, RunStatus.FAILED, false))
        assertEquals(FeedbackCue.SUCCESS, feedbackCue(RunStatus.IDLE, RunStatus.IDLE, true))
        assertNull(feedbackCue(RunStatus.SUCCESS, RunStatus.SUCCESS, false))
        assertNull(feedbackCue(RunStatus.RUNNING, RunStatus.RUNNING, false))
        assertNull(feedbackCue(RunStatus.RUNNING, RunStatus.UNSUPPORTED, false))
        assertNull(feedbackCue(RunStatus.RUNNING, RunStatus.IDLE, false))
    }
}
