package com.locospanish.ai
import com.locospanish.ai.realtime.*
import com.locospanish.ai.model.*
import com.locospanish.ai.tutor.*
import org.junit.Assert.*
import org.junit.Test
class ReplyDeliveryTest {
 @Test fun ripostePrecedesTheCorrectionOnlyForAConfirmedMistake() {
  val joke="Kurwa, recepcja wynajmie ci półkę na ręczniki zamiast pokoju."
  val error=ReplyDelivery.instruction(Settings(),joke,"ERROR",false,"")
  assertTrue(error.contains(joke));assertTrue(error.contains("DOSŁOWNIE"));assertTrue(error.indexOf(joke)<error.indexOf("POTEM"))
  val correct=ReplyDelivery.instruction(Settings(),joke,"CORRECT",false,"")
  assertFalse(correct.contains(joke));assertTrue(correct.contains("Bez riposty o błędzie"));assertTrue(correct.contains("szyderczo"))
  val help=ReplyDelivery.instruction(Settings(),joke,"UNCERTAIN",true,"")
  assertTrue(help.startsWith("NAJPIERW odpowiedz na rzeczywiste pytanie"))
  assertFalse(help.contains(joke));assertTrue(help.contains("Bez wymuszania powtarzania"))
 }
 @Test fun reviewKeepsRoastWithoutGivingAwayAnswer() {
  val q=ReviewSession();q.start(listOf(LessonWord("llave","klucz")),null)
  val text=ReplyDelivery.instruction(Settings(),"","ERROR",false,q.instruction())
  assertTrue(text.contains("NAJPIERW W AUDIO"));assertTrue(text.contains("Mocne polskie przekleństwa"));assertTrue(text.contains("NIE ZDRADZAJ"));assertFalse(text.contains("llave"))
 }
 @Test fun offAndLightAreRespected() {
  val off=Settings(personality=Settings().personality.copy(roastMode=false))
  assertTrue(ReplyDelivery.instruction(off,"","CORRECT",false,"").startsWith("Bez docinki."))
  assertFalse(ReplyDelivery.instruction(off,"","UNCERTAIN",true,"").contains("POTEM dodaj świeżą"))
  assertTrue(ReplyDelivery.instruction(off,"","UNCERTAIN",true,"").contains("Bez docinki"))
  val light=Settings(personality=Settings().personality.copy(profanity=Profanity.LIGHT))
  assertTrue(ReplyDelivery.instruction(light,"","ERROR",false,"").contains("Tylko lekkie przekleństwa"))
 }
 @Test fun a1EndOfSpeechWaitIsShorterWithoutChangingConversationLevel() {
  fun silence(level:Level)=GeminiProtocol.setup(Settings(level=level),"hotel","").getAsJsonObject("setup").getAsJsonObject("realtimeInputConfig").getAsJsonObject("automaticActivityDetection").get("silenceDurationMs").asInt
  assertEquals(750,silence(Level.A1));assertEquals(850,silence(Level.B2))
 }
 @Test fun memoizationPreservesNumbersAccentsAndTildeWhenReusedConcurrently() {
  val inputs=listOf("treinta y uno","31","la habitación","habitación","ano","año","el café","Café")
  val before=inputs.map { AssessmentPolicy.key(it) to AssessmentPolicy.speech(it) }
  val pool=java.util.concurrent.Executors.newFixedThreadPool(4)
  try { val jobs=(0 until 100).map { pool.submit<Boolean> { inputs.map { AssessmentPolicy.key(it) to AssessmentPolicy.speech(it) }==before } };assertTrue(jobs.all { it.get() }) } finally { pool.shutdownNow() }
  assertEquals("31",AssessmentPolicy.key("treinta y uno"));assertNotEquals(AssessmentPolicy.key("ano"),AssessmentPolicy.key("año"));assertEquals(AssessmentPolicy.key("la habitación"),AssessmentPolicy.key("habitación"))
 }
}
