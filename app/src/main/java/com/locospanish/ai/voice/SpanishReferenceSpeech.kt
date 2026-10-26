package com.locospanish.ai.voice

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

/** The Spanish reference has its own native voice and never inherits comic prosody. */
class SpanishReferenceSpeech(
    context:Context,key:String,voice:String,
    private val native:SupplementSpeaker=SystemSpeech(context,Locale.forLanguageTag("es-ES"),.92f,1f,2500),
    private val fallback:SupplementSpeaker=SpanishWordSpeech(context,key,voice)
):SupplementSpeaker {
    private val lock=Mutex()
    private val cache=object:LinkedHashMap<String,ByteArray>(32,.75f,true) {
        override fun removeEldestEntry(eldest:MutableMap.MutableEntry<String,ByteArray>?)=size>40
    }
    @Volatile private var closed=false
    private var nativeUnavailable=false
    override suspend fun prepare(text:String) { synthesize(text) }
    override suspend fun synthesize(text:String):ByteArray=lock.withLock {
        check(!closed);require(text.isNotBlank() && text.length<=SpanishWordSpeech.MAX_TEXT)
        cache[text]?.let { return@withLock it.copyOf() }
        val local=if(nativeUnavailable) null else try {
            native.synthesize(text).also { check(it.isNotEmpty() && it.size%2==0) }
        } catch(error:Exception) {
            if(error is CancellationException && error !is TimeoutCancellationException) throw error
            nativeUnavailable=true;null
        }
        val pcm=local ?: fallback.synthesize(text)
        check(!closed && pcm.isNotEmpty() && pcm.size%2==0)
        cache[text]=pcm.copyOf();pcm
    }
    override fun close() { closed=true;native.close();fallback.close() }
}
