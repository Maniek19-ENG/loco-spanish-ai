package com.locospanish.ai
import com.locospanish.ai.tutor.*
import com.locospanish.ai.realtime.*
import com.locospanish.ai.model.*
import org.junit.Assert.*
import org.junit.Test
class QuestionAndRoastTest {
 @Test fun maximumRoastRejectsFormalPraiseWithAnAppendedSwear() {
  for(text in listOf("Świetnie, kurwa, bardzo dobrze ci poszło.","Poprawna odpowiedź, kurwa. Kontynuujmy ćwiczenie."))
   assertEquals("",SpokenRoastPolicy.freshPrimary(Settings(),text,"CORRECT",emptyList()))
  val joke="Kurwa, recepcjonista usłyszał twój klucz i właśnie zmienił hotel w jebany escape room."
  assertEquals(joke,SpokenRoastPolicy.freshPrimary(Settings(),joke,"ERROR",emptyList()))
 }
 @Test fun questionAlwaysAsksForSpanishAnswer() {
  for(word in listOf(LessonWord("la llave","klucz"),LessonWord("el café","kawa"))) {
   assertEquals("Jak powiesz po hiszpańsku „${word.pl}”?",LessonTurnPolicy.question(word))
   assertTrue(LessonTurnPolicy.instruction(word,"").contains(LessonTurnPolicy.question(word)))
  }
 }
 @Test fun repeatedJokeIsRejectedDespitePunctuationAndChangedSwear() {
  val old="Twój mózg odpalił tryb samolotowy, kurwa."
  for(next in listOf(old,"TWÓJ MÓZG ODPALIŁ TRYB SAMOLOTOWY!","Twój mózg właśnie odpalił tryb samolotowy znowu, jebany."))
   assertEquals("",SpokenRoastPolicy.freshPrimary(Settings(),next,"ERROR",listOf(old)))
 }
 @Test fun differentContextualPunchlineSurvivesAndSettingsApply() {
  val new="Kurwa, z taką rezerwacją recepcjonista wystawi rachunek twojej walizce."
  val old="Twój mózg odpalił tryb samolotowy, kurwa."
  assertEquals(new,SpokenRoastPolicy.freshPrimary(Settings(),new,"CORRECT",listOf(old)))
  assertEquals("",SpokenRoastPolicy.freshPrimary(Settings(personality=Settings().personality.copy(roastMode=false)),new,"CORRECT",emptyList()))
  assertEquals("",SpokenRoastPolicy.freshPrimary(Settings(),"Twoja wymowa jest fatalna, kurwa.","CORRECT",emptyList()))
 }
 @Test fun reusedMotifAndForcedSwearPrefixAreRejected() {
  assertEquals("",SpokenRoastPolicy.freshPrimary(Settings(),"Kurwa, twój mózg domaga się urlopu.","ERROR",listOf("Twój mózg włączył autopilota, kurwa.")))
  assertEquals("",SpokenRoastPolicy.freshPrimary(Settings(),"Walizka zaraz zamówi taksówkę.","CORRECT",emptyList()))
  assertNotEquals(SpokenRoastPolicy.creativeBrief(0,emptyList()),SpokenRoastPolicy.creativeBrief(1,emptyList()))
 }
 @Test fun creativityUsesContextWithoutCannedPunchline() {
  val text=SpokenRoastPolicy.freshInstruction(Settings(),true)
  assertTrue(text.contains("sparodiuj"));assertTrue(text.contains("KONKRETNEJ wpadki"));assertTrue(text.contains("odrzucona jako powtórka"));assertTrue(text.contains("bieżącej wypowiedzi"));assertTrue(text.contains("tej samej odpowiedzi"))
 }
}
