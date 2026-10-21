package com.irlachievements.app.ui

import android.content.Context
import android.media.*
import android.os.Handler
import android.os.Looper
import android.os.Build
import com.irlachievements.app.domain.challenges.StereoCue
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException

/** Short locally generated PCM. Never adjusts system volume or opens a microphone. */
internal class StereoCuePlayer(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var track: AudioTrack? = null
    private var routeLost = false
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private val focus = GameAudioFocus(audio) { routeLost = true; runCatching { track?.pause() } }
    private val headphoneTypes = buildSet {
        add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
        add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
        add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        add(AudioDeviceInfo.TYPE_USB_HEADSET)
        if (Build.VERSION.SDK_INT >= 31) add(AudioDeviceInfo.TYPE_BLE_HEADSET)
    }

    fun unavailableReason(): String? = when {
        audio == null -> "No se puede acceder a la salida de audio."
        audio.ringerMode != AudioManager.RINGER_MODE_NORMAL -> "Desactiva el modo silencio para realizar este reto."
        audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0 -> "El volumen multimedia está silenciado. Ajústalo a un nivel cómodo."
        audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).none { it.type in headphoneTypes } -> "Conecta auriculares estéreo para distinguir las direcciones."
        else -> null
    }

    fun stop() {
        focus.release()
        routeLost = true
        val previous = track
        track = null
        if (previous != null) { runCatching { previous.stop() }; previous.release() }
    }

    suspend fun play(side: Int): String? {
        stop()
        val manager = audio ?: return "No se puede acceder a la salida de audio."
        unavailableReason()?.let { return it }
        if (manager.isMusicActive) return "Pausa el audio de otras aplicaciones para hacer la prueba."
        val output = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type in headphoneTypes }
            ?: return "Conecta auriculares estéreo."
        routeLost = false
        try {
            if (!focus.acquire(attributes)) return "Audio no disponible ahora. Vuelve a intentarlo."
            val samples = StereoCue.samples(side)
            val next = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(StereoCue.sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(samples.size * 2).build()
            track = next
            if (next.state != AudioTrack.STATE_INITIALIZED || !next.setPreferredDevice(output)) return "No se pudo preparar la salida estéreo."
            next.addOnRoutingChangedListener(AudioRouting.OnRoutingChangedListener { routing ->
                if (track === next && routing.routedDevice?.id != output.id) { routeLost = true; runCatching { next.pause() } }
            }, Handler(Looper.getMainLooper()))
            next.setVolume(.35f)
            if (next.write(samples, 0, samples.size) != samples.size) return "No se pudo preparar el sonido completo."
            next.play()
            delay(100)
            if (routeLost || next.routedDevice?.id != output.id) return "La salida de audio cambió. Reconecta los auriculares y repite la prueba."
            delay(StereoCue.durationMs.toLong() + 150)
            return if (routeLost || unavailableReason() != null) "Comprueba los auriculares y repite la prueba." else null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return "No se pudo reproducir la señal. Comprueba la salida de audio."
        } finally { stop() }
    }
}
