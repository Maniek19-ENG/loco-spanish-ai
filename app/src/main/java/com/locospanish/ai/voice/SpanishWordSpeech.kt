package com.locospanish.ai.voice

import android.content.Context
import java.util.Base64
import com.google.gson.JsonParser
import com.locospanish.ai.realtime.GeminiProtocol
import com.locospanish.ai.model.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

/** Pronunciation is generated without Polish speech or jokes in its language context. */
class SpanishWordSpeech(
    context: Context, private val key: String, private val voice: String,
    private val factory: WebSocket.Factory = OkHttpClient.Builder().pingInterval(20,java.util.concurrent.TimeUnit.SECONDS).build()
) : SupplementSpeaker {
    private val directory=File(context.cacheDir,"spanish-reference-es-ES-v3").apply { mkdirs() }
    private val mutex=Mutex()
    @Volatile private var closed=false
    private data class Pending(val text:String,val result:CompletableDeferred<ByteArray> = CompletableDeferred(),val bytes:ByteArrayOutputStream = ByteArrayOutputStream(),var spoken:String="")
    @Volatile private var socket:WebSocket? = null
    @Volatile private var ready:CompletableDeferred<Unit>? = null
    @Volatile private var active:Pending? = null
    private val connectionEpoch=java.util.concurrent.atomic.AtomicInteger()

    companion object {
        const val MAX_TEXT=160
        fun cacheKey(voice:String,text:String)=MessageDigest.getInstance("SHA-256").digest(("es-ES-v3|"+voice+"|"+text).toByteArray()).joinToString("") { "%02x".format(it) }
        fun setup(voice: String) = GeminiProtocol.setup(Settings(voice=voice),"free","").also { root ->
            val setup=root.getAsJsonObject("setup")
            listOf("tools","inputAudioTranscription","realtimeInputConfig").forEach(setup::remove)
            setup.getAsJsonObject("systemInstruction").getAsJsonArray("parts")[0].asJsonObject.addProperty("text",
                "Eres un locutor nativo de España. Habla SOLAMENTE español, con pronunciación castellana natural y clara. Lee únicamente el texto indicado, palabra o frase breve, una sola vez, con dicción natural de España. Conserva las vocales puras, las consonantes españolas y la sílaba tónica marcada por las tildes. Si es una frase, conserva su entonación natural; no la conviertas en una lista de palabras. Incluye el artículo si aparece. No saludes, no expliques, no traduzcas, no añadas frases ni risas. Conserva sus tildes y el acento léxico. No uses la fonética polaca ni imites el acento del alumno. No hagas bromas ni caricatures la pronunciación. No añadas explicaciones ni traducciones.")
        }
    }
    override suspend fun prepare(text:String) { synthesize(text) }
    override suspend fun synthesize(text:String):ByteArray = mutex.withLock {
        check(!closed);require(text.isNotBlank() && text.length<=MAX_TEXT)
        val hash=cacheKey(voice,text)
        val cached=File(directory,"$hash.pcm")
        if(cached.exists() && cached.length() in 2..500000) return@withLock withContext(Dispatchers.IO) { cached.readBytes() }
        val pending=Pending(text);active=pending
        try {
            withTimeout(6000) {
                ensureConnection().await()
                check(socket?.send(GeminiProtocol.textTurn("Lee solamente este texto en español: "+GeminiProtocol.json("texto" to text)).toString()) == true)
                pending.result.await().also { pcm -> withContext(Dispatchers.IO) { cached.writeBytes(pcm) } }
            }
        } catch(error:Exception) { disconnect();throw error }
        finally { if(active === pending) active=null }
    }
    private fun ensureConnection():CompletableDeferred<Unit> {
        ready?.let { if(socket != null && !it.isCancelled) return it }
        val connected=CompletableDeferred<Unit>();ready=connected
        val epoch=connectionEpoch.incrementAndGet()
        val request=Request.Builder().url("wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent").header("x-goog-api-key",key).build()
        socket=factory.newWebSocket(request,object:WebSocketListener() {
            override fun onOpen(webSocket:WebSocket,response:Response) { if(epoch == connectionEpoch.get()) webSocket.send(setup(voice).toString()) }
            override fun onMessage(webSocket:WebSocket,text:String) {
                if(epoch != connectionEpoch.get()) return
                runCatching {
                    val event=JsonParser.parseString(text).asJsonObject
                    if(event.has("error")) error("Wzorzec wymowy jest chwilowo niedostępny.")
                    if(event.has("setupComplete")) connected.complete(Unit)
                    val pending=active ?: return
                    if(pending.result.isCompleted) return
                    event.getAsJsonObject("serverContent")?.let { content ->
                        content.getAsJsonObject("outputTranscription")?.get("text")?.asString?.let { fragment -> pending.spoken=if(fragment.startsWith(pending.spoken)) fragment else pending.spoken+fragment }
                        content.getAsJsonObject("modelTurn")?.getAsJsonArray("parts")?.forEach { part ->
                            part.asJsonObject.getAsJsonObject("inlineData")?.let { data ->
                                if(data.get("mimeType")?.asString?.startsWith("audio/pcm") == true) {
                                    check(SpanishReferencePolicy.pcmFormat(data.get("mimeType").asString)) { "Nieobsługiwany format hiszpańskiego wzorca." }
                                    val pcm=Base64.getDecoder().decode(data.get("data").asString)
                                    check(pcm.size % 2 == 0)
                                    check(pending.bytes.size()+pcm.size<=500000);pending.bytes.write(pcm)
                                }
                            }
                        }
                        if(content.get("turnComplete")?.asBoolean == true) {
                            check(pending.bytes.size()>0)
                            check(pending.spoken.isBlank() || SpanishReferencePolicy.matches(pending.text,pending.spoken)) { "Wzorzec zawiera inne słowo lub dodatkowe zdanie." }
                            pending.result.complete(pending.bytes.toByteArray())
                        }
                    }
                }.onFailure { failConnection(it) }
            }
            override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?) { if(epoch==connectionEpoch.get()) failConnection(IllegalStateException("Wzorzec wymowy jest chwilowo niedostępny.")) }
            override fun onClosing(webSocket:WebSocket,code:Int,reason:String) { webSocket.close(code,null);if(epoch==connectionEpoch.get()) failConnection(IllegalStateException("Wzorzec wymowy został przerwany.")) }
        })
        return connected
    }
    private fun failConnection(error:Throwable) { ready?.completeExceptionally(error);active?.result?.completeExceptionally(error);disconnect() }
    private fun disconnect() { connectionEpoch.incrementAndGet();socket?.cancel();socket=null;ready=null }
    override fun close() { closed=true;active?.result?.cancel();ready?.cancel();disconnect() }
}
