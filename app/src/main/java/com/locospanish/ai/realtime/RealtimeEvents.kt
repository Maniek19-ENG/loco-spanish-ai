package com.locospanish.ai.realtime

import com.locospanish.ai.model.*

/** Pure reducer, tested without audio hardware. Playback events, not generation completion, drive SPEAKING. */
object RealtimeEvents {
    fun phase(current: VoicePhase, type: String, playing: Boolean): VoicePhase = when(type) {
        "input_audio_buffer.speech_started", "output_audio_buffer.stopped", "output_audio_buffer.cleared" -> VoicePhase.LISTENING
        "input_audio_buffer.speech_stopped", "response.created" -> if (playing) VoicePhase.SPEAKING else VoicePhase.THINKING
        "output_audio_buffer.started" -> VoicePhase.SPEAKING
        "response.done" -> if (playing) VoicePhase.SPEAKING else VoicePhase.LISTENING
        else -> current
    }
    fun transcript(list: List<Transcript>, id: String, role: String, text: String, delta: Boolean): List<Transcript> {
        val existing = list.firstOrNull { it.id == id }
        val value = Transcript(id, role, if (delta) (existing?.text.orEmpty() + text) else text, existing?.interrupted ?: false)
        return if (existing == null) list + value else list.map { if (it.id == id) value else it }
    }
}
