package com.locospanish.ai.tutor

/** A noun's teaching form is shared by the card, native voice and grading target. */
object TeachingForm {
 private val articles=setOf("el","la","los","las","un","una","unos","unas")
 private val catalog by lazy {
  (TopicLessons.plans.keys.flatMap { TopicLessons.vocabulary(it)+LessonReserve.words(it) })
   .filter { it.es.substringBefore(' ').lowercase() in articles }
   .associateBy { AssessmentPolicy.key(it.es) }
 }
 fun complete(word:LessonWord,article:String=""):LessonWord {
  val text=word.es.trim().replace(Regex("\\s+")," ")
  if(!LessonTurnPolicy.singleWord(text) || text.substringBefore(' ').lowercase() in articles) return word.copy(es=text)
  val prefix=article.trim().lowercase().takeIf { it in articles }
  val full=catalog[AssessmentPolicy.key(text)]?.es ?: prefix?.let { "$it $text" } ?: text
  return word.copy(es=full)
 }
 fun refine(current:LessonWord?,proposed:LessonWord):LessonWord? {
  if(current==null || AssessmentPolicy.key(current.es)!=AssessmentPolicy.key(proposed.es)) return current
  if(current.es.substringBefore(' ').lowercase() in articles) return current
  if(proposed.es.substringBefore(' ').lowercase() !in articles) return current
  return proposed.copy(pl=current.pl)
 }
}
