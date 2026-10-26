package com.locospanish.ai.tutor

enum class LessonAction { REPEAT, SLOWER, EXPLAIN, TRANSLATE, REVIEW, NEXT, RESUME }

/** Owns the quiz independently of the model's suggested next word. */
class ReviewSession {
 private var queue:List<LessonWord> = emptyList()
 var active=false;private set
 var index=0;private set
 var correct=0;private set
 var skipped=0;private set
 val total get()=queue.size
 val finished get()=active && index>=queue.size
 val current get()=if(active) queue.getOrNull(index) else null
 fun start(words:Collection<LessonWord>,last:String?):Boolean {
  val unique=words.filter { it.es.isNotBlank() && it.pl.isNotBlank() }.distinctBy { AssessmentPolicy.key(it.es) }
  if(unique.isEmpty()) return false
  queue=unique.filter { AssessmentPolicy.key(it.es)!=AssessmentPolicy.key(last.orEmpty()) }.shuffled()+unique.filter { AssessmentPolicy.key(it.es)==AssessmentPolicy.key(last.orEmpty()) }
  active=true;index=0;correct=0;skipped=0;return true
 }
 fun advance(passed:Boolean):LessonWord? {
  if(active && !finished) { if(passed) correct++ else skipped++;index++ }
  return current
 }
 fun stop() { active=false;queue=emptyList();index=0;correct=0;skipped=0 }
 fun instruction():String = if(finished) "ODPYTYWANIE ZAKOŃCZONE. Wynik: $correct poprawnych, $skipped pominiętych, razem $total. Podsumuj krótko i czekaj. Nie rozpoczynaj zwykłej lekcji. Powrót tylko po przycisku Wróć do nauki." else
  "TRWA ODPYTYWANIE ${index+1}/$total. Pytanie: ${LessonTurnPolicy.question(current)}. Po krótkiej docince zgodnej z ustawieniami zadaj tylko to pytanie (bez dodatkowych pytań). NIE ZDRADZAJ hiszpańskiego słowa ani nie odtwarzaj wzorca przed odpowiedzią. Nie proponuj nowych słów. Przy błędzie popraw i zostań przy tym pytaniu; poprawną odpowiedź zatwierdza aplikacja. Tryb trwa do końca kolejki lub wyraźnego powrotu do nauki."
}
