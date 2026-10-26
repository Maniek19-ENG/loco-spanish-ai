package com.locospanish.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.locospanish.ai.voice.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpanishReferenceSpeechTest {
 private class Voice(private val fail:Boolean=false):SupplementSpeaker {
  val words=mutableListOf<String>();var closed=false
  override suspend fun synthesize(text:String):ByteArray { words.add(text);if(fail) error("Unavailable voice");return byteArrayOf(0,20,0,30) }
  override fun close() { closed=true }
 }
 @Test fun nativeSpanishVoiceGetsExactAccentsAndReferenceIsReused()=runBlocking {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val native=Voice();val fallback=Voice()
  val speaker=SpanishReferenceSpeech(context,"FAKE_TEST_KEY","Kore",native,fallback)
  try {
   for(word in listOf("el café","la habitación","el año","papá","papa")) { speaker.prepare(word);assertEquals(4,speaker.synthesize(word).size) }
   assertEquals(listOf("el café","la habitación","el año","papá","papa"),native.words)
   assertTrue(fallback.words.isEmpty())
  } finally { speaker.close() }
  assertTrue(native.closed);assertTrue(fallback.closed)
 }
 @Test fun missingNativeVoiceFallsBackWithoutRetryingItForEveryWord()=runBlocking {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val native=Voice(true);val fallback=Voice()
  val speaker=SpanishReferenceSpeech(context,"FAKE_TEST_KEY","Kore",native,fallback)
  try {
   speaker.synthesize("el café");speaker.synthesize("la habitación");speaker.synthesize("el café")
   assertEquals(1,native.words.size)
   assertEquals(listOf("el café","la habitación"),fallback.words)
  } finally { speaker.close() }
 }
 @Test fun cancellingPreparationDoesNotStartAnotherVoice()=runBlocking {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val started=CompletableDeferred<Unit>();val fallback=Voice()
  val native=object:SupplementSpeaker {
   override suspend fun synthesize(text:String):ByteArray { started.complete(Unit);awaitCancellation() }
   override fun close() {}
  }
  val speaker=SpanishReferenceSpeech(context,"FAKE_TEST_KEY","Kore",native,fallback)
  try {
   val job=launch { speaker.synthesize("el café") };started.await();job.cancelAndJoin()
   assertTrue(fallback.words.isEmpty())
  } finally { speaker.close() }
 }
}
