package com.locospanish.ai.tutor

/** Checks the spoken exercise against the same target used by the lesson card. */
object LessonSpeechContract {
    private fun fold(text:String)=TopicLessons.fold(text).replace('ł','l').replace(Regex("[^a-z0-9ñ]+")," ").trim()
    private val requests=Regex("(?:jak (?:powiesz|powiedziec|jest|bedzie|sie mowi|przetlumaczysz)|jak po (?:hiszpansku|polsku) (?:powiesz|powiedziec|jest|bedzie)|jak to powiesz|co to bedzie po (?:hiszpansku|polsku)|powiedz po hiszpansku|przetlumacz|powtorz)([^?!.]*)(?:[?!.]|$)")
    fun mismatch(text:String,target:LessonWord):Boolean {
        val normalized=TopicLessons.fold(text).replace('ł','l')
        val asksArticle=Regex("rodzajnik|\\bel (?:czy|albo|lub) la\\b|\\bla (?:czy|albo|lub) el\\b").containsMatchIn(normalized)
        val shownArticle=target.es.substringBefore(' ').lowercase() in setOf("el","la","los","las","un","una","unos","unas")
        if(asksArticle && !shownArticle) return true
        if(Regex("\\bel (?:czy|albo|lub) la\\b|\\bla (?:czy|albo|lub) el\\b").containsMatchIn(normalized) &&
            target.es.substringBefore(' ').lowercase() in setOf("un","una","unos","unas")) return true
        val expected=listOf(fold(target.pl),fold(target.es)).filter { it.isNotBlank() }
        return requests.findAll(normalized).any { match ->
            val tail=fold(match.groupValues[1])
            if((match.value.startsWith("jak jest") || match.value.startsWith("jak bedzie")) && !tail.contains("po hiszpansku") && !tail.contains("po polsku")) false
            else if(Regex("\\bpo polsku\\b").containsMatchIn(fold(match.value))) true
            else {
                val content=tail.replace(Regex("\\b(?:po hiszpansku|slowo|wyraz|prosze|jeszcze raz|mi|to|ten|teraz)\\b")," ").trim()
                content.isNotBlank() && expected.none { " $tail ".contains(" $it ") }
            }
        }
    }
    fun instruction(target:LessonWord)=
        "JEDYNE AKTUALNE ZADANIE: ${target.es} — ${target.pl}. Po komentarzu wypowiedz dokładnie: ${LessonTurnPolicy.question(target)} Nie pytaj o inne znaczenie ani o poprzednią propozycję. Kierunek polski → hiszpański."
}
