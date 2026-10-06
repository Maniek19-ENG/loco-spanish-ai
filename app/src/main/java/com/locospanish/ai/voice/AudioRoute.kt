package com.locospanish.ai.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

class AudioRoute(context: Context, onLost: () -> Unit) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private var oldMode = AudioManager.MODE_NORMAL
    private var oldSpeaker = false
    private var acquired = false
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .setOnAudioFocusChangeListener { if (it < 0) onLost() }.build()
    @Suppress("DEPRECATION")
    fun acquire() {
        check(manager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "Mikrofon lub audio są zajęte przez inną aplikację." }
        oldMode = manager.mode; oldSpeaker = manager.isSpeakerphoneOn; acquired = true
        manager.mode = AudioManager.MODE_IN_COMMUNICATION
        manager.isSpeakerphoneOn = !manager.isWiredHeadsetOn && !manager.isBluetoothScoOn
    }
    @Suppress("DEPRECATION")
    fun release() { if (acquired) { manager.mode = oldMode; manager.isSpeakerphoneOn = oldSpeaker; manager.abandonAudioFocusRequest(focus); acquired = false } }
}
