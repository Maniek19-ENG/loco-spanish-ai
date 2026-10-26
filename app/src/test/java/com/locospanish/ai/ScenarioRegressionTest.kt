package com.locospanish.ai
import com.locospanish.ai.tutor.*
import com.locospanish.ai.realtime.*
import com.locospanish.ai.model.*
import org.junit.Assert.*
import org.junit.Test
class ScenarioRegressionTest {
 @Test fun everyScenarioKeepsItsOwnWordsAndSkipsConfirmedVocabulary() {
  for(id in TopicLessons.plans.keys) {
   val seen=mutableListOf<String>()
   repeat(8) {
    val next=LessonTurnPolicy.next(id,LessonWord("café","kawa"),seen,seen,wordsOnly=true,preferScenario=true)!!
    assertTrue(TopicLessons.vocabulary(id).contains(next))
    assertFalse(seen.map(AssessmentPolicy::key).contains(AssessmentPolicy.key(next.es)))
    seen.add(next.es)
   }
  }
 }
 @Test fun hotelRejectsFoodAfterItsBankIsExhausted() {
  val all=TopicLessons.vocabulary("hotel").map { it.es }
  assertNull(LessonTurnPolicy.next("hotel",LessonWord("el plato","danie"),all,all,wordsOnly=true,preferScenario=true))
  assertEquals("balcón",LessonTurnPolicy.next("hotel",LessonWord("balcón","balkon"),all,all,wordsOnly=true,preferScenario=true)?.es)
 }
 @Test fun correctRecognizedWordCannotBeRejectedByInventedGrammar() {
  val word=LessonWord("la habitación","pokój")
  for(answer in listOf("habitación","la habitación","una habitación","cuarto","el cuarto","un cuarto")) {
   val grade=WordAttemptPolicy.evaluate(word,answer,Assessment("ERROR",answer,"otra","zmyślona korekta"),true)
   assertEquals("CORRECT",grade.verdict)
   assertTrue(WordAttemptPolicy.canAdvance(word,answer,grade))
  }
  assertEquals("ERROR",WordAttemptPolicy.evaluate(word,"el habitación",Assessment("ERROR","el habitación","la habitación","rodzajnik"),true).verdict)
 }
 @Test fun wordsWithoutStoredArticleDoNotInventAGenderError() {
  val word=LessonWord("cama","łóżko")
  val grade=WordAttemptPolicy.evaluate(word,"la cama",Assessment("CORRECT","la cama"),true)
  assertEquals("CORRECT",grade.verdict)
  assertTrue(WordAttemptPolicy.canAdvance(word,"la cama",grade))
 }
 @Test fun revisedTranscriptionIsNotDuplicated() {
  val reducer=GeminiTranscript()
  var state=reducer.apply(VoiceState(),GeminiProtocol.json("inputTranscription" to GeminiProtocol.json("text" to "habitaci")))
  state=reducer.apply(state,GeminiProtocol.json("inputTranscription" to GeminiProtocol.json("text" to "Habitación", "finished" to true)))
  assertEquals("Habitación",state.transcript.last().text)
 }
 @Test fun interruptedNewAnswerGetsANewId() {
  val reducer=GeminiTranscript()
  val before=reducer.apply(VoiceState(),GeminiProtocol.json("inputTranscription" to GeminiProtocol.json("text" to "gato")))
  val after=reducer.apply(before,GeminiProtocol.json("interrupted" to true,"inputTranscription" to GeminiProtocol.json("text" to "perro")))
  assertEquals(2,after.transcript.count { it.role == "user" })
  assertEquals("perro",after.transcript.last().text)
 }
}
