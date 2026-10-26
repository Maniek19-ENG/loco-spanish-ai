package com.locospanish.ai.voice

import java.text.Normalizer
import java.util.Locale

/** Validates the reference text, not the learner's pronunciation or acoustic accent. */
object SpanishReferencePolicy {
    private val separator=Regex("[^\\p{L}\\p{N}]+")
    fun text(value:String)=separator.replace(Normalizer.normalize(value,Normalizer.Form.NFC).lowercase(Locale.ROOT)," ").trim()
    fun matches(expected:String,actual:String)=text(expected)==text(actual)
    fun pcmFormat(mime:String):Boolean {
        val parts=mime.lowercase(Locale.ROOT).split(';').map(String::trim)
        return parts.firstOrNull()=="audio/pcm" && parts.drop(1).all { it=="rate=24000" }
    }
}
