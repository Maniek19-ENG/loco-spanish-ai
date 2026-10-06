package com.locospanish.ai.realtime

import android.content.Context
import android.util.Base64
import com.google.gson.*
import com.locospanish.ai.model.*
import com.locospanish.ai.voice.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit

class GeminiLiveClient(
    context: Context, private val scope: CoroutineScope,
    private val http: WebSocket.Factory = OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS).readTimeout(0,TimeUnit.SECONDS).pingInterval(20,TimeUnit.SECONDS).build(),
    private val endpoint: String = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent",
    private val audioFactory: () -> LiveAudio = { PcmAudio(context,scope) }
) {
    private val mutable = MutableStateFlow(VoiceState())
    val state = mutable.asStateFlow()
    private var socket: WebSocket? = null
    private var audio: LiveAudio? = null
    private var timeout: Job? = null
    @Volatile private var epoch = 0
    private var transcript = GeminiTranscript()
    private val toolIds = mutableSetOf<String>()
    private val cancelledTools = mutableSetOf<String>()
    private val completionIds = mutableSetOf<String>()

    fun start(settings: Settings, key: String, scenario: String, memory: String, reset: Boolean = true) {
        close(); val generation = epoch
        if(reset) { mutable.value = VoiceState(); toolIds.clear(); completionIds.clear() }
        transcript = GeminiTranscript(); cancelledTools.clear()
        mutable.update { it.copy(phase = VoicePhase.CONNECTING, connected = false, message = "Łączę z Gemini Live…") }
        if(key.isBlank()) { fail("Wpisz klucz Gemini w Ustawieniach."); return }
        fun dispatch(action: () -> Unit) { scope.launch { if(epoch == generation) action() } }
        val resume = if(reset) "" else state.value.transcript.takeLast(6).joinToString("\n") { "${it.role}: ${it.text.take(250)}" }
        val request = Request.Builder().url(endpoint).header("x-goog-api-key",key).build()
        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { dispatch {
                webSocket.send(GeminiProtocol.setup(settings,scenario,("Ostatnia rozmowa:\n" + resume + "\nWcześniejsza nauka:\n" + memory).take(2400)).toString())
            } }
            override fun onMessage(webSocket: WebSocket, text: String) { dispatch {
                try { event(JsonParser.parseString(text).asJsonObject, generation, reset, settings, scenario) }
                catch(_: SecurityException) { fail("Brak dostępu do mikrofonu. Włącz uprawnienie w ustawieniach telefonu.") }
                catch(_: Exception) { fail("Odebrano nieprawidłową odpowiedź Gemini. Spróbuj ponownie.") }
            } }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = onMessage(webSocket,bytes.utf8())
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { dispatch { fail(GeminiProtocol.problem(response?.code ?: 0)) } }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code,null); dispatch { fail(GeminiProtocol.problem(code,reason)) } }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { dispatch { fail(GeminiProtocol.problem(code,reason)) } }
        })
        timeout = scope.launch { delay(25000); if(epoch == generation && !state.value.connected) fail("Upłynął czas łączenia. Sprawdź internet, klucz i dostępność Gemini.") }
    }
    private fun event(e: JsonObject, generation: Int, reset: Boolean, settings: Settings, scenario: String) {
        if(e.has("error")) { val error = e.getAsJsonObject("error"); fail(GeminiProtocol.problem(error.get("code")?.asInt ?: 0,error.str("message"))); return }
        if(e.has("setupComplete")) {
            timeout?.cancel(); mutable.update { it.copy(connected = true,phase = VoicePhase.LISTENING,message = "Gemini Live • rozmowa przez internet") }
            val device = audioFactory(); audio = device
            device.start(onInput = { bytes ->
                if(epoch == generation && !state.value.muted) {
                    val ws = socket
                    if(ws != null && (ws.queueSize() > 256000 || !ws.send(GeminiProtocol.audio(Base64.encodeToString(bytes,Base64.NO_WRAP)).toString()))) {
                        scope.launch { if(epoch == generation) fail("Sieć nie nadąża za rozmową. Spróbuj ponownie przy lepszym połączeniu.") }
                    }
                }
            }, onError = { scope.launch { if(epoch == generation) fail("Mikrofon lub głośnik jest niedostępny. Sprawdź uprawnienia i spróbuj ponownie.") } },
                onPlaying = { playing -> scope.launch { if(epoch == generation && state.value.connected) mutable.update { it.copy(phase = if(playing) VoicePhase.SPEAKING else VoicePhase.LISTENING) } } })
            device.mute(state.value.muted)
            command(GeminiProtocol.startCommand(settings, scenario, reconnect = !reset))
        }
        e.getAsJsonObject("serverContent")?.let { content ->
            mutable.value = transcript.apply(state.value,content)
            if(content.flag("interrupted")) { audio?.interrupt(); mutable.update { it.copy(phase = VoicePhase.LISTENING) } }
            val parts = content.getAsJsonObject("modelTurn")?.getAsJsonArray("parts")
            parts?.forEach { part -> part.asJsonObject.getAsJsonObject("inlineData")?.let { data ->
                if(!content.flag("interrupted") && data.str("mimeType").startsWith("audio/pcm")) {
                    if(audio?.enqueue(Base64.decode(data.str("data"),Base64.DEFAULT)) != true) fail("Odtwarzanie audio nie nadąża. Spróbuj ponownie.")
                }
            } }
            if(content.has("inputTranscription") && state.value.phase != VoicePhase.SPEAKING) mutable.update { it.copy(phase = VoicePhase.THINKING) }
        }
        e.getAsJsonObject("toolCallCancellation")?.getAsJsonArray("ids")?.forEach { id ->
            cancelledTools.add(id.asString)
            completionIds.remove(id.asString)
            mutable.update { it.copy(learning = it.learning.filterNot { item -> item.id == id.asString }, completed = completionIds.isNotEmpty()) }
        }
        e.getAsJsonObject("toolCall")?.getAsJsonArray("functionCalls")?.let { calls ->
            val responses = calls.map { call ->
                val fn = call.asJsonObject; val id = fn.str("id"); val name = fn.str("name"); val args = fn.getAsJsonObject("args") ?: JsonObject()
                var saved = false
                if(id.isNotEmpty() && id !in cancelledTools && toolIds.add(id) && args.str("evidence").isNotBlank()) {
                    when(name) {
                        "record_learning" -> if(args.str("kind") in setOf("word","difficult_word","grammar","pronunciation","repetition") && args.str("term").isNotBlank()) {
                            val item = LearningEvent(id,args.str("kind"),args.str("term").take(150),args.str("correction").take(200),args.str("explanation").take(300),args.str("evidence").take(300))
                            mutable.update { it.copy(learning = it.learning + item) }; saved = true
                        }
                        "complete_scenario" -> { completionIds.add(id); mutable.update { it.copy(completed = true) }; saved = true }
                    }
                }
                GeminiProtocol.json("id" to id,"name" to name,"response" to GeminiProtocol.json("saved" to saved))
            }
            send(GeminiProtocol.json("toolResponse" to GeminiProtocol.json("functionResponses" to responses)))
        }
        if(e.has("goAway")) mutable.update { it.copy(message = "Google wkrótce zamknie tę sesję. Po rozłączeniu wybierz „Spróbuj ponownie”.") }
    }
    private fun send(value: JsonObject) { if(socket?.send(value.toString()) != true) fail("Nie udało się wysłać wiadomości. Spróbuj ponownie.") }
    fun command(text: String) { if(state.value.connected) { audio?.interrupt(); send(GeminiProtocol.textTurn(text)); mutable.update { it.copy(phase = VoicePhase.THINKING) } } }
    fun mute() {
        val muted = !state.value.muted; audio?.mute(muted); mutable.update { it.copy(muted = muted) }
        if(muted && state.value.connected) send(GeminiProtocol.json("realtimeInput" to GeminiProtocol.json("audioStreamEnd" to true)))
    }
    private fun fail(message: String) { close(); mutable.update { it.copy(phase = VoicePhase.ERROR,connected = false,message = message) } }
    private fun close() { epoch++; timeout?.cancel(); timeout = null; socket?.cancel(); socket = null; audio?.close(); audio = null }
    fun stop() { close(); mutable.update { it.copy(phase = VoicePhase.IDLE,connected = false,muted = false,message = "Rozmowa zakończona.") } }
}
