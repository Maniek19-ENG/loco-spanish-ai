package com.locospanish.ai
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.locospanish.ai.data.LocalRepository
import com.locospanish.ai.repository.ProgressCalculator
import com.locospanish.ai.tutor.WordKnowledge
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.*
import org.junit.Test
class StartupDataTest {
 private fun repo()=LocalRepository(object:DataStore<Preferences> {
  override val data=emptyFlow<Preferences>()
  override suspend fun updateData(transform:suspend (Preferences)->Preferences):Preferences=error("unused")
 })
 @Test fun olderSessionsAndNullFieldsDoNotCrashHomeOrPreparation() {
  val lib=repo().decodeLibrary("""{"sessions":[null,{"id":"old","startedAt":1000,"scenarioId":"hotel","level":"A1"},{"id":"second","startedAt":2000,"scenarioId":null,"level":null,"transcript":[null,{"id":"t","role":"user","text":null}],"learning":[null,{"id":"w","kind":"known_word","term":"llave","explanation":"klucz","evidence":"llave","correction":null}]}]}""")
  assertEquals(2,lib.sessions.size)
  assertTrue(lib.sessions.first().learning.isEmpty())
  assertEquals("",lib.sessions.last().transcript.single().text)
  assertEquals(listOf("llave"),WordKnowledge.confirmed(lib))
  assertEquals("klucz",WordKnowledge.reviewWords(lib).single().pl)
  assertEquals(listOf("llave"),ProgressCalculator.calculate(lib).words)
 }
 @Test fun intactHistorySurvivesDecoding() {
  val lib=repo().decodeLibrary("""{"sessions":[{"id":"history","startedAt":1000,"durationSeconds":300,"scenarioId":"hotel","level":"A1","transcript":[{"id":"a","role":"user","text":"reserva"}],"learning":[{"id":"b","kind":"known_word","term":"reserva","correction":"","explanation":"rezerwacja","evidence":"reserva"}]}]}""")
  assertEquals(300L,lib.sessions.single().durationSeconds)
  assertEquals("reserva",lib.sessions.single().transcript.single().text)
  assertEquals("rezerwacja",WordKnowledge.reviewWords(lib).single().pl)
 }
}
