package com.locospanish.ai

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.locospanish.ai.model.VoicePhase
import com.locospanish.ai.ui.VoiceOrb
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class VoiceOrbTest {
    @Test fun lastUpdate2323IsEmbeddedWithMatchingInstallCodeAndHash() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.cacheDir,"rollback-22-test.apk")
        try {
            com.locospanish.ai.ui.rollbackInput(context,"2.3.23").use { input -> file.outputStream().use { input.copyTo(it) } }
            val info=context.packageManager.getPackageArchiveInfo(file.path,0)!!
            assertEquals(context.packageName,info.packageName);assertEquals("2.3.23",info.versionName)
            assertEquals(56L,info.longVersionCode)
            val hash=java.security.MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
            assertEquals(com.locospanish.ai.ui.ROLLBACK_23_SHA256,hash)
        } finally { file.delete() }
    }
    @Test fun exactLast2324UpdateIsPreservedAndInstallable() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.cacheDir,"rollback-24-test.apk")
        try {
            com.locospanish.ai.ui.rollbackInput(context,"2.3.24").use { input -> file.outputStream().use { input.copyTo(it) } }
            val info=context.packageManager.getPackageArchiveInfo(file.path,0)!!
            assertEquals("2.3.24",info.versionName);assertEquals(56L,info.longVersionCode)
            val hash=java.security.MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
            assertEquals(com.locospanish.ai.ui.ROLLBACK_24_SHA256,hash)
        } finally { file.delete() }
    }
    @Test fun colorsAndMotionRenderOnAndroid() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val current=mutableStateOf(VoicePhase.LISTENING)
        val level=mutableStateOf(0f)
        var displayedActivity:MainActivity?=null
        fun onMain(action:()->Unit) { val done=java.util.concurrent.CountDownLatch(1);android.os.Handler(android.os.Looper.getMainLooper()).post { try { action() } finally { done.countDown() } };check(done.await(10,java.util.concurrent.TimeUnit.SECONDS)) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> displayedActivity=activity; activity.setContent {
                MaterialTheme(colorScheme=darkColorScheme()) {
                    Column(Modifier.fillMaxSize().background(Color(0xFF010708)).padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                        Text("LOCO / LIVE",color=Color(0xFF80FFF2),style=MaterialTheme.typography.titleMedium)
                        VoiceOrb(current.value,audioLevel=level.value)
                        Text(current.value.label,color=Color(0xFFE9FFFC))
                    }
                }
            } }
            fun capture(name:String):Bitmap {
                Thread.sleep(900)
                val bitmap=instrumentation.uiAutomation.takeScreenshot()!!
                File(context.getExternalFilesDir(null),"orb-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
                return bitmap
            }
            fun count(bitmap:Bitmap,channel:Int):Int {
                var n=0
                for(y in 0 until bitmap.height step 2) for(x in 0 until bitmap.width step 2) {
                    val c=bitmap.getPixel(x,y);val r=android.graphics.Color.red(c);val g=android.graphics.Color.green(c);val b=android.graphics.Color.blue(c)
                    if(channel==0 && b>80 && b>r*1.3 && b>g*1.1) n++
                    if(channel==1 && g>80 && g>r*1.3 && g>b*1.1) n++
                    if(channel==2 && r>80 && r>g*1.3 && r>b*1.1) n++
                }
                return n
            }
            val blue=capture("listening")
            assertTrue("Listening must be visibly blue",count(blue,0)>300)
            val moved=capture("moving")
            assertFalse("Mesh must animate",blue.sameAs(moved))
            onMain { level.value=1f }
            val loud=capture("loud")
            fun width(bitmap:Bitmap):Int {
                var left=bitmap.width;var right=0
                for(y in bitmap.height/4 until bitmap.height*4/5) for(x in 0 until bitmap.width) {
                    val c=bitmap.getPixel(x,y);val r=android.graphics.Color.red(c);val g=android.graphics.Color.green(c);val b=android.graphics.Color.blue(c)
                    if(b>70 && b>r*1.5 && b>g*1.1) { left=minOf(left,x);right=maxOf(right,x) }
                }
                return right-left
            }
            assertTrue("Louder PCM must visibly enlarge orb",width(loud)>width(moved)*1.18f)
            onMain { current.value=VoicePhase.SPEAKING }
            val green=capture("speaking")
            assertTrue("Speaking must be visibly green",count(green,1)>300)
            onMain { current.value=VoicePhase.ERROR }
            val red=capture("error")
            assertTrue("Error must be visibly red",count(red,2)>300)
            onMain { displayedActivity!!.setContent {} }
        }
    }
    @Test fun embeddedPreviousVersionIsInstallableAndSharedPrivately() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=File(context.cacheDir,"rollback").apply { mkdirs() }
        val file=File(directory,"LOCO-2.3.8.apk")
        com.locospanish.ai.ui.rollbackInput(context,"2.3.8").use { input -> file.outputStream().use { input.copyTo(it) } }
        val info=context.packageManager.getPackageArchiveInfo(file.path,0)!!
        assertEquals(context.packageName,info.packageName)
        assertEquals("2.3.8",info.versionName)
        assertEquals(56L,info.longVersionCode)
        val uri=androidx.core.content.FileProvider.getUriForFile(context,"${context.packageName}.rollback",file)
        assertEquals("content",uri.scheme)
        context.contentResolver.openInputStream(uri)!!.use { assertTrue(it.read()!=-1) }
    }
}
