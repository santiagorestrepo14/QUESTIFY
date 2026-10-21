package com.irlachievements.app.ui

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.irlachievements.app.domain.challenges.RunStatus

internal enum class FeedbackCue { SUCCESS, FAILURE }

internal fun feedbackCue(previous: RunStatus, current: RunStatus, newUnlock: Boolean): FeedbackCue? = when {
    newUnlock -> FeedbackCue.SUCCESS
    previous == RunStatus.RUNNING && current == RunStatus.SUCCESS -> FeedbackCue.SUCCESS
    previous == RunStatus.RUNNING && current == RunStatus.FAILED -> FeedbackCue.FAILURE
    else -> null
}

/** Short local tones; never alter system volume, request audio focus or play in background. */
@Composable
internal fun GameFeedback(state: AppUiState, logoCelebrationId: String? = null) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val audio = remember(context) { context.getSystemService(AudioManager::class.java) }
    val tone = remember(state.soundEnabled) {
        if (state.soundEnabled) runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 25) }.getOrNull() else null
    }
    var previousStatus by remember { mutableStateOf(state.challengeRun.status) }
    var previousUnlock by remember { mutableStateOf(state.celebration?.id) }
    var previousLogo by remember { mutableStateOf<String?>(null) }
    DisposableEffect(tone, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) tone?.stopTone()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); tone?.release() }
    }
    LaunchedEffect(state.challengeRun.status, state.celebration?.id, logoCelebrationId) {
        val unlock = state.celebration?.id
        val cue = feedbackCue(previousStatus, state.challengeRun.status,
            (unlock != null && unlock != previousUnlock) || (logoCelebrationId != null && logoCelebrationId != previousLogo))
        previousLogo = logoCelebrationId
        previousStatus = state.challengeRun.status
        previousUnlock = unlock
        if (state.soundEnabled && !state.challengeRun.microphoneActive && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
            audio?.ringerMode == AudioManager.RINGER_MODE_NORMAL &&
            audio.getStreamVolume(AudioManager.STREAM_MUSIC) > 0 && !audio.isMusicActive) {
            when (cue) {
                FeedbackCue.SUCCESS -> tone?.startTone(ToneGenerator.TONE_PROP_ACK, 130)
                FeedbackCue.FAILURE -> tone?.startTone(ToneGenerator.TONE_PROP_NACK, 100)
                null -> Unit
            }
        }
    }
}
