package com.locospanish.ai
import com.google.gson.*
import com.locospanish.ai.tutor.*
import com.locospanish.ai.model.*
object AssessmentBridge {
    @JvmStatic fun main(args:Array<String>) {
        val request=JsonParser.parseString(System.`in`.bufferedReader(Charsets.UTF_8).readText()).asJsonObject
        val utterance=request.get("utterance").asString
        val fields=request.getAsJsonObject("args")
        val assessment=AssessmentPolicy.evaluate(utterance,fields)
        val result=AssessmentPolicy.feedback(assessment,true,true)
        result.addProperty("saved",assessment.verdict != "UNCERTAIN")
        val words=AssessmentPolicy.vocabulary(fields,assessment,request.get("user_id")?.asString ?: "test")
        result.add("learned_words",Gson().toJsonTree(words))
        val known=request.get("known_words")?.takeIf { it.isJsonArray } ?: JsonArray()
        result.add("already_known_words",known)
        result.addProperty("vocabulary_instruction","Słowa z already_known_words są już znane. Nie odpytuj z nich w kółko; rozwijaj nowe słownictwo i sytuacje.")
        val taught=request.get("taught_words")?.takeIf { it.isJsonArray }?.asJsonArray?.map { it.asString }.orEmpty()
        val topic=fields.get("learning_topic")?.asString.orEmpty()
        val scenario=request.get("scenario")?.asString ?: "hotel"
        val next=LessonTurnPolicy.next(scenario,LessonWord(fields.get("next_word")?.asString.orEmpty(),fields.get("next_translation")?.asString.orEmpty()),known.asJsonArray.map { it.asString },taught,topic.isBlank(),true,if(assessment.verdict == "ERROR") assessment.correction else "")
        result.add("recently_taught_words",Gson().toJsonTree(taught))
        result.addProperty("lesson_instruction",LessonTurnPolicy.instruction(next,topic))
        result.addProperty("next_word_result",next?.es.orEmpty())
        result.addProperty("next_translation_result",next?.pl.orEmpty())
        result.addProperty("delivery_instruction",com.locospanish.ai.realtime.GeminiProtocol.turnGuidance(com.locospanish.ai.model.Settings().withFilmStyle(true),assessment.verdict))
        result.addProperty("pronunciation_instruction",TopicLessons.pronunciation)
        result.addProperty("planned_roast",com.locospanish.ai.realtime.SpokenRoastPolicy.primary(Settings().withFilmStyle(true),fields.get("roast")?.asString.orEmpty(),assessment.verdict))
        result.addProperty("roast_delivery","Jeśli planned_roast nie jest puste, wypowiedz je dosłownie przed nowym słowem, z szyderczą intonacją. Nie zastępuj go grzeczną pochwałą ani łagodniejszym tekstem. To ta sama odpowiedź audio, bez dodatkowej tury. Przy potwierdzonym błędzie i włączonej korekcie możesz krótko teatralnie podnieść głos w polskiej docince; hiszpańska poprawka pozostaje spokojna. Przy CORRECT żart dotyczy sytuacji, nie jakości wymowy, akcentu lub fikcyjnego błędu. Potem od razu nauka i konkretne zadanie.")
        print(result)
    }
}
