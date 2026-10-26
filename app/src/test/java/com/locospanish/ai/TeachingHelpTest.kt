package com.locospanish.ai
import com.locospanish.ai.tutor.*
import org.junit.Assert.*
import org.junit.Test
class TeachingHelpTest {
 private val word=LessonWord("la llave","klucz")
 @Test fun deicticPronunciationRequestsSelectCurrentWordButSpecificOtherWordsDoNot() {
  for(text in listOf("Jak to powiedzieć?","Jak mam to powiedzieć?","Jak się to mówi?","Jak wymówić?","Jak to wymówić?","Nie wiem","Nie umiem","Powtórz to","Jak powiedzieć klucz?")) {
   assertTrue(text,LearnerIntent.currentWordReference(text,word))
  }
  for(text in listOf("Jak powiedzieć kawa?","Jak powiedzieć chleb?","Dlaczego reserva ma taki rodzajnik?","llave"))
   assertFalse(text,LearnerIntent.currentWordReference(text,word))
 }
 @Test fun notKnowingIsARequestForTeachingRatherThanAnIncorrectAttempt() {
  for(text in listOf("Nie wiem","Nie umiem","Nie rozumiem","Jak to powiedzieć?"))
   assertTrue(text,LearnerIntent.questionInsteadOfTrial(text,word))
 }
 @Test fun conversationalRejectionsCannotOverrideVerifiedCorrectAnswer() {
  for(text in listOf("Nie, to nie to!","To nie ta odpowiedź.","Nie tym razem.","Nie trafiłeś.","Spróbuj jeszcze raz.","Powtórz to jeszcze raz."))
   assertTrue(text,SpokenTeachingPolicy.rejectsCorrectAnswer(text))
  assertFalse(SpokenTeachingPolicy.rejectsCorrectAnswer("To nie było źle. Dobra odpowiedź."))
 }
 @Test fun laughingIsNotAnAnswerAndFalseRejectionMustBeBlocked() {
  assertTrue(SpokenTeachingPolicy.onlyLaughter("Ha ha, kurwa!"))
  assertFalse(SpokenTeachingPolicy.onlyLaughter("Ha ha, kurwa, recepcjonista przeniósł cię do szafy!"))
  assertFalse(SpokenTeachingPolicy.answersCurrentWord("Ha ha, kurwa, recepcjonista kupił popcorn!",word))
  assertTrue(SpokenTeachingPolicy.answersCurrentWord("Po hiszpańsku la llave. To znaczy klucz.",word))
  for(text in listOf("Źle, powiedziałeś to źle.","To było źle.","Nie tak się wymawia.","Niepoprawna odpowiedź."))
   assertTrue(text,SpokenTeachingPolicy.rejectsCorrectAnswer(text))
  assertFalse(SpokenTeachingPolicy.rejectsCorrectAnswer("Poprawnie. Kurwa, recepcja może wrócić do pracy."))
 }
}
