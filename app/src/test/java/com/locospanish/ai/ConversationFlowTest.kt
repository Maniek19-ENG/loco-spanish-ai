package com.locospanish.ai

import com.locospanish.ai.model.*
import com.locospanish.ai.realtime.*
import com.locospanish.ai.tutor.*
import org.junit.Assert.*
import org.junit.Test

class ConversationFlowTest {
 @Test fun repeatedRecognitionOfTheSameWordIsOneValidTrial() {
  val word=LessonWord("la reserva","rezerwacja")
  for(text in listOf("reserva reserva","la reserva la reserva","reserva, reserva, reserva")) {
   val result=WordAttemptPolicy.evaluate(word,text,Assessment("UNCERTAIN",text))
   assertEquals("CORRECT",result.verdict);assertTrue(WordAttemptPolicy.canAdvance(word,text,result))
  }
  for(text in listOf("gato gato","reserva gato")) {
   assertFalse(WordAttemptPolicy.canAdvance(word,text,WordAttemptPolicy.evaluate(word,text,Assessment("UNCERTAIN",text))))
  }
  val wrongArticle="el habitación el habitación"
  assertEquals("ERROR",WordAttemptPolicy.evaluate(LessonWord("la habitación","pokój"),wrongArticle,Assessment("UNCERTAIN",wrongArticle)).verdict)
 }
 @Test fun laughterIsAVocalCueInsideAnActualErrorRiposte() {
  val joke="Kurwa, z takim gato zarezerwujesz kotu recepcję zamiast pokoju."
  val versions=(0..3).map { SpokenLaughter.add(Settings(),joke,"ERROR",it) }
  assertEquals(4,versions.toSet().size)
  assertTrue(versions.all { it.startsWith(joke) && it.length>joke.length })
  assertEquals(joke,SpokenLaughter.add(Settings(),joke,"CORRECT",0))
  assertEquals("",SpokenLaughter.add(Settings(),"","ERROR",0))
  assertEquals(joke,SpokenLaughter.add(Settings(personality=Settings().personality.copy(roastMode=false)),joke,"ERROR",0))
  assertEquals(joke+" Ha!",SpokenLaughter.add(Settings(),joke+" Ha!","ERROR",0))
 }
 @Test fun laughterDoesNotBlockVerifiedSpeechPrefix() {
  val script="Kurwa, ta rezerwacja otworzyła koci hotel. Heh! Jak powiesz po hiszpańsku pokój?"
  assertTrue(SpokenReplyPlan.ready(script,"Kurwa, ta rezerwacja otworzyła koci hotel. Ha!"))
  assertTrue(ReplyDelivery.performance(Settings(),"ERROR").contains("PRZED twoją odpowiedzią"))
 }
}
