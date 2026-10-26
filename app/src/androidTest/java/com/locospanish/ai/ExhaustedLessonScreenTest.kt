package com.locospanish.ai

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.locospanish.ai.model.*
import com.locospanish.ai.realtime.*
import com.locospanish.ai.ui.*
import com.locospanish.ai.voice.*
import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class ExhaustedLessonScreenTest {
 private class Socket:WebSocket,WebSocket.Factory {
  lateinit var listener:WebSocketListener
  lateinit var req:Request
  val sent=CopyOnWriteArrayList<String>()
  override fun newWebSocket(request:Request,listener:WebSocketListener):WebSocket { req=request;this.listener=listener;return this }
  override fun request()=req
  override fun queueSize()=0L
  override fun send(text:String):Boolean { sent.add(text);return true }
  override fun send(bytes:ByteString)=true
  override fun close(code:Int,reason:String?)=true
  override fun cancel() {}
  fun event(text:String)=listener.onMessage(this,text)
 }
 @Test fun allKnownLessonStillShowsNextTasksWithoutVoiceWordTool() = exercise(false)
 @Test fun plannerFailureStillShowsAndSpeaksNextScenarioWords() = exercise(true)
 private fun exercise(plannerFails:Boolean) {
  val ins=InstrumentationRegistry.getInstrumentation();val ui=ins.uiAutomation
  val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
  val socket=Socket();val spoken=CopyOnWriteArrayList<String>()
  lateinit var client:GeminiLiveClient
  var created=false
  var activity:MainActivity?=null
  fun onMain(block:()->Unit) { val latch=CountDownLatch(1);var error:Throwable?=null;Handler(Looper.getMainLooper()).post { try { block() } catch(t:Throwable) { error=t } finally { latch.countDown() } };check(latch.await(5,TimeUnit.SECONDS));error?.let { throw it } }
  fun find(node:AccessibilityNodeInfo?,text:String):AccessibilityNodeInfo? {
   if(node==null) return null
   if(node.text?.toString()==text && node.isVisibleToUser) return node
   for(i in 0 until node.childCount) find(node.getChild(i),text)?.let { return it }
   return null
  }
  fun waitText(text:String,timeout:Long=2500):AccessibilityNodeInfo {
   val start=SystemClock.elapsedRealtime()
   while(SystemClock.elapsedRealtime()-start<timeout) { find(ui.rootInActiveWindow,text)?.let { return it };SystemClock.sleep(40) }
   val snapshot=client.state.value
   java.io.File(ins.targetContext.getExternalFilesDir(null),"exhausted-failure-2.3.16.txt").writeText("Expected=$text, actual=${snapshot.currentWord}, phase=${snapshot.phase}, message=${snapshot.message}\n"+socket.sent.takeLast(3).joinToString("\n"))
   ui.takeScreenshot()?.let { image -> java.io.File(ins.targetContext.getExternalFilesDir(null),"exhausted-failure-2.3.16.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) } }
   throw AssertionError("Brak widocznego słowa: $text; stan=${snapshot.currentWord}; ${snapshot.message}")
  }
  fun scroll(node:AccessibilityNodeInfo?):Boolean {
   if(node==null) return false
   if(node.isScrollable && node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true
   for(i in 0 until node.childCount) if(scroll(node.getChild(i))) return true
   return false
  }
  try {
   onMain {
    client=GeminiLiveClient(ins.targetContext,scope,http=socket,waitForLessonCard=true,wordCache=null,wordPlanner={_,_,_,_->delay(200);if(plannerFails) error("HTTP 429");listOf(
      com.locospanish.ai.tutor.LessonWord("el minibar","minibar"),com.locospanish.ai.tutor.LessonWord("la persiana","roleta"),com.locospanish.ai.tutor.LessonWord("la bombilla","żarówka"),com.locospanish.ai.tutor.LessonWord("el colchón","materac"))},audioFactory={object:LiveAudio {
     override fun start(onInput:(ByteArray)->Unit,onError:()->Unit,onPlaying:(Boolean)->Unit) {}
     override fun enqueue(bytes:ByteArray)=true
     override fun interrupt() {}
     override fun mute(value:Boolean) {}
     override fun close() {}
    }},wordSpeakerFactory={_,_->object:SupplementSpeaker {
     override suspend fun synthesize(text:String):ByteArray { delay(1800);spoken.add(text);return ByteArray(4) }
     override fun close() {}
    }})
    created=true
    client.start(Settings(),"FAKE_TEST_KEY","hotel","",knownWords=(com.locospanish.ai.tutor.TopicLessons.vocabulary("hotel")+if(plannerFails) emptyList() else com.locospanish.ai.tutor.LessonReserve.words("hotel")).map { it.es })
    socket.listener.onOpen(socket,Response.Builder().request(socket.req).protocol(Protocol.HTTP_1_1).code(101).message("test").build())
    socket.event("{\"setupComplete\":{}}")
   }
   ActivityScenario.launch(MainActivity::class.java).use { scenario ->
    scenario.onActivity { a -> activity=a;a.setContent {
     val display=androidx.compose.runtime.remember { LiveDisplay(client.state,scope) }
     MaterialTheme(colorScheme=darkColorScheme()) {
      Column(Modifier.fillMaxSize().background(Color(0xFF010708)).statusBarsPadding().padding(16.dp)) {
       LiveConversationScreen(0,"Hotel",Settings().personality,display.controls,display.lesson,display.transcript,client.audioLevel,{},{},{},client::action,{},client::wordPresented)
      }
     }
    } }
    try {
     val firstWord=if(plannerFails) "el balcón" else "el minibar"
     val initial=waitText(firstWord,15000);val original=Rect();initial.getBoundsInScreen(original)
     // A real streamed response plus a long transcript makes the full screen scrollable.
     onMain {
      repeat(120) { socket.event(GeminiProtocol.json("serverContent" to GeminiProtocol.json("outputTranscription" to GeminiProtocol.json("text" to "rozmowa numer $it "))).toString()) }
      socket.event("{\"serverContent\":{\"turnComplete\":true}}")
     }
     SystemClock.sleep(600)
     assertTrue("Dolna część rozmowy musi się przewijać",scroll(ui.rootInActiveWindow))
     repeat(3) { SystemClock.sleep(120);scroll(ui.rootInActiveWindow) }
     val after=Rect();waitText(firstWord).getBoundsInScreen(after);assertEquals(original.top,after.top)
     val progression=if(plannerFails) listOf("balcón" to "la manta","manta" to "la sábana","sábana" to "el armario") else listOf("minibar" to "la persiana","persiana" to "la bombilla","bombilla" to "el colchón")
     for((index,pair) in progression.withIndex()) {
      val (answer,expected)=pair
      val start=SystemClock.elapsedRealtime()
      onMain {
       socket.event(GeminiProtocol.json("serverContent" to GeminiProtocol.json("inputTranscription" to GeminiProtocol.json("text" to answer,"finished" to true))).toString())
       socket.event(GeminiProtocol.json("toolCall" to GeminiProtocol.json("functionCalls" to listOf(GeminiProtocol.json("id" to "visible-$index","name" to "assess_user_turn","args" to GeminiProtocol.json("verdict" to "CORRECT","heard_text" to answer,"evidence" to answer,"confidence" to "CERTAIN","words" to emptyList<String>()))))).toString())
       socket.event("{\"serverContent\":{\"turnComplete\":true}}")
      }
      val node=waitText(expected,1200);val bounds=Rect();node.getBoundsInScreen(bounds)
      assertEquals("Zadanie musi pozostać przypięte",original.top,bounds.top)
      assertTrue("Karta nie może czekać na syntezę audio",SystemClock.elapsedRealtime()-start<1500)
      val until=SystemClock.elapsedRealtime()+4000
      while(expected !in spoken && SystemClock.elapsedRealtime()<until) SystemClock.sleep(30)
      assertTrue("Dźwięk i ekran muszą mieć to samo słowo",expected in spoken)
     }
     ui.takeScreenshot()?.let { image -> java.io.File(ins.targetContext.getExternalFilesDir(null),"exhausted-2.3.16.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) } }
    } finally { onMain { activity!!.setContent {} } }
   }
  } finally { onMain { if(created) client.stop() };scope.cancel() }
 }
}
