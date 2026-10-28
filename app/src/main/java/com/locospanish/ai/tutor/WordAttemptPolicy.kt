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
        sentenceMatch(word,text)?.let { return it }
        val core=AssessmentPolicy.speech(AssessmentPolicy.display(word.es))
        val alternatives=equivalents.firstOrNull { core in it } ?: setOf(core)
        return alternatives.any { AssessmentPolicy.contains(text,it) }
    }
    /** A whole sentence (B1+) counts when most of its content words were said, in any wording close to the model. */
    private fun sentenceMatch(word:LessonWord,text:String):Boolean? {
        val stop=setOf("el","la","los","las","un","una","unos","unas","de","a","en","y","que","por","con","me","te","se","lo","le","mi","tu","su","es","al","del")
        val target=AssessmentPolicy.speech(word.es).split(' ').filter { it.isNotBlank() && it !in stop }
        if(target.size<3) return null
        val said=AssessmentPolicy.speech(text).split(' ').map(::soundKey).toSet()
        return target.count { soundKey(it) in said } >= Math.ceil(target.size*0.7).toInt()
    }
    /** Spelling-independent Spanish sound key: silent h, b/v, ll/y, soft c/z/s and Polish-style w are one sound. */
    fun soundKey(text:String):String {
        var s=AssessmentPolicy.speech(text).replace(" ","").replace("ch","\u0001").replace("h","").replace("\u0001","ch")
        s=s.replace(Regex("g(?=[ei])"),"x").replace("j","x").replace("qu","k").replace(Regex("gu(?=[ei])"),"g")
            .replace(Regex("c(?=[ei])"),"s").replace("z","s").replace("c","k").replace("ll","y").replace("v","b").replace("w","b")
            .replace("ñ","ny").replace(Regex("y$"),"i")
        return s.replace(Regex("([^r])\\1+"),"$1")
    }
    /** The same sound written the Polish way, as the transcriber often spells a Pole's Spanish (nocze, jo, kabaljo). */
    fun polishKey(text:String):String {
        var s=text.lowercase().replace(Regex("[^\\p{L}]+"),"")
        s=s.replace("ch","x").replace("h","x").replace("cz","\u0002").replace("ć","\u0002").replace(Regex("ci(?=[aeouó])"),"\u0002")
            .replace("sz","s").replace("ś","s").replace(Regex("si(?=[aeouó])"),"s").replace("dż","y").replace("rz","y").replace("ż","y").replace("ź","y")
            .replace("y","i").replace(Regex("(?<=[bcdfgklmnprstwz])j"),"i").replace("j","y").replace("ł","l").replace("w","b").replace("v","b").replace("c","s").replace("z","s").replace("qu","k")
            .replace("ń","ny").replace("ó","u").replace("ę","e").replace("ą","o").replace("ll","y")
        s=java.text.Normalizer.normalize(s,java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"),"").replace("\u0002","kh").replace(Regex("y$"),"i")
        return s.replace(Regex("([^r])\\1+"),"$1")
    }
    private fun heardKeys(text:String):Set<String> {
        val raw=AssessmentPolicy.display(trialText(text))
        val keys=setOf(soundKey(raw),polishKey(raw)).filter { it.isNotBlank() }
        // A leading article glued to the noun by the transcriber ("lano che") is still that noun.
        return keys.flatMap { k -> listOf(k)+Regex("^(?:unas|unos|una|un|las|los|la|el)(?=.{3,})").find(k)?.let { listOf(k.substring(it.value.length)) }.orEmpty() }.toSet()
    }
    private fun distance(a:String,b:String):Int {
        var previous=IntArray(b.length+1) { it }
        for(i in 1..a.length) {
            val row=IntArray(b.length+1);row[0]=i
            for(j in 1..b.length) row[j]=minOf(previous[j]+1,row[j-1]+1,previous[j-1]+if(a[i-1]==b[j-1]) 0 else 1)
            previous=row
        }
        return previous[b.length]
    }
    private fun alternativesOf(word:LessonWord):Set<String> {
        val core=AssessmentPolicy.speech(AssessmentPolicy.display(word.es))
        return equivalents.firstOrNull { core in it } ?: setOf(core)
    }
    /** The transcript spells the target differently but it sounds identical in Spanish (abitación, cerbeza, yave). */
    fun soundsLike(word:LessonWord,text:String):Boolean {
        val heard=heardKeys(text)
        return alternativesOf(word).any { soundKey(it) in heard }
    }
    /** A near miss of a single word: likely a transcription slip, never proof of a real mistake. */
    fun nearMiss(word:LessonWord,text:String):Boolean {
        val heard=heardKeys(text).filter { it.length>=3 }
        return alternativesOf(word).any { val target=soundKey(it); target.length>=3 && heard.any { h -> distance(h,target)<=if(target.length>=6) 2 else 1 } }
    }
    /** The transcript must at least sound close to the target; Gemini's word alone never credits something else. */
    private fun soundsClose(word:LessonWord,text:String):Boolean {
        val heard=heardKeys(text)
        return alternativesOf(word).any { val target=soundKey(it); heard.any { h -> distance(h,target)<=maxOf(1,target.length/3) } }
    }
    private val catalogKeys by lazy { (TopicLessons.plans.keys.flatMap { TopicLessons.vocabulary(it) }).map { AssessmentPolicy.key(it.es) }.toSet() }
    /** Gemini hears the audio; its CERTAIN CORRECT with the target in heard_text wins over a garbled transcript. */
    private fun modelHeardTarget(word:LessonWord,text:String,modelHeard:String):Boolean {
        if(modelHeard.isBlank() || !(matches(word,modelHeard) || soundsLike(word,modelHeard)) || !soundsClose(word,text)) return false
        val transcript=AssessmentPolicy.speech(trialText(text))
        // A Spanish negation of the target itself ("no es la noche") is not an answer.
        if(Regex("^(?:no|nie)\\b").containsMatchIn(transcript) && (matches(word,text) || Regex("\\b(?:es|son|esta)\\b").containsMatchIn(transcript))) return false
        // A clearly different lesson word in the transcript is not overridden.
        val other=AssessmentPolicy.key(AssessmentPolicy.display(trialText(text)))
        return !(other in catalogKeys && other!=AssessmentPolicy.key(word.es) && !nearMiss(word,text))
    }
    /** Another lesson word said alongside the card word ("mañana… hoy") makes the answer ambiguous, never a pass. */
    fun saysOtherLessonWord(word:LessonWord,text:String):Boolean {
        val own=alternativesOf(word).map { AssessmentPolicy.key(it) }.toSet()+AssessmentPolicy.key(word.es)
        val tokens=AssessmentPolicy.speech(text).split(' ').filter { it.isNotBlank() }
        val pieces=tokens+tokens.windowed(2).map { it.joinToString(" ") }
        return pieces.any { val k=AssessmentPolicy.key(it); k.length>=3 && k in catalogKeys && k !in own && own.none { o -> o.contains(k) } }
    }
    fun evaluate(word:LessonWord?,text:String,checked:Assessment, reliableTranscript:Boolean = false, modelHeard:String = ""):Assessment {
        if(word == null || newTopic(text)) return checked
        if(text.isBlank() || needsHelp(text)) return Assessment("UNCERTAIN",text,explanation="Brak odpowiedzi na ćwiczone słowo. Pomóż i zostań przy tym samym zadaniu; nie zaliczaj komentarza.")
        val trial=trialText(text)
        val bare=AssessmentPolicy.speech(trial)
        if(LessonTurnPolicy.singleWord(word.es) && saysOtherLessonWord(word,text))
            return if(matches(word,text)) Assessment("UNCERTAIN",text,explanation="Uczeń powiedział też inne słowo niż ${word.es}. Nie zaliczaj; niech powie samo słowo z karty.")
                else Assessment("ERROR",text,word.es,"To inne słowo niż ${word.es} (${word.pl}).")
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
        // A whole utterance that sounds like the target (also split or Polish-spelled by the transcriber) is that answer.
        if(genderCorrect && ((singleTrial && matches(word,text)) || (bare.split(' ').size<=3 && soundsLike(word,text))))
            return Assessment("CORRECT",text,explanation="Rozpoznano poprawną odpowiedź na ćwiczone słowo. Nie oceniaj wymowy na podstawie samej pisowni.")
        if(modelHeardTarget(word,text,modelHeard))
            return Assessment("CORRECT",text,explanation="Gemini usłyszało poprawne słowo ${word.es}; automatyczny zapis tekstu je przekręcił. Zalicz bez sugerowania błędu.")
        if(singleTrial && nearMiss(word,text) && checked.verdict!="ERROR")
            return Assessment("UNCERTAIN",text,explanation="Transkrypcja jest bliska słowa ${word.es}, ale niepewna. Nie śmiej się i nie zarzucaj błędu; poproś krótko o jeszcze jedną wyraźną próbę.")
        if(checked.verdict == "CORRECT" && !matches(word,text)) {
            return if(LessonTurnPolicy.singleWord(text.trim().trimEnd('.', '!', '?'))) Assessment("ERROR",text,word.es,"Ćwiczymy słowo „${word.pl}”. Ta odpowiedź nie odpowiada aktualnemu słowu.")
            // A longer reply without the card word is not a pass for the word task, whatever Gemini graded.
            else Assessment("UNCERTAIN",text,explanation="Nie usłyszano ćwiczonego słowa ${word.es}. Nie zaliczaj; niech uczeń powie je jeszcze raz.")
        }
        return checked
    }
    fun canAdvance(word:LessonWord?,text:String,checked:Assessment,modelHeard:String = "")=word != null && text.isNotBlank() && !needsHelp(text) && !newTopic(text) && checked.verdict == "CORRECT" &&
        !(LessonTurnPolicy.singleWord(word.es) && saysOtherLessonWord(word,text)) && (matches(word,text) || soundsLike(word,text) || modelHeardTarget(word,text,modelHeard))
    fun hold(word:LessonWord?)="NIE PRZECHODŹ DO KOLEJNEGO SŁOWA. Nie zaliczono nowej poprawnej odpowiedzi. " +
        (word?.let { "Aktualne zadanie: ${it.es} — ${it.pl}. " } ?: "") +
        "Przy błędzie popraw tę próbę; przy pytaniu lub nie wiem pomóż krótko. Przy braku wypowiedzi czekaj na ucznia. Bez nowego słowa, bez fałszywej pochwały i bez kończenia lekcji."
}
