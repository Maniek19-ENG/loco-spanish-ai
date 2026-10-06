package com.locospanish.ai

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.core.okio.OkioStorage
import okio.FileSystem
import okio.Path.Companion.toPath
import com.locospanish.ai.data.LocalRepository
import com.locospanish.ai.model.*
import com.locospanish.ai.personality.PersonalityEngine
import com.locospanish.ai.repository.ProgressCalculator
import com.locospanish.ai.realtime.RealtimeEvents
import com.locospanish.ai.tutor.PromptBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.time.*

class DomainTest {
    @Test fun legacySettingsUpgradeOnceToRequestedMaximumRoast() {
        val repo = LocalRepository(PreferenceDataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer, producePath = { Files.createTempFile("style", ".pb").toString().toPath() }),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)))
        val oldJson = repo.encodeSettings(Settings(personality = PersonalityEngine.preset(Preset.FRIEND))).replace(",\"styleRevision\":1", "")
        val upgraded = repo.decodeSettings(oldJson)
        assertEquals(PersonalityEngine.preset(Preset.ROAST), upgraded.personality)
        val chosen = upgraded.copy(personality = PersonalityEngine.preset(Preset.TEACHER))
        assertEquals(chosen, repo.decodeSettings(repo.encodeSettings(chosen)))
    }
    @Test fun roastOffOverridesIntensity() { assertEquals(0, PersonalityEngine.effectiveRoast(Personality(roast = 100, roastMode = false))) }
    @Test fun presetsAreDistinct() {
        val roast = PersonalityEngine.preset(Preset.ROAST)
        assertTrue(roast.roastMode); assertEquals(Profanity.STRONG, roast.profanity)
        assertFalse(PersonalityEngine.preset(Preset.TEACHER).roastMode)
    }
    @Test fun personalityInputsAreBounded() {
        val result = PersonalityEngine.normalize(Personality(sarcasm = -2, roast = 101, customPrompt = "x".repeat(3000)))
        assertEquals(0, result.sarcasm); assertEquals(100, result.roast); assertEquals(2000, result.customPrompt.length)
    }
    @Test fun languageSupportDecreasesWithLevel() {
        assertEquals(6, Level.entries.size)
        Level.entries.zipWithNext().forEach { (a, b) -> assertTrue(a.polishHelp > b.polishHelp); assertTrue(a.pace <= b.pace) }
        assertFalse(Level.A1.guidance.contains("Najpierw podaj wzór"))
    }
    @Test fun scenariosHaveUniqueIdsAndGoals() { assertEquals(18, Scenarios.all.size); assertEquals(18, Scenarios.all.map { it.id }.toSet().size); assertTrue(Scenarios.all.all { it.goal.isNotBlank() }) }
    @Test fun emptyProgressIsActuallyEmpty() {
        val p = ProgressCalculator.calculate(Library()); assertEquals(0, p.sessions); assertEquals(0L, p.seconds); assertTrue(p.words.isEmpty()); assertEquals(0, p.streak)
    }
    private fun session(id: String, day: String, seconds: Long = 60) = Session(id, LocalDate.parse(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), seconds, "daily", Level.A1)
    @Test fun streakIgnoresDuplicateSessionsAndAllowsYesterday() {
        val lib = Library(listOf(session("1", "2026-09-21"), session("2", "2026-09-22"), session("3", "2026-09-22")))
        val p = ProgressCalculator.calculate(lib, LocalDate.parse("2026-09-23"), ZoneOffset.UTC)
        assertEquals(2, p.streak); assertEquals(180L, p.seconds); assertEquals(0L, p.todaySeconds)
    }
    @Test fun staleStreakIsZero() { assertEquals(0, ProgressCalculator.calculate(Library(listOf(session("1", "2026-09-20"))), LocalDate.parse("2026-09-23"), ZoneOffset.UTC).streak) }
    @Test fun connectedSecondsAndCompletionAreNotInvented() {
        val lib = Library(listOf(session("1", "2026-09-23", 0), session("2", "2026-09-23")))
        val p = ProgressCalculator.calculate(lib, LocalDate.parse("2026-09-23"), ZoneOffset.UTC)
        assertEquals(1, p.sessions); assertTrue(p.completed.isEmpty()); assertEquals(60L, p.todaySeconds)
    }
    @Test fun duplicateToolIdsDoNotInflateProgress() {
        val word = LearningEvent("a", "word", "Hola", "", "", "Hola")
        val lib = Library(listOf(session("1", "2026-09-23").copy(learning = listOf(word, word))))
        assertEquals(listOf("hola"), ProgressCalculator.calculate(lib).words)
    }
    @Test fun memoryIsBoundedAndKeepsRecentDialogueForContinuity() {
        val events = (1..30).map { LearningEvent("$it", "grammar", "term$it", "fixed", "explain", "evidence") }
        val lib = Library(listOf(session("1", "2026-09-23").copy(learning = events, transcript = listOf(Transcript("x", "user", "PRIVATE_TRANSCRIPT")))))
        val memory = PromptBuilder.memory(lib)
        assertTrue(memory.contains("PRIVATE_TRANSCRIPT")); assertTrue(memory.length <= 2400)
        assertTrue(memory.contains("term30")); assertFalse(memory.contains("term1 →"))
    }
    @Test fun playbackOutlivesGeneration() { assertEquals(VoicePhase.SPEAKING, RealtimeEvents.phase(VoicePhase.SPEAKING, "response.done", true)) }
    @Test fun interruptionReturnsToListening() { assertEquals(VoicePhase.LISTENING, RealtimeEvents.phase(VoicePhase.SPEAKING, "input_audio_buffer.speech_started", false)) }
    @Test fun finalTranscriptReplacesDeltasWithoutDuplicate() {
        var result = RealtimeEvents.transcript(emptyList(), "a", "user", "Bus", true)
        result = RealtimeEvents.transcript(result, "a", "user", "co", true)
        result = RealtimeEvents.transcript(result, "a", "user", "Busco trabajo", false)
        assertEquals(1, result.size); assertEquals("Busco trabajo", result.single().text)
    }
    @Test fun settingsAndSessionsSurviveRepositoryReopen() = runBlocking {
        val file = Files.createTempDirectory("loco-test").resolve("settings.preferences_pb").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        // Android FileStorage uses platform file operations. Okio uses actual JVM filesystem operations on Windows.
        fun storage() = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer, producePath = { file.absolutePath.toPath() })
        val store = PreferenceDataStoreFactory.create(storage = storage(), scope = scope)
        val repo = LocalRepository(store)
        val settings = Settings(level = Level.C1, speed = 1.1f, personality = PersonalityEngine.preset(Preset.ROAST).copy(customPrompt = "Żółć ¡hola!"))
        repo.saveSettings(settings); repo.upsert(session("same", "2026-09-23")); repo.upsert(session("same", "2026-09-23", 90))
        scope.coroutineContext[Job]!!.cancelAndJoin()
        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val reopened = LocalRepository(PreferenceDataStoreFactory.create(storage = storage(), scope = scope2))
            assertEquals(settings, reopened.settings.first()); assertEquals(1, reopened.library.first().sessions.size)
            assertEquals(90L, reopened.library.first().sessions.single().durationSeconds)
            reopened.deleteHistory(); assertTrue(reopened.library.first().sessions.isEmpty()); assertEquals(settings, reopened.settings.first())
        } finally { scope2.coroutineContext[Job]!!.cancelAndJoin(); file.delete(); file.parentFile?.delete() }
    }
}
