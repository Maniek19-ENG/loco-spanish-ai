package com.locospanish.ai.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.locospanish.ai.model.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.locospanish.ai.realtime.GeminiProtocol
import com.locospanish.ai.personality.PersonalityEngine
import com.google.gson.JsonParser

val Context.locoStore by preferencesDataStore("loco")
class LocalRepository(private val store: DataStore<Preferences>) {
    private val gson = Gson()
    private val settingsKey = stringPreferencesKey("settings_v1")
    private val libraryKey = stringPreferencesKey("library_v1")
    val settings = store.data.map { decodeSettings(it[settingsKey]) }.flowOn(Dispatchers.IO)
    val library = store.data.map { p -> decodeLibrary(p[libraryKey]) }.flowOn(Dispatchers.IO)
    fun decodeLibrary(raw:String?):Library {
        val decoded=raw?.let { gson.fromJson(it,Library::class.java) } ?: return Library()
        return Library(decoded.sessions.orEmpty().filterNotNull().map { session ->
            session.copy(id=session.id.orEmpty(),scenarioId=session.scenarioId ?: "daily",level=session.level ?: Level.A1,
                transcript=session.transcript.orEmpty().filterNotNull().map { it.copy(id=it.id.orEmpty(),role=it.role ?: "assistant",text=it.text.orEmpty()) },
                learning=session.learning.orEmpty().filterNotNull().map { it.copy(id=it.id.orEmpty(),kind=it.kind.orEmpty(),term=it.term.orEmpty(),correction=it.correction.orEmpty(),explanation=it.explanation.orEmpty(),evidence=it.evidence.orEmpty()) })
        })
    }
    fun decodeSettings(raw: String?): Settings {
        var value = raw?.let { gson.fromJson(it, Settings::class.java) } ?: Settings()
        val needsStyleUpgrade = raw != null && runCatching {
            val json = JsonParser.parseString(raw).asJsonObject
            !json.has("styleRevision") || json.get("styleRevision").asInt < 1
        }.getOrDefault(true)
        if (needsStyleUpgrade) value = value.copy(personality = PersonalityEngine.preset(Preset.ROAST), styleRevision = 1)
        val needsBehaviorUpgrade = raw != null && runCatching {
            val json = JsonParser.parseString(raw).asJsonObject
            !json.has("behaviorRevision") || !json.has("audioRoute") || json.get("behaviorRevision").asInt < 2
        }.getOrDefault(true)
        if (needsBehaviorUpgrade) value = value.copy(audioRoute = AudioRoutePreference.AUTO, legacyBehavior = false, behaviorRevision = 2)
        val needsRoastUpgrade = raw != null && runCatching {
            val json = JsonParser.parseString(raw).asJsonObject
            !json.has("roastStyleRevision") || json.get("roastStyleRevision").asInt < 6
        }.getOrDefault(true)
        if (needsRoastUpgrade) value = value.copy(
            previousBehavior = value.previousBehavior ?: PreviousBehavior(value.personality, value.autoCorrection, value.legacyBehavior),
            filmBackup = FilmBackup(value.personality, value.previousVersionMode, value.autoCorrection), tiktokStyle = true,
            personality = value.personality.copy(sarcasm=100,cheek=100,roast=100,profanity=Profanity.STRONG,roastMode=true), legacyBehavior = false,
            previousVersionMode = false, roastStyleRevision = 6)
        return if(value.voice in GeminiProtocol.voices) value else value.copy(voice = "Kore")
    }
    fun encodeSettings(settings: Settings): String = gson.toJson(settings)
    suspend fun saveSettings(settings: Settings) = withContext(Dispatchers.IO) { store.edit { it[settingsKey] = encodeSettings(settings) }; Unit }
    suspend fun upsert(session: Session) = withContext(Dispatchers.IO) { store.edit { p ->
        val current = decodeLibrary(p[libraryKey])
        p[libraryKey] = gson.toJson(Library((current.sessions.filterNot { it.id == session.id } + session).sortedBy { it.startedAt }))
    }; Unit }
    suspend fun deleteHistory() { store.edit { it.remove(libraryKey) } }
}

