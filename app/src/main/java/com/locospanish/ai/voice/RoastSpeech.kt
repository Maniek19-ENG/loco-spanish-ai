package com.locospanish.ai.voice

import android.content.Context
import android.media.AudioFormat
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object RoastPcm {
    fun mono24k(bytes: ByteArray, rate: Int, channels: Int): ByteArray {
        require(rate in 8000..96000 && channels in 1..2 && bytes.size % (channels * 2) == 0)
        val frames = bytes.size / (channels * 2)
        if(frames == 0) return byteArrayOf()
        val output = ByteArray((frames.toLong() * 24000 / rate).toInt() * 2)
        fun sample(frame: Int): Int = (0 until channels).sumOf { channel ->
            val offset = (frame.coerceIn(0, frames - 1) * channels + channel) * 2
            ((bytes[offset].toInt() and 255) or (bytes[offset + 1].toInt() shl 8)).toShort().toInt()
        } / channels
        for(i in 0 until output.size / 2) {
            val position = i.toDouble() * rate / 24000
            val frame = position.toInt()
            val part = position - frame
            val value = (sample(frame) * (1 - part) + sample(frame + 1) * part).toInt().coerceIn(-32768, 32767)
            output[i * 2] = value.toByte(); output[i * 2 + 1] = (value shr 8).toByte()
        }
        return output
    }
}

interface SupplementSpeaker {
    suspend fun prepare(text:String) {}
    suspend fun synthesize(text: String): ByteArray
    fun close()
}
class SystemSpeech(private val context: Context, private val locale:Locale=Locale.forLanguageTag("pl-PL"),
    private val rate:Float=1.06f, private val pitch:Float=.9f, private val timeoutMs:Long=12000) : SupplementSpeaker {
    private val ready = CompletableDeferred<Boolean>()
    private data class Pending(val result: CompletableDeferred<ByteArray>, val file: File,
        val bytes: ByteArrayOutputStream = ByteArrayOutputStream(), var rate: Int = 24000,
        var channels: Int = 1, var format: Int = AudioFormat.ENCODING_PCM_16BIT)
    private val pending = ConcurrentHashMap<String, Pending>()
    private val engine = TextToSpeech(context) { status -> ready.complete(status == TextToSpeech.SUCCESS) }
    init {
        engine.setOnUtteranceProgressListener(object: UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onBeginSynthesis(id: String?, rate: Int, format: Int, channels: Int) {
                id?.let { pending[it] }?.let { it.rate = rate; it.format = format; it.channels = channels }
            }
            override fun onAudioAvailable(id: String?, audio: ByteArray?) {
                id?.let { pending[it] }?.let { item -> if(audio != null && item.bytes.size() + audio.size <= 1500000) item.bytes.write(audio) }
            }
            override fun onDone(id: String?) {
                id?.let { pending.remove(it) }?.let { item ->
                    runCatching {
                        check(item.format == AudioFormat.ENCODING_PCM_16BIT && item.bytes.size() > 0)
                        RoastPcm.mono24k(item.bytes.toByteArray(), item.rate, item.channels)
                    }.fold({item.result.complete(it)}, {item.result.completeExceptionally(it)})
                    item.file.delete()
                }
            }
            @Deprecated("Legacy callback") override fun onError(id: String?) {
                id?.let { pending.remove(it) }?.let { it.file.delete(); it.result.completeExceptionally(IllegalStateException("Brak polskiego głosu docinek")) }
            }
        })
    }
    override suspend fun synthesize(text: String): ByteArray = withTimeout(timeoutMs) {
        check(ready.await()) { "Uruchom polski głos systemowy w ustawieniach telefonu." }
        check(engine.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE) { "Brak wymaganego języka syntezy." }
        val voices=engine.voices.orEmpty().toList()
        val selected=SystemVoicePolicy.choose(voices.map {
            SystemVoiceChoice(it.name,it.locale.language,it.locale.country,it.isNetworkConnectionRequired,
                TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty(),it.quality,it.latency)
        },locale.language,locale.country)
        val referenceVoice=voices.firstOrNull { it.name==selected } ?: error("Brak dostępnego głosu ${locale.toLanguageTag()}.")
        check(engine.setVoice(referenceVoice) == TextToSpeech.SUCCESS)
        engine.setSpeechRate(rate); engine.setPitch(pitch)
        val id = UUID.randomUUID().toString()
        val item = Pending(CompletableDeferred(), File.createTempFile("loco-roast-", ".wav", context.cacheDir))
        pending[id] = item
        try {
            check(engine.synthesizeToFile(text.take(750), Bundle(), item.file, id) == TextToSpeech.SUCCESS)
            item.result.await()
        } finally { pending.remove(id); item.file.delete(); if(!item.result.isCompleted) engine.stop() }
    }
    override fun close() {
        engine.stop(); engine.shutdown()
        pending.values.forEach { it.result.cancel(); it.file.delete() }; pending.clear()
    }
}

class RoastSpeech(context:Context):SupplementSpeaker by SystemSpeech(context)


