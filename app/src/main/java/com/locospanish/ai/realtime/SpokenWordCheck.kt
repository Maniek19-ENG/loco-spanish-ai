package com.locospanish.ai.realtime

import com.google.gson.JsonParser
import com.locospanish.ai.tutor.LessonWord
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Second listen of one learner answer: the Live transcriber does not know which word is expected and turns a Polish-accented
 * "beber" into "deber" or "Bargteheide". A model told the target judges the same audio far more reliably.
 */
class SpokenWordCheck(private val http:Call.Factory=OkHttpClient.Builder().callTimeout(4,TimeUnit.SECONDS).build()) {
    companion object {
        fun wav(pcm:ByteArray,rate:Int=16000):ByteArray {
            val out=java.io.ByteArrayOutputStream(pcm.size+44)
            fun int(v:Int)=out.write(byteArrayOf(v.toByte(),(v shr 8).toByte(),(v shr 16).toByte(),(v shr 24).toByte()))
            fun short(v:Int)=out.write(byteArrayOf(v.toByte(),(v shr 8).toByte()))
            out.write("RIFF".toByteArray());int(36+pcm.size);out.write("WAVEfmt ".toByteArray());int(16);short(1);short(1);int(rate);int(rate*2);short(2);short(16)
            out.write("data".toByteArray());int(pcm.size);out.write(pcm)
            return out.toByteArray()
        }
        fun body(pcm:ByteArray,word:LessonWord):com.google.gson.JsonObject {
            val prompt="Nagranie to odpowiedź polskiego ucznia hiszpańskiego. Miał powiedzieć po hiszpańsku: „${word.es}” (${word.pl}). "+
                "Polski akcent, niewyraźna artykulacja, brak lub inny rodzajnik i doklejone słowa typu kurwa, eee, de NIE są błędem. "+
                "said_target=true tylko gdy w nagraniu naprawdę pada „${word.es}” (albo to samo słowo z akcentem). Inne słowo, polskie tłumaczenie, cisza lub sam głos lektora = false. heard: co słychać."
            val schema=GeminiProtocol.json("type" to "object","properties" to GeminiProtocol.json("said_target" to GeminiProtocol.json("type" to "boolean"),"heard" to GeminiProtocol.json("type" to "string")),"required" to listOf("said_target","heard"))
            return GeminiProtocol.json("contents" to listOf(GeminiProtocol.json("role" to "user","parts" to listOf(
                GeminiProtocol.json("inlineData" to GeminiProtocol.json("mimeType" to "audio/wav","data" to android.util.Base64.encodeToString(wav(pcm),android.util.Base64.NO_WRAP))),
                GeminiProtocol.json("text" to prompt)))),
                "generationConfig" to GeminiProtocol.json("responseMimeType" to "application/json","responseSchema" to schema,"maxOutputTokens" to 128,"thinkingConfig" to GeminiProtocol.json("thinkingBudget" to 0)))
        }
        fun decode(raw:String):Pair<Boolean,String>? = runCatching { JsonParser.parseString(raw).asJsonObject.let { it.get("said_target").asBoolean to it.str("heard") } }.getOrNull()
    }
    /** True/false from the second listen, or null when it could not answer; never throws. */
    suspend fun said(key:String,pcm:ByteArray,word:LessonWord):Pair<Boolean,String>? = suspendCancellableCoroutine { cont ->
        if(key.isBlank() || pcm.size<3200) { cont.resume(null);return@suspendCancellableCoroutine }
        val request=Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models/${GeminiWordPlanner.MODEL}:generateContent").header("x-goog-api-key",key)
            .post(body(pcm,word).toString().toRequestBody("application/json".toMediaType())).build()
        val call=http.newCall(request);cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object:Callback {
            override fun onFailure(call:Call,e:java.io.IOException) { if(cont.isActive) cont.resume(null) }
            override fun onResponse(call:Call,response:Response) {
                val result=runCatching { response.use {
                    if(!it.isSuccessful) return@use null
                    val parts=JsonParser.parseString(it.body.string()).asJsonObject.getAsJsonArray("candidates")?.firstOrNull()?.asJsonObject?.getAsJsonObject("content")?.getAsJsonArray("parts")
                    decode(parts?.filterNot { part -> part.asJsonObject.flag("thought") }?.joinToString("") { part -> part.asJsonObject.str("text") }.orEmpty())
                } }.getOrNull()
                if(cont.isActive) cont.resume(result)
            }
        })
    }
}

/** The last seconds of microphone audio, so one answer can be listened to again. */
class MicTape(private val maxBytes:Int=16000*2*8) {
    private val chunks=ArrayDeque<Pair<Long,ByteArray>>()
    private var size=0
    @Synchronized fun add(bytes:ByteArray,at:Long) {
        chunks.addLast(at to bytes.copyOf());size+=bytes.size
        while(size>maxBytes && chunks.isNotEmpty()) size-=chunks.removeFirst().second.size
    }
    @Synchronized fun since(from:Long):ByteArray {
        val parts=chunks.filter { it.first>=from }.map { it.second }
        val out=ByteArray(parts.sumOf { it.size });var offset=0
        parts.forEach { it.copyInto(out,offset);offset+=it.size }
        return out
    }
}
