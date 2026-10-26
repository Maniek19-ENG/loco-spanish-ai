package com.locospanish.ai

import com.locospanish.ai.voice.*
import com.locospanish.ai.realtime.*
import com.locospanish.ai.model.*
import com.locospanish.ai.tutor.*
import org.junit.Assert.*
import org.junit.Test

class PronunciationAndComedyTest {
 @Test fun spanishVoiceSelectionNeverFallsBackToPolishOrAnotherAccent() {
  val voices=listOf(SystemVoiceChoice("polish","pl","PL",false,true,500,100),SystemVoiceChoice("mexican","es","MX",false,true,500,100),SystemVoiceChoice("uninstalled","es","ES",false,false,500,100),SystemVoiceChoice("spain-network","es","ES",true,true,500,100),SystemVoiceChoice("spain-local","es","ES",false,true,300,200))
  assertEquals("spain-local",SystemVoicePolicy.choose(voices,"es","ES"))
  assertEquals("spain-network",SystemVoicePolicy.choose(voices.dropLast(1),"es","ES"))
  assertNull(SystemVoicePolicy.choose(voices.take(3),"es","ES"))
 }
 @Test fun correctedAndUncertainTrialsAlsoRejectUnrelatedJokes() {
  val word=LessonWord("la reserva","rezerwacja")
  for(verdict in listOf("CORRECT","UNCERTAIN","ERROR")) {
   assertEquals("",SpokenRoastPolicy.contextualPrimary(Settings(),"Kurwa, twój router właśnie odpalił kabaret.",verdict,emptyList(),"reserva",word))
   assertTrue(SpokenRoastPolicy.contextualPrimary(Settings(),"Rezerwacja, kurwa? Recepcjonista już stawia szlaban, bo twój pokój zaraz odjedzie z placu.",verdict,emptyList(),"reserva",word).isNotBlank())
  }
 }
 @Test fun exerciseQuestionCannotMakeAnUnrelatedRiposteLookRelevant() {
  val word=LessonWord("la reserva","rezerwacja")
  val wrong="Kurwa, twój router właśnie odpalił kabaret. Jak powiesz po hiszpańsku rezerwacja?"
  assertTrue(SpokenRoastPolicy.replyIssue(Settings(),wrong,"","ERROR",emptyList(),"gato",word).isNotBlank())
  val right="Kurwa, z takim gato zarezerwujesz kotu recepcję zamiast pokoju."
  assertEquals("",SpokenRoastPolicy.replyIssue(Settings(),right+" Jak powiesz po hiszpańsku rezerwacja?",right,"ERROR",emptyList(),"gato",word))
  assertTrue(SpokenRoastPolicy.replyIssue(Settings(),"Jak powiesz po hiszpańsku rezerwacja?","","ERROR",emptyList(),"gato",word).isNotBlank())
 }
 @Test fun performanceKeepsMockeryOutOfTheSpanishReference() {
  val prompt=GeminiProtocol.prompt(Settings().withFilmStyle(true),"hotel","")
  assertFalse(prompt.contains("śmiech odtwarza aplikacja"))
  assertFalse(prompt.contains("Śmiech zapewnia aplikacja"))
  assertTrue(prompt.contains("aplikacja odtwarza pełny szyderczy śmiech"))
  val error=ReplyDelivery.performance(Settings(),"ERROR")
  assertTrue(error.contains("W GŁOSIE"));assertTrue(error.contains("PRZED twoją odpowiedzią"));assertTrue(error.contains("poprawkę po hiszpańsku wymawiaj prawidłowo"))
  assertFalse(ReplyDelivery.performance(Settings(),"CORRECT").contains("reakcję na tę konkretną pomyłkę"))
  assertFalse(ReplyDelivery.performance(Settings(personality=Settings().personality.copy(roastMode=false)),"ERROR").contains("szyderczą"))
 }
}
