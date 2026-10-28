package com.locospanish.ai.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.locospanish.ai.data.*
import com.locospanish.ai.model.*
import com.locospanish.ai.realtime.*
import com.locospanish.ai.tutor.PromptBuilder
import com.locospanish.ai.tutor.WordKnowledge
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

@OptIn(FlowPreview::class,ExperimentalCoroutinesApi::class)
class LocoViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = LocalRepository(app.locoStore)
    private val access = AccessTokenStore(app)
    private val realtime = GeminiLiveClient(app, viewModelScope,waitForLessonCard=true)
    val voice = realtime.state
    val audioLevel=realtime.audioLevel
    private val display=LiveDisplay(realtime.state,viewModelScope)
    val lessonDisplay=display.lesson
    val liveUi=display.controls
    val liveTranscript=display.transcript
    val settings = MutableStateFlow(Settings())
    val library = MutableStateFlow(Library())
    val progress=library.mapLatest { value -> withContext(Dispatchers.Default) { com.locospanish.ai.repository.ProgressCalculator.calculate(value) } }
        .catch { notice.value="Nie udało się obliczyć podsumowania postępów." }
        .stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),com.locospanish.ai.repository.ProgressCalculator.calculate(Library()))
    val loaded = MutableStateFlow(false)
    val active = MutableStateFlow(false)
    val editingPersonality = MutableStateFlow(false)
    val selected = MutableStateFlow("daily")
    val seconds = MutableStateFlow(0L)
    val notice = MutableStateFlow("")
    val sample = MutableStateFlow("")
    val sampling = MutableStateFlow(false)
    private val previewClient = GeminiPreview()
    val hasAccess = MutableStateFlow(false)
    private var current: Session? = null
    private var timer: Job? = null
    private var preparing:Job?=null
    private var saveSettingsJob: Job? = null
    private var finishing = false
    private var mutedBeforeEditing = false
    private val sessionSave = Mutex()
    init {
        com.locospanish.ai.StartupDiagnostics.previous(app).takeIf { it.isNotBlank() }?.let { notice.value="Poprzednie zamknięcie aplikacji: $it" }
        viewModelScope.launch {
            try { settings.value = repository.settings.first(); library.value = repository.library.first(); hasAccess.value=withContext(Dispatchers.IO) { access.read().isNotBlank() }; selected.value = library.value.sessions.lastOrNull()?.scenarioId?.takeIf { id -> Scenarios.all.any { it.id == id } } ?: "daily"; loaded.value = true }
            catch (cancelled:CancellationException) { throw cancelled }
            catch (_: Exception) { notice.value = "Nie udało się odczytać części lokalnych danych. Zachowano je w telefonie.";loaded.value=true }
        }
        viewModelScope.launch { repository.library.catch { notice.value = "Błąd odczytu historii." }.collect { library.value = it } }
        viewModelScope.launch {
            voice.map { it.transcript to it.learning }.distinctUntilChanged().debounce(500).collect { checkpoint() }
        }
    }
    fun update(value: Settings) {
        if (settings.value.personality != value.personality) sample.value = ""
        settings.value = value; saveSettingsJob?.cancel()
        saveSettingsJob = viewModelScope.launch {
            try { delay(200); repository.saveSettings(value); if(notice.value == "Nie udało się zapisać ustawień.") notice.value = "" } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { notice.value = "Nie udało się zapisać ustawień." }
        }
    }
    fun changePersonality(transform: (Personality) -> Personality) {
        val latest=settings.value
        update(latest.copy(personality=com.locospanish.ai.personality.PersonalityEngine.normalize(transform(latest.personality))))
    }
    fun setFilmStyle(enabled: Boolean) = update(settings.value.withFilmStyle(enabled))
    fun setRoast(enabled: Boolean) = update(settings.value.withRoast(enabled))
    fun selectPersonality(preset: Preset) {
        val latest=settings.value
        val personality=if(preset == Preset.CUSTOM) latest.personality.copy(preset=preset)
            else com.locospanish.ai.personality.PersonalityEngine.preset(preset)
        val next=latest.copy(personality=personality, filmBackup=if(latest.tiktokStyle) latest.filmBackup?.copy(personality=personality) else latest.filmBackup)
        update(if(latest.tiktokStyle && !latest.previousVersionMode) next.withFilmStyle(true) else next)
    }
    fun setAutoCorrection(enabled: Boolean) = update(settings.value.copy(autoCorrection=enabled))
    fun saveAccess(value: String) { runCatching { access.save(value.trim()); hasAccess.value = value.isNotBlank(); update(settings.value.copy(freeTierConfirmed = false)) }
        .onFailure { notice.value = "Nie udało się zapisać klucza w magazynie telefonu." } }
    fun start() {
        if (active.value || finishing || !loaded.value) return
        if (!hasAccess.value) { notice.value = "Najpierw wpisz klucz Gemini w Ustawieniach."; return }
        if (!settings.value.freeTierConfirmed) { notice.value = "Potwierdź w Ustawieniach, że projekt klucza ma Free Tier i wyłączone płatności."; return }
        seconds.value = 0; notice.value = ""; active.value = true
        current = Session(UUID.randomUUID().toString(), System.currentTimeMillis(), scenarioId = selected.value, level = settings.value.level)
        connectPrepared(true)
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
    private fun snapshot(): Session? = current?.copy(durationSeconds = seconds.value, transcript = voice.value.transcript,
        learning = voice.value.learning, completed = voice.value.completed)
    private suspend fun checkpoint() = sessionSave.withLock {
        if(!active.value || finishing) return@withLock
        snapshot()?.takeIf { it.durationSeconds > 0 || it.transcript.isNotEmpty() || it.learning.isNotEmpty() }?.let {
            try { repository.upsert(it) } catch (_: Exception) { notice.value = "Nie udało się zapisać sesji. Sprawdź wolne miejsce." }
        }
    }
    fun finish() {
        if (!active.value || finishing) return
        finishing = true; preparing?.cancel();preparing=null; timer?.cancel(); timer = null
        val session = snapshot(); realtime.stop(); active.value = false; editingPersonality.value = false
        viewModelScope.launch {
            try { sessionSave.withLock { if (session != null && (session.durationSeconds > 0 || session.transcript.isNotEmpty() || session.learning.isNotEmpty())) repository.upsert(session) } }
            catch (_: Exception) { notice.value = "Błąd zapisu ostatniej sesji." }
            finally { current = null; finishing = false }
        }
    }
    fun wordPresented(word:String,meaning:String) { realtime.wordPresented(word,meaning) }
    fun retry() { if(voice.value.connected && voice.value.taskError.isNotBlank()) realtime.retryTask() else if(active.value && preparing?.isActive!=true) connectPrepared(false) }
    private data class Prepared(val memory:String,val words:List<String>,val review:List<com.locospanish.ai.tutor.LessonWord>,val comments:List<String>)
    private fun connectPrepared(reset:Boolean) {
        preparing?.cancel()
        val chosen=settings.value.forDialog();val scenario=selected.value;val history=library.value
        realtime.preparing()
        preparing=viewModelScope.launch {
            try {
                val data=withContext(Dispatchers.Default) { Prepared(PromptBuilder.memory(history,scenario,chosen.level),WordKnowledge.confirmed(history),WordKnowledge.reviewWords(history),WordKnowledge.recentComments(history)) }
                val key=withContext(Dispatchers.IO) { access.read() }
                if(active.value) realtime.start(chosen,key,scenario,data.memory,reset=reset,knownWords=data.words,reviewWords=data.review,recentComments=data.comments)
            } catch(cancelled:CancellationException) { throw cancelled }
            catch(_:Exception) { realtime.stop();active.value=false;notice.value="Nie udało się przygotować rozmowy. Klucz i historia pozostają zapisane. Spróbuj ponownie." }
        }
    }
    fun editPersonality() {
        if (!active.value || editingPersonality.value) return
        mutedBeforeEditing = voice.value.muted
        preparing?.cancel();preparing=null;realtime.stop()
        editingPersonality.value = true
    }
    fun resumeAfterPersonality() {
        if (!editingPersonality.value) return
        editingPersonality.value = false
        retry()
        if (active.value && mutedBeforeEditing) realtime.mute()
    }
    fun mute() = realtime.mute()
    fun lessonAction(action:com.locospanish.ai.tutor.LessonAction) = realtime.action(action)
    fun help(text: String) = realtime.command(text)
    fun askWhy() = realtime.command("Uczeń pyta: DLACZEGO? Wyjaśnij krótko i konkretnie, dlaczego jego ostatnia odpowiedź była dobra albo zła i jaka jest zasada (rodzajnik, forma, znaczenie). Z sarkazmem i przekleństwem zgodnie z ustawieniami. Nie oceniaj tego pytania jak próby i nie zmieniaj zadania; potem zadaj to samo pytanie co wcześniej.")
    fun preview() {
        if(sampling.value) return
        if(!hasAccess.value || !settings.value.freeTierConfirmed) { notice.value = "Zapisz klucz Gemini i potwierdź Free Tier w Ustawieniach."; return }
        viewModelScope.launch {
            sampling.value = true; sample.value = ""
            val tested = settings.value.forDialog()
            try {
                val result = previewClient.generate(tested,access.read(),selected.value)
                if (tested.personality == settings.value.forDialog().personality) sample.value = result
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



