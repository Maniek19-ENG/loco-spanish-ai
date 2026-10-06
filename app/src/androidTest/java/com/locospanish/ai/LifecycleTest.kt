package com.locospanish.ai

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.locospanish.ai.ui.LocoViewModel
import com.locospanish.ai.data.LocalRepository
import com.locospanish.ai.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LifecycleTest {
    @Test fun activityRecreatesWithoutMicrophoneOrCredentials() {
        var original: LocoViewModel? = null
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val model = ViewModelProvider(activity)[LocoViewModel::class.java]
                original = model; model.selected.value = "maintenance"
            }
            scenario.recreate()
            scenario.onActivity { check(!it.isFinishing); assertSame(original, ViewModelProvider(it)[LocoViewModel::class.java]); assertEquals("maintenance", original!!.selected.value) }
        }
    }
    @Test fun androidDataStorePersistsSettingsAndDeletesOnlyHistory(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "instrumentation.preferences_pb")
        file.delete()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repo = LocalRepository(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
        val settings = Settings(level = Level.B2, voice = "Kore")
        repo.saveSettings(settings)
        repo.upsert(Session("test", 1000, 65, "daily", Level.A1))
        assertEquals(settings, repo.settings.first()); assertEquals(65L, repo.library.first().sessions.single().durationSeconds)
        repo.deleteHistory(); assertTrue(repo.library.first().sessions.isEmpty()); assertEquals(settings, repo.settings.first())
        scope.coroutineContext[Job]!!.cancelAndJoin(); file.delete()
    }
}

