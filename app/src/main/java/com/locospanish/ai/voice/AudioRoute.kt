package com.locospanish.ai.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioDeviceInfo
import android.os.Build
import androidx.annotation.RequiresApi
import com.locospanish.ai.model.AudioRoutePreference

class AudioRoute(context: Context, private val preference: AudioRoutePreference, onLost: () -> Unit) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private var oldMode = AudioManager.MODE_NORMAL
    private var oldSpeaker = false
    private var acquired = false
    private var selectedModernDevice = false
    var label: String = "audio telefonu"
        private set
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .setOnAudioFocusChangeListener { if (it < 0) onLost() }.build()
    @Suppress("DEPRECATION")
    fun acquire() {
        check(manager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "Mikrofon lub audio są zajęte przez inną aplikację." }
        oldMode = manager.mode; oldSpeaker = manager.isSpeakerphoneOn; acquired = true
        manager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) acquireModern() else acquireLegacy()
    }

    private fun bluetooth(type: Int) = type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER))

    private fun wired(type: Int) = type == AudioDeviceInfo.TYPE_WIRED_HEADSET || type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
        type == AudioDeviceInfo.TYPE_USB_HEADSET || type == AudioDeviceInfo.TYPE_USB_DEVICE

    @RequiresApi(Build.VERSION_CODES.S)
    private fun acquireModern() {
        val devices = manager.availableCommunicationDevices
        val chosen = when (preference) {
            AudioRoutePreference.BLUETOOTH -> devices.firstOrNull { bluetooth(it.type) }
            AudioRoutePreference.PHONE -> devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            AudioRoutePreference.AUTO -> devices.firstOrNull { bluetooth(it.type) }
                ?: devices.firstOrNull { wired(it.type) }
                ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        }
        selectedModernDevice = chosen != null && manager.setCommunicationDevice(chosen)
        label = when {
            chosen != null && selectedModernDevice && bluetooth(chosen.type) -> "słuchawki Bluetooth"
            chosen != null && selectedModernDevice && wired(chosen.type) -> "słuchawki przewodowe"
            preference == AudioRoutePreference.BLUETOOTH -> "Bluetooth niedostępny • głośnik telefonu"
            else -> "audio telefonu"
        }
    }

    @Suppress("DEPRECATION")
    private fun acquireLegacy() {
        val devices = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val useBluetooth = preference != AudioRoutePreference.PHONE && devices.any { bluetooth(it.type) }
        if (useBluetooth) {
            manager.startBluetoothSco(); manager.isBluetoothScoOn = true; manager.isSpeakerphoneOn = false
            label = "słuchawki Bluetooth"
        } else {
            manager.isSpeakerphoneOn = !manager.isWiredHeadsetOn
            label = if (preference == AudioRoutePreference.BLUETOOTH) "Bluetooth niedostępny • głośnik telefonu" else "audio telefonu"
        }
    }
    @Suppress("DEPRECATION")
    fun release() { if (acquired) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (selectedModernDevice) manager.clearCommunicationDevice()
        } else if (manager.isBluetoothScoOn) {
            manager.stopBluetoothSco(); manager.isBluetoothScoOn = false
        }
        manager.mode = oldMode; manager.isSpeakerphoneOn = oldSpeaker; manager.abandonAudioFocusRequest(focus); acquired = false
    } }
}
