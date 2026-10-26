package com.locospanish.ai.tutor
object LearnerIntent {
 fun explicitReference(text:String):Boolean {
  val t=TopicLessons.fold(text).replace('ł','l')
  return Regex("powtorz|wolniej|wymow|powiedziec|jak.*mowi|przeczytaj|wymowic|nie slysze").containsMatchIn(t)
 }
 fun question(text:String):Boolean {
  val t=AssessmentPolicy.speech(text.lowercase().replace('ł','l')).replace(Regex("^(?:a |ale |ej |powiedz mi |chce wiedziec |chcialem zapytac )"),"")
  return AssessmentPolicy.isLearningQuestion(text) || text.trim().endsWith("?") || Regex("^(?:dlaczego|czemu|czy|jak|co|kiedy|gdzie|ile|jaki|jaka|jakie|wyjasnij|wytlumacz|mozesz|moglbys|moge|mozna|to znaczy|przetlumacz|nie rozumiem|nie wiem|nie umiem|por que|puedes|me explicas|explica|traduce|como|que|cual|donde|cuanto)(?: |$)").containsMatchIn(t)
 }
 fun questionInsteadOfTrial(text:String,word:LessonWord?)=question(text) && !(word!=null && WordAttemptPolicy.directAnswer(word,text))
 fun currentWordReference(text:String,word:LessonWord?):Boolean {
  if(word==null) return false
  val t=TopicLessons.fold(text).replace('ł','l').replace(Regex("[^a-z0-9 ]")," ").replace(Regex("\\s+")," ").trim()
   .replace(Regex("^(?:a |ale |ej |prosze |powiedz mi )"),"")
  if(Regex("^(?:nie wiem|nie umiem|nie rozumiem|powtorz|wolniej)$").matches(t)) return true
  if(!question(text) && !t.startsWith("powtorz")) return false
  val generic=Regex("^(?:jak (?:to |mam to |sie to )?(?:powiedziec|wymowic)|jak (?:to )?sie (?:to )?mowi|jak (?:jest |to jest )?po hiszpansku|(?:powtorz|przeczytaj|wymow)(?: to| ten wyraz| to slowo| slowo)?)(?: jeszcze raz| poprawnie| po hiszpansku| prosze)*$")
  if(generic.matches(t)) return true
  val target=TopicLessons.fold(word.pl).replace('ł','l')
  return Regex("powiedziec|wymowic|powtorz|przeczytaj|sie mowi|po hiszpansku").containsMatchIn(t) &&
   (AssessmentPolicy.contains(text,word.es) || (target.isNotBlank() && " $t ".contains(" $target ")))
 }
}
