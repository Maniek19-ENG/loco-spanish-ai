package com.locospanish.ai
import com.locospanish.ai.tutor.*
import org.junit.Assert.*
import org.junit.Test
class ReviewSessionTest {
 @Test fun queueDoesNotResumeLessonOrRepeatLastWordFirst() {
  val q=ReviewSession()
  val words=listOf(LessonWord("casa","dom"),LessonWord("agua","woda"),LessonWord("pan","chleb"))
  assertTrue(q.start(words+words,"pan"));assertEquals(3,q.total);assertNotEquals("pan",q.current!!.es)
  q.advance(true);q.advance(false);q.advance(true)
  assertTrue(q.active);assertTrue(q.finished);assertNull(q.current);assertEquals(2,q.correct);assertEquals(1,q.skipped)
  q.advance(true);assertEquals(2,q.correct)
  q.stop();assertFalse(q.active)
 }
 @Test fun noEmptyOrUntranslatedQuiz() { assertFalse(ReviewSession().start(listOf(LessonWord("casa","")),null)) }
 @Test fun questionsAreNotWordTrialsButQuestionWordsCanBeAnswers() {
  val word=LessonWord("qué","co")
  assertTrue(LearnerIntent.questionInsteadOfTrial("Jak użyć tego słowa?",word))
  assertTrue(LearnerIntent.questionInsteadOfTrial("Dlaczego tak się mówi",word))
  assertTrue(LearnerIntent.questionInsteadOfTrial("A możesz wyjaśnić ten rodzajnik",word))
  assertTrue(LearnerIntent.questionInsteadOfTrial("Nie rozumiem tego znaczenia",word))
  assertTrue(LearnerIntent.questionInsteadOfTrial("Przetłumacz mi to jeszcze raz",word))
  assertFalse(LearnerIntent.questionInsteadOfTrial("qué",word))
  assertFalse(LearnerIntent.questionInsteadOfTrial("qué?",word))
  assertFalse(LearnerIntent.questionInsteadOfTrial("la casa",LessonWord("la casa","dom")))
 }
}
