package com.locospanish.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.locospanish.ai.data.AccessTokenStore
import com.locospanish.ai.model.*
import com.locospanish.ai.realtime.*
import com.locospanish.ai.voice.LiveAudio
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.*
import okio.ByteString
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeminiIntegrationTest {
    private class FakeAudio : LiveAudio {
        var started=false; var closed=false; var muted=false; var interrupts=0; var packets=0
        var input: ((ByteArray)->Unit)? = null
        override fun start(onInput:(ByteArray)->Unit,onError:()->Unit,onPlaying:(Boolean)->Unit) { started=true; input=onInput }
        override fun enqueue(bytes:ByteArray):Boolean { packets++; return true }
        override fun interrupt() { interrupts++ }
        override fun mute(value:Boolean) { muted=value }
        override fun close() { closed=true }
    }
    private class FakeSocket : WebSocket, WebSocket.Factory {
        lateinit var listener:WebSocketListener
        lateinit var req:Request
        val sent=mutableListOf<String>()
        var cancelled=false; var connections=0
        override fun newWebSocket(request:Request,listener:WebSocketListener):WebSocket { this.req=request; this.listener=listener; connections++; return this }
        override fun request()=req
        override fun queueSize()=0L
        override fun send(text:String):Boolean { synchronized(sent) { sent.add(text) }; return !cancelled }
        override fun send(bytes:ByteString)=true
        override fun close(code:Int,reason:String?)=true
        override fun cancel(){ cancelled=true }
        fun open(){ listener.onOpen(this,Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(101).message("test").build()) }
        fun event(json:String){ listener.onMessage(this,json) }
    }
    private suspend fun runClient(block:suspend (GeminiLiveClient,FakeSocket,FakeAudio)->Unit) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        val socket=FakeSocket(); val audio=FakeAudio()
        val client=GeminiLiveClient(context,scope,http=socket,audioFactory={audio})
        try { withContext(Dispatchers.Main) { client.start(Settings(),"FAKE_TEST_KEY","daily",""); block(client,socket,audio) } }
        finally { withContext(Dispatchers.Main) { client.stop() }; scope.cancel() }
    }
    @Test fun microphoneStartsOnlyAfterSetupAndMuteStopsSending()=runBlocking {
        runClient { client,socket,audio ->
            assertFalse(audio.started); socket.open(); delay(50)
            assertTrue(socket.sent.single().contains("\"setup\""))
            assertEquals("FAKE_TEST_KEY",socket.req.header("x-goog-api-key")); assertFalse(socket.req.url.toString().contains("FAKE_TEST_KEY"))
            socket.event("{\"setupComplete\":{}}")
            withTimeout(2000){ client.state.first { it.connected } }
            assertTrue(audio.started)
            audio.input!!(ByteArray(32)); val before=socket.sent.size
            client.mute(); audio.input!!(ByteArray(32))
            assertTrue(audio.muted); assertEquals(before+1,socket.sent.size)
            assertTrue(socket.sent.last().contains("audioStreamEnd"))
        }
    }
    @Test fun quotaClosesAudioAndDoesNotRetry()=runBlocking {
        runClient { client,socket,audio ->
            socket.open(); socket.event("{\"setupComplete\":{}}")
            socket.event("{\"error\":{\"code\":429,\"message\":\"RESOURCE_EXHAUSTED\"}}")
            withTimeout(2000){client.state.first { it.phase==VoicePhase.ERROR }}
            assertTrue(audio.closed); assertTrue(socket.cancelled); assertTrue(client.state.value.message.contains("limit"))
            delay(250); assertEquals(1,socket.connections)
        }
    }
    @Test fun lateEventsAfterStopCannotReopenMicrophone()=runBlocking {
        runClient { client,socket,audio ->
            socket.open(); client.stop(); socket.event("{\"setupComplete\":{}}")
            delay(100); assertFalse(audio.started); assertFalse(client.state.value.connected)
        }
    }
    @Test fun interruptedAudioFlushesPlaybackAndKeepsTranscript()=runBlocking {
        runClient { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}")
            socket.event("{\"serverContent\":{\"outputTranscription\":{\"text\":\"Hola\"},\"modelTurn\":{\"parts\":[{\"inlineData\":{\"mimeType\":\"audio/pcm;rate=24000\",\"data\":\"AAAAAA==\"}}]}}}")
            socket.event("{\"serverContent\":{\"interrupted\":true}}")
            delay(100); assertEquals(1,audio.packets); assertTrue(audio.interrupts>=2)
            assertTrue(client.state.value.transcript.single().interrupted)
        }
    }
    @Test fun toolsDeduplicateAndCancellationUndoesCompletion()=runBlocking {
        runClient { client,socket,_ ->
            socket.event("{\"setupComplete\":{}}")
            val call="{\"toolCall\":{\"functionCalls\":[{\"id\":\"word1\",\"name\":\"record_learning\",\"args\":{\"kind\":\"word\",\"term\":\"hola\",\"evidence\":\"hola\"}},{\"id\":\"goal1\",\"name\":\"complete_scenario\",\"args\":{\"evidence\":\"Uczeń zamówił kawę\"}}]}}"
            socket.event(call); socket.event(call); delay(100)
            assertEquals(1,client.state.value.learning.size); assertTrue(client.state.value.completed)
            socket.event("{\"toolCallCancellation\":{\"ids\":[\"goal1\",\"word1\"]}}")
            delay(50); assertFalse(client.state.value.completed); assertTrue(client.state.value.learning.isEmpty())
        }
    }
    @Test fun keyIsEncryptedAndCanBeRemoved() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val store=AccessTokenStore(context)
        // Test APK is installed only on the test emulator, never on the user's phone.
        try { store.save("FAKE_TEST_KEY"); assertEquals("FAKE_TEST_KEY",AccessTokenStore(context).read())
            assertFalse(context.getSharedPreferences("gemini_access",0).getString("value","")!!.contains("FAKE_TEST_KEY"))
        } finally { store.save("") }
        assertEquals("",store.read())
    }
}
