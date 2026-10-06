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
    @get:Rule val microphone:GrantPermissionRule=GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)
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
