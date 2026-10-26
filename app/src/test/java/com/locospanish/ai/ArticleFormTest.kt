package com.locospanish.ai
import com.locospanish.ai.tutor.*
import com.locospanish.ai.realtime.*
import com.locospanish.ai.model.Settings
import org.junit.Assert.*
import org.junit.Test

class ArticleFormTest {
 @Test fun knownNounsGainTheirStoredArticleAndVerbsRemainBare() {
  assertEquals("la habitación",TeachingForm.complete(LessonWord("habitación","pokój")).es)
  assertEquals("el precio",TeachingForm.complete(LessonWord("precio","cena")).es)
  assertEquals("la ventana",TeachingForm.complete(LessonWord("ventana","okno"),"la").es)
  assertEquals("hablar",TeachingForm.complete(LessonWord("hablar","mówić")).es)
  assertEquals("una habitación",TeachingForm.complete(LessonWord("una habitación","pokój")).es)
  assertEquals(LessonWord("el minibar","minibar"),TeachingForm.refine(LessonWord("minibar","minibar"),LessonWord("el minibar","barek")))
  assertEquals(LessonWord("minibar","minibar"),TeachingForm.refine(LessonWord("minibar","minibar"),LessonWord("la persiana","roleta")))
 }
 @Test fun unseenArticleCannotBeAnExtraSpokenExercise() {
  assertTrue(LessonSpeechContract.mismatch("Jaki rodzajnik: el czy la?",LessonWord("ventana","okno")))
  assertTrue(LessonSpeechContract.mismatch("El czy la?",LessonWord("una bebida","napój")))
  assertFalse(LessonSpeechContract.mismatch("Jak powiesz po hiszpańsku okno?",LessonWord("la ventana","okno")))
 }
 @Test fun typedGeneratedWordKeepsArticleOnTheTeachingForm() {
  val words=GeminiWordPlanner.decode("""{"words":[{"es":"ventana","pl":"okno","article":"la"}]}""",com.locospanish.ai.model.Level.A1,emptyList())
  assertEquals("la ventana",words.single().es)
 }
 @Test fun aContextualModelJokeDoesNotDisappearWhenItOmitsTheRequiredSwear() {
  val joke=SpokenRoastPolicy.contextualPrimary(Settings(),"Z tym gato recepcjonista zostawił ci kuwetę zamiast klucza.","ERROR",emptyList(),"gato",LessonWord("la reserva","rezerwacja"))
  assertTrue(joke.startsWith("Kurwa"));assertTrue(joke.contains("kuwetę"))
 }
 @Test fun aSwearDoesNotMakeAStockComparisonAStrongRiposte() {
  assertEquals("",SpokenRoastPolicy.freshPrimary(Settings(),"Twoja rezerwacja, kurwa, ma większe wymagania niż apartament prezydenta.","ERROR",emptyList()))
 }
}
