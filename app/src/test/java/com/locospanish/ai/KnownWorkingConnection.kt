package com.locospanish.ai
import com.google.gson.*
import com.locospanish.ai.model.*
import com.locospanish.ai.realtime.*

/** Frozen setup from the installer used before the article change (SHA-256 41757063...). */
internal object KnownWorkingConnection {
 private val gson=Gson()
 private val MODEL=GeminiProtocol.MODEL
 private val voices=GeminiProtocol.voices
 private fun json(vararg values:Pair<String,Any?>)=GeminiProtocol.json(*values)
 private fun prompt(s:Settings,scenario:String,memory:String)=GeminiProtocol.prompt(s,scenario,memory)
    fun setup(s: Settings, scenario: String, memory: String): JsonObject {
        val props = json("kind" to json("type" to "STRING", "enum" to listOf("word","known_word","difficult_word","grammar","pronunciation","repetition")))
        listOf("term","correction","explanation","evidence").forEach { props.add(it, json("type" to "STRING")) }
        val assessment = json("verdict" to json("type" to "STRING", "enum" to listOf("CORRECT","ERROR","UNCERTAIN")), "evidence" to json("type" to "STRING"))
        listOf("heard_text","correction","explanation","next_word","next_translation","learning_topic","next_question").forEach { assessment.add(it,json("type" to "STRING")) }
        assessment.add("next_question",json("type" to "STRING","description" to "Na A1 puste: aplikacja pyta o jedno słowo. Na A2-C2 jedno naturalne pytanie rozwijające scenariusz na wybranym poziomie i związane z next_word, zamiast stale żądać powtórzenia. Nie zmieniaj tematu. Przy prośbie o pomoc puste."))
        assessment.add("intent",json("type" to "STRING","enum" to listOf("ANSWER","HELP","QUESTION","TOPIC","SKIP","CHAT"),"description" to "Rozpoznaj SENS prawdziwej wypowiedzi, nie tylko słowa kluczowe. ANSWER: próba zadania, HELP: uczeń nie umie lub potrzebuje wskazówki, QUESTION: rzeczywiste pytanie także pośrednie, TOPIC: zmiana tematu, SKIP: pominięcie bez zaliczania, CHAT: komentarz lub rozmowa, na którą trzeba odpowiedzieć. Nie klasyfikuj pytań jako błędnych prób."))
        assessment.add("help_answer",json("type" to "STRING","description" to "Przy HELP/QUESTION/CHAT konkretna krótka odpowiedź na rzeczywistą wypowiedź po polsku. Przy nie wiem wyjaśnij aktualne słowo i daj praktyczną podpowiedź, nie samo powtórz. Jeśli dotychczasowa wskazówka nie pomogła, zmień sposób tłumaczenia. Przy ANSWER puste."))
        assessment.add("confidence",json("type" to "STRING","enum" to listOf("CERTAIN","UNCERTAIN")))
        assessment.add("error_kind",json("type" to "STRING","enum" to listOf("NONE","GRAMMAR","VOCABULARY","PRONUNCIATION")))
        assessment.add("words",json("type" to "ARRAY","items" to json("type" to "OBJECT","properties" to json(
            "term" to json("type" to "STRING"),"translation" to json("type" to "STRING"),"evidence" to json("type" to "STRING"),"known" to json("type" to "BOOLEAN")),"required" to listOf("term","translation","evidence","known"))))
        assessment.add("roast_anchor",json("type" to "STRING","description" to "Dosłowny krótki fragment rzeczywistej wypowiedzi ucznia albo ćwiczonego słowa/znaczenia, z którego wynika puenta. Żart ma odnosić się do tego konkretu znaczeniowo; nie musi go mechanicznie cytować. Nie dopisuj fikcyjnej wpadki."))
        assessment.add("roast", json("type" to "STRING", "description" to "Krótka polska docinka do tej sytuacji, do wypowiedzenia przed nauką W TEJ SAMEJ odpowiedzi: potoczna, zabawna puenta z konkretnego szczegółu ostatniej wypowiedzi, zgodna z poziomem przekleństw. Zmieniaj metafory, nie kopiuj poprzednich docinków. Przy ERROR żartuj z dokładnego heard_text, porównując je z correction i aktualnym znaczeniem: konkretny szczegół oraz absurdalny skutek. Do 25 słów, bez grzecznego dobrze i losowych wyzwisk. Pusta tylko przy wyłączonym roaście. Przy UNCERTAIN nie wyłączaj charakteru: żartuj z sytuacji lub nieporozumienia, ale nie twierdź, że uczeń popełnił błąd."))
        val tools = listOf(json("functionDeclarations" to listOf(
            json("name" to "assess_user_turn", "behavior" to "BLOCKING", "description" to "OBOWIĄZKOWA techniczna ocena KAŻDEJ nowej wypowiedzi ucznia przed audio, również poprawnej i poprawionej. Rejestruje rzeczywiście użyte słowa; to nie quiz ani pytanie do ucznia.", "parameters" to json("type" to "OBJECT", "properties" to assessment, "required" to listOf("verdict","evidence","heard_text","intent","help_answer","correction","explanation","confidence","error_kind","words","roast","roast_anchor","next_word","next_translation","learning_topic","next_question"))),
            json("name" to "teach_word", "behavior" to "BLOCKING", "description" to "Przed nowym hiszpańskim wzorcem na KAŻDYM poziomie wywołaj teach_word: term to dokładny tekst słowa albo krótkiego zwrotu, translation to znaczenie po polsku. Aplikacja pokaże tekst i odtworzy osobny czysty hiszpański wzorzec. Na A1 tylko pojedyncze słowo; na wyższych poziomach dopuszczalny krótki zwrot do 12 słów. Przy nowym słowie wywołaj przed własnym audio, jeśli nie otrzymałeś word_audio_played=true lub word_audio_pending=true z assess_user_turn. To przykład nauczyciela, nie postęp ucznia.", "parameters" to json("type" to "OBJECT","properties" to json("term" to json("type" to "STRING"),"translation" to json("type" to "STRING")),"required" to listOf("term","translation"))),
            json("name" to "record_learning", "behavior" to "BLOCKING", "description" to "Zapisz rzeczywistą obserwację z dowodem.", "parameters" to json("type" to "OBJECT", "properties" to props, "required" to listOf("kind","term","correction","explanation","evidence"))),
            json("name" to "complete_scenario", "behavior" to "BLOCKING", "description" to "Potwierdź osiągnięcie celu przez ucznia.", "parameters" to json("type" to "OBJECT", "properties" to json("evidence" to json("type" to "STRING")), "required" to listOf("evidence")))
        ).filter { s.level != Level.A1 || it.get("name").asString != "complete_scenario" }))
        if (s.previousVersionMode) return PreviousGeminiProtocol.setup(s.restoredPrevious(), scenario, memory).also {
            it.getAsJsonObject("setup").getAsJsonObject("systemInstruction").getAsJsonArray("parts")[0].asJsonObject.addProperty("text",prompt(s,scenario,memory))
            it.getAsJsonObject("setup").add("tools",gson.toJsonTree(tools))
        }
        return json("setup" to json("model" to "models/$MODEL", "generationConfig" to json("responseModalities" to listOf("AUDIO"),
            "speechConfig" to json("voiceConfig" to json("prebuiltVoiceConfig" to json("voiceName" to s.voice.takeIf { it in voices }.orEmpty().ifEmpty { "Kore" })))),
            "systemInstruction" to json("parts" to listOf(json("text" to prompt(s,scenario,memory)))),
            "inputAudioTranscription" to json(), "outputAudioTranscription" to json(), "tools" to tools,
            "contextWindowCompression" to json("slidingWindow" to json()),
            "realtimeInputConfig" to json("automaticActivityDetection" to json("silenceDurationMs" to if(s.level==Level.A1) 750 else 850), "activityHandling" to "START_OF_ACTIVITY_INTERRUPTS")))
    }
}
