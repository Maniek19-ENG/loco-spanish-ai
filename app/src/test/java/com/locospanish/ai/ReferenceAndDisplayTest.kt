package com.locospanish.ai
import com.locospanish.ai.model.*
import com.locospanish.ai.voice.*
import com.locospanish.ai.tutor.*
import org.junit.Assert.*
import org.junit.Test
class ReferenceAndDisplayTest {
 @Test fun referenceRetainsSpanishDistinctiveCharacters() {
  assertTrue(SpanishReferencePolicy.matches("la habitación", "La habitación."))
  assertTrue(SpanishReferencePolicy.matches("¿Dónde está?", "Dónde está"))
  assertFalse(SpanishReferencePolicy.matches("año", "ano"))
  assertFalse(SpanishReferencePolicy.matches("papá", "papa"))
  assertFalse(SpanishReferencePolicy.matches("la llave", "Repite la llave"))
 }
 @Test fun referenceRejectsWrongSampleRateRatherThanChangingPitch() {
  assertTrue(SpanishReferencePolicy.pcmFormat("audio/pcm;rate=24000"))
  assertFalse(SpanishReferencePolicy.pcmFormat("audio/pcm;rate=16000"))
  assertFalse(SpanishReferencePolicy.pcmFormat("audio/mp3"))
 }
 @Test fun streamedTextDoesNotChangeLessonDisplay() {
  val state=VoiceState(currentWord="la llave",currentMeaning="klucz")
  assertEquals(LessonDisplay.from(state),LessonDisplay.from(state.copy(transcript=listOf(Transcript("1","assistant","dużo napisów")),phase=VoicePhase.SPEAKING)))
  assertNotEquals(LessonDisplay.from(state),LessonDisplay.from(state.copy(currentWord="la reserva")))
 }
 @Test fun shortPhrasesAreSupportedWithoutPromotingBeginnerToSentences() {
  val phrase=LessonWord("Necesito una habitación tranquila","Potrzebuję spokojnego pokoju")
  assertEquals(phrase,LessonTurnPolicy.next("hotel",phrase,emptyList(),emptyList(),false,false))
  assertNull(LessonTurnPolicy.next("hotel",phrase,emptyList(),emptyList(),false,true))
 }
 @Test fun pronunciationCacheHasNewIdentityAndKeepsAccents() {
  assertNotEquals(SpanishWordSpeech.cacheKey("Kore","papá"),SpanishWordSpeech.cacheKey("Kore","papa"))
  val old=java.security.MessageDigest.getInstance("SHA-256").digest("Kore|la llave".toByteArray()).joinToString("") { "%02x".format(it) }
  assertNotEquals(old,SpanishWordSpeech.cacheKey("Kore","la llave"))
  val prompt=SpanishWordSpeech.setup("Kore").toString()
  assertTrue(prompt.contains("SOLAMENTE español"));assertTrue(prompt.contains("sílaba tónica"));assertFalse(prompt.contains("functionDeclarations"))
 }
}
