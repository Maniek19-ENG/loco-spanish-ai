package com.locospanish.ai
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.locospanish.ai.data.*
import com.locospanish.ai.model.*
import com.locospanish.ai.ui.LocoViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
@RunWith(AndroidJUnit4::class)
class StartupTest {
 @Test fun savedHistoryColdStartAndRealStartButtonRemainResponsive():Unit=runBlocking {
  val ins=InstrumentationRegistry.getInstrumentation();val context=ins.targetContext
  val store=context.locoStore;val prefs=store.data.first();val repo=LocalRepository(store)
  val access=AccessTokenStore(context);val oldKey=access.read()
  val libraryKey=stringPreferencesKey("library_v1");val settingsKey=stringPreferencesKey("settings_v1")
  fun onMain(block:()->Unit) { val latch=CountDownLatch(1);var failure:Throwable?=null;Handler(Looper.getMainLooper()).post { try { block() } catch(t:Throwable) { failure=t } finally { latch.countDown() } };check(latch.await(2,TimeUnit.SECONDS)) { "Main thread blocked" };failure?.let { throw it } }
  var activity:MainActivity?=null
  try {
   access.save("FAKE_TEST_KEY")
   val terms=com.locospanish.ai.tutor.TopicLessons.plans.keys.flatMap { com.locospanish.ai.tutor.TopicLessons.vocabulary(it) }.distinctBy { it.es }.take(80)
   val history=Library((0 until 25).map { i -> Session("saved-$i",1000L+i,60,"hotel",Level.A1,transcript=listOf(Transcript("u-$i","user","reserva")),learning=terms.mapIndexed { j,w -> LearningEvent("$i-$j","known_word",w.es,"",w.pl,w.es) }) })
   val json=com.google.gson.Gson().toJson(history)
   store.edit { it[libraryKey]=json;it[settingsKey]=repo.encodeSettings(Settings(onboardingDone=true,freeTierConfirmed=true)) }
   context.packageManager // Do not request microphone through a permission dialog in this regression.
   ins.uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.RECORD_AUDIO").use { fd -> java.io.FileInputStream(fd.fileDescriptor).use { it.readBytes() } }
   ActivityScenario.launch(MainActivity::class.java).use { scenario ->
    scenario.onActivity { activity=it }
    fun find(node:AccessibilityNodeInfo?):AccessibilityNodeInfo? {
     if(node==null) return null
     if(node.text?.toString()=="ROZPOCZNIJ ROZMOWĘ") return node
     for(i in 0 until node.childCount) find(node.getChild(i))?.let { return it }
     return null
    }
    var button:AccessibilityNodeInfo?=null
    fun scroll(node:AccessibilityNodeInfo?):Boolean {
     if(node==null) return false
     if(node.isScrollable && node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true
     for(i in 0 until node.childCount) if(scroll(node.getChild(i))) return true
     return false
    }
    withTimeout(30000) { while(button==null || button?.isVisibleToUser!=true) { val root=ins.uiAutomation.rootInActiveWindow;button=find(root);if(button==null || button?.isVisibleToUser!=true) scroll(root);delay(250) } }
    var clickable=button
    while(clickable!=null && !clickable!!.isClickable) clickable=clickable!!.parent
    assertTrue("Actual start button must accept click",clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true)
    withTimeout(5000) { var started=false;while(!started) { onMain { started=ViewModelProvider(activity!!)[LocoViewModel::class.java].active.value };delay(50) } }
    repeat(5) { delay(100);onMain { assertFalse(activity!!.isFinishing) } }
    onMain { ViewModelProvider(activity!!)[LocoViewModel::class.java].finish() }
    delay(200)
   }
   val restored=repo.library.first()
   assertTrue(restored.sessions.any { it.id=="saved-0" })
  } finally {
   access.save(oldKey)
   store.edit { p -> for(key in listOf(libraryKey,settingsKey)) { val old=prefs[key];if(old==null) p.remove(key) else p[key]=old } }
  }
 }
}
