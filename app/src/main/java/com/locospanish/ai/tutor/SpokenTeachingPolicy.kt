package com.locospanish.ai.tutor

/** Teaching and factual feedback must survive the comic delivery. */
object SpokenTeachingPolicy {
 fun onlyLaughter(text:String):Boolean {
  val t=TopicLessons.fold(text).replace('ł','l')
  val laughter=Regex("\\b(?:ha|he|hi|ho|(?:ha){2,}|(?:he){2,}|(?:hi){2,}|smiech|chichot)\\b")
  if(!laughter.containsMatchIn(t)) return false
  return laughter.replace(t," ").replace(Regex("\\b(?:kurwa|kurde|cholera|jebany|pierdolony|chuj|no|ale|o|ach)\\b")," ").replace(Regex("[^a-z0-9]+"),"").isBlank()
 }
 fun assertsError(text:String):Boolean {
  val t=TopicLessons.fold(text).replace('ł','l')
  return Regex("(?:^|[.!?]\\s*)(?:nie[,! ]+)?(?:to nie to|to nie ta odpowiedz|nie tym razem|nie trafiles|nie zgadza sie|to nie jest poprawn[ae])(?:[,.!?]|$)").containsMatchIn(t) ||
   Regex("(?:^|[.!?]\\s*)(?:nie[, ]+)?(?:zle|blednie|niepoprawnie)[,.!?]").containsMatchIn(t) ||
   Regex("\\b(?:to (?:bylo|jest) (?:zle|niepoprawne|bledne)|zla odpowiedz|niepoprawna odpowiedz|powiedziales (?:to )?zle|zle (?:powiedziales|wymowiles)|nie tak sie (?:mowi|wymawia)|niepoprawnie (?:to )?(?:wymowiles|powiedziales))\\b").containsMatchIn(t)
 }
 fun rejectsCorrectAnswer(text:String):Boolean = assertsError(text) ||
  Regex("(?:^|[.!?]\\s*)(?:sprobuj jeszcze raz|powtorz (?:to|slowo|odpowiedz)(?: jeszcze raz)?)(?:[,.!?]|$)")
   .containsMatchIn(TopicLessons.fold(text).replace('ł','l'))
 fun answersCurrentWord(text:String,word:LessonWord):Boolean {
  val t=" "+TopicLessons.fold(text).replace('ł','l').replace(Regex("[^a-z0-9 ]")," ").replace(Regex("\\s+")," ").trim()+" "
  val meaning=TopicLessons.fold(word.pl).replace('ł','l')
  return AssessmentPolicy.contains(text,word.es) || (meaning.isNotBlank() && t.contains(" $meaning "))
 }
 fun helpAnswer(word:LessonWord)="Po hiszpańsku: ${word.es}. To znaczy: ${word.pl}."
}
