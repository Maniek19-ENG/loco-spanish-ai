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
    val library = store.data.map { p -> p[libraryKey]?.let { gson.fromJson(it, Library::class.java) } ?: Library() }.flowOn(Dispatchers.IO)
    fun decodeSettings(raw: String?): Settings {
        var value = raw?.let { gson.fromJson(it, Settings::class.java) } ?: Settings()
        val needsStyleUpgrade = raw != null && runCatching {
            val json = JsonParser.parseString(raw).asJsonObject
            !json.has("styleRevision") || json.get("styleRevision").asInt < 1
        }.getOrDefault(true)
        if (needsStyleUpgrade) value = value.copy(personality = PersonalityEngine.preset(Preset.ROAST), styleRevision = 1)
        return if(value.voice in GeminiProtocol.voices) value else value.copy(voice = "Kore")
    }
    fun encodeSettings(settings: Settings): String = gson.toJson(settings)
    suspend fun saveSettings(settings: Settings) = withContext(Dispatchers.IO) { store.edit { it[settingsKey] = encodeSettings(settings) }; Unit }
    suspend fun upsert(session: Session) = withContext(Dispatchers.IO) { store.edit { p ->
        val current = p[libraryKey]?.let { gson.fromJson(it, Library::class.java) } ?: Library()
        p[libraryKey] = gson.toJson(Library((current.sessions.filterNot { it.id == session.id } + session).sortedBy { it.startedAt }))
    }; Unit }
    suspend fun deleteHistory() { store.edit { it.remove(libraryKey) } }
}
