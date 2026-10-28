package com.locospanish.ai

import com.locospanish.ai.realtime.*
import com.locospanish.ai.voice.PcmOutputQueue
import com.locospanish.ai.tutor.*
import com.locospanish.ai.model.Settings
import org.junit.Assert.*
import org.junit.Test

class VoicePipelineTest {
 @Test fun protocolDoesNotDelegateErrorComedyToARemovedLaughClip() {
  val prompt=GeminiProtocol.prompt(Settings(),"hotel","")
  assertFalse(prompt.contains("Śmiech zapewnia aplikacja"))
  assertFalse(prompt.contains("Śmiech przy potwierdzonym ERROR zapewnia aplikacja"))
  val setup=GeminiProtocol.setup(Settings(),"hotel","").getAsJsonObject("setup")
  val roast=setup.getAsJsonArray("tools")[0].asJsonObject.getAsJsonArray("functionDeclarations")[0].asJsonObject.getAsJsonObject("parameters").getAsJsonObject("properties").getAsJsonObject("roast").get("description").asString
  assertTrue(roast.contains("heard_text"));assertTrue(roast.contains("nie wyłączaj charakteru"))
 }
 @Test fun verifiedPrefixCanStartBeforeTheWholeSentenceHasArrived() {
  val script="Kurwa, twoja rezerwacja właśnie otworzyła recepcji kabaret. Jak powiesz po hiszpańsku pokój?"
  assertTrue(SpokenReplyPlan.ready(script,"Kurwa, twoja rezerwacja właśnie"))
  assertTrue(SpokenReplyPlan.ready(script,"Ha ha, kurwa, twoja rezerwacja właśnie"))
  assertFalse(SpokenReplyPlan.ready(script,"To było źle, powiedz"))
  assertFalse(SpokenReplyPlan.ready("","Kurwa, twoja rezerwacja właśnie"))
 }
 @Test fun burstOfSmallPacketsUsesByteBudgetInsteadOfA120PacketLimit() {
  val q=PcmOutputQueue(1_000_000)
  repeat(300) { assertTrue(q.offer(0,ByteArray(2000),false)) }
  assertEquals(600_000,q.pendingBytes)
  assertFalse(q.offer(0,ByteArray(600_000),false))
  val active=q.channel.tryReceive().getOrThrow()
  q.drain();assertEquals(2000,q.pendingBytes)
  q.release(active);assertEquals(0,q.pendingBytes)
  assertTrue(q.offer(1,ByteArray(500_000),true))
  q.drain();q.close();assertFalse(q.offer(1,ByteArray(4),false));assertEquals(0,q.pendingBytes)
 }
 @Test fun errorRiposteMustReferToTheSpokenOrTargetWord() {
  val word=LessonWord("la reserva","rezerwacja")
  assertEquals("",SpokenRoastPolicy.contextualPrimary(Settings(),"Kurwa, twój router właśnie odpalił kabaret.","ERROR",emptyList(),"gato",word))
  val joke="Kurwa, z takim gato zarezerwujesz kotu recepcję zamiast pokoju."
  assertTrue(SpokenRoastPolicy.contextualPrimary(Settings(),joke,"ERROR",emptyList(),"gato",word).startsWith(joke.trimEnd('.')))
 }
 @Test fun scriptKeepsQuestionAndCorrectionAndDoesNotLeakQuizAnswers() {
  val word=LessonWord("la llave","klucz")
  val script=SpokenReplyPlan.script("Kurwa, ten klucz otwiera chyba kabaret.",word,"ERROR","Rodzajnik nie pasuje do rzeczownika.",false,false)
  assertTrue(script.contains("Rodzajnik nie pasuje"));assertTrue(script.endsWith(LessonTurnPolicy.question(word)))
  assertFalse(script.contains("la llave"))
  assertEquals("",SpokenReplyPlan.script("żart",word,"CORRECT","",true,false))
 }
}
