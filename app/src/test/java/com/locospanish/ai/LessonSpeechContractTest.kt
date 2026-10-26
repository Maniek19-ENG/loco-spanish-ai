package com.locospanish.ai

import com.locospanish.ai.tutor.*
import org.junit.Assert.*
import org.junit.Test

class LessonSpeechContractTest {
 private val room=LessonWord("la habitación","pokój")
 @Test fun rejectsDifferentMeaningAndWrongDirection() {
  assertTrue(LessonSpeechContract.mismatch("Jak powiesz po hiszpańsku „klucz”?",room))
  assertTrue(LessonSpeechContract.mismatch("Jak jest po hiszpańsku kawa?",room))
  assertTrue(LessonSpeechContract.mismatch("Jak powiesz po polsku habitación?",room))
  assertTrue(LessonSpeechContract.mismatch("Powtórz: la llave.",room))
  assertTrue(LessonSpeechContract.mismatch("Jak po hiszpańsku powiesz klucz?",room))
  assertTrue(LessonSpeechContract.mismatch("Jak się mówi po hiszpańsku klucz?",room))
  assertTrue(LessonSpeechContract.mismatch("Jak po polsku powiesz habitación?",room))
 }
 @Test fun acceptsAccentsArticlesAndPolishQuestion() {
  for(text in listOf("Jak powiesz po hiszpańsku „pokój”?","Kurwa, recepcja już drży. Jak powiesz po hiszpańsku pokoj?","Powtórz la habitación.","Jak powiesz po hiszpańsku pokój?"))
   assertFalse(text,LessonSpeechContract.mismatch(text,room))
 }
 @Test fun checksEveryQuestionAndLeavesOrdinaryConversationAlone() {
  assertTrue(LessonSpeechContract.mismatch("Jak powiesz po hiszpańsku pokój? A jak powiesz po hiszpańsku klucz?",room))
  assertFalse(LessonSpeechContract.mismatch("Jak będzie wyglądać twoja podróż?",room))
  assertFalse(LessonSpeechContract.mismatch("Kurwa, recepcjonista już zamawia popcorn.",room))
 }
 @Test fun canonicalContractUsesExactlyTheDisplayedTarget() {
  val text=LessonSpeechContract.instruction(room)
  assertTrue(text.contains("la habitación — pokój"))
  assertTrue(text.contains(LessonTurnPolicy.question(room)))
 }
}
