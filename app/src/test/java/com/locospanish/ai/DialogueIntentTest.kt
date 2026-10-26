package com.locospanish.ai

import com.locospanish.ai.model.*
import com.locospanish.ai.realtime.*
import com.locospanish.ai.tutor.*
import org.junit.Assert.*
import org.junit.Test

class DialogueIntentTest {
 private val word=LessonWord("la reserva","rezerwacja")
 @Test fun meaningCanBeUnderstoodWithoutHardcodedQuestionPrefix() {
  val text="Kompletnie zgubiłem sens tego wyrazu"
  assertEquals(DialogueIntent.Kind.HELP,DialogueIntent.read(GeminiProtocol.json("heard_text" to text,"intent" to "HELP"),text,word))
  assertEquals(DialogueIntent.Kind.ANSWER,DialogueIntent.read(GeminiProtocol.json("heard_text" to "inne zdanie","intent" to "HELP"),text,word))
 }
 @Test fun aCorrectWordIsNotTurnedIntoHelpByAModelLabel() {
  assertEquals(DialogueIntent.Kind.ANSWER,DialogueIntent.read(GeminiProtocol.json("heard_text" to "reserva","intent" to "HELP"),"reserva",word))
 }
 @Test fun modelProposalWinsOverFixedQueueButWrongScenarioDoesNot() {
  assertEquals("el balcón",LessonTurnPolicy.next("hotel",LessonWord("el balcón","balkon"),emptyList(),emptyList(),wordsOnly=true,preferScenario=true)?.es)
  assertNotEquals("el plato",LessonTurnPolicy.next("hotel",LessonWord("el plato","danie"),emptyList(),emptyList(),wordsOnly=true,preferScenario=true)?.es)
  for(id in TopicLessons.plans.keys) {
   val first=LessonTurnPolicy.next(id,null,emptyList(),emptyList(),wordsOnly=true,preferScenario=true)!!
   assertTrue(LessonTurnPolicy.singleWord(first.es));assertTrue(TopicLessons.belongsTo(id,first))
  }
 }
 @Test fun nextAnswerAfterGenerationCompleteHasANewOwnerWithoutTurnComplete() {
  val transcript=GeminiTranscript()
  var s=transcript.apply(VoiceState(),GeminiProtocol.json("inputTranscription" to GeminiProtocol.json("text" to "reserva")))
  val first=s.transcript.last().id
  s=transcript.apply(s,GeminiProtocol.json("generationComplete" to true))
  s=transcript.apply(s,GeminiProtocol.json("inputTranscription" to GeminiProtocol.json("text" to "habitación")))
  assertNotEquals(first,s.transcript.last().id)
  assertEquals(listOf("reserva","habitación"),s.transcript.map { it.text })
 }
 @Test fun helpDoesNotAlwaysReplayAudioAndA1DoesNotDemandPhrases() {
  assertFalse(LearnerIntent.explicitReference("nie wiem"))
  assertTrue(LearnerIntent.explicitReference("jak to wymówić?"))
  assertFalse(Level.A1.guidance.contains("2–5"))
  val props=GeminiProtocol.setup(Settings(),"hotel","").getAsJsonObject("setup").getAsJsonArray("tools")[0].asJsonObject.getAsJsonArray("functionDeclarations")[0].asJsonObject.getAsJsonObject("parameters").getAsJsonObject("properties")
  assertTrue(props.has("intent"));assertTrue(props.has("help_answer"));assertTrue(props.has("roast_anchor"))
 }
}
