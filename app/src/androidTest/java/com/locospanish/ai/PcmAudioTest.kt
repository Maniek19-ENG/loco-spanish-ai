package com.locospanish.ai

import android.Manifest
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.locospanish.ai.voice.PcmAudio
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class PcmAudioTest {
    @Test fun packetBurstDrainsCompletelyWithoutLosingAudio():Unit=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch(MainActivity::class.java).use {
            val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
            val errors=AtomicInteger(0);val audio=PcmAudio(context,scope)
            try {
                withContext(Dispatchers.Main) {
                    audio.start({},{errors.incrementAndGet()},{})
                    audio.mute(true)
                    repeat(200) { assertTrue("packet $it",audio.enqueue(ByteArray(480))) }
                    assertTrue("complete buffered reply",audio.enqueue(ByteArray(240000)))
                }
                withTimeout(12000) { while(audio.pendingAudioBytes()!=0 && errors.get()==0) delay(20) }
                assertEquals(0,errors.get());assertEquals(0,audio.pendingAudioBytes())
            } finally { withContext(Dispatchers.Main) { audio.close() };scope.cancel() }
        }
    }
    @get:Rule val microphone:GrantPermissionRule=GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)
    @Test fun loudnessMeterTracksActualPlaybackAndClearsOnInterrupt():Unit=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch(MainActivity::class.java).use {
            val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
            val level=java.util.concurrent.atomic.AtomicReference(0f)
            val audio=PcmAudio(context,scope)
            try {
                audio.observeLevel { level.set(it) }
                withContext(Dispatchers.Main) { audio.start({},{},{ });audio.mute(true) }
                val tone=ByteArray(48000) { if(it%2==0) 6000.toByte() else (6000 shr 8).toByte() }
                assertTrue(audio.enqueueReference(tone))
                withTimeout(4000) { while(level.get()<.5f) delay(20) }
                withContext(Dispatchers.Main) { audio.interrupt() }
                assertEquals(0f,level.get(),0f)
                delay(120);assertEquals(0f,level.get(),0f)
            } finally { withContext(Dispatchers.Main) { audio.close() };scope.cancel() }
        }
    }
    @Test fun referencePlaybackIsNotForwardedAsLearnerMicrophone():Unit=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch(MainActivity::class.java).use {
            val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
            val count=AtomicInteger(0);val playing=java.util.concurrent.atomic.AtomicBoolean(false)
            val audio=PcmAudio(context,scope)
            try {
                withContext(Dispatchers.Main){audio.start({count.incrementAndGet()},{}, {playing.set(it)})}
                withTimeout(5000){while(count.get()==0) delay(20)}
                assertTrue(audio.enqueueReference(ByteArray(96000)))
                withTimeout(5000){while(!playing.get()) delay(20)}
                delay(150);val before=count.get();delay(500);assertEquals(before,count.get())
                withTimeout(5000){while(count.get()==before) delay(20)}
            } finally { withContext(Dispatchers.Main){audio.close()};scope.cancel() }
        }
    }
    @Test fun actualAndroidAudioCanStartPlayInterruptAndRestart():Unit=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch(MainActivity::class.java).use {
            repeat(2) {
                val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
                val count=AtomicInteger(0); val errors=AtomicInteger(0)
                val audio=PcmAudio(context,scope)
                try {
                    withContext(Dispatchers.Main) { audio.start({ count.incrementAndGet() },{ errors.incrementAndGet() },{}) }
                    withTimeout(5000){ while(count.get()==0 && errors.get()==0) delay(50) }
                    assertTrue(count.get()>0); assertEquals(0,errors.get())
                    assertTrue(audio.enqueue(ByteArray(4800)))
                    delay(150)
                    withContext(Dispatchers.Main){ audio.interrupt(); audio.mute(true) }
                    delay(150); val mutedCount=count.get(); delay(250)
                    assertEquals(mutedCount,count.get()); assertEquals(0,errors.get())
                } finally { withContext(Dispatchers.Main){ audio.close() }; scope.cancel(); delay(150) }
            }
        }
    }
}
