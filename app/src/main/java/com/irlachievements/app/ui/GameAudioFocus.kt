package com.irlachievements.app.ui

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/** Never resumes automatically after interruptions: the player must request another cue. */
internal class GameAudioFocus(private val manager: AudioManager?, private val onLost: () -> Unit) {
    var interrupted = true
        private set
    private var request: AudioFocusRequest? = null
    fun acquire(attributes: AudioAttributes): Boolean {
        release()
        val audio = manager ?: return false
        lateinit var next: AudioFocusRequest
        next = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes).setAcceptsDelayedFocusGain(false)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener({ change ->
                if (request === next && change < 0) { interrupted = true; onLost() }
            }, Handler(Looper.getMainLooper())).build()
        request = next
        interrupted = audio.requestAudioFocus(next) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (interrupted) release()
        return !interrupted
    }
    fun release() {
        interrupted = true
        val old = request
        request = null
        if (old != null) runCatching { manager?.abandonAudioFocusRequest(old) }
    }
}
