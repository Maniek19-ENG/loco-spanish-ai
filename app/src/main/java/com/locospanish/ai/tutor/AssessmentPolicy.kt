package com.locospanish.ai.tutor

import com.google.gson.JsonObject
import com.locospanish.ai.model.*
import java.text.Normalizer

data class Assessment(val verdict: String, val heard: String, val correction: String = "", val explanation: String = "")
object AssessmentPolicy {
    private val speechCache=java.util.concurrent.ConcurrentHashMap<String,String>()
    private val keyCache=java.util.concurrent.ConcurrentHashMap<String,String>()
    private val marks=Regex("\\p{M}")
    private val nonSpeech=Regex("[^a-zñ0-9]+")
    private val article=Regex("^(el|la|los|las|un|una|unos|unas) ",RegexOption.IGNORE_CASE)
    private val accents=mapOf("cafe" to "café","habitacion" to "habitación","direccion" to "dirección")
    private fun memo(cache:java.util.concurrent.ConcurrentHashMap<String,String>,text:String,calculate:()->String):String {
        if(text.length>500) return calculate()
        cache[text]?.let { return it }
        val result=calculate()
        if(cache.size>=2048) cache.clear()
        return cache.putIfAbsent(text,result) ?: result
    }
    fun normal(text: String) = Normalizer.normalize(text.lowercase().replace('ñ','\u0001'),Normalizer.Form.NFD)
        .replace(marks, "").replace('\u0001','ñ').replace(nonSpeech," ").trim()
    private val numberPatterns: List<Pair<Regex,String>> = run {
        val units=listOf("cero","uno","dos","tres","cuatro","cinco","seis","siete","ocho","nueve","diez","once","doce","trece","catorce","quince","dieciseis","diecisiete","dieciocho","diecinueve")
        val numbers=units.mapIndexed { n,word -> word to n }.toMap().toMutableMap()
        numbers["veinte"]=20
        val twenty=listOf("veintiuno","veintidos","veintitres","veinticuatro","veinticinco","veintiseis","veintisiete","veintiocho","veintinueve")
        twenty.forEachIndexed { n,word -> numbers[word]=21+n };numbers["veintiun"]=21
        listOf("treinta","cuarenta","cincuenta","sesenta","setenta","ochenta","noventa").forEachIndexed { n,word ->
            val base=30+n*10;numbers[word]=base
            (1..9).forEach { numbers["$word y ${units[it]}"]=base+it };numbers["$word y un"]=base+1
        }
        numbers["cien"]=100
        numbers.entries.sortedByDescending { it.key.length }.map { (word,n) -> Regex("(?<![a-zñ0-9])"+word+"(?![a-zñ0-9])") to n.toString() }
    }
    fun speech(text: String): String = memo(speechCache,text) {
        var value=normal(text)
        numberPatterns.forEach { (pattern,n) -> value=value.replace(pattern,n) }
        value
    }
    fun display(term: String): String {
        val bare=term.trim().replace(article,"")
        return accents[normal(bare)] ?: bare
    }
    fun key(term:String):String=memo(keyCache,term) { uncachedKey(term) }
    private fun uncachedKey(term: String): String {
        val shown=display(term).lowercase()
        val spoken=speech(shown)
        if(Regex("^[0-9]+$").matches(spoken)) return spoken
        return Normalizer.normalize(shown,Normalizer.Form.NFC).replace(Regex("[^\\p{L}\\p{N}]+")," ").trim()
    }
    fun contains(utterance: String, term: String): Boolean {
        val token=speech(key(term))
        return token.isNotBlank() && Regex("(?<![a-zñ0-9])"+Regex.escape(token)+"(?![a-zñ0-9])").containsMatchIn(speech(utterance))
    }
    private fun structuralCorrection(text: String): Pair<String,String>? {
        var corrected=text
        val notes=mutableListOf<String>()
        val verbs=mapOf("tiene" to "tengo","es" to "soy","eres" to "soy","somos" to "soy","son" to "soy","quiere" to "quiero","quieres" to "quiero","queremos" to "quiero","quieren" to "quiero")
        val subject=Regex("^\\s*yo\\s+(tiene|es|eres|somos|son|quiere|quieres|queremos|quieren)\\b",RegexOption.IGNORE_CASE).find(corrected)
        if(subject != null) {
            corrected=corrected.replaceRange(subject.range,"Yo ${verbs.getValue(subject.groupValues[1].lowercase())}")
            notes.add("Po yo używamy formy pierwszej osoby czasownika.")
        }
        val feminine=setOf("habitacion","habitaciones","reserva","reservas","llave","llaves","cuenta","cuentas","mesa","mesas","tarjeta","tarjetas","direccion","direcciones","noche","noches","cerveza","cervezas")
        val masculine=setOf("cafe","cafes","precio","precios","billete","billetes","pasaporte","pasaportes","trabajo","trabajos","circuito","circuitos","equipaje","equipajes","vuelo","vuelos","piso","pisos","clima")
        corrected=Regex("\\b(un|una|el|la|unos|unas|los|las)\\s+([\\p{L}]+)\\b",RegexOption.IGNORE_CASE).replace(corrected) { match ->
            val article=match.groupValues[1].lowercase();val noun=normal(match.groupValues[2])
            val wanted=when {
                noun in feminine -> mapOf("un" to "una","el" to "la","unos" to "unas","los" to "las")[article]
                noun in masculine -> mapOf("una" to "un","la" to "el","unas" to "unos","las" to "los")[article]
                else -> null
            }
            if(wanted == null) match.value else {
                notes.add("Rodzajnik musi zgadzać się z rodzajem rzeczownika.")
                (if(match.groupValues[1].first().isUpperCase()) wanted.replaceFirstChar { it.uppercase() } else wanted)+" "+match.groupValues[2]
            }
        }
        return if(notes.isEmpty()) null else corrected to notes.distinct().joinToString(" ")
    }
    fun isLearningQuestion(text: String) = Regex("^(jak powiedziec|co znaczy|jak sie mowi|przetlumacz|como se dice|que significa|es correcto decir)\\b").containsMatchIn(normal(text))
    private fun value(args: JsonObject, name: String) = args.get(name)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    fun evaluate(utterance: String, args: JsonObject): Assessment {
        if(utterance.isBlank()) return Assessment("UNCERTAIN", "", explanation="Brak prawdziwej wypowiedzi ucznia; nie oceniaj polecenia aplikacji.")
        val actual=normal(utterance)
        val heard=value(args,"heard_text").ifBlank { value(args,"evidence") }
        if(speech(heard) != speech(utterance)) return Assessment("UNCERTAIN",utterance,explanation="Ocena nie odpowiada rozpoznanej wypowiedzi; nie wymyślaj błędu ani pochwały.")
        if(value(args,"confidence") != "CERTAIN") return Assessment("UNCERTAIN",utterance,explanation="Niepewna ocena; poproś o wyraźniejszą próbę bez potwierdzania poprawności.")
        val number="(?:[0-9]+|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|trece|catorce|quince|dieciseis|diecisiete|dieciocho|diecinueve|veinte|veintiun|veintiuno|veintidos|veintitres|veinticuatro|veinticinco|veintiseis|veintisiete|veintiocho|veintinueve|treinta|cuarenta|cincuenta|sesenta|setenta|ochenta|noventa|cien)(?: y (?:un|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve))?"
        val age=Regex("^(yo )?soy ($number) años$").matchEntire(actual)
        if(age != null) { val n=speech(age.groupValues[2]); return Assessment("ERROR",utterance,if(n == "1") "Tengo un año." else "Tengo $n años.","Wiek wyrażamy przez tener, nie ser.") }
        val spokenAge=actual
        val wrongOne=Regex("^(yo )?tengo (1|un|uno|veintiuno|(?:treinta|cuarenta|cincuenta|sesenta|setenta|ochenta|noventa) y uno) años$").matchEntire(spokenAge)
        if(wrongOne != null) {
            val ageWord=wrongOne.groupValues[2]
            val fixed=when(ageWord) { "1","un","uno" -> "un año";"veintiuno" -> "veintiún años";else -> ageWord.removeSuffix("uno")+"un años" }
            return Assessment("ERROR",utterance,"Tengo $fixed.","Przed rzeczownikiem rodzaju męskiego używamy un, a przy jednym roku singular año.")
        }
        val correctAge=Regex("^(yo )?tengo ($number) años$").matchEntire(actual)
        if(correctAge != null && correctAge.groupValues[2] != "1" && value(args,"error_kind") != "PRONUNCIATION") return Assessment("CORRECT",utterance)
        val languageQuestion=isLearningQuestion(utterance)
        if(languageQuestion) return Assessment("CORRECT",utterance)
        structuralCorrection(utterance)?.let { (corrected,reason) -> return Assessment("ERROR",utterance,corrected,reason) }
        val proposed=value(args,"verdict")
        val correction=value(args,"correction")
        val explanation=value(args,"explanation")
        if(proposed == "ERROR") {
            fun equivalent(text: String) = speech(text).removePrefix("yo ")
                .removeSuffix(" por favor").replace(Regex("^(quisiera|deseo|me gustaria) "),"quiero ")
                .replace("un cafe","cafe").replace("una reserva","reserva").replace("una habitacion","habitacion")
            if(correction.isBlank() || explanation.isBlank()) return Assessment("UNCERTAIN",utterance,explanation="Brak konkretnej poprawki i uzasadnienia błędu.")
            if(equivalent(correction) == equivalent(utterance)) {
                if(value(args,"error_kind") == "PRONUNCIATION") return Assessment("UNCERTAIN",utterance,explanation="Ta sama pisownia nie dowodzi błędnej wymowy. Akcent nie jest automatycznie błędem.")
                return Assessment("CORRECT",utterance)
            }
            if(structuralCorrection(correction) != null) return Assessment("UNCERTAIN",utterance,explanation="Proponowana poprawka zawiera błąd; nie oznaczaj poprawnej wypowiedzi jako błędnej na tej podstawie.")
            if(value(args,"error_kind") !in setOf("GRAMMAR","VOCABULARY","PRONUNCIATION")) return Assessment("UNCERTAIN",utterance,explanation="Brak potwierdzonego rodzaju rzeczywistego błędu.")
            return Assessment("ERROR",utterance,correction,explanation)
        }
        return Assessment(if(proposed == "CORRECT") "CORRECT" else "UNCERTAIN",utterance)
    }
    fun feedback(assessment: Assessment, isLearner: Boolean, correctionEnabled: Boolean): JsonObject {
        val result=JsonObject()
        result.addProperty("is_learner_turn",isLearner)
        result.addProperty("verified_verdict",assessment.verdict)
        result.addProperty("verified_correction",assessment.correction)
        result.addProperty("assessment_reason",assessment.explanation)
        result.addProperty("assessment_instruction",if(!isLearner) "To polecenie aplikacji lub początek sesji, nie próba ucznia. Nie oceniaj, nie proś o powtarzanie; rozpocznij lub kontynuuj dialog według polecenia." else when(assessment.verdict) {
            "ERROR" -> if(correctionEnabled) "Potwierdzony błąd. Nie mów że było poprawnie. Wyraźnie podaj verified_correction i krótko assessment_reason. Potem rozwijaj dialog." else "Korekta automatyczna wyłączona. Nie narzucaj poprawki; zachowaj styl zgodny z ustawieniami."
            "CORRECT" -> "Potwierdzona poprawna wypowiedź. Nie poprawiaj jej ani nie twórz fikcyjnego błędu. Docinka dotyczy tylko sytuacji."
            else -> "Ocena niepewna. Nie mów że jest dobrze ani że źle. Poproś o wyraźniejsze powtórzenie; nie wyśmiewaj wymyślonego błędu."
        })
        return result
    }
    fun vocabulary(args: JsonObject, assessment: Assessment, userId: String): List<LearningEvent> {
        if(assessment.verdict == "UNCERTAIN" || userId.isBlank() || isLearningQuestion(assessment.heard)) return emptyList()
        return args.get("words")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { item ->
            if(!item.isJsonObject) return@mapNotNull null
            val word=item.asJsonObject;val term=display(value(word,"term").take(80));val evidence=value(word,"evidence")
            if(!contains(assessment.heard,term) || normal(evidence).isBlank() || !speech(assessment.heard).contains(speech(evidence)) || !contains(evidence,term)) return@mapNotNull null
            if(assessment.verdict == "ERROR" && !contains(assessment.correction,term)) return@mapNotNull null
            val known=word.get("known")?.takeIf { it.isJsonPrimitive }?.runCatching { asBoolean }?.getOrDefault(false) ?: false
            LearningEvent("vocab:$userId:${key(term)}",if(known) "known_word" else "word",term,"",value(word,"translation").take(160),evidence.take(300))
        }?.distinctBy { it.id }.orEmpty()
    }
}
