package com.locospanish.ai
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.locospanish.ai.model.*
import com.locospanish.ai.ui.LiveWordCard
import com.locospanish.ai.ui.VoiceOrb
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
@RunWith(AndroidJUnit4::class)
class WordDisplayTest {
 @Test fun wordCardChangesVisiblyWhileOrbAndTranscriptKeepUpdating() {
  val instrument=InstrumentationRegistry.getInstrumentation()
  val automation=instrument.uiAutomation
  val word=MutableStateFlow(LessonDisplay("la reserva","rezerwacja"))
  val stream=mutableStateOf("")
  var activity:MainActivity?=null
  fun onMain(block:()->Unit) { val latch=CountDownLatch(1);Handler(Looper.getMainLooper()).post { try { block() } finally { latch.countDown() } };check(latch.await(10,TimeUnit.SECONDS)) }
  fun contains(node:AccessibilityNodeInfo?,text:String):Boolean {
   if(node==null) return false
   if(node.text?.toString()==text) return true
   return (0 until node.childCount).any { contains(node.getChild(it),text) }
  }
  ActivityScenario.launch(MainActivity::class.java).use { scenario ->
   scenario.onActivity { a -> activity=a;a.setContent { MaterialTheme { Column(Modifier.background(Color(0xFF031214))) { LiveWordCard(word);VoiceOrb(VoicePhase.SPEAKING,audioLevel=.5f);Text(stream.value) } } } }
   try {
    val readyStart=SystemClock.elapsedRealtime()
    while(!contains(automation.rootInActiveWindow,"la reserva") && SystemClock.elapsedRealtime()-readyStart<15000) SystemClock.sleep(150)
    assertTrue("Początkowy widok testu nie jest gotowy",contains(automation.rootInActiveWindow,"la reserva"))
    for((es,pl) in listOf("la llave" to "klucz","la habitación" to "pokój","el ascensor" to "winda","la recepción" to "recepcja")) {
     val start=SystemClock.elapsedRealtime()
     onMain { word.value=LessonDisplay(es,pl) }
     var visible=false
     while(SystemClock.elapsedRealtime()-start<1500) {
      onMain { stream.value=("tekst rozmowy "+SystemClock.elapsedRealtime()).repeat(12) }
      if(contains(instrument.uiAutomation.rootInActiveWindow,es)) { visible=true;break }
      SystemClock.sleep(30)
     }
     assertTrue("Słowo nie pojawiło się na ekranie: $es",visible)
    }
    val screenshot=instrument.uiAutomation.takeScreenshot()
    screenshot?.let { image -> java.io.File(instrument.targetContext.getExternalFilesDir(null),"word-display-2.3.14.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) } }
   } finally {
    automation.takeScreenshot()?.let { image -> java.io.File(instrument.targetContext.getExternalFilesDir(null),"word-display-last-2.3.14.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) } }
    fun dump(node:AccessibilityNodeInfo?):String { if(node==null) return "NULL";return node.text.toString()+"\n"+(0 until node.childCount).joinToString("\n") { dump(node.getChild(it)) } }
    java.io.File(instrument.targetContext.getExternalFilesDir(null),"word-display-nodes-2.3.14.txt").writeText(dump(automation.rootInActiveWindow))
    onMain { activity!!.setContent {} }
   }
  }
 }
}
