package com.locospanish.ai.tutor

/** Keeps a teaching suggestion separate from earned vocabulary progress. */
object LessonTurnPolicy {
    fun singleWord(text: String) = Regex("^(?:(?:el|la|los|las|un|una|unos|unas) )?[\\p{L}]+$",RegexOption.IGNORE_CASE).matches(text.trim())
    fun next(scenario: String, proposed: LessonWord?, known: Collection<String>, taught: Collection<String>, allowScenarioFallback: Boolean = true, wordsOnly: Boolean = false, verifiedCorrection: String = "", preferScenario: Boolean = false, level: com.locospanish.ai.model.Level = com.locospanish.ai.model.Level.A1): LessonWord? {
        val corrected=verifiedCorrection.trim().trimEnd('.', '!', '?')
        if(wordsOnly && corrected.isNotBlank() && singleWord(corrected)) {
            val meaning=proposed?.takeIf { AssessmentPolicy.key(it.es) == AssessmentPolicy.key(corrected) }?.pl
                ?: TopicLessons.plans.values.flatMap { it.words }.firstOrNull { AssessmentPolicy.key(it.es) == AssessmentPolicy.key(corrected) }?.pl
                ?: "poprawna forma poprzedniego słowa"
            return LessonWord(corrected,meaning)
        }
        val excluded = (known + taught).map(AssessmentPolicy::key).toSet()
        fun available(word: LessonWord) = word.es.isNotBlank() && word.pl.isNotBlank() &&
            word.es.length <= (if(wordsOnly) 70 else 160) && word.es.trim().split(Regex("\\s+")).size <= (if(wordsOnly) 3 else 12) &&
            AssessmentPolicy.key(word.es) !in excluded && (!wordsOnly || singleWord(word.es))
        if(proposed != null && available(proposed) && (!preferScenario || TopicLessons.belongsTo(scenario,proposed))) {
            // Retain the native article and spelling of known material, while accepting new AI material.
            return if(wordsOnly && preferScenario) TopicLessons.vocabulary(scenario).firstOrNull {
                AssessmentPolicy.key(it.es)==AssessmentPolicy.key(proposed.es) && available(it)
            } ?: proposed else proposed
        }
        // Above A1 the fallback is level material (phrases on A2, whole sentences from B1), never the A1 noun list.
        if(level != com.locospanish.ai.model.Level.A1) return if(allowScenarioFallback) LevelPhrases.words(scenario,level).firstOrNull(::available) else null
        if(preferScenario && allowScenarioFallback) TopicLessons.vocabulary(scenario).firstOrNull(::available)?.let { return it }
        return if(allowScenarioFallback) TopicLessons.get(scenario).words.firstOrNull(::available) else null
    }

    fun question(word: LessonWord?) = word?.let { "Jak powiesz po hiszpańsku „${it.pl.trim().take(100)}”?" }.orEmpty()
    const val languageInstruction = "Komentarz nauczyciela i treść pytania są po polsku, ale zadanie polega na podaniu słowa PO HISZPAŃSKU. Uczeń odpowiada po hiszpańsku, nie tłumaczy na polski. Zadaj dokładnie pytanie learner_question, jeśli nie jest puste."
    fun instruction(word: LessonWord?, requestedTopic: String): String {
        val topic = requestedTopic.trim().take(120)
        return buildString {
            append(languageInstruction+" ")
            if(word != null) append("Pytanie do ucznia: "+question(word)+" ")
            append("Najpierw odpowiedz na rzeczywistą prośbę ucznia, także pytanie o znaczenie lub wymowę. ")
            if(topic.isNotBlank()) append("Aktualny temat nauki ucznia: $topic. Nie wracaj do innego tematu. ")
            if(word != null) append("Nowe słowo do objaśnienia: ${word.es} — ${word.pl}. Na A1 podaj je osobno, polskie znaczenie i jedno łatwe zadanie; odpowiedź jednym słowem wystarczy. ")
            else append("Poprzednie propozycje już były ćwiczone. Wymyśl JEDNO INNE proste słowo z aktualnego tematu, poza recently_taught_words i already_known_words. Nie wracaj do ostatniego słowa listy. ")
            append("Jeśli uczeń prosi o konkretną rzecz zamiast tego słowa, jego prośba ma pierwszeństwo. Na A1 kontynuuj pojedyncze słowa, nie przechodź sam do zdań. Nie kończ lekcji po poprawnej próbie ani po wyczerpaniu propozycji. Kolejne słowo i zadanie teraz, aż użytkownik sam zakończy sesję. Nie pytaj co dalej. Nie czekaj na żadne dodatkowe polecenie: wypowiedz teraz docinkę i lekcję w jednej odpowiedzi audio.")
        }
    }
}
