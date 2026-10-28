package com.locospanish.ai

import com.locospanish.ai.data.LocalRepository
import com.locospanish.ai.model.*
import com.locospanish.ai.personality.PersonalityEngine
import com.locospanish.ai.realtime.*
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.core.okio.OkioStorage
import kotlinx.coroutines.*
import okio.FileSystem
import okio.Path.Companion.toPath
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class RoastUpgradeTest {
    @Test fun upgradeSnapshotsOldCharacterAndDoesNotResetAgain() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = LocalRepository(PreferenceDataStoreFactory.create(
                storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer,
                    producePath = { Files.createTempFile("roast-upgrade", ".pb").toString().toPath() }), scope = scope))
            val old = Settings(level = Level.B2, voice = "Puck", speed = 1.1f, autoCorrection = false,
                personality = PersonalityEngine.preset(Preset.FRIEND), legacyBehavior = true,
                onboardingDone = true, freeTierConfirmed = true)
            val raw = repo.encodeSettings(old).replace(",\"roastStyleRevision\":6", "")
            val fresh = repo.decodeSettings(raw)
            assertEquals(PreviousBehavior(old.personality, false, true), fresh.previousBehavior)
            assertEquals(old.personality.copy(sarcasm=100,cheek=100,roast=100,profanity=Profanity.STRONG,roastMode=true), fresh.personality)
            assertFalse(fresh.legacyBehavior)
            assertEquals(old.level, fresh.level)
            assertEquals(old.voice, fresh.voice)
            assertTrue(fresh.onboardingDone); assertTrue(fresh.freeTierConfirmed)
            val changed = fresh.copy(personality = PersonalityEngine.preset(Preset.TEACHER), previousVersionMode = true)
            assertEquals(changed, repo.decodeSettings(repo.encodeSettings(changed)))
        } finally { scope.cancel() }
    }
    @Test fun rollbackExactlyMatchesLastVersionAndHasNoNewTurnGuidance() {
        val previous = PreviousBehavior(PersonalityEngine.preset(Preset.FRIEND), false, false)
        val s = Settings(previousVersionMode = true, previousBehavior = previous)
        for (level in Level.entries) {
            val current = s.copy(level = level)
            assertTrue(GeminiProtocol.prompt(current, "cafe", "memory").startsWith(PreviousGeminiProtocol.prompt(current.restoredPrevious(), "cafe", "memory")))
            assertTrue(GeminiProtocol.startCommand(current, "cafe", false).startsWith(PreviousGeminiProtocol.startCommand(current.restoredPrevious(), "cafe", false)))
        }
        assertEquals("", GeminiProtocol.turnGuidance(s, "ERROR"))
        assertEquals("", GeminiProtocol.turnGuidance(s, "CORRECT"))
    }
    @Test fun onlyConfirmedMistakesGetRipostesAndUncertainAudioGetsHelp() {
        val s = Settings()
        assertTrue(GeminiProtocol.turnGuidance(s, "CORRECT").contains("BEZ riposty o błędzie"));assertTrue(GeminiProtocol.turnGuidance(s, "CORRECT").contains("szyderczo"))
        assertTrue(GeminiProtocol.turnGuidance(s, "CORRECT").contains("Nie wymyślaj błędu"))
        assertTrue(GeminiProtocol.turnGuidance(s, "ERROR").contains("poprawkę"))
        assertTrue(GeminiProtocol.turnGuidance(s, "UNCERTAIN").contains("każ powtórzyć"))
        assertTrue(GeminiProtocol.turnGuidance(s, "UNCERTAIN").contains("bez riposty"));assertFalse(GeminiProtocol.turnGuidance(s, "UNCERTAIN").contains("poproś"))
        assertTrue(GeminiProtocol.prompt(s, "daily", "").contains("SZYDERCZY, IRONICZNY TON CAŁY CZAS"))
        assertEquals("", GeminiProtocol.turnGuidance(s.copy(personality = PersonalityEngine.preset(Preset.TEACHER)), "CORRECT"))
        assertTrue(GeminiProtocol.turnGuidance(s.copy(personality = s.personality.copy(profanity = Profanity.OFF)), "ERROR").contains("Bez przekleństw"))
    }
}





