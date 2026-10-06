package com.locospanish.ai.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.locospanish.ai.data.*
import com.locospanish.ai.model.*
import com.locospanish.ai.realtime.*
import com.locospanish.ai.tutor.PromptBuilder
import com.locospanish.ai.tutor.Variety
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

class LocoViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = LocalRepository(app.locoStore)
    private val access = AccessTokenStore(app)
    private val realtime = GeminiLiveClient(app, viewModelScope)
    val voice = realtime.state
    val settings = MutableStateFlow(Settings())
    val library = MutableStateFlow(Library())
    val loaded = MutableStateFlow(false)
    val active = MutableStateFlow(false)
    val editingPersonality = MutableStateFlow(false)
    val selected = MutableStateFlow("daily")
    val seconds = MutableStateFlow(0L)
    val notice = MutableStateFlow("")
    val sample = MutableStateFlow("")
    val sampling = MutableStateFlow(false)
    private val previewClient = GeminiPreview()
    val hasAccess = MutableStateFlow(access.read().isNotBlank())
    private var current: Session? = null
    private var timer: Job? = null
    private var saveSettingsJob: Job? = null
    private var lastAngle = ""
    private var finishing = false
    private var mutedBeforeEditing = false
    init {
        viewModelScope.launch {
            try { settings.value = repository.settings.first(); library.value = repository.library.first(); loaded.value = true }
            catch (_: Exception) { notice.value = "Nie udało się odczytać lokalnych danych. Uruchom aplikację ponownie." }
        }
        viewModelScope.launch { repository.library.catch { notice.value = "Błąd odczytu historii." }.collect { library.value = it } }
    }
    fun update(value: Settings) {
        if (settings.value.personality != value.personality) sample.value = ""
        settings.value = value; saveSettingsJob?.cancel()
        saveSettingsJob = viewModelScope.launch {
            try { repository.saveSettings(value) } catch (_: Exception) { notice.value = "Nie udało się zapisać ustawień." }
        }
    }
    fun saveAccess(value: String) { runCatching { access.save(value.trim()); hasAccess.value = value.isNotBlank(); update(settings.value.copy(freeTierConfirmed = false)) }
        .onFailure { notice.value = "Nie udało się zapisać klucza w magazynie telefonu." } }
    fun start() {
        if (active.value || finishing || !loaded.value) return
        if (!hasAccess.value) { notice.value = "Najpierw wpisz klucz Gemini w Ustawieniach."; return }
        if (!settings.value.freeTierConfirmed) { notice.value = "Potwierdź w Ustawieniach, że projekt klucza ma Free Tier i wyłączone płatności."; return }
        seconds.value = 0; notice.value = ""; active.value = true
        current = Session(UUID.randomUUID().toString(), System.currentTimeMillis(), scenarioId = selected.value, level = settings.value.level)
        lastAngle = Variety.angle(selected.value, lastAngle)
        realtime.start(settings.value, access.read(), selected.value, memory(), true, lastAngle)
        timer = viewModelScope.launch {
            var last = SystemClock.elapsedRealtime(); var elapsed = 0L
            while (isActive && active.value) {
                delay(1000)
                val now = SystemClock.elapsedRealtime()
                if (voice.value.connected) elapsed += now - last
                last = now; seconds.value = elapsed / 1000
                if (seconds.value > 0 && seconds.value % 5L == 0L) checkpoint()
            }
        }
    }
    private fun memory() = PromptBuilder.memory(Library(library.value.sessions.filter { it.id != current?.id }))
    private fun snapshot(): Session? = current?.copy(durationSeconds = seconds.value, transcript = voice.value.transcript,
        learning = voice.value.learning, completed = voice.value.completed)
    private suspend fun checkpoint() { snapshot()?.takeIf { it.durationSeconds > 0 }?.let {
        try { repository.upsert(it) } catch (_: Exception) { notice.value = "Nie udało się zapisać sesji. Sprawdź wolne miejsce." }
    } }
    fun finish() {
        if (!active.value || finishing) return
        finishing = true; timer?.cancel(); timer = null
        val session = snapshot(); realtime.stop(); active.value = false; editingPersonality.value = false
        viewModelScope.launch {
            try { if (session != null && session.durationSeconds > 0) repository.upsert(session) }
            catch (_: Exception) { notice.value = "Błąd zapisu ostatniej sesji." }
            finally { current = null; finishing = false }
        }
    }
    fun retry() { if (active.value) realtime.start(settings.value, access.read(), selected.value, memory(), false, lastAngle) }
    fun editPersonality() {
        if (!active.value || editingPersonality.value) return
        mutedBeforeEditing = voice.value.muted
        realtime.stop()
        editingPersonality.value = true
    }
    fun resumeAfterPersonality() {
        if (!editingPersonality.value) return
        editingPersonality.value = false
        retry()
        if (active.value && mutedBeforeEditing) realtime.mute()
    }
    fun mute() = realtime.mute()
    fun help(text: String) = realtime.command(text)
    fun preview() {
        if(sampling.value) return
        if(!hasAccess.value || !settings.value.freeTierConfirmed) { notice.value = "Zapisz klucz Gemini i potwierdź Free Tier w Ustawieniach."; return }
        viewModelScope.launch {
            sampling.value = true; sample.value = ""
            val tested = settings.value
            try {
                val result = previewClient.generate(tested,access.read(),selected.value)
                if (tested.personality == settings.value.personality) sample.value = result
            }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { sample.value = e.message ?: "Nie udało się wygenerować próbki." }
            finally { sampling.value = false }
        }
    }
    fun deleteHistory() {
        if (active.value || finishing) return
        viewModelScope.launch { try { repository.deleteHistory(); notice.value = "Lokalna historia została usunięta." }
            catch (_: Exception) { notice.value = "Nie udało się usunąć historii." } }
    }
    override fun onCleared() { realtime.stop() }
}
