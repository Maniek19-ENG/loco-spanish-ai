package com.locospanish.ai.tutor

/** A correct sentence/comment is not necessarily a correct answer to the active word task. */
object WordAttemptPolicy {
    private fun intent(text:String)=AssessmentPolicy.speech(text.lowercase().replace('ł','l'))
    fun newTopic(text:String)=Regex("^(?:chce .*?(?:uczyc|nauczyc|poznac|slowa|slowek|slowo)|ucz(?:my| mnie)?|naucz|zmien .*?(?:temat|scenariusz)|teraz .*?(?:slowa|ucz)|podaj (?:inne|nowe|nastepne) slowo|nastepne slowo|pomin .*slowo|quiero (?:aprender|estudiar)|ensename|otra palabra|cambia .*tema)\\b").containsMatchIn(intent(text))
    fun needsHelp(text:String)=Regex("^(?:nie (?:wiem|umiem|rozumiem|powiedzialem|slysze)|powtorz|wolniej|czekaj|tak|nie|ok|okej|dobrze|aha|mhm|jasne|spoko|dzieki|halo|hej|super|swietnie|wow|ha|haha|hahaha|hehe|stop|koniec|zakoncz)(?:\\b|$)").containsMatchIn(intent(text)) || AssessmentPolicy.isLearningQuestion(text)
    fun wantsReference(text:String)=Regex("^(?:nie (?:wiem|umiem|rozumiem)|powtorz|wolniej|jak (?:powiedziec|sie mowi))\\b").containsMatchIn(intent(text))
    private val equivalents=listOf(setOf("habitacion","cuarto"),setOf("reserva","reservacion"),setOf("billete","boleto"),setOf("coche","carro","auto"))
    private fun compactRepeatedTrial(text:String):String {
        val tokens=AssessmentPolicy.speech(text).split(' ').filter { it.isNotBlank() }
        for(width in 1..2) {
            if(tokens.size%width!=0 || tokens.size/width !in 2..3) continue
            val units=tokens.chunked(width)
            val first=units.first().joinToString(" ")
            if(units.all { it==units.first() } && LessonTurnPolicy.singleWord(first)) return first
        }
        return text
    }
    private fun trialText(text:String):String {
        val clean=compactRepeatedTrial(text.trim().trim('¿','¡','?','!','.',',','"','«','»','“','”','‘','’').replace(Regex("\\s+")," "))
        return clean.replace(Regex("^(?:la palabra es|creo que es|se dice|es|to jest)\\s+",RegexOption.IGNORE_CASE),"")
            .trim().trim('¿','¡','?','!','.',',','"','«','»','“','”','‘','’')
    }
    /** Whole answer or a short affirmative frame, never a negation/question merely containing it. */
    fun directAnswer(word:LessonWord,text:String):Boolean {
        val answer=AssessmentPolicy.speech(AssessmentPolicy.display(trialText(text)))
        val expected=AssessmentPolicy.speech(AssessmentPolicy.display(word.es))
        val variants=equivalents.firstOrNull { expected in it } ?: setOf(expected)
        return answer.isNotBlank() && answer in variants
    }
    fun matches(word:LessonWord,text:String):Boolean {
        val core=AssessmentPolicy.speech(AssessmentPolicy.display(word.es))
        val alternatives=equivalents.firstOrNull { core in it } ?: setOf(core)
        return alternatives.any { AssessmentPolicy.contains(text,it) }
    }
    fun evaluate(word:LessonWord?,text:String,checked:Assessment, reliableTranscript:Boolean = false):Assessment {
        if(word == null || newTopic(text)) return checked
        if(text.isBlank() || needsHelp(text)) return Assessment("UNCERTAIN",text,explanation="Brak odpowiedzi na ćwiczone słowo. Pomóż i zostań przy tym samym zadaniu; nie zaliczaj komentarza.")
        val trial=trialText(text)
        val bare=AssessmentPolicy.speech(trial)
        val singleTrial=LessonTurnPolicy.singleWord(trial) || Regex("^[0-9]+$").matches(bare) || directAnswer(word,text)
        val article=bare.substringBefore(' ').takeIf { it in setOf("el","la","los","las","un","una","unos","unas") }
        val actualCore=AssessmentPolicy.speech(AssessmentPolicy.display(trial))
        fun articleFamily(value:String)=when(value) { "un" -> "el";"una" -> "la";"unos" -> "los";"unas" -> "las";else -> value }
        val expectedArticle=articleFamily(when(actualCore) { "cuarto","billete","boleto","coche","carro","auto" -> "el"; "habitacion","reserva","reservacion" -> "la"; else -> word.es.substringBefore(' ').lowercase() })
        val genderCorrect=article == null || expectedArticle !in setOf("el","la","los","las") || articleFamily(article)==expectedArticle
        // A lexical match comes from the learner's actual transcript, not a model-invented correction.
        // Definite and indefinite articles of the same gender are both valid answers.
        if(expectedArticle in setOf("el","la","los","las") && !genderCorrect && matches(word,text) && singleTrial)
            return Assessment("ERROR",text,word.es,"Rodzajnik nie pasuje do tego rzeczownika.")
        if(genderCorrect && singleTrial && matches(word,text))
            return Assessment("CORRECT",text,explanation="Rozpoznano poprawną odpowiedź na ćwiczone słowo. Nie oceniaj wymowy na podstawie samej pisowni.")
        if(checked.verdict == "CORRECT" && !matches(word,text)) {
            return if(LessonTurnPolicy.singleWord(text.trim().trimEnd('.', '!', '?'))) Assessment("ERROR",text,word.es,"Ćwiczymy słowo „${word.pl}”. Ta odpowiedź nie odpowiada aktualnemu słowu.")
            else checked
        }
        return checked
    }
    fun canAdvance(word:LessonWord?,text:String,checked:Assessment)=word != null && text.isNotBlank() && !needsHelp(text) && !newTopic(text) && checked.verdict == "CORRECT" && matches(word,text)
    fun hold(word:LessonWord?)="NIE PRZECHODŹ DO KOLEJNEGO SŁOWA. Nie zaliczono nowej poprawnej odpowiedzi. " +
        (word?.let { "Aktualne zadanie: ${it.es} — ${it.pl}. " } ?: "") +
        "Przy błędzie popraw tę próbę; przy pytaniu lub nie wiem pomóż krótko. Przy braku wypowiedzi czekaj na ucznia. Bez nowego słowa, bez fałszywej pochwały i bez kończenia lekcji."
}
