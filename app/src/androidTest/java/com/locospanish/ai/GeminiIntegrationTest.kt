package com.locospanish.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.locospanish.ai.data.AccessTokenStore
import com.locospanish.ai.data.locoStore
import androidx.lifecycle.viewModelScope
import com.locospanish.ai.model.*
import com.locospanish.ai.realtime.*
import com.locospanish.ai.tutor.LessonAction
import com.locospanish.ai.voice.LiveAudio
import com.locospanish.ai.voice.SupplementSpeaker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.*
import okio.ByteString
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeminiIntegrationTest {
    @Test fun unassessedWrongFeedbackIsNotPlayedBeforeTheCorrectSingleWordIsVerified()=runBlocking {
        val settings=Settings(personality=Settings().personality.copy(roastMode=false))
        runClient(settings=settings,scenario="hotel",assessmentRetryMs=10000) { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}")
            delay(50);recognized(socket,"reserva")
            socket.event(GeminiProtocol.json("serverContent" to GeminiProtocol.json("outputTranscription" to GeminiProtocol.json("text" to "Nie, to nie to!"),"modelTurn" to GeminiProtocol.json("parts" to listOf(GeminiProtocol.json("inlineData" to GeminiProtocol.json("mimeType" to "audio/pcm;rate=24000","data" to "AAAAAA==")))),"generationComplete" to true)).toString())
            delay(80);assertEquals(0,audio.packets)
            withTimeout(1000) { while(socket.sent.none { it.contains("TECHNICZNE PONOWIENIE OCENY") }) delay(5) }
            assertEquals("la reserva",client.state.value.currentWord)
            assessment(socket,"after-premature-wrong-feedback","reserva")
            assertEquals("la habitación",client.state.value.currentWord)
            assertTrue(client.state.value.learning.any { it.kind=="known_word" && it.term=="reserva" })
            assertTrue(client.state.value.learning.none { it.kind in setOf("grammar","pronunciation") })
        }
    }
    @Test fun lateRepeatedCorrectSingleWordIsNotRegradedAgainstTheNextTask()=runBlocking {
        val settings=Settings(personality=Settings().personality.copy(roastMode=false))
        runClient(settings=settings,scenario="hotel",wordAudio=true) { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}")
            delay(50);socket.event("{\"serverContent\":{\"turnComplete\":true}}")
            recognized(socket,"reserva");assessment(socket,"first-correct-word","reserva")
            socket.event("{\"serverContent\":{\"turnComplete\":true}}")
            withTimeout(1200) { while("la habitación" !in audio.words) delay(5) }
            assertEquals("la habitación",client.state.value.currentWord)
            recognized(socket,"reserva")
            socket.event(GeminiProtocol.json("toolCall" to GeminiProtocol.json("functionCalls" to listOf(GeminiProtocol.json("id" to "late-correct-word","name" to "assess_user_turn","args" to GeminiProtocol.json("verdict" to "ERROR","heard_text" to "reserva","evidence" to "reserva","confidence" to "CERTAIN","error_kind" to "VOCABULARY","correction" to "la habitación","explanation" to "To inne słowo niż pokój.","words" to emptyList<String>()))))).toString())
            withTimeout(1200) { while(socket.sent.none { it.contains("toolResponse") && it.contains("late-correct-word") }) delay(5) }
            val body=com.google.gson.JsonParser.parseString(socket.sent.last()).asJsonObject.getAsJsonObject("toolResponse").getAsJsonArray("functionResponses")[0].asJsonObject.getAsJsonObject("response")
            assertEquals("CORRECT",body.get("verified_verdict").asString)
            assertFalse(body.get("word_trial_accepted").asBoolean)
            assertEquals("la habitación",client.state.value.currentWord)
            assertEquals(1,client.state.value.learning.count { it.kind=="known_word" })
            assertTrue(client.state.value.learning.none { it.kind in setOf("grammar","pronunciation") })
        }
    }
    @Test fun correctAnswerFramesOverrideTheModelsWrongVerdictWithoutLaughOrCorrection()=runBlocking {
        val settings=Settings(personality=Settings().personality.copy(roastMode=false))
        for(answer in listOf("La   reserva.","Es una reserva.","Se dice reserva.")) runClient(settings=settings,scenario="hotel") { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}")
            delay(50);recognized(socket,answer)
            socket.event(GeminiProtocol.json("toolCall" to GeminiProtocol.json("functionCalls" to listOf(GeminiProtocol.json("id" to "false-frame-error","name" to "assess_user_turn","args" to GeminiProtocol.json("verdict" to "ERROR","heard_text" to answer,"evidence" to answer,"confidence" to "CERTAIN","error_kind" to "VOCABULARY","correction" to "la llave","explanation" to "Zmyślona odmowa","intent" to "CHAT","words" to emptyList<String>()))))).toString())
            withTimeout(1200) { while(socket.sent.none { it.contains("toolResponse") && it.contains("false-frame-error") }) delay(5) }
            val body=com.google.gson.JsonParser.parseString(socket.sent.last()).asJsonObject.getAsJsonObject("toolResponse").getAsJsonArray("functionResponses")[0].asJsonObject.getAsJsonObject("response")
            assertEquals(answer,"CORRECT",body.get("verified_verdict").asString)
            assertEquals("la habitación",client.state.value.currentWord)
            assertTrue(client.state.value.learning.any { it.kind=="known_word" && it.term=="reserva" })
            assertTrue(client.state.value.learning.none { it.kind in setOf("grammar","pronunciation") })
            assertTrue(audio.packetSizes.isEmpty())
        }
    }
    @Test fun falseSpokenRejectionIsNeverPlayedForCorrectOrUncertainTrial()=runBlocking {
        val settings=Settings(personality=Settings().personality.copy(roastMode=false))
        for(correct in listOf(true,false)) runClient(settings=settings,scenario="hotel") { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}")
            delay(50);val answer=if(correct) "reserva" else "gato";recognized(socket,answer)
            assessment(socket,"speech-grade",answer,if(correct) "CORRECT" else "UNCERTAIN")
            fun speak(text:String,done:Boolean)=socket.event(GeminiProtocol.json("serverContent" to GeminiProtocol.json("outputTranscription" to GeminiProtocol.json("text" to text),"modelTurn" to GeminiProtocol.json("parts" to listOf(GeminiProtocol.json("inlineData" to GeminiProtocol.json("mimeType" to "audio/pcm;rate=24000","data" to "AAAAAA==")))),"generationComplete" to done)).toString())
            speak("Nie, to nie to!",false);delay(40);assertTrue(audio.packetSizes.isEmpty())
            socket.event("{\"serverContent\":{\"generationComplete\":true}}")
            delay(80);assertTrue(audio.packetSizes.isEmpty())
            assertTrue(socket.sent.last().contains(if(correct) "Poprawna odpowiedź ucznia została potwierdzona" else "Nie potwierdzono błędu ucznia"))
            speak(com.locospanish.ai.tutor.LessonTurnPolicy.question(com.locospanish.ai.tutor.LessonWord(client.state.value.currentWord,client.state.value.currentMeaning)),true)
            withTimeout(1200) { while(audio.packets==0) delay(5) }
            assertEquals(listOf(4),audio.packetSizes)
            assertTrue(client.state.value.learning.none { it.kind in setOf("grammar","pronunciation") })
        }
    }
    @Test fun anUnplayedTextDraftDoesNotEraseTheActualContextualRiposte()=runBlocking {
        runClient(scenario="hotel",nativeAudio=true) { _,socket,audio ->
            socket.event("{\"setupComplete\":{}}")
            socket.event("{\"serverContent\":{\"turnComplete\":true}}")
            delay(50);recognized(socket,"gato")
            val joke="Kurwa, z takim gato zarezerwujesz kotu recepcję zamiast pokoju."
            socket.event(GeminiProtocol.json("serverContent" to GeminiProtocol.json("outputTranscription" to GeminiProtocol.json("text" to joke))).toString())
            delay(40)
            socket.event(GeminiProtocol.json("toolCall" to GeminiProtocol.json("functionCalls" to listOf(GeminiProtocol.json("id" to "unplayed-draft-roast","name" to "assess_user_turn","args" to GeminiProtocol.json("heard_text" to "gato","evidence" to "gato","confidence" to "CERTAIN","verdict" to "ERROR","correction" to "la reserva","explanation" to "Gato to kot, a ćwiczymy rezerwację.","error_kind" to "VOCABULARY","roast" to joke,"words" to emptyList<String>()))))).toString())
            withTimeout(2000) { while(socket.sent.none { it.contains("unplayed-draft-roast") && it.contains("toolResponse") }) delay(5) }
            val body=com.google.gson.JsonParser.parseString(socket.sent.first { it.contains("unplayed-draft-roast") && it.contains("toolResponse") }).asJsonObject.getAsJsonObject("toolResponse").getAsJsonArray("functionResponses")[0].asJsonObject.getAsJsonObject("response")
            assertTrue(body.get("planned_roast").asString.startsWith(joke.trimEnd('.')))
            assertFalse(body.get("roast_needs_new_idea").asBoolean)
            delay(150)
            // The riposte stays in the Gemini voice; no system speech engine reads it.
            assertTrue(audio.nativeTexts.isEmpty());assertEquals(listOf(96000),audio.packetSizes)
        }
    }
    @Test fun teacherAndSpanishReferenceWaitUntilTheCardIsActuallyPresented()=runBlocking {
        val settings=Settings(personality=Settings().personality.copy(roastMode=false))
        runClient(settings=settings,scenario="hotel",wordAudio=true,waitForCard=true) { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}")
            delay(90)
            assertEquals("la reserva",client.state.value.currentWord);assertEquals(0,audio.packets)
            client.wordPresented("la reserva","inne znaczenie");delay(20);assertEquals(0,audio.packets)
            client.wordPresented("la reserva","rezerwacja")
            withTimeout(1500) { while(audio.packets==0) delay(5) }
            socket.event("{\"serverContent\":{\"turnComplete\":true}}")
            delay(40);recognized(socket,"reserva");assessment(socket,"visible-next","reserva")
            val before=audio.packets
            val visibleBody=com.google.gson.JsonParser.parseString(socket.sent.last()).asJsonObject.getAsJsonObject("toolResponse").getAsJsonArray("functionResponses")[0].asJsonObject.getAsJsonObject("response")
            assertFalse(visibleBody.get("word_audio_played").asBoolean)
            assertTrue(visibleBody.get("word_audio_pending").asBoolean)
            socket.event("{\"serverContent\":{\"generationComplete\":true}}")
            delay(80)
            assertEquals("la habitación",client.state.value.currentWord);assertEquals(before,audio.packets)
            client.wordPresented("la reserva","rezerwacja");delay(20);assertEquals(before,audio.packets)
            client.wordPresented("la habitación","pokój")
            withTimeout(1500) { while(audio.packets==before) delay(5) }
        }
    }
    @Test fun cardShownBeforeSocketSetupStillReleasesTheFirstWord()=runBlocking {
        runClient(scenario="hotel",wordAudio=true,waitForCard=true) { client,socket,audio ->
            client.wordPresented("la reserva","rezerwacja")
            socket.event("{\"setupComplete\":{}}")
            withTimeout(1500) { while(audio.packets==0) delay(5) }
            assertEquals("la reserva",audio.words.first())
        }
    }
    @Test fun missingSpokenRiposteIsRepairedInTheSameGeminiVoice()=runBlocking {
        runClient(scenario="hotel",nativeAudio=true) { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}")
            socket.event("{\"serverContent\":{\"turnComplete\":true}}")
            delay(50);recognized(socket,"gato")
            val joke="Z tym gato recepcjonista zostawił ci kuwetę zamiast klucza."
            socket.event(GeminiProtocol.json("toolCall" to GeminiProtocol.json("functionCalls" to listOf(GeminiProtocol.json("id" to "native-roast","name" to "assess_user_turn","args" to GeminiProtocol.json("heard_text" to "gato","confidence" to "CERTAIN","verdict" to "ERROR","correction" to "la reserva","explanation" to "Gato to kot. Ćwiczymy rezerwację.","error_kind" to "VOCABULARY","evidence" to "gato","roast" to joke,"words" to emptyList<String>()))))).toString())
            withTimeout(2000) { while(socket.sent.none { it.contains("native-roast") && it.contains("toolResponse") }) delay(5) }
            val nativeBody=com.google.gson.JsonParser.parseString(socket.sent.first { it.contains("native-roast") && it.contains("toolResponse") }).asJsonObject.getAsJsonObject("toolResponse").getAsJsonArray("functionResponses")[0].asJsonObject.getAsJsonObject("response")
            assertEquals("ERROR",nativeBody.get("verified_verdict").asString)
            assertTrue(nativeBody.get("planned_roast").asString.contains("Kurwa"))
            delay(150);assertTrue(audio.nativeTexts.isEmpty())
            val commands=socket.sent.count { it.contains("clientContent") }
            socket.event(GeminiProtocol.json("serverContent" to GeminiProtocol.json("outputTranscription" to GeminiProtocol.json("text" to "Jak powiesz po hiszpańsku rezerwacja?"),"modelTurn" to GeminiProtocol.json("parts" to listOf(GeminiProtocol.json("inlineData" to GeminiProtocol.json("mimeType" to "audio/pcm;rate=24000","data" to "AAAAAA==")))),"generationComplete" to true)).toString())
            withTimeout(1500) { while(socket.sent.count { it.contains("clientContent") }==commands) delay(5) }
            assertTrue(socket.sent.last().contains("Brakuje świeżej"))
            assertFalse(4 in audio.packetSizes);assertTrue(audio.nativeTexts.isEmpty())
            assertEquals("la reserva",client.state.value.currentWord)
        }
    }
    @Test fun connectionSetupIsValidatedBeforeSimulatedServerAcknowledgesIt()=runBlocking {
        runClient(scenario="hotel",wordAudio=true) { client,socket,audio ->
            socket.open()
            withTimeout(2000) { while(socket.sent.none { it.contains("setup") }) delay(5) }
            val setup=com.google.gson.JsonParser.parseString(socket.sent.first { it.contains("setup") }).asJsonObject.getAsJsonObject("setup")
            val declarations=setup.getAsJsonArray("tools")[0].asJsonObject.getAsJsonArray("functionDeclarations")
            val teach=declarations.first { it.asJsonObject.get("name").asString=="teach_word" }.asJsonObject.getAsJsonObject("parameters")
            assertEquals(listOf("term","translation"),teach.getAsJsonArray("required").map { it.asString })
            assertFalse(teach.getAsJsonObject("properties").has("article"))
            assertFalse(setup.toString().contains("next_article"))
            socket.event("{\"setupComplete\":{}}")
            withTimeout(2000) { while(!client.state.value.connected || !audio.started || audio.words.isEmpty()) delay(5) }
            assertEquals("la reserva",client.state.value.currentWord)
            assertEquals("la reserva",audio.words.first())
        }
    }
    @Test fun bareCachedNounCanGainAnArticleWithoutChangingTheExerciseOrProgress()=runBlocking {
        val known=(com.locospanish.ai.tutor.TopicLessons.vocabulary("hotel")+com.locospanish.ai.tutor.LessonReserve.words("hotel")).map { it.es }
        runClient(scenario="hotel",known=known,wordAudio=true,planner={_,_,_,_->listOf(com.locospanish.ai.tutor.LessonWord("minibar","minibar"))}) { client,socket,_ ->
            socket.event("{\"setupComplete\":{}}")
            withTimeout(2000) { while(client.state.value.currentWord!="minibar" || !client.state.value.connected) delay(5) }
            socket.event(GeminiProtocol.json("toolCall" to GeminiProtocol.json("functionCalls" to listOf(GeminiProtocol.json("id" to "refine-cached-noun","name" to "teach_word","args" to GeminiProtocol.json("term" to "el minibar","translation" to "barek"))))).toString())
            withTimeout(1500) { while(client.state.value.currentWord!="el minibar") delay(5) }
            assertEquals("minibar",client.state.value.currentMeaning)
            assertTrue(client.state.value.learning.isEmpty())
        }
    }
    @Test fun modelArticleIsVisibleAndIsTheExactNativeVoiceTarget()=runBlocking {
        runClient(scenario="hotel",wordAudio=true) { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}")
            socket.event("{\"serverContent\":{\"turnComplete\":true}}")
            delay(60);recognized(socket,"reserva")
            socket.event(GeminiProtocol.json("toolCall" to GeminiProtocol.json("functionCalls" to listOf(GeminiProtocol.json("id" to "article-task","name" to "assess_user_turn","args" to GeminiProtocol.json("heard_text" to "reserva","confidence" to "CERTAIN","verdict" to "CORRECT","next_word" to "la persiana","next_translation" to "roleta","words" to emptyList<String>()))))).toString())
            withTimeout(2000) { while(socket.sent.none { it.contains("toolResponse") && it.contains("article-task") }) delay(5) }
            assertEquals("la persiana",client.state.value.currentWord)
            val body=com.google.gson.JsonParser.parseString(socket.sent.last()).asJsonObject.getAsJsonObject("toolResponse").getAsJsonArray("functionResponses")[0].asJsonObject.getAsJsonObject("response")
            assertEquals("la persiana",body.get("current_word").asString)
            socket.event("{\"serverContent\":{\"generationComplete\":true}}")
            withTimeout(1500) { while("la persiana" !in audio.words) delay(5) }
            assertFalse("persiana" in audio.words)
        }
    }
    @Test fun aNaturalSkipRequestMovesOnWithoutMarkingAnUnknownWordAsLearned()=runBlocking {
        runClient(scenario="hotel",wordAudio=true) { client,socket,_ ->
            socket.event("{\"setupComplete\":{}}")
            socket.event("{\"serverContent\":{\"turnComplete\":true}}")
            delay(60)
            val text="Odpuśćmy ten wyraz, weźmy inne hasło"
            recognized(socket,text)
            socket.event(GeminiProtocol.json("toolCall" to GeminiProtocol.json("functionCalls" to listOf(GeminiProtocol.json("id" to "semantic-skip","name" to "assess_user_turn","args" to GeminiProtocol.json("heard_text" to text,"confidence" to "CERTAIN","verdict" to "ERROR","correction" to "la reserva","intent" to "SKIP","next_word" to "el balcón","next_translation" to "balkon","words" to emptyList<String>()))))).toString())
            withTimeout(2000) { while(socket.sent.none { it.contains("toolResponse") && it.contains("semantic-skip") }) delay(5) }
            assertEquals("el balcón",client.state.value.currentWord)
            assertTrue(client.state.value.learning.isEmpty())
        }
    }
    @Test fun nativeReferenceEntryDoesNotMaskTheModelsActualQuestion()=runBlocking {
        runClient(scenario="hotel",wordAudio=true,wordDelayMs=120) { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}")
            socket.event(GeminiProtocol.json("serverContent" to GeminiProtocol.json("outputTranscription" to GeminiProtocol.json("text" to "Zaczynamy. "))).toString())
            withTimeout(2000) { while(audio.packets==0) delay(5) }
            val before=audio.packets
            assertTrue(client.state.value.transcript.last { it.role=="assistant" }.id.startsWith("word:"))
            socket.event(GeminiProtocol.json("serverContent" to GeminiProtocol.json("outputTranscription" to GeminiProtocol.json("text" to "Jak powiesz po hiszpańsku klucz?"),"modelTurn" to GeminiProtocol.json("parts" to listOf(GeminiProtocol.json("inlineData" to GeminiProtocol.json("mimeType" to "audio/pcm;rate=24000","data" to "AAAAAA==")))),"generationComplete" to true)).toString())
            delay(100)
            assertEquals(before,audio.packets)
            assertTrue(socket.sent.last().contains("Poprzednie pytanie nie pasowało do ekranu"))
            assertEquals("la reserva",client.state.value.currentWord)
        }
    }
    @Test fun advancedLearnerGetsAConversationQuestionRatherThanAWordQuiz()=runBlocking {
        for(level in listOf(Level.A2,Level.B1,Level.B2,Level.C1,Level.C2)) runClient(settings=Settings(level=level),scenario="hotel",wordAudio=true) { client,socket,_ ->
            socket.event("{\"setupComplete\":{}}")
            socket.event("{\"serverContent\":{\"turnComplete\":true}}")
            delay(50)
            val text="Tengo una reserva"
            recognized(socket,text)
            val question=when(level) {
                Level.A2 -> "¿Quieres cambiar tu reserva?"
                Level.B1 -> "¿Por qué quieres cambiar tu reserva?"
                Level.B2 -> "¿Qué harías si el hotel perdiera tu reserva?"
                Level.C1 -> "¿Cómo negociarías una compensación por la cancelación de tu reserva?"
                else -> "¿Qué matiz aporta el registro de tu reclamación al negociar la reserva?"
            }
            socket.event(GeminiProtocol.json("toolCall" to GeminiProtocol.json("functionCalls" to listOf(GeminiProtocol.json("id" to "advanced-${level.name}","name" to "assess_user_turn","args" to GeminiProtocol.json("heard_text" to text,"evidence" to text,"confidence" to "CERTAIN","verdict" to "CORRECT","intent" to "ANSWER","next_word" to "cambiar la reserva","next_translation" to "zmienić rezerwację","next_question" to question,"words" to emptyList<String>()))))).toString())
            withTimeout(2000) { while(socket.sent.none { it.contains("toolResponse") && it.contains("advanced-${level.name}") }) delay(5) }
            val body=com.google.gson.JsonParser.parseString(socket.sent.last()).asJsonObject.getAsJsonObject("toolResponse").getAsJsonArray("functionResponses")[0].asJsonObject.getAsJsonObject("response")
            assertEquals(question,body.get("learner_question").asString)
            assertEquals("cambiar la reserva",client.state.value.currentWord)
            assertTrue(body.get("task_contract").asString.contains(level.name))
        }
    }
    @Test fun semanticHelpKeepsVisibleWordAndAnswersInsteadOfGradingOrRepeating()=runBlocking {
        runClient(scenario="hotel",wordAudio=true) { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}")
            socket.event("{\"serverContent\":{\"turnComplete\":true}}")
            delay(100)
            val before=audio.words.count { it=="la reserva" }
            val text="Kompletnie zgubiłem sens tego wyrazu"
            recognized(socket,text)
            socket.event(GeminiProtocol.json("toolCall" to GeminiProtocol.json("functionCalls" to listOf(GeminiProtocol.json("id" to "semantic-help","name" to "assess_user_turn","args" to GeminiProtocol.json("heard_text" to text,"confidence" to "CERTAIN","verdict" to "ERROR","intent" to "HELP","help_answer" to "Rezerwacja oznacza, że hotel trzyma dla ciebie pokój.","roast" to "","words" to emptyList<String>()))))).toString())
            withTimeout(2000) { while(socket.sent.none { it.contains("toolResponse") && it.contains("semantic-help") }) delay(5) }
            val body=com.google.gson.JsonParser.parseString(socket.sent.last()).asJsonObject.getAsJsonObject("toolResponse").getAsJsonArray("functionResponses")[0].asJsonObject.getAsJsonObject("response")
            assertEquals("UNCERTAIN",body.get("verified_verdict").asString)
            assertEquals("",body.get("learner_question").asString)
            assertTrue(body.get("help_answer").asString.contains("hotel"))
            assertEquals("la reserva",client.state.value.currentWord)
            assertTrue(client.state.value.learning.isEmpty())
            delay(100);assertEquals(before,audio.words.count { it=="la reserva" })
        }
    }
    @Test fun twoAnswersAdvanceAndStayVisibleWhenOnlyGenerationCompleteArrives()=runBlocking {
        runClient(scenario="hotel",wordAudio=true) { client,socket,_ ->
            socket.event("{\"setupComplete\":{}}")
            socket.event("{\"serverContent\":{\"generationComplete\":true}}")
            delay(60)
            recognized(socket,"reserva");assessment(socket,"first-without-turn-complete","reserva")
            assertEquals("la habitación",client.state.value.currentWord)
            socket.event("{\"serverContent\":{\"generationComplete\":true}}")
            delay(60)
            recognized(socket,"habitación");assessment(socket,"second-without-turn-complete","habitación")
            assertEquals("la llave",client.state.value.currentWord)
            assertEquals("klucz",client.state.value.currentMeaning)
            assertEquals(2,client.state.value.learning.count { it.kind=="known_word" })
        }
    }
    @Test fun repeatedDontKnowChangesHelpContextWithoutAudioOrProgressLoop()=runBlocking {
        runClient(scenario="hotel",wordAudio=true) { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}")
            socket.event("{\"serverContent\":{\"turnComplete\":true}}")
            delay(80)
            val before=audio.words.count { it=="la reserva" }
            repeat(3) { index ->
                recognized(socket,"nie wiem");assessment(socket,"need-help-$index","nie wiem","ERROR")
                val body=com.google.gson.JsonParser.parseString(socket.sent.last()).asJsonObject.getAsJsonObject("toolResponse").getAsJsonArray("functionResponses")[0].asJsonObject.getAsJsonObject("response")
                assertEquals(index+1,body.get("help_attempt").asInt)
                assertEquals("",body.get("learner_question").asString)
                socket.event("{\"serverContent\":{\"generationComplete\":true}}")
                delay(60)
                assertEquals("la reserva",client.state.value.currentWord)
            }
            assertTrue(client.state.value.learning.isEmpty())
            assertEquals(before,audio.words.count { it=="la reserva" })
        }
    }
    @Test fun barContinuesImmediatelyAfterAllOldWordsEvenWhenPlannerIsUnavailable()=runBlocking {
        val known=com.locospanish.ai.tutor.TopicLessons.vocabulary("bar").map { it.es }+
            listOf("la copa","el cóctel","el refresco","la aceituna","el limón","la naranja","la tapa","la terraza","la música","pedir","brindar","beber")
        var calls=0
        runClient(scenario="bar",known=known,wordAudio=true,planner={_,_,_,_->calls++;error("HTTP 429")}) { client,socket,audio ->
            assertEquals("la pajita",client.state.value.currentWord)
            socket.event("{\"setupComplete\":{}}")
            withTimeout(1000) { while(audio.packets==0) delay(5) }
            delay(80)
            assertTrue(client.state.value.taskError.isBlank());assertFalse(client.state.value.taskPending)
            assertEquals(1,calls)
            val commandsBefore=socket.sent.count { it.contains("clientContent") }
            recognized(socket,"pajita");assessment(socket,"bar-continuation","pajita")
            assertEquals("el posavasos",client.state.value.currentWord)
            assertTrue(client.state.value.taskError.isBlank());assertEquals(1,calls)
            assertEquals(commandsBefore,socket.sent.count { it.contains("clientContent") })
        }
    }
    @Test fun downloadedWordsArriveInBackgroundWithoutInterruptingCurrentWord()=runBlocking {
        val known=com.locospanish.ai.tutor.TopicLessons.vocabulary("hotel").map { it.es }
        runClient(scenario="hotel",known=known,wordAudio=true,planner={_,_,_,_->delay(180);listOf(com.locospanish.ai.tutor.LessonWord("el minibar","minibar"))}) { client,socket,_ ->
            assertEquals("el balcón",client.state.value.currentWord)
            socket.event("{\"setupComplete\":{}}")
            delay(300)
            assertEquals("el balcón",client.state.value.currentWord)
            assertTrue(client.state.value.taskError.isBlank())
            recognized(socket,"balcón");assessment(socket,"finish-before-download","balcón")
            assertEquals("el minibar",client.state.value.currentWord)
        }
    }
    @Test fun mobileLatencyBeyondThreeAndAHalfSecondsDoesNotDiscardNewMaterial()=runBlocking {
        val known=(com.locospanish.ai.tutor.TopicLessons.vocabulary("hotel")+com.locospanish.ai.tutor.LessonReserve.words("hotel")).map { it.es }
        runClient(scenario="hotel",known=known,wordAudio=true,responseTimeoutMs=150,taskLimit=8000,planner={_,_,_,_->delay(3900);listOf(com.locospanish.ai.tutor.LessonWord("el minibar","minibar"))}) { client,socket,_ ->
            socket.event("{\"setupComplete\":{}}")
            delay(150)
            socket.event("{\"serverContent\":{\"turnComplete\":true}}")
            delay(50)
            assertEquals(VoicePhase.THINKING,client.state.value.phase)
            assertTrue(client.state.value.taskPending)
            withTimeout(6000) { while(client.state.value.currentWord!="el minibar") delay(10) }
            assertTrue(client.state.value.taskError.isBlank());assertFalse(client.state.value.taskPending)
            assertEquals(1,socket.sent.count { it.contains("clientContent") && it.contains("Dobierz nowe hasło") })
        }
    }
    @Test fun automaticReferenceDoesNotRepeatAcrossErrorTurnsButRepeatButtonStillWorks()=runBlocking {
        runClient(scenario="hotel",wordAudio=true) { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}")
            withTimeout(1000) { while(audio.words.isEmpty()) delay(5) }
            socket.event("{\"serverContent\":{\"turnComplete\":true}}")
            for((index,wrong) in listOf("gato","perro","gato").withIndex()) {
                recognized(socket,wrong);assessment(socket,"wrong-$index",wrong,"ERROR")
                val body=com.google.gson.JsonParser.parseString(socket.sent.last()).asJsonObject.getAsJsonObject("toolResponse").getAsJsonArray("functionResponses")[0].asJsonObject.getAsJsonObject("response")
                assertTrue(body.get("word_audio_played").asBoolean)
                assertFalse(body.get("word_audio_pending").asBoolean)
                socket.event("{\"serverContent\":{\"turnComplete\":true}}")
                delay(50)
            }
            assertEquals(1,audio.words.count { it=="la reserva" })
            assertEquals("la reserva",client.state.value.currentWord)
            client.action(LessonAction.REPEAT)
            socket.event("{\"serverContent\":{\"turnComplete\":true}}")
            withTimeout(1000) { while(audio.words.count { it=="la reserva" }<2) delay(5) }
            assertEquals(2,audio.words.count { it=="la reserva" })
        }
    }
    /** A long, messy conversation with a scripted Gemini: after every learner turn LOCO must speak again or listen, never stall. */
    @Test fun longMessyConversationNeverGoesSilent()=runBlocking {
        for(level in listOf(Level.A1,Level.A2,Level.B1)) runClient(settings=Settings(level=level),scenario="hotel",assessmentRetryMs=150,cardFallbackMs=300,silenceGuardMs=500) { client,socket,audio ->
            kotlinx.coroutines.coroutineScope {
                val chunk=android.util.Base64.encodeToString(ByteArray(19200),android.util.Base64.NO_WRAP)
                fun card()=com.locospanish.ai.tutor.LessonWord(client.state.value.currentWord,client.state.value.currentMeaning)
                fun speak(text:String,complete:Boolean=true) {
                    socket.event(GeminiProtocol.json("serverContent" to GeminiProtocol.json("outputTranscription" to GeminiProtocol.json("text" to text))).toString())
                    repeat(3) { socket.event("{\"serverContent\":{\"modelTurn\":{\"parts\":[{\"inlineData\":{\"mimeType\":\"audio/pcm;rate=24000\",\"data\":\"$chunk\"}}]}}}") }
                    if(complete) { socket.event("{\"serverContent\":{\"generationComplete\":true}}");socket.event("{\"serverContent\":{\"turnComplete\":true}}") }
                }
                fun reply()="Ja pierdolę, ty pajacu, recepcjonistka już się śmieje z twojej walizki. "+com.locospanish.ai.tutor.LessonTurnPolicy.question(card())
                var toolNo=0
                fun grade(heard:String,verdict:String) {
                    val id="soak-${level}-${toolNo++}"
                    socket.event(GeminiProtocol.json("toolCall" to GeminiProtocol.json("functionCalls" to listOf(GeminiProtocol.json("id" to id,"name" to "assess_user_turn","args" to GeminiProtocol.json(
                        "verdict" to verdict,"heard_text" to heard,"evidence" to heard,"confidence" to "CERTAIN","correction" to if(verdict=="ERROR") card().es else "","explanation" to if(verdict=="ERROR") "Inne słowo." else "",
                        "error_kind" to "VOCABULARY","roast" to "Kurwa, z takim $heard recepcja da ci kuwetę zamiast pokoju.","words" to emptyList<String>()))))).toString())
                }
                var lastHeard="";var lastVerdict="CORRECT";var nudgesWithoutTool=0;var emptyReplies=0;var cutReplies=0
                fun answer() {
                    when {
                        emptyReplies>0 -> { emptyReplies--;socket.event("{\"serverContent\":{\"turnComplete\":true}}") }
                        cutReplies>0 -> { cutReplies--;speak("Ja pierdolę, ty",complete=false);socket.event("{\"serverContent\":{\"interrupted\":true}}");socket.event("{\"serverContent\":{\"turnComplete\":true}}") }
                        else -> speak(reply())
                    }
                }
                var seen=0
                val responder=launch {
                    while(true) {
                        val batch=synchronized(socket.sent) { socket.sent.drop(seen).also { seen=socket.sent.size } }
                        for(message in batch) {
                            delay(25)
                            when {
                                message.contains("TECHNICZNE PONOWIENIE") -> if(nudgesWithoutTool-->0) speak("Kurwa, gadaj dalej, matole.") else grade(lastHeard,lastVerdict)
                                message.contains("toolResponse") -> if(message.contains("assess_user_turn") || message.contains("soak-")) answer()
                                message.contains("clientContent") -> answer()
                            }
                        }
                        delay(10)
                    }
                }
                Diag.clear()
                socket.event("{\"setupComplete\":{}}")
                withTimeout(3000) { while(client.state.value.currentWord.isBlank()) delay(10) }
                suspend fun turn(label:String,block:suspend ()->Unit) {
                    delay(150)
                    val before=audio.packets
                    block()
                    val ok=withTimeoutOrNull(5000) { while(!(audio.packets>before && client.state.value.phase!=VoicePhase.THINKING)) delay(20);true } ?: false
                    if(!ok) Diag.dump().lines().takeLast(140).forEach { android.util.Log.e("LocoDiag",it) }
                    assertTrue("$level / $label: LOCO went silent (phase=${client.state.value.phase}).",ok)
                    assertTrue("$level / $label: disconnected",client.state.value.connected && client.state.value.phase!=VoicePhase.ERROR)
                }
                repeat(3) { round ->
                    turn("correct $round") { val w=card().es;lastHeard=w;lastVerdict="CORRECT";recognized(socket,w);delay(30);grade(w,"CORRECT") }
                    turn("wrong $round") { lastHeard="gato";lastVerdict="ERROR";recognized(socket,"gato");delay(30);grade("gato","ERROR") }
                    turn("grade skipped once $round") { val w=card().es;lastHeard=w;lastVerdict="CORRECT";recognized(socket,w) }
                    turn("grade skipped twice $round") { nudgesWithoutTool=1;lastHeard="eee no sé";lastVerdict="UNCERTAIN";recognized(socket,"eee no sé") }
                    turn("premature draft $round") { val w=card().es;lastHeard=w;lastVerdict="CORRECT";recognized(socket,w);speak("No i co tu",complete=false);delay(30);grade(w,"CORRECT") }
                    turn("question $round") { lastHeard="co to znaczy?";lastVerdict="UNCERTAIN";recognized(socket,"co to znaczy?");delay(30);grade("co to znaczy?","UNCERTAIN") }
                    // Gemini answers a grade with an empty turn; then its own voice through the speaker cuts the reply.
                    turn("empty reply $round") { val w=card().es;lastHeard=w;lastVerdict="CORRECT";emptyReplies=1;recognized(socket,w);delay(30);grade(w,"CORRECT") }
                    turn("echo cut $round") { lastHeard="gato";lastVerdict="ERROR";cutReplies=1;recognized(socket,"gato");delay(30);grade("gato","ERROR");delay(200);val cut=audio.packets
                        val resumed=withTimeoutOrNull(4000) { while(audio.packets==cut) delay(20);true } ?: false
                        if(!resumed) Diag.dump().lines().takeLast(80).forEach { android.util.Log.e("LocoDiag",it) }
                        assertTrue("$level: LOCO stayed silent after its reply was cut",resumed) }
                    turn("other lesson word $round") { lastHeard="mañana";lastVerdict="CORRECT";recognized(socket,"mañana");delay(30);grade("mañana","CORRECT") }
                }
                responder.cancel()
            }
        }
    }
    @Test fun aReplyWithoutAGradeAfterTheRetryIsStillHeardNotSilenced()=runBlocking {
        runClient(scenario="hotel",assessmentRetryMs=120) { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}")
            delay(60);recognized(socket,"gato")
            withTimeout(1000) { while(socket.sent.none { it.contains("TECHNICZNE PONOWIENIE OCENY") }) delay(5) }
            val before=audio.packets
            socket.event(GeminiProtocol.json("serverContent" to GeminiProtocol.json("outputTranscription" to GeminiProtocol.json("text" to "Kurwa, recepcja dalej czeka na rezerwację."))).toString())
            socket.event("{\"serverContent\":{\"modelTurn\":{\"parts\":[{\"inlineData\":{\"mimeType\":\"audio/pcm;rate=24000\",\"data\":\"AAAAAA==\"}}]}}}")
            socket.event("{\"serverContent\":{\"turnComplete\":true}}")
            withTimeout(1500) { while(audio.packets==before) delay(10) }
            assertNotEquals(VoicePhase.THINKING,client.state.value.phase)
            assertEquals("la reserva",client.state.value.currentWord)
        }
    }
    @Test fun missingAssessmentIsRequestedOnceAndThenAdvancesExactlyOneWord()=runBlocking {
        runClient(scenario="hotel",wordAudio=true,assessmentRetryMs=120) { client,socket,_ ->
            socket.event("{\"setupComplete\":{}}")
            delay(60);recognized(socket,"reserva")
            withTimeout(1000) { while(socket.sent.none { it.contains("TECHNICZNE PONOWIENIE OCENY") }) delay(5) }
            assertEquals("la reserva",client.state.value.currentWord)
            assertTrue(client.state.value.learning.none { it.kind=="known_word" })
            assessment(socket,"recovered-grade","reserva")
            assertEquals("la habitación",client.state.value.currentWord)
            val count=client.state.value.learning.count { it.kind=="known_word" }
            assessment(socket,"same-answer-again","reserva")
            delay(200)
            assertEquals("la habitación",client.state.value.currentWord)
            assertEquals(count,client.state.value.learning.count { it.kind=="known_word" })
            assertEquals(1,socket.sent.count { it.contains("clientContent") && it.contains("TECHNICZNE PONOWIENIE OCENY") })
        }
    }
    @Test fun everyUnassessedActualUtteranceGetsOneRecoveryWithoutAutomaticCredit()=runBlocking {
        for(level in listOf(Level.A1,Level.A2,Level.B2)) for(utterance in listOf("gato","Co znaczy gracias?")) {
            runClient(settings=Settings(level=level),scenario="hotel",assessmentRetryMs=80) { client,socket,_ ->
                socket.event("{\"setupComplete\":{}}")
                delay(50);val before=client.state.value.currentWord;recognized(socket,utterance)
                withTimeout(1200) { while(socket.sent.none { it.contains("TECHNICZNE PONOWIENIE OCENY") }) delay(5) }
                delay(100)
                val recovery=socket.sent.filter { it.contains("TECHNICZNE PONOWIENIE OCENY") }
                assertEquals(1,recovery.size);assertTrue(recovery.single().contains("poprawność NIE jest z góry ustalona"))
                assertEquals(before,client.state.value.currentWord)
                assertTrue(client.state.value.learning.none { it.kind=="known_word" })
            }
        }
    }
    @Test fun duplicatedAsrWordAdvancesOnceAndPresentsTheNextWordOnce()=runBlocking {
        runClient(scenario="hotel",wordAudio=true) { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}")
            withTimeout(1000) { while(audio.words.isEmpty()) delay(5) }
            socket.event("{\"serverContent\":{\"turnComplete\":true}}")
            recognized(socket,"reserva reserva")
            assessment(socket,"duplicated-word","reserva reserva","UNCERTAIN")
            assertEquals("la habitación",client.state.value.currentWord)
            socket.event("{\"serverContent\":{\"turnComplete\":true}}")
            withTimeout(1000) { while("la habitación" !in audio.words) delay(5) }
            assertEquals(1,audio.words.count { it=="la reserva" })
            assertEquals(1,audio.words.count { it=="la habitación" })
            assertEquals(1,client.state.value.learning.count { it.kind=="known_word" })
        }
    }
    @Test fun unrelatedSpokenRoastGetsOneRepairWithoutChangingTheWord()=runBlocking {
        runClient(scenario="hotel") { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}")
            delay(60);recognized(socket,"gato")
            socket.event(GeminiProtocol.json("toolCall" to GeminiProtocol.json("functionCalls" to listOf(GeminiProtocol.json("id" to "off-topic-roast","name" to "assess_user_turn","args" to GeminiProtocol.json("verdict" to "ERROR","heard_text" to "gato","confidence" to "CERTAIN","correction" to "la reserva","explanation" to "Ćwiczymy rezerwację, nie kota.","error_kind" to "VOCABULARY","evidence" to "gato","roast" to "Kurwa, twój router właśnie odpalił kabaret.","words" to emptyList<String>()))))).toString())
            withTimeout(2000) { while(socket.sent.none { it.contains("off-topic-roast") && it.contains("toolResponse") }) delay(5) }
            val response=com.google.gson.JsonParser.parseString(socket.sent.last()).asJsonObject.getAsJsonObject("toolResponse").getAsJsonArray("functionResponses")[0].asJsonObject.getAsJsonObject("response")
            assertEquals("",response.get("planned_roast").asString)
            fun answer(text:String)=socket.event(GeminiProtocol.json("serverContent" to GeminiProtocol.json("outputTranscription" to GeminiProtocol.json("text" to text),"modelTurn" to GeminiProtocol.json("parts" to listOf(GeminiProtocol.json("inlineData" to GeminiProtocol.json("mimeType" to "audio/pcm;rate=24000","data" to "AAAAAA==")))),"turnComplete" to true)).toString())
            answer("Kurwa, twój router właśnie odpalił kabaret. Jak powiesz po hiszpańsku rezerwacja?")
            delay(80);assertEquals(listOf(96000),audio.packetSizes)
            assertTrue(socket.sent.last().contains("Brakuje świeżej, mocnej riposty"))
            answer("Kurwa, z takim gato zarezerwujesz kotu recepcję zamiast pokoju. Jak powiesz po hiszpańsku rezerwacja?")
            withTimeout(1000) { while(4 !in audio.packetSizes) delay(5) }
            assertEquals(listOf(4),audio.packetSizes.filter { it<24000 });assertTrue(audio.packetSizes.any { it>=24000 });assertEquals("la reserva",client.state.value.currentWord)
            assertTrue(client.state.value.learning.none { it.kind=="known_word" })
            assertEquals(1,socket.sent.count { it.contains("clientContent") && it.contains("Brakuje świeżej, mocnej riposty") })
        }
    }
    @Test fun interruptedOpeningStillRecoversInsteadOfListeningInSilence()=runBlocking {
        runClient(scenario="hotel",responseTimeoutMs=180) { client,socket,_ ->
            socket.event("{\"setupComplete\":{}}")
            delay(50)
            socket.event("{\"serverContent\":{\"interrupted\":true,\"turnComplete\":true}}")
            delay(50);assertEquals(VoicePhase.THINKING,client.state.value.phase)
            delay(200);assertEquals(VoicePhase.RECONNECTING,client.state.value.phase)
            assertEquals("la reserva",client.state.value.currentWord)
        }
    }
    @Test fun openingReferencePlaysBeforeAnyLiveAnswer()=runBlocking {
        runClient(scenario="hotel",wordAudio=true,wordDelayMs=180) { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}")
            withTimeout(1000) { while(!audio.started) delay(5) }
            audio.input!!(ByteArray(32))
            assertFalse(socket.sent.any { it.contains("realtimeInput") && it.contains("data") })
            withTimeout(1000) { while(audio.packets==0) delay(5) }
            assertEquals("la reserva",audio.words.first())
            assertEquals("la reserva",client.state.value.currentWord)
            assertTrue(socket.sent.any { it.contains("Aplikacja odtwarza już pierwsze słowo") })
            audio.input!!(ByteArray(32))
            assertTrue(socket.sent.any { it.contains("realtimeInput") && it.contains("data") })
        }
    }
    @Test fun generatedOpeningWordAlsoPlaysWithoutWaitingForLiveComment()=runBlocking {
        val known=(com.locospanish.ai.tutor.TopicLessons.vocabulary("hotel")+com.locospanish.ai.tutor.LessonReserve.words("hotel")).map { it.es }
        runClient(scenario="hotel",known=known,wordAudio=true,planner={_,_,_,_->delay(120);listOf(com.locospanish.ai.tutor.LessonWord("el minibar","minibar"))}) { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}")
            withTimeout(1800) { while(audio.packets==0) delay(5) }
            assertEquals("el minibar",client.state.value.currentWord)
            assertEquals("el minibar",audio.words.first())
        }
    }
    @Test fun generationCompleteReleasesVerifiedAudioWithoutWaitingForTurnComplete()=runBlocking {
        runClient(scenario="hotel") { _,socket,audio ->
            socket.event("{\"setupComplete\":{}}")
            delay(80)
            socket.event(GeminiProtocol.json("serverContent" to GeminiProtocol.json("outputTranscription" to GeminiProtocol.json("text" to "Jak powiesz po hiszpańsku rezerwacja?"),"modelTurn" to GeminiProtocol.json("parts" to listOf(GeminiProtocol.json("inlineData" to GeminiProtocol.json("mimeType" to "audio/pcm;rate=24000","data" to "AAAAAA==")))))).toString())
            delay(60);assertEquals(0,audio.packets)
            socket.event("{\"serverContent\":{\"generationComplete\":true}}")
            withTimeout(1000) { while(audio.packets==0) delay(5) }
            socket.event("{\"serverContent\":{\"turnComplete\":true}}")
            delay(70);assertEquals(1,audio.packets)
        }
    }
    @Test fun unfinishedBufferedAudioDoesNotDisableAutomaticRecovery()=runBlocking {
        runClient(scenario="hotel",responseTimeoutMs=180) { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}")
            delay(60)
            socket.event("{\"serverContent\":{\"modelTurn\":{\"parts\":[{\"inlineData\":{\"mimeType\":\"audio/pcm;rate=24000\",\"data\":\"AAAAAA==\"}}]}}}")
            delay(220)
            assertEquals(0,audio.packets)
            assertEquals(VoicePhase.RECONNECTING,client.state.value.phase)
            withTimeout(2000) { while(socket.connections<2) delay(10) }
            assertEquals(2,socket.connections)
        }
    }
    @Test fun confirmedVocabularyErrorKeepsTaskAndSpeaksItsContextualRiposte()=runBlocking {
        runClient(scenario="hotel") { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}")
            delay(60);recognized(socket,"gato")
            val joke="Kurwa, z takim gato zarezerwujesz kotu recepcję zamiast pokoju."
            socket.event(GeminiProtocol.json("toolCall" to GeminiProtocol.json("functionCalls" to listOf(GeminiProtocol.json("id" to "real-word-error","name" to "assess_user_turn","args" to GeminiProtocol.json("verdict" to "ERROR","heard_text" to "gato","confidence" to "CERTAIN","correction" to "la reserva","explanation" to "Gato to kot, a ćwiczymy rezerwację.","error_kind" to "VOCABULARY","roast" to joke,"words" to emptyList<String>()))))).toString())
            withTimeout(2000) { while(socket.sent.none { it.contains("real-word-error") && it.contains("toolResponse") }) delay(5) }
            val body=com.google.gson.JsonParser.parseString(socket.sent.last()).asJsonObject.getAsJsonObject("toolResponse").getAsJsonArray("functionResponses")[0].asJsonObject.getAsJsonObject("response")
            assertEquals("ERROR",body.get("verified_verdict").asString)
            assertTrue(body.get("planned_roast").asString.startsWith(joke.trimEnd('.')))
            assertEquals("la reserva",client.state.value.currentWord)
            val script=body.get("spoken_script").asString
            socket.event(GeminiProtocol.json("serverContent" to GeminiProtocol.json("outputTranscription" to GeminiProtocol.json("text" to script),"modelTurn" to GeminiProtocol.json("parts" to listOf(GeminiProtocol.json("inlineData" to GeminiProtocol.json("mimeType" to "audio/pcm;rate=24000","data" to "AAAAAA==")))))).toString())
            withTimeout(800) { while(4 !in audio.packetSizes) delay(5) }
            assertEquals(listOf(96000,4),audio.packetSizes)
            val commands=socket.sent.count { it.contains("clientContent") }
            socket.event("{\"serverContent\":{\"generationComplete\":true}}")
            withTimeout(1500) { while(audio.packetSizes.none { it>=24000 }) delay(5) }
            assertEquals(2,audio.packets)
            assertEquals(commands,socket.sent.count { it.contains("clientContent") })
            assertTrue(client.state.value.learning.none { it.kind=="known_word" })
        }
    }
    @Test fun correctAnswerIsAnsweredWithoutWaitingForTheWholeReply()=runBlocking {
        runClient(scenario="hotel") { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}")
            delay(60);recognized(socket,"reserva")
            val joke="Kurwa, twoja rezerwacja właśnie otworzyła recepcji kabaret."
            socket.event(GeminiProtocol.json("toolCall" to GeminiProtocol.json("functionCalls" to listOf(GeminiProtocol.json("id" to "early-audio","name" to "assess_user_turn","args" to GeminiProtocol.json("verdict" to "CORRECT","heard_text" to "reserva","confidence" to "CERTAIN","roast" to joke,"words" to emptyList<String>()))))).toString())
            withTimeout(2000) { while(socket.sent.none { it.contains("early-audio") && it.contains("toolResponse") }) delay(5) }
            val body=com.google.gson.JsonParser.parseString(socket.sent.last()).asJsonObject.getAsJsonObject("toolResponse").getAsJsonArray("functionResponses")[0].asJsonObject.getAsJsonObject("response")
            // A correct answer also gets a riposte, but it is spoken without waiting for validation.
            assertTrue(body.get("planned_roast").asString.isNotBlank())
            fun emitAudio(text:String,done:Boolean)=socket.event(GeminiProtocol.json("serverContent" to GeminiProtocol.json("outputTranscription" to GeminiProtocol.json("text" to text),"modelTurn" to GeminiProtocol.json("parts" to listOf(GeminiProtocol.json("inlineData" to GeminiProtocol.json("mimeType" to "audio/pcm;rate=24000","data" to "AAAAAA==")))),"turnComplete" to done)).toString())
            emitAudio("Dobrze.",false)
            delay(60)
            // Under 0.5 s of audio is collected before the first sound instead of being played in fragments.
            assertEquals(0,audio.packets)
            val interrupts=audio.interrupts
            emitAudio(" Jak powiesz po hiszpańsku „pokój”?",true);delay(70)
            assertEquals(listOf(8),audio.packetSizes);assertEquals(interrupts,audio.interrupts)
            assertEquals("la habitación",client.state.value.currentWord)
        }
    }
    @Test fun laughterCanAccompanyARiposteButCannotReplaceTheReply()=runBlocking {
        runClient(scenario="hotel") { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}")
            delay(60)
            fun answer(text:String)=socket.event(GeminiProtocol.json("serverContent" to GeminiProtocol.json("outputTranscription" to GeminiProtocol.json("text" to text),"modelTurn" to GeminiProtocol.json("parts" to listOf(GeminiProtocol.json("inlineData" to GeminiProtocol.json("mimeType" to "audio/pcm;rate=24000","data" to "AAAAAA==")))),"turnComplete" to true)).toString())
            answer("Hahaha, kurwa!");delay(70)
            assertEquals(0,audio.packets)
            assertTrue(socket.sent.last().contains("Nie odtwarzaj samego śmiechu"))
            answer("Kurwa, recepcjonista otworzył dla ciebie muzeum kolejek, ha ha! Jak powiesz po hiszpańsku rezerwacja?")
            delay(70)
            assertEquals(1,audio.packets)
            assertTrue(client.state.value.connected)
        }
    }
    @Test fun unavailableReferenceRequestsSpokenHelpFromLiveOnlyOnce()=runBlocking {
        runClient(scenario="hotel") { client,socket,_ ->
            socket.event("{\"setupComplete\":{}}")
            delay(70)
            recognized(socket,"Jak to powiedzieć?")
            withTimeout(2500) { while(socket.sent.none { it.contains("Wzorzec wymowy aplikacji jest niedostępny") }) delay(10) }
            assertTrue(client.state.value.connected)
            assertEquals(1,socket.sent.count { it.contains("Wzorzec wymowy aplikacji jest niedostępny") })
            assertTrue(client.state.value.learning.isEmpty())
        }
    }
    @Test fun correctIndefiniteArticleTargetAcceptsDefiniteAnswerDespiteModelError()=runBlocking {
        runClient(scenario="bar",wordAudio=true) { client,socket,_ ->
            socket.event("{\"setupComplete\":{}}")
            socket.event("{\"serverContent\":{\"turnComplete\":true}}")
            delay(70)
            assertEquals("una bebida",client.state.value.currentWord)
            recognized(socket,"la bebida")
            socket.event(GeminiProtocol.json("toolCall" to GeminiProtocol.json("functionCalls" to listOf(GeminiProtocol.json("id" to "article-variant","name" to "assess_user_turn","args" to GeminiProtocol.json("verdict" to "ERROR","heard_text" to "bebida","confidence" to "UNCERTAIN","correction" to "una bebida","explanation" to "Zmyślony błąd rodzajnika","error_kind" to "GRAMMAR","words" to emptyList<String>()))))).toString())
            withTimeout(2000) { while(socket.sent.none { it.contains("toolResponse") && it.contains("article-variant") }) delay(5) }
            assertTrue(socket.sent.last().contains("verified_verdict\":\"CORRECT"))
            assertNotEquals("una bebida",client.state.value.currentWord)
            assertTrue(client.state.value.learning.any { it.kind=="known_word" && it.term=="bebida" })
            assertTrue(client.state.value.learning.none { it.kind in setOf("grammar","pronunciation") })
        }
    }
    @Test fun pronunciationHelpPlaysReferenceWithoutAnyModelToolAndRejectsJokeOnlyReply()=runBlocking {
        runClient(scenario="hotel",wordAudio=true) { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}")
            socket.event("{\"serverContent\":{\"turnComplete\":true}}")
            delay(90)
            val before=audio.packets
            recognized(socket,"Jak to powiedzieć?")
            withTimeout(2500) { while(audio.packets<=before) delay(10) }
            assertEquals("la reserva",audio.words.last())
            assertTrue(client.state.value.learning.isEmpty())
            val afterReference=audio.packets
            fun answer(text:String)=socket.event(GeminiProtocol.json("serverContent" to GeminiProtocol.json("outputTranscription" to GeminiProtocol.json("text" to text),"modelTurn" to GeminiProtocol.json("parts" to listOf(GeminiProtocol.json("inlineData" to GeminiProtocol.json("mimeType" to "audio/pcm;rate=24000","data" to "AAAAAA==")))),"turnComplete" to true)).toString())
            answer("Ha ha, kurwa, recepcjonista kupił popcorn!")
            delay(80)
            assertEquals(afterReference,audio.packets)
            assertTrue(socket.sent.last().contains("Po hiszpańsku: la reserva"))
            answer("Po hiszpańsku la reserva. To znaczy rezerwacja. Kurwa, recepcja odetchnęła.")
            delay(80)
            assertTrue(audio.packets>afterReference)
            assertEquals("la reserva",client.state.value.currentWord)
            assertTrue(client.state.value.learning.isEmpty())
        }
    }
    @Test fun helpToolAnswersFirstAndDoesNotMarkQuestionAsAnError()=runBlocking {
        runClient(scenario="hotel",wordAudio=true) { client,socket,_ ->
            socket.event("{\"setupComplete\":{}}")
            socket.event("{\"serverContent\":{\"turnComplete\":true}}")
            delay(70)
            recognized(socket,"Jak to powiedzieć?");assessment(socket,"pronunciation-help","Jak to powiedzieć?","ERROR")
            val body=com.google.gson.JsonParser.parseString(socket.sent.last()).asJsonObject.getAsJsonObject("toolResponse").getAsJsonArray("functionResponses")[0].asJsonObject.getAsJsonObject("response")
            assertTrue(body.get("delivery_instruction").asString.startsWith("NAJPIERW odpowiedz"))
            assertTrue(body.get("help_answer").asString.contains("la reserva"))
            assertTrue(body.get("word_audio_pending").asBoolean)
            assertFalse(body.get("allow_next_word").asBoolean)
            assertEquals("UNCERTAIN",body.get("verified_verdict").asString)
            assertTrue(client.state.value.learning.isEmpty())
        }
    }
    @Test fun audioCannotRejectALocallyConfirmedCorrectWord()=runBlocking {
        runClient(scenario="hotel") { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}")
            socket.event("{\"serverContent\":{\"turnComplete\":true}}")
            delay(60)
            recognized(socket,"reserva");assessment(socket,"correct-word","reserva")
            val before=audio.packets
            socket.event(GeminiProtocol.json("serverContent" to GeminiProtocol.json("outputTranscription" to GeminiProtocol.json("text" to "Źle, powiedziałeś to źle."),"modelTurn" to GeminiProtocol.json("parts" to listOf(GeminiProtocol.json("inlineData" to GeminiProtocol.json("mimeType" to "audio/pcm;rate=24000","data" to "AAAAAA==")))),"turnComplete" to true)).toString())
            delay(80)
            assertEquals(before,audio.packets)
            assertTrue(socket.sent.last().contains("Poprawna odpowiedź ucznia została potwierdzona"))
            assertTrue(client.state.value.learning.any { it.kind=="known_word" })
            assessment(socket,"control-assessment","","UNCERTAIN")
            socket.event(GeminiProtocol.json("serverContent" to GeminiProtocol.json("outputTranscription" to GeminiProtocol.json("text" to "Niepoprawna odpowiedź."),"modelTurn" to GeminiProtocol.json("parts" to listOf(GeminiProtocol.json("inlineData" to GeminiProtocol.json("mimeType" to "audio/pcm;rate=24000","data" to "AAAAAA==")))),"turnComplete" to true)).toString())
            delay(80);assertEquals(before,audio.packets)
        }
    }
    @Test fun wrongSpokenQuestionIsBlockedThenRetriedAgainstDisplayedWord()=runBlocking {
        for(level in Level.entries) runClient(settings=Settings(level=level),scenario="hotel") { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}")
            delay(70)
            // Each level starts from its own material: a word on A1, a phrase on A2, a whole sentence from B1.
            val card=client.state.value.currentWord;val meaning=client.state.value.currentMeaning
            assertEquals(level==Level.A1,com.locospanish.ai.tutor.LessonTurnPolicy.singleWord(card))
            if(level>=Level.B1) assertTrue(card,card.split(' ').size>=4)
            fun answer(text:String)=socket.event(GeminiProtocol.json("serverContent" to GeminiProtocol.json("outputTranscription" to GeminiProtocol.json("text" to text),"modelTurn" to GeminiProtocol.json("parts" to listOf(GeminiProtocol.json("inlineData" to GeminiProtocol.json("mimeType" to "audio/pcm;rate=24000","data" to "AAAAAA==")))),"turnComplete" to true)).toString())
            answer("Jak powiesz po hiszpańsku pokój?")
            delay(80)
            assertEquals(0,audio.packets)
            assertEquals(card,client.state.value.currentWord)
            assertTrue(socket.sent.last().contains(meaning))
            assertFalse(client.state.value.transcript.any { it.text.contains("hiszpańsku pokój") })
            answer("Kurwa, recepcja już liczy krzesła. Jak powiesz po hiszpańsku $meaning?")
            delay(80)
            assertEquals(1,audio.packets)
            assertEquals(card,client.state.value.currentWord)
        }
    }
    @Test fun repeatedToolIdReturnsCurrentTaskInsteadOfOldCachedWord()=runBlocking {
        runClient(scenario="hotel") { client,socket,_ ->
            socket.event("{\"setupComplete\":{}}")
            delay(60)
            val call=GeminiProtocol.json("toolCall" to GeminiProtocol.json("functionCalls" to listOf(GeminiProtocol.json("id" to "cached-target","name" to "teach_word","args" to GeminiProtocol.json("term" to "la reserva","translation" to "rezerwacja"))))).toString()
            socket.event(call);delay(60)
            client.action(LessonAction.NEXT)
            assertEquals("la habitación",client.state.value.currentWord)
            socket.event(call);delay(60)
            val response=com.google.gson.JsonParser.parseString(socket.sent.last()).asJsonObject.getAsJsonObject("toolResponse").getAsJsonArray("functionResponses")[0].asJsonObject.getAsJsonObject("response")
            assertEquals("la habitación",response.get("current_word").asString)
            assertTrue(response.get("learner_question").asString.contains("pokój"))
            assertTrue(response.get("task_contract").asString.contains("la habitación"))
        }
    }
    @Test fun repeatedWrongQuestionDoesNotLoopOrChangeDisplayedTask()=runBlocking {
        runClient(scenario="hotel") { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}")
            delay(60)
            val wrong=GeminiProtocol.json("serverContent" to GeminiProtocol.json("outputTranscription" to GeminiProtocol.json("text" to "Jak powiesz po hiszpańsku klucz?"),"modelTurn" to GeminiProtocol.json("parts" to listOf(GeminiProtocol.json("inlineData" to GeminiProtocol.json("mimeType" to "audio/pcm;rate=24000","data" to "AAAAAA==")))),"turnComplete" to true)).toString()
            socket.event(wrong);delay(60);socket.event(wrong);delay(60)
            assertEquals(0,audio.packets)
            assertEquals(1,socket.sent.count { it.contains("Poprzednie pytanie nie pasowało") })
            assertEquals("la reserva",client.state.value.currentWord)
            assertTrue(client.state.value.message.contains("rezerwacja"))
            assertTrue(client.state.value.connected)
        }
    }
    @Test fun roastContextRefersToAttemptedWordRatherThanNextExercise()=runBlocking {
        runClient(scenario="hotel",wordAudio=true) { client,socket,_ ->
            socket.event("{\"setupComplete\":{}}")
            socket.event("{\"serverContent\":{\"turnComplete\":true}}")
            delay(80)
            recognized(socket,"reserva");assessment(socket,"roast-target","reserva")
            val response=com.google.gson.JsonParser.parseString(socket.sent.last()).asJsonObject.getAsJsonObject("toolResponse").getAsJsonArray("functionResponses")[0].asJsonObject.getAsJsonObject("response")
            val context=response.getAsJsonObject("roast_context")
            assertEquals("la reserva",context.get("attempted_word").asString)
            assertEquals(client.state.value.currentWord,context.get("next_word").asString)
            assertNotEquals(context.get("attempted_word").asString,context.get("next_word").asString)
        }
    }
    private class FakeAudio : LiveAudio {
        val words=java.util.concurrent.CopyOnWriteArrayList<String>()
        var playbackSpeed=1f
        override fun setSpeed(speed:Float) { this.playbackSpeed=speed }
        var started=false; var closed=false; var muted=false; var interrupts=0; var packets=0
        val packetSizes=mutableListOf<Int>()
        val nativeTexts=mutableListOf<String>()
        var meter: ((Float)->Unit)? = null
        override fun observeLevel(listener:(Float)->Unit) { meter=listener }
        var input: ((ByteArray)->Unit)? = null
        override fun start(onInput:(ByteArray)->Unit,onError:()->Unit,onPlaying:(Boolean)->Unit) { started=true; input=onInput }
        override fun enqueue(bytes:ByteArray):Boolean { packets++;packetSizes.add(bytes.size);return true }
        override fun interrupt() { interrupts++ }
        override fun mute(value:Boolean) { muted=value }
        override fun close() { closed=true }
    }
    private class FakeSocket : WebSocket, WebSocket.Factory {
        lateinit var listener:WebSocketListener
        lateinit var req:Request
        val sent=mutableListOf<String>()
        var cancelled=false; var connections=0
        override fun newWebSocket(request:Request,listener:WebSocketListener):WebSocket { this.req=request; this.listener=listener; cancelled=false; connections++; return this }
        override fun request()=req
        override fun queueSize()=0L
        override fun send(text:String):Boolean { synchronized(sent) { sent.add(text) }; return !cancelled }
        override fun send(bytes:ByteString)=true
        override fun close(code:Int,reason:String?)=true
        override fun cancel(){ cancelled=true }
        fun open(){ listener.onOpen(this,Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(101).message("test").build()) }
        fun event(json:String){ listener.onMessage(this,json) }
    }
    private suspend fun runClient(settings: Settings = Settings(), scenario: String = "daily", responseTimeoutMs: Long = 40000, wordAudio: Boolean = false, wordDelayMs:Long = 0, memory:String = "", known:Collection<String> = emptyList(), prepared:MutableList<String>?=null, planner:suspend (String,String,Level,Collection<String>)->List<com.locospanish.ai.tutor.LessonWord> = {_,_,_,_->suspendCancellableCoroutine { }}, taskLimit:Long=10000, assessmentRetryMs:Long=1800,waitForCard:Boolean=false,nativeAudio:Boolean=false,cardFallbackMs:Long=60000,silenceGuardMs:Long=60000, block:suspend (GeminiLiveClient,FakeSocket,FakeAudio)->Unit) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        val socket=FakeSocket(); val audio=FakeAudio()
        val client=GeminiLiveClient(context,scope,http=socket,audioFactory={audio}, responseTimeoutMs=responseTimeoutMs,wordPlanner=planner,taskTimeoutMs=taskLimit,wordCache=null, assessmentRetryMs=assessmentRetryMs,waitForLessonCard=waitForCard,cardFallbackMs=cardFallbackMs,silenceGuardMs=silenceGuardMs, wordSpeakerFactory={ _,_ -> object: SupplementSpeaker { override suspend fun prepare(text:String) { prepared?.add(text) }; override suspend fun synthesize(text:String):ByteArray { if(!wordAudio) error("Wzorzec niedostępny w teście");delay(wordDelayMs);audio.words.add(text);return ByteArray(4) };override fun close() {} } }, supplementFactory = { object: SupplementSpeaker {
            override suspend fun synthesize(text: String): ByteArray { if(!nativeAudio) error("No Polish voice installed");audio.nativeTexts.add(text);return ByteArray(8) { 2 } }
            override fun close() {}
        } })
        try { withContext(Dispatchers.Main) { client.start(settings,"FAKE_TEST_KEY",scenario,memory,knownWords=known.toList()); block(client,socket,audio) } }
        finally { withContext(Dispatchers.Main) { client.stop() }; scope.cancel() }
    }
    private fun recognized(socket:FakeSocket,text:String) {
        socket.event(GeminiProtocol.json("serverContent" to GeminiProtocol.json("inputTranscription" to GeminiProtocol.json("text" to text))).toString())
    }
    private suspend fun assessment(socket:FakeSocket,id:String,text:String,verdict:String="CORRECT",words:List<com.google.gson.JsonObject> = emptyList()) {
        socket.event(GeminiProtocol.json("toolCall" to GeminiProtocol.json("functionCalls" to listOf(GeminiProtocol.json("id" to id,"name" to "assess_user_turn","args" to GeminiProtocol.json("verdict" to verdict,"heard_text" to text,"evidence" to text,"confidence" to "CERTAIN","correction" to "","explanation" to "","error_kind" to "GRAMMAR","words" to words))))).toString())
        withTimeout(2000) { while(socket.sent.none { it.contains("toolResponse") && it.contains(id) }) delay(5) }
    }
    @Test fun correctWordHeardByGeminiAdvancesDespiteAGarbledTranscript()=runBlocking {
        runClient(scenario="hotel") { client,socket,_ ->
            socket.event("{\"setupComplete\":{}}");delay(80)
            val target=client.state.value.currentWord
            val garbled="erre se va"
            socket.event("{\"serverContent\":{\"turnComplete\":true}}")
            recognized(socket,garbled)
            // Gemini heard the right word; only the automatic transcript is broken.
            socket.event(GeminiProtocol.json("toolCall" to GeminiProtocol.json("functionCalls" to listOf(GeminiProtocol.json("id" to "garbled","name" to "assess_user_turn","args" to GeminiProtocol.json("verdict" to "CORRECT","heard_text" to target,"evidence" to target,"confidence" to "CERTAIN","correction" to "","explanation" to "","error_kind" to "NONE","words" to emptyList<String>()))))).toString())
            withTimeout(2000) { while(socket.sent.none { it.contains("toolResponse") && it.contains("garbled") }) delay(5) }
            val body=com.google.gson.JsonParser.parseString(socket.sent.last { it.contains("garbled") }).asJsonObject.getAsJsonObject("toolResponse").getAsJsonArray("functionResponses")[0].asJsonObject.getAsJsonObject("response")
            assertEquals("CORRECT",body.get("verified_verdict").asString)
            assertTrue(body.get("word_trial_accepted").asBoolean)
            // The visible transcript shows what Gemini heard, not the garbled text.
            assertEquals(target,client.state.value.transcript.last { it.role=="user" }.text)
            withTimeout(1000) { while(client.state.value.currentWord==target) delay(5) }
            assertTrue(client.state.value.learning.any { com.locospanish.ai.tutor.AssessmentPolicy.key(it.term)==com.locospanish.ai.tutor.AssessmentPolicy.key(target) })
        }
    }
    @Test fun plannerOutageContinuesEveryScenarioAndDoesNotAdvanceOnSilence()=runBlocking {
        for(scenario in com.locospanish.ai.tutor.TopicLessons.plans.keys) {
            val known=com.locospanish.ai.tutor.TopicLessons.vocabulary(scenario).map { it.es }
            var calls=0
            runClient(scenario=scenario,known=known,wordAudio=true,taskLimit=150,planner={_,_,_,_->calls++;throw IllegalStateException("HTTP 429")}) { client,socket,audio ->
                socket.event("{\"setupComplete\":{}}")
                withTimeout(2000) { while(client.state.value.currentWord.isBlank()) delay(10) }
                val first=client.state.value.currentWord
                assertTrue(com.locospanish.ai.tutor.LessonReserve.words(scenario).any { it.es==first })
                assertFalse(client.state.value.taskPending);assertTrue(client.state.value.taskError.isBlank())
                socket.event("{\"serverContent\":{\"turnComplete\":true}}")
                delay(40)
                assertEquals(first,client.state.value.currentWord);assertTrue(client.state.value.learning.isEmpty())
                client.action(LessonAction.NEXT)
                assertNotEquals(first,client.state.value.currentWord)
                assertTrue(client.state.value.currentMeaning.isNotBlank());assertEquals(1,calls)
                assertTrue(client.state.value.connected);assertFalse(audio.closed)
            }
        }
    }
    @Test fun stalledPlannerUsesReserveBeforeWaitingForVoiceTool()=runBlocking {
        val known=com.locospanish.ai.tutor.TopicLessons.vocabulary("daily").map { it.es }
        runClient(scenario="daily",known=known,taskLimit=150) { client,socket,_ ->
            socket.event("{\"setupComplete\":{}}")
            withTimeout(2000) { while(client.state.value.currentWord.isBlank() || !client.state.value.connected) delay(10) }
            assertEquals("despertarse",client.state.value.currentWord)
            assertTrue(client.state.value.taskError.isBlank());assertFalse(client.state.value.taskPending)
            assertTrue(client.state.value.connected)
        }
    }
    @Test fun downloadedMaterialSurvivesCacheRecreationWithoutRepeatingKnownWords() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val topic="cache-test-${System.nanoTime()}"
        val words=com.locospanish.ai.tutor.LessonReserve.words("daily")
        LessonWordCache(context).save(topic,Level.A1,words)
        val reopened=LessonWordCache(context)
        assertEquals(words,reopened.read(topic,Level.A1,emptyList()))
        assertEquals(words.drop(15),reopened.read(topic,Level.A1,words.take(15).map { it.es }))
        assertTrue(reopened.read(topic,Level.B1,emptyList()).isEmpty())
        assertTrue(reopened.read("other-$topic",Level.A1,emptyList()).isEmpty())
    }
    @Test fun firstTaskIsAvailableBeforeLiveSetupOnEveryLevel()=runBlocking {
        for(level in Level.entries) runClient(settings=Settings(level=level)) { client,_,_ ->
            assertFalse(client.state.value.connected);assertTrue(client.state.value.currentWord.isNotBlank())
            assertTrue(client.state.value.learning.isEmpty())
        }
    }
    @Test fun exhaustedVocabularyGetsNewBatchWithoutVoiceToolCall()=runBlocking {
        val known=(com.locospanish.ai.tutor.TopicLessons.vocabulary("hotel")+com.locospanish.ai.tutor.LessonReserve.words("hotel")).map { it.es }
        runClient(scenario="hotel",known=known,wordAudio=true,planner={_,_,_,_->delay(200);listOf(com.locospanish.ai.tutor.LessonWord("el minibar","minibar"),com.locospanish.ai.tutor.LessonWord("la persiana","roleta"))}) { client,socket,_ ->
            socket.event("{\"setupComplete\":{}}")
            withTimeout(2000) { while(client.state.value.currentWord!="el minibar") delay(10) }
            assertFalse(client.state.value.taskPending);assertTrue(client.state.value.learning.isEmpty())
            socket.event("{\"serverContent\":{\"turnComplete\":true}}")
            recognized(socket,"minibar");assessment(socket,"buffer-next","minibar")
            assertEquals("la persiana",client.state.value.currentWord)
        }
    }
    @Test fun plannerTimeoutKeepsVoiceConnectedAndOffersRetry()=runBlocking {
        val known=(com.locospanish.ai.tutor.TopicLessons.vocabulary("hotel")+com.locospanish.ai.tutor.LessonReserve.words("hotel")).map { it.es }
        runClient(scenario="hotel",known=known,taskLimit=150) { client,socket,_ ->
            socket.event("{\"setupComplete\":{}}")
            withTimeout(2000) { while(client.state.value.taskError.isBlank()) delay(10) }
            assertTrue(client.state.value.connected);assertNotEquals(VoicePhase.ERROR,client.state.value.phase)
            assertFalse(client.state.value.taskPending);assertTrue(client.state.value.taskError.isNotBlank())
        }
    }
    @Test fun failedPlannerCanRetryWithoutClosingLiveSocket()=runBlocking {
        val calls=java.util.concurrent.atomic.AtomicInteger()
        val known=(com.locospanish.ai.tutor.TopicLessons.vocabulary("hotel")+com.locospanish.ai.tutor.LessonReserve.words("hotel")).map { it.es }
        runClient(scenario="hotel",known=known,wordAudio=true,planner={_,_,_,_->delay(100);if(calls.incrementAndGet()==1) error("quota");listOf(com.locospanish.ai.tutor.LessonWord("el minibar","minibar"))}) { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}")
            withTimeout(2000) { while(client.state.value.taskError.isBlank()) delay(10) }
            assertTrue(client.state.value.connected);assertFalse(audio.closed)
            client.retryTask()
            withTimeout(2000) { while(client.state.value.currentWord!="el minibar") delay(10) }
            assertEquals(1,socket.connections);assertTrue(client.state.value.connected)
            assertTrue(client.state.value.taskError.isBlank())
        }
    }
    @Test fun liveToolCanSupplyTaskWhenSeparatePlannerFails()=runBlocking {
        val known=(com.locospanish.ai.tutor.TopicLessons.vocabulary("hotel")+com.locospanish.ai.tutor.LessonReserve.words("hotel")).map { it.es }
        runClient(scenario="hotel",known=known,wordAudio=true,planner={_,_,_,_->delay(100);error("unavailable")}) { client,socket,_ ->
            socket.event("{\"setupComplete\":{}}")
            withTimeout(2000) { while(client.state.value.taskError.isBlank()) delay(10) }
            assertTrue(client.state.value.connected)
            socket.event(GeminiProtocol.json("toolCall" to GeminiProtocol.json("functionCalls" to listOf(GeminiProtocol.json("id" to "live-fallback","name" to "teach_word","args" to GeminiProtocol.json("term" to "el minibar","translation" to "minibar"))))).toString())
            withTimeout(2000) { while(client.state.value.currentWord!="el minibar") delay(10) }
            assertTrue(client.state.value.taskError.isBlank());assertTrue(client.state.value.connected)
        }
    }
    @Test fun lastLocalWordContinuesThroughIndependentPlanner()=runBlocking {
        val known=(com.locospanish.ai.tutor.TopicLessons.vocabulary("hotel").dropLast(1)+com.locospanish.ai.tutor.LessonReserve.words("hotel")).map { it.es }
        runClient(scenario="hotel",known=known,wordAudio=true,planner={_,_,_,_->delay(200);listOf(com.locospanish.ai.tutor.LessonWord("el minibar","minibar"))}) { client,socket,_ ->
            assertEquals("el baño",client.state.value.currentWord)
            socket.event("{\"setupComplete\":{}}")
            socket.event("{\"serverContent\":{\"turnComplete\":true}}")
            delay(100);recognized(socket,"baño");assessment(socket,"exhausted","baño")
            withTimeout(2000) { while(client.state.value.currentWord!="el minibar") delay(10) }
            assertFalse(client.state.value.taskPending)
        }
    }
    @Test fun higherLevelPhraseUsesSeparateSpanishReference()=runBlocking {
        runClient(settings=Settings(level=Level.A2),scenario="hotel",wordAudio=true) { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}" );delay(80)
            val phrase="Necesito una habitación tranquila"
            socket.event(GeminiProtocol.json("toolCall" to GeminiProtocol.json("functionCalls" to listOf(GeminiProtocol.json("id" to "phrase","name" to "teach_word","args" to GeminiProtocol.json("term" to phrase,"translation" to "Potrzebuję spokojnego pokoju"))))).toString())
            withTimeout(2000) { while(client.state.value.currentWord!=phrase) delay(10) }
            assertEquals(phrase,client.state.value.currentWord)
            socket.event("{\"serverContent\":{\"turnComplete\":true}}")
            withTimeout(2000) { while(phrase !in audio.words) delay(10) }
            assertEquals(phrase,client.state.value.currentWord)
        }
    }
    @Test fun spanishReferenceRejectsChangedAccentAndWrongPcmRate()=runBlocking {
        for((spoken,mime) in listOf("papa" to "audio/pcm;rate=24000","papá" to "audio/pcm;rate=16000")) {
            val context=InstrumentationRegistry.getInstrumentation().targetContext;val socket=FakeSocket()
            val speaker=com.locospanish.ai.voice.SpanishWordSpeech(context,"FAKE_TEST_KEY","accent-"+java.util.UUID.randomUUID(),socket)
            try {
                val result=async(Dispatchers.Main) { runCatching { speaker.synthesize("papá") }.exceptionOrNull() };delay(100)
                socket.open();socket.event("{\"setupComplete\":{}}" );delay(80)
                socket.event(GeminiProtocol.json("serverContent" to GeminiProtocol.json("outputTranscription" to GeminiProtocol.json("text" to spoken),"modelTurn" to GeminiProtocol.json("parts" to listOf(GeminiProtocol.json("inlineData" to GeminiProtocol.json("mimeType" to mime,"data" to "AAAAAA==")))),"turnComplete" to true)).toString())
                assertNotNull(result.await())
            } finally { speaker.close() }
        }
    }
    @Test fun prefetchDoesNotAdvanceTaskOrEarnVocabulary()=runBlocking {
        val prepared=java.util.concurrent.CopyOnWriteArrayList<String>()
        runClient(scenario="hotel",wordAudio=true,prepared=prepared) { client,socket,_ ->
            socket.event("{\"setupComplete\":{}}" );delay(80)
            socket.event("{\"serverContent\":{\"turnComplete\":true}}")
            withTimeout(2000) { while("la habitación" !in prepared) delay(10) }
            assertEquals("la reserva",client.state.value.currentWord)
            assertTrue(client.state.value.learning.isEmpty())
            recognized(socket,"reserva");assessment(socket,"prefetched","reserva")
            socket.event("{\"serverContent\":{\"turnComplete\":true}}")
            withTimeout(2000) { while("la llave" !in prepared) delay(10) }
            assertEquals("la habitación",client.state.value.currentWord)
        }
    }
    @Test fun heavyHistoryKeepsWordUpdatesAndRoastReplyFast()=runBlocking {
        val known=com.locospanish.ai.tutor.TopicLessons.plans.keys.filter { it!="hotel" }.flatMap { com.locospanish.ai.tutor.TopicLessons.vocabulary(it) }.map { it.es }.filter { term -> com.locospanish.ai.tutor.TopicLessons.vocabulary("hotel").none { com.locospanish.ai.tutor.AssessmentPolicy.key(it.es)==com.locospanish.ai.tutor.AssessmentPolicy.key(term) } }
        runClient(scenario="hotel",known=known,wordAudio=true,wordDelayMs=3000) { client,socket,_ ->
            socket.event("{\"setupComplete\":{}}" );delay(80)
            delay(60)
            repeat(8) { i ->
                val old=client.state.value.currentWord
                assertTrue(old.isNotBlank())
                socket.event("{\"serverContent\":{\"turnComplete\":true}}")
                val start=android.os.SystemClock.elapsedRealtime()
                recognized(socket,old)
                assessment(socket,"fast-word-$i",old)
                withTimeout(750) { while(client.state.value.currentWord==old) delay(5) }
                val elapsed=android.os.SystemClock.elapsedRealtime()-start
                assertTrue("Word display must update without waiting for audio: $elapsed ms",elapsed<750)
                assertTrue(socket.sent.last().contains("NAJPIERW W AUDIO"))
            }
        }
    }
    @Test fun questionsAnswerWithoutForcingTrialOrRecordingProgress()=runBlocking {
        runClient(scenario="hotel",wordAudio=true) { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}" );delay(80)
            delay(60)
            val target=client.state.value.currentWord
            recognized(socket,"Dlaczego reserva ma taki rodzajnik?")
            assessment(socket,"real-question","Dlaczego reserva ma taki rodzajnik?","ERROR")
            delay(80)
            val reply=com.google.gson.JsonParser.parseString(socket.sent.last()).asJsonObject.getAsJsonObject("toolResponse").getAsJsonArray("functionResponses")[0].asJsonObject.getAsJsonObject("response")
            assertEquals(target,client.state.value.currentWord)
            assertTrue(client.state.value.learning.isEmpty())
            assertFalse(reply.get("allow_next_word").asBoolean)
            assertEquals("",reply.get("learner_question").asString)
            assertTrue(reply.get("delivery_instruction").asString.contains("rzeczywiste pytanie"))
            assertFalse(reply.get("word_audio_pending").asBoolean)
            assertTrue(audio.words.size<=1)
        }
    }
    @Test fun typedControlsPreserveWordAndChangeRealAudioSpeed()=runBlocking {
        runClient(scenario="hotel") { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}" );delay(80)
            delay(60)
            val target=client.state.value.currentWord
            for(action in listOf(com.locospanish.ai.tutor.LessonAction.REPEAT,com.locospanish.ai.tutor.LessonAction.EXPLAIN,com.locospanish.ai.tutor.LessonAction.TRANSLATE,com.locospanish.ai.tutor.LessonAction.SLOWER)) {
                client.action(action)
                assertEquals(target,client.state.value.currentWord)
                assertTrue(socket.sent.last().contains("clientContent"))
            }
            assertEquals(.75f,audio.playbackSpeed,0f)
            client.action(com.locospanish.ai.tutor.LessonAction.NEXT)
            assertNotEquals(target,client.state.value.currentWord)
            val second=client.state.value.currentWord
            client.action(com.locospanish.ai.tutor.LessonAction.NEXT)
            assertNotEquals(second,client.state.value.currentWord)
            assertNotEquals(target,client.state.value.currentWord)
            client.mute();assertTrue(audio.muted)
            client.mute();assertFalse(audio.muted)
        }
    }
    @Test fun quizRemainsActiveAcrossAnswersQuestionsAndSkips()=runBlocking {
        runClient(scenario="hotel",known=listOf("la reserva","la habitación","la llave")) { client,socket,_ ->
            socket.event("{\"setupComplete\":{}}" );delay(80)
            delay(60)
            val lesson=client.state.value.currentWord
            client.action(com.locospanish.ai.tutor.LessonAction.REVIEW)
            assertTrue(client.state.value.reviewMode)
            assertTrue(client.state.value.reviewTotal>=3)
            val target=client.state.value.currentWord
            recognized(socket,"Dlaczego to słowo ma rodzajnik?")
            assessment(socket,"quiz-question","Dlaczego to słowo ma rodzajnik?")
            delay(50)
            assertEquals(0,client.state.value.reviewIndex)
            assertEquals(target,client.state.value.currentWord)
            socket.event("{\"serverContent\":{\"turnComplete\":true}}")
            recognized(socket,"nie wiem");assessment(socket,"quiz-help","nie wiem","UNCERTAIN");delay(50)
            assertEquals(0,client.state.value.reviewIndex)
            socket.event("{\"serverContent\":{\"turnComplete\":true}}")
            recognized(socket,target);assessment(socket,"quiz-correct",target);delay(50)
            assertEquals(1,client.state.value.reviewIndex)
            assertTrue(client.state.value.reviewMode)
            assertNotEquals(target,client.state.value.currentWord)
            val learning=client.state.value.learning.size
            while(!client.state.value.reviewCompleted) client.action(com.locospanish.ai.tutor.LessonAction.NEXT)
            assertTrue(client.state.value.reviewMode)
            assertEquals(1,client.state.value.reviewCorrect)
            assertEquals(learning,client.state.value.learning.size)
            assertEquals("",client.state.value.currentWord)
            client.action(com.locospanish.ai.tutor.LessonAction.RESUME)
            assertFalse(client.state.value.reviewMode)
            assertEquals(lesson,client.state.value.currentWord)
        }
    }
    @Test fun questionDirectionAndRepeatedRoastAreFixedInActualToolResponse()=runBlocking {
        runClient(scenario="hotel") { _,socket,_ ->
            socket.event("{\"setupComplete\":{}}" );delay(80)
            recognized(socket,"reserva")
            val joke="Twój mózg odpalił tryb samolotowy, kurwa."
            fun grade(id:String,word:String)=socket.event(GeminiProtocol.json("toolCall" to GeminiProtocol.json("functionCalls" to listOf(GeminiProtocol.json("id" to id,"name" to "assess_user_turn","args" to GeminiProtocol.json("verdict" to "CORRECT","heard_text" to word,"confidence" to "CERTAIN","roast" to joke))))).toString())
            fun response()=com.google.gson.JsonParser.parseString(socket.sent.last()).asJsonObject.getAsJsonObject("toolResponse").getAsJsonArray("functionResponses")[0].asJsonObject.getAsJsonObject("response")
            grade("first-joke","reserva");delay(80)
            assertEquals("es",response().get("learner_target_language").asString)
            assertTrue(response().get("learner_question").asString.contains("po hiszpańsku"))
            socket.event(GeminiProtocol.json("serverContent" to GeminiProtocol.json("outputTranscription" to GeminiProtocol.json("text" to joke),"turnComplete" to true)).toString())
            recognized(socket,"habitación");grade("repeated-joke","habitación");delay(80)
            assertEquals("",response().get("planned_roast").asString)
            assertTrue(response().get("roast_needs_new_idea").asBoolean)
            assertTrue(response().get("creative_roast_instruction").asString.contains("NOWĄ"))
            assertTrue(response().get("recent_roasts_to_avoid").asJsonArray.any { it.asString.contains("samolotowy") })
            assertEquals("Jak powiesz po hiszpańsku „klucz”?",response().get("learner_question").asString)
        }
    }
    @Test fun audioLevelFollowsDeviceAndResetsWhenStopped()=runBlocking {
        runClient { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}" );delay(80)
            delay(60);audio.meter!!.invoke(.8f);delay(60)
            assertEquals(.8f,client.audioLevel.value,.001f)
            client.stop();audio.meter!!.invoke(1f);delay(60)
            assertEquals(0f,client.audioLevel.value,0f)
        }
    }
    @Test fun nextCommandUpdatesScreenAndReturnsAuthoritativeWord()=runBlocking {
        runClient(scenario="hotel") { client,socket,_ ->
            socket.event("{\"setupComplete\":{}}" );delay(80)
            delay(80)
            val old=client.state.value.currentWord
            client.command("Chcę kontynuować lekcję. Nie powtarzaj poprzedniego słowa.")
            socket.event(GeminiProtocol.json("toolCall" to GeminiProtocol.json("functionCalls" to listOf(GeminiProtocol.json("id" to "screen-next","name" to "teach_word","args" to GeminiProtocol.json("term" to "café","translation" to "kawa"))))).toString())
            delay(100)
            assertNotEquals(old,client.state.value.currentWord)
            val reply=com.google.gson.JsonParser.parseString(socket.sent.last()).asJsonObject.getAsJsonObject("toolResponse").getAsJsonArray("functionResponses")[0].asJsonObject.getAsJsonObject("response")
            assertEquals(client.state.value.currentWord,reply.get("current_word").asString)
            assertEquals(client.state.value.currentMeaning,reply.get("current_translation").asString)
        }
    }
    @Test fun answerToPreviousAudibleWordIsNotCalledWrongBeforeNewReference()=runBlocking {
        runClient(scenario="hotel",wordAudio=true,wordDelayMs=200) { client,socket,_ ->
            socket.event("{\"setupComplete\":{}}" );delay(80)
            socket.event("{\"serverContent\":{\"turnComplete\":true}}")
            delay(260)
            recognized(socket,"reserva");assessment(socket,"first-correct","reserva");delay(30)
            assertEquals("la habitación",client.state.value.currentWord)
            socket.event(GeminiProtocol.json("serverContent" to GeminiProtocol.json("interrupted" to true,"inputTranscription" to GeminiProtocol.json("text" to "reserva"))).toString())
            assessment(socket,"old-audible-answer","reserva");delay(40)
            assertTrue(socket.sent.last().contains("verified_verdict\":\"CORRECT"))
            assertTrue(socket.sent.last().contains("allow_next_word\":false"))
            assertEquals("la habitación",client.state.value.currentWord)
            assertTrue(client.state.value.learning.none { it.kind in setOf("grammar","pronunciation") })
        }
    }
    @Test fun reconnectRetainsCurrentHotelTarget()=runBlocking {
        runClient(scenario="hotel") { client,socket,_ ->
            socket.event("{\"setupComplete\":{}}" );delay(80)
            recognized(socket,"reserva");assessment(socket,"before-reconnect","reserva");delay(80)
            assertEquals("la habitación",client.state.value.currentWord)
            socket.listener.onClosing(socket,1011,"Internal error")
            delay(1200);socket.open();socket.event("{\"setupComplete\":{}}" );delay(80)
            delay(80)
            assertEquals("la habitación",client.state.value.currentWord)
            assertTrue(socket.sent.last().contains("clientContent"))
            recognized(socket,"habitación");assessment(socket,"after-reconnect","habitación");delay(80)
            assertEquals("la llave",client.state.value.currentWord)
        }
    }
    @Test fun scenarioHistoryCannotOverrideInitialHotelWord()=runBlocking {
        runClient(scenario="hotel",memory="KONTYNUACJA_LEKCJI: rozmowa o jedzeniu",known=listOf("reserva")) { client,socket,_ ->
            socket.event("{\"setupComplete\":{}}" );delay(80)
            delay(100)
            assertEquals("la habitación",client.state.value.currentWord)
            recognized(socket,"habitación")
            socket.event(GeminiProtocol.json("toolCall" to GeminiProtocol.json("functionCalls" to listOf(GeminiProtocol.json("id" to "false-model-error","name" to "assess_user_turn","args" to GeminiProtocol.json("verdict" to "ERROR","heard_text" to "habitación","confidence" to "CERTAIN","error_kind" to "PRONUNCIATION","correction" to "habitación","explanation" to "zła wymowa","next_word" to "café","next_translation" to "kawa","learning_topic" to "jedzenie"))))).toString())
            delay(100)
            assertTrue(socket.sent.last().contains("verified_verdict\":\"CORRECT"))
            assertEquals("la llave",client.state.value.currentWord)
            assertTrue(client.state.value.learning.any { it.kind == "known_word" && it.term == "habitación" })
        }
    }
    @Test fun continuousHotelWordsDoNotRepeatOrFollowFoodProposal()=runBlocking {
        runClient(scenario="hotel") { client,socket,_ ->
            socket.event("{\"setupComplete\":{}}" );delay(80)
            delay(60)
            val seen=mutableSetOf<String>()
            repeat(10) { i ->
                val word=client.state.value.currentWord
                assertTrue(seen.add(word))
                assertTrue(com.locospanish.ai.tutor.TopicLessons.vocabulary("hotel").any { com.locospanish.ai.tutor.TeachingForm.complete(it).es == word })
                socket.event("{\"serverContent\":{\"turnComplete\":true}}")
                recognized(socket,word)
                assessment(socket,"word-$i",word)
                delay(50)
                assertTrue(socket.sent.last().contains("word_trial_accepted\":true"))
            }
            assertEquals(10,client.state.value.learning.count { it.kind == "known_word" })
        }
    }
    @Test fun toolReplyDoesNotWaitForSlowSpanishGeneration()=runBlocking {
        runClient(scenario="hotel",wordAudio=true,wordDelayMs=3000) { _,socket,audio ->
            socket.event("{\"setupComplete\":{}}" );delay(80)
            delay(80);recognized(socket,"reserva");assessment(socket,"fast-reply","reserva")
            withTimeout(1000) { while(!socket.sent.any { it.contains("toolResponse") && it.contains("fast-reply") }) delay(10) }
            assertTrue(audio.words.isEmpty())
            assertTrue(socket.sent.last().contains("word_audio_pending\":true"))
        }
    }
    @Test fun acknowledgmentAndWrongWordDoNotAdvanceOrBecomeKnown()=runBlocking {
        runClient(scenario="hotel",wordAudio=true) { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}" );delay(80)
            delay(100)
            recognized(socket,"tak");assessment(socket,"ack","tak");delay(100)
            assertTrue(socket.sent.last().contains("allow_next_word\":false"));assertTrue(client.state.value.learning.none { it.kind in setOf("word","known_word") })
            socket.event("{\"serverContent\":{\"turnComplete\":true}}")
            recognized(socket,"gato");assessment(socket,"wrong-word","gato");delay(100)
            assertTrue(socket.sent.last().contains("verified_verdict\":\"ERROR"))
            assertTrue(audio.words.all { it == "la reserva" })
            assertTrue(client.state.value.learning.none { it.kind in setOf("word","known_word") })
        }
    }
    @Test fun silentToolAndRepeatedAssessmentCannotAdvanceTarget()=runBlocking {
        runClient(scenario="hotel",wordAudio=true) { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}" );delay(80)
            delay(100);assessment(socket,"no-input","reserva");delay(100)
            assertEquals(listOf("la reserva"),audio.words);assertTrue(client.state.value.learning.isEmpty())
            recognized(socket,"reserva");assessment(socket,"actual","reserva");delay(100)
            assertEquals(listOf("la reserva","la habitación"),audio.words)
            assessment(socket,"different-tool-id","reserva");delay(100)
            assertEquals(2,audio.words.size);assertTrue(socket.sent.last().contains("duplicate_attempt\":true"))
        }
    }
    @Test fun spanishConnectionIsReusedForDifferentUncachedWords()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val socket=FakeSocket()
        val speaker=com.locospanish.ai.voice.SpanishWordSpeech(context,"FAKE_TEST_KEY","reuse-test-"+java.util.UUID.randomUUID(),socket)
        fun reply(word:String)=socket.event(GeminiProtocol.json("serverContent" to GeminiProtocol.json("outputTranscription" to GeminiProtocol.json("text" to word),"modelTurn" to GeminiProtocol.json("parts" to listOf(GeminiProtocol.json("inlineData" to GeminiProtocol.json("mimeType" to "audio/pcm;rate=24000","data" to "AAAAAA==")))),"turnComplete" to true)).toString())
        try {
            val first=async(Dispatchers.Main){speaker.synthesize("la llave")};delay(100);socket.open();socket.event("{\"setupComplete\":{}}" );delay(80)
            reply("la llave");assertEquals(4,first.await().size)
            val second=async(Dispatchers.Main){speaker.synthesize("el perro")};delay(100);reply("el perro");assertEquals(4,second.await().size)
            assertEquals(1,socket.connections)
        } finally { speaker.close() }
    }
    @Test fun a1CannotBeCompletedByFirstWord()=runBlocking {
        runClient { client,socket,_ ->
            socket.event("{\"setupComplete\":{}}" );delay(80)
            socket.event("{\"toolCall\":{\"functionCalls\":[{\"id\":\"early-finish\",\"name\":\"complete_scenario\",\"args\":{\"evidence\":\"perro\"}}]}}")
            delay(100);assertFalse(client.state.value.completed);assertTrue(client.state.value.connected)
        }
    }
    @Test fun spanishReferenceHasSpanishOnlyContextAndReusesCache()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val socket=FakeSocket()
        val speaker=com.locospanish.ai.voice.SpanishWordSpeech(context,"FAKE_TEST_KEY","cache-test-"+java.util.UUID.randomUUID(),socket)
        try {
            val first=async(Dispatchers.Main) { speaker.synthesize("la llave") };delay(100)
            socket.open();assertTrue(socket.sent.single().contains("SOLAMENTE español"));assertFalse(socket.sent.single().contains("functionDeclarations"))
            socket.event("{\"setupComplete\":{}}" );delay(80)
            socket.event("{\"serverContent\":{\"outputTranscription\":{\"text\":\"La llave.\"},\"modelTurn\":{\"parts\":[{\"inlineData\":{\"mimeType\":\"audio/pcm;rate=24000\",\"data\":\"AAAAAA==\"}}]},\"turnComplete\":true}}")
            assertEquals(4,first.await().size)
            assertEquals(4,speaker.synthesize("la llave").size);assertEquals(1,socket.connections)
        } finally { speaker.close() }
    }
    @Test fun spanishReferenceRejectsUnexpectedSentence()=runBlocking {
        supervisorScope {
            val context=InstrumentationRegistry.getInstrumentation().targetContext
            val socket=FakeSocket()
            val speaker=com.locospanish.ai.voice.SpanishWordSpeech(context,"FAKE_TEST_KEY","reject-test-"+java.util.UUID.randomUUID(),socket)
            try {
                val first=async(Dispatchers.Main) { runCatching { speaker.synthesize("la llave") }.exceptionOrNull() };delay(100)
                socket.open();socket.event("{\"setupComplete\":{}}" );delay(80)
                socket.event("{\"serverContent\":{\"outputTranscription\":{\"text\":\"Necesito la llave.\"},\"modelTurn\":{\"parts\":[{\"inlineData\":{\"mimeType\":\"audio/pcm;rate=24000\",\"data\":\"AAAAAA==\"}}]},\"turnComplete\":true}}")
                assertNotNull(first.await())
            } finally { speaker.close() }
        }
    }
    @Test fun explicitReplayPlaysCachedWordInsteadOfAdvancingLesson()=runBlocking {
        runClient(scenario="hotel",wordAudio=true) { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}" );delay(80)
            delay(100);client.command("Powtórz wymowę ostatniego słowa")
            socket.event(GeminiProtocol.json("toolCall" to GeminiProtocol.json("functionCalls" to listOf(GeminiProtocol.json("id" to "replay-word","name" to "teach_word","args" to GeminiProtocol.json("term" to "la reserva","translation" to "rezerwacja"))))).toString())
            delay(100);assertEquals(listOf("la reserva","la reserva"),audio.words)
            assertTrue(client.state.value.learning.isEmpty())
        }
    }
    @Test fun spanishWordIsPlayedOnceAndTeacherExampleDoesNotBecomeProgress()=runBlocking {
        runClient(scenario="hotel",wordAudio=true) { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}" );delay(80)
            delay(100);assertEquals(listOf("la reserva"),audio.words);assertEquals(0,audio.interrupts)
            assertTrue(client.state.value.learning.isEmpty())
            recognized(socket,"reserva")
            assessment(socket,"spanish-word","reserva",words=listOf(GeminiProtocol.json("term" to "reserva","translation" to "rezerwacja","evidence" to "reserva","known" to true)))
            delay(100);assertEquals(listOf("la reserva","la habitación"),audio.words)
            assessment(socket,"spanish-word","reserva");delay(100)
            assertEquals(2,audio.words.size)
            assertTrue(socket.sent.last().contains("word_audio_pending\":true"))
            assertFalse(client.state.value.learning.any { it.term == "habitación" })
        }
    }
    @Test fun draftBeforeAssessmentIsDiscardedAndValidatedAudioPlaysOnce()=runBlocking {
        runClient(settings=Settings(personality=Settings().personality.copy(roastMode=false))) { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}" );delay(80)
            recognized(socket,"hoy")
            socket.event("{\"serverContent\":{\"outputTranscription\":{\"text\":\"Wstęp do powtórzenia\"},\"modelTurn\":{\"parts\":[{\"inlineData\":{\"mimeType\":\"audio/pcm;rate=24000\",\"data\":\"AAAAAA==\"}}]}}}")
            delay(80);assertEquals(0,audio.packets)
            assessment(socket,"draft-grade","hoy");delay(80)
            socket.event("{\"serverContent\":{\"outputTranscription\":{\"text\":\"Nowa lekcja\"},\"modelTurn\":{\"parts\":[{\"inlineData\":{\"mimeType\":\"audio/pcm;rate=24000\",\"data\":\"AAAAAA==\"}}]},\"turnComplete\":true}}")
            delay(80);assertEquals(1,audio.packets)
            assertFalse(client.state.value.transcript.any { it.text.contains("Wstęp do powtórzenia") })
        }
    }
    @Test fun requestWithoutAssessmentStillGetsOneCompleteAnswer()=runBlocking {
        runClient { _,socket,audio ->
            socket.event("{\"setupComplete\":{}}" );delay(80)
            recognized(socket,"Chcę uczyć się zwierząt")
            socket.event("{\"serverContent\":{\"modelTurn\":{\"parts\":[{\"inlineData\":{\"mimeType\":\"audio/pcm;rate=24000\",\"data\":\"AAAAAA==\"}}]},\"turnComplete\":true}}")
            delay(100);assertEquals(1,audio.packets)
        }
    }
    @Test fun duplicateAssessmentReturnsSameFeedbackAndDoesNotResetLesson()=runBlocking {
        runClient(scenario="hotel") { client,socket,_ ->
            socket.event("{\"setupComplete\":{}}" );delay(80)
            recognized(socket,"reserva")
            assessment(socket,"repeat-tool","reserva",words=listOf(GeminiProtocol.json("term" to "reserva","translation" to "rezerwacja","evidence" to "reserva","known" to true)))
            delay(100); val first=socket.sent.last()
            assessment(socket,"repeat-tool","reserva")
            delay(100);assertEquals(first,socket.sent.last())
            assertEquals(1,client.state.value.learning.count { it.term == "reserva" })
            assertTrue(first.contains("lesson_instruction"));assertTrue(first.contains("la habitación"))
        }
    }
    @Test fun microphoneStartsOnlyAfterSetupAndMuteStopsSending()=runBlocking {
        runClient { client,socket,audio ->
            assertFalse(audio.started); socket.open(); delay(50)
            assertTrue(socket.sent.single().contains("\"setup\""))
            assertEquals("FAKE_TEST_KEY",socket.req.header("x-goog-api-key")); assertFalse(socket.req.url.toString().contains("FAKE_TEST_KEY"))
            socket.event("{\"setupComplete\":{}}" );delay(80)
            withTimeout(2000){ client.state.first { it.connected } }
            assertTrue(audio.started)
            audio.input!!(ByteArray(32)); val before=socket.sent.size
            client.mute(); audio.input!!(ByteArray(32))
            assertTrue(audio.muted); assertEquals(before+1,socket.sent.size)
            assertTrue(socket.sent.last().contains("audioStreamEnd"))
        }
    }
    @Test fun quotaClosesAudioAndDoesNotRetry()=runBlocking {
        runClient { client,socket,audio ->
            socket.open(); socket.event("{\"setupComplete\":{}}" );delay(80)
            socket.event("{\"error\":{\"code\":429,\"message\":\"RESOURCE_EXHAUSTED\"}}")
            withTimeout(2000){client.state.first { it.phase==VoicePhase.ERROR }}
            assertTrue(audio.closed); assertTrue(socket.cancelled); assertTrue(client.state.value.message.contains("limit"))
            delay(250); assertEquals(1,socket.connections)
        }
    }
    @Test fun lateEventsAfterStopCannotReopenMicrophone()=runBlocking {
        runClient { client,socket,audio ->
            socket.open(); client.stop(); socket.event("{\"setupComplete\":{}}" );delay(80)
            delay(100); assertFalse(audio.started); assertFalse(client.state.value.connected)
        }
    }
    @Test fun interruptedDraftIsDiscardedBeforeTaskValidation()=runBlocking {
        runClient { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}" );delay(80)
            socket.event("{\"serverContent\":{\"outputTranscription\":{\"text\":\"Hola\"},\"modelTurn\":{\"parts\":[{\"inlineData\":{\"mimeType\":\"audio/pcm;rate=24000\",\"data\":\"AAAAAA==\"}}]}}}")
            val interruptsBefore=audio.interrupts
            socket.event("{\"serverContent\":{\"interrupted\":true}}")
            delay(100); assertEquals(0,audio.packets); assertEquals(interruptsBefore+1,audio.interrupts)
            assertTrue(client.state.value.transcript.single().interrupted)
            socket.event("{\"serverContent\":{\"turnComplete\":true}}")
            delay(40);assertEquals(0,audio.packets)
        }
    }
    @Test fun toolsDeduplicateAndCancellationUndoesCompletion()=runBlocking {
        runClient(settings=Settings(level=Level.A2)) { client,socket,_ ->
            socket.event("{\"setupComplete\":{}}" );delay(80)
            recognized(socket,"hola");assessment(socket,"hola-grade","hola")
            val call="{\"toolCall\":{\"functionCalls\":[{\"id\":\"word1\",\"name\":\"record_learning\",\"args\":{\"kind\":\"word\",\"term\":\"hola\",\"evidence\":\"hola\"}},{\"id\":\"goal1\",\"name\":\"complete_scenario\",\"args\":{\"evidence\":\"Uczeń zamówił kawę\"}}]}}"
            socket.event(call); socket.event(call); delay(100)
            assertEquals(1,client.state.value.learning.size); assertTrue(client.state.value.completed)
            socket.event("{\"toolCallCancellation\":{\"ids\":[\"goal1\",\"word1\"]}}")
            delay(50); assertFalse(client.state.value.completed); assertTrue(client.state.value.learning.isEmpty())
        }
    }
    @Test fun verifiedAssessmentInjectsTheOriginalLaughOnceBeforeSpokenReply()=runBlocking {
        runClient { _,socket,audio ->
            socket.event("{\"setupComplete\":{}}" );delay(80)
            recognized(socket,"Yo soy 31 años.")
            val error=GeminiProtocol.json("toolCall" to GeminiProtocol.json("functionCalls" to listOf(GeminiProtocol.json("id" to "assessment1","name" to "assess_user_turn","args" to GeminiProtocol.json("verdict" to "ERROR","heard_text" to "Yo soy 31 años.","evidence" to "Yo soy 31 años.","confidence" to "CERTAIN","words" to emptyList<String>()))))).toString()
            socket.event(error); socket.event(error); delay(100)
            assertEquals(listOf(96000),audio.packetSizes)
            assertTrue(socket.sent.last().contains("assessment1"))
        }
    }
    @Test fun missingPolishVoiceNeverKillsLiveAudio()=runBlocking {
        runClient { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}" );delay(80)
            socket.event("{\"serverContent\":{\"outputTranscription\":{\"text\":\"Kawa to café.\"},\"modelTurn\":{\"parts\":[{\"inlineData\":{\"mimeType\":\"audio/pcm;rate=24000\",\"data\":\"AAAAAA==\"}}]}}}")
            delay(100); assertEquals(0,audio.packets);assertTrue(client.state.value.connected)
            socket.event("{\"serverContent\":{\"turnComplete\":true}}")
            delay(150);assertEquals(1,audio.packets)
            assertTrue(client.state.value.connected); assertFalse(audio.closed)
            assertFalse(socket.sent.any { it.contains("DODATKOWA_DOCINKA") })
            assertTrue(client.state.value.connected)
            socket.event("{\"toolCall\":{\"functionCalls\":[{\"id\":\"synthetic\",\"name\":\"record_learning\",\"args\":{\"kind\":\"word\",\"term\":\"fake\",\"evidence\":\"fake\"}}]}}")
            delay(50); assertTrue(client.state.value.learning.isEmpty())
            val before = socket.sent.size
            socket.event("{\"serverContent\":{\"outputTranscription\":{\"text\":\"No kurwa.\"},\"modelTurn\":{\"parts\":[{\"inlineData\":{\"mimeType\":\"audio/pcm;rate=24000\",\"data\":\"AAAAAA==\"}}]},\"turnComplete\":true}}")
            delay(150); assertEquals(before, socket.sent.size); assertTrue(client.state.value.connected)
        }
    }
    @Test fun rollbackStreamsWithoutStartingSupplementVoice()=runBlocking {
        runClient(Settings(previousVersionMode = true)) { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}" );delay(80)
            socket.event("{\"serverContent\":{\"outputTranscription\":{\"text\":\"Kawa to café.\"},\"modelTurn\":{\"parts\":[{\"inlineData\":{\"mimeType\":\"audio/pcm;rate=24000\",\"data\":\"AAAAAA==\"}}]},\"turnComplete\":true}}")
            delay(150); assertEquals(1,audio.packets); assertTrue(client.state.value.connected)
            assertFalse(client.state.value.message.contains("niedostępny"))
        }
    }
    @Test fun rollbackRestoresSavedCharacterNotNewRoast()=runBlocking {
        val saved = PreviousBehavior(personality = com.locospanish.ai.personality.PersonalityEngine.preset(Preset.TEACHER))
        runClient(Settings(previousVersionMode = true, previousBehavior = saved)) { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}" );delay(80)
            socket.event("{\"toolCall\":{\"functionCalls\":[{\"id\":\"error-old\",\"name\":\"assess_user_turn\",\"args\":{\"verdict\":\"ERROR\",\"evidence\":\"soy veinte años\"}}]}}")
            delay(100); assertEquals(0,audio.packets); assertTrue(client.state.value.connected)
        }
    }
    @Test fun hotelAdvancesOnlyAfterActualCorrectHotelWord()=runBlocking {
        runClient(scenario="hotel") { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}" );delay(80)
            socket.event("{\"serverContent\":{\"inputTranscription\":{\"text\":\"Tengo una reserva\"}}}")
            assessment(socket,"hotel-correct","Tengo una reserva",words=listOf(GeminiProtocol.json("term" to "la reserva","translation" to "rezerwacja","evidence" to "Tengo una reserva","known" to true)))
            delay(100)
            assertTrue(client.state.value.learning.any { it.kind=="known_word" && it.term=="reserva" })
            val response=com.google.gson.JsonParser.parseString(socket.sent.last()).asJsonObject
                .getAsJsonObject("toolResponse").getAsJsonArray("functionResponses")[0].asJsonObject.getAsJsonObject("response")
            assertFalse(response.has("next_spanish"))
            assertTrue(response.get("conversation_context").asString.contains("Tengo una reserva"))
            assertTrue(response.has("continue_dialogue"))
            assertTrue(client.state.value.connected)
        }
    }
    @Test fun falsePositiveGradeDoesNotPlayLaughOrStoreAnError()=runBlocking {
        runClient { client,socket,audio ->
            socket.event("{\"setupComplete\":{}}" );delay(80)
            recognized(socket,"Yo tengo 31 años.")
            socket.event(GeminiProtocol.json("toolCall" to GeminiProtocol.json("functionCalls" to listOf(GeminiProtocol.json("id" to "false-error","name" to "assess_user_turn","args" to GeminiProtocol.json("verdict" to "ERROR","heard_text" to "Yo tengo 31 años.","confidence" to "CERTAIN","correction" to "Soy 31 años.","explanation" to "Błędna propozycja","error_kind" to "GRAMMAR","words" to emptyList<String>()))))).toString())
            delay(100);assertEquals(0,audio.packets);assertTrue(client.state.value.learning.none { it.kind == "grammar" })
            assertTrue(socket.sent.last().contains("verified_verdict"));assertTrue(socket.sent.last().contains("CORRECT"))
        }
    }
    @Test fun knownWordIsRecordedAndProvidedToNextTurn()=runBlocking {
        runClient(settings=Settings(level=Level.A2)) { client,socket,_ ->
            socket.event("{\"setupComplete\":{}}" );delay(80)
            recognized(socket,"Quiero un café.")
            assessment(socket,"word-grade","Quiero un café.",words=listOf(GeminiProtocol.json("term" to "café","translation" to "kawa","evidence" to "Quiero un café.","known" to true)))
            delay(100);assertTrue(client.state.value.learning.any { it.kind == "known_word" && it.term == "café" })
            assertTrue(socket.sent.last().contains("already_known_words"));assertTrue(socket.sent.last().contains("café"))
        }
    }
    @Test fun settingsStyleAndCorrection100SurviveReload()=runBlocking {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as android.app.Application
        val repo=com.locospanish.ai.data.LocalRepository(app.locoStore);val original=repo.settings.first()
        val store=androidx.lifecycle.ViewModelStore()
        try {
            val vm=withContext(Dispatchers.Main){com.locospanish.ai.ui.LocoViewModel(app).also { store.put("first",it) }}
            withTimeout(3000){vm.loaded.first { it }}
            withContext(Dispatchers.Main){vm.selectPersonality(Preset.FRIEND);vm.setFilmStyle(true);vm.changePersonality { it.copy(correction=100) };vm.changePersonality { it.copy(sarcasm=92) }}
            delay(700)
            assertTrue(vm.settings.value.tiktokStyle);assertEquals(Preset.FRIEND,vm.settings.value.personality.preset);assertEquals(100,vm.settings.value.forDialog().personality.correction)
            withContext(Dispatchers.Main){store.clear()}
            val reloaded=repo.settings.first()
            assertTrue(reloaded.tiktokStyle);assertEquals(100,reloaded.personality.correction);assertEquals(92,reloaded.personality.sarcasm)
        } finally { withContext(Dispatchers.Main){store.clear()};repo.saveSettings(original) }
    }
    @Test fun wordKnowledgeSurvivesLocalRepositoryReload()=runBlocking {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as android.app.Application
        val repo=com.locospanish.ai.data.LocalRepository(app.locoStore);val original=repo.library.first()
        try {
            val event=LearningEvent("persisted:vocab:u:café","known_word","café","","kawa","Quiero un café.")
            repo.upsert(Session("persisted-word-test",System.currentTimeMillis(),scenarioId="cafe",level=Level.A1,learning=listOf(event),transcript=listOf(Transcript("u","user","Quiero un café."))))
            val reopened=com.locospanish.ai.data.LocalRepository(app.locoStore).library.first()
            assertTrue(com.locospanish.ai.tutor.WordKnowledge.mastered(reopened).contains("café"))
            assertTrue(com.locospanish.ai.tutor.PromptBuilder.memory(reopened,"hotel",Level.A2).contains("café"))
        } finally { repo.deleteHistory();original.sessions.forEach { repo.upsert(it) } }
    }
    @Test fun lateTranscriptIsAllowedToFinishBeforeAssessment()=runBlocking {
        runClient { _,socket,_ ->
            socket.event("{\"setupComplete\":{}}" );delay(80)
            recognized(socket,"Tengo una")
            assessment(socket,"late-transcript","Tengo una reserva")
            delay(50);recognized(socket," reserva")
            delay(400)
            assertTrue(socket.sent.last().contains("verified_verdict"));assertTrue(socket.sent.last().contains("CORRECT"))
        }
    }
    @Test fun freeTalkAlsoReceivesTheCurrentConversation()=runBlocking {
        runClient(scenario="free") { _,socket,_ ->
            socket.event("{\"setupComplete\":{}}" );delay(80)
            socket.event("{\"serverContent\":{\"inputTranscription\":{\"text\":\"Chcę opowiedzieć o podróży\"}}}")
            socket.event("{\"toolCall\":{\"functionCalls\":[{\"id\":\"free-context\",\"name\":\"assess_user_turn\",\"args\":{\"verdict\":\"CORRECT\"}}]}}")
            delay(100)
            val response=com.google.gson.JsonParser.parseString(socket.sent.last()).asJsonObject.getAsJsonObject("toolResponse").getAsJsonArray("functionResponses")[0].asJsonObject.getAsJsonObject("response")
            assertTrue(response.get("conversation_context").asString.contains("podróży"))
            assertFalse(response.has("next_spanish"))
        }
    }
    @Test fun serverErrorRetriesAndKeepsConversation()=runBlocking {
        runClient { client,socket,_ ->
            socket.event("{\"setupComplete\":{}}" );delay(80)
            socket.event("{\"serverContent\":{\"inputTranscription\":{\"text\":\"Dos noches\"}}}")
            socket.listener.onClosing(socket,1011,"Internal error")
            delay(1200); assertEquals(2,socket.connections)
            socket.open(); socket.event("{\"setupComplete\":{}}" );delay(80)
            delay(100); assertTrue(client.state.value.connected)
            assertTrue(client.state.value.transcript.any { it.text.contains("Dos noches") })
            assertTrue(socket.sent.any { it.contains("Dos noches") && it.contains("setup") })
        }
    }
    @Test fun stopCancelsPendingReconnect()=runBlocking {
        runClient { client,socket,_ ->
            socket.event("{\"setupComplete\":{}}" );delay(80)
            socket.listener.onClosing(socket,1011,"Internal error")
            client.stop(); delay(1200)
            assertEquals(1,socket.connections); assertEquals(VoicePhase.IDLE,client.state.value.phase)
        }
    }
    @Test fun emptyOpeningCompletionKeepsWaitingForSpokenPrompt()=runBlocking {
        runClient { client,socket,_ ->
            socket.event("{\"setupComplete\":{}}" );delay(80)
            socket.event("{\"serverContent\":{\"turnComplete\":true}}")
            delay(100); assertEquals(VoicePhase.THINKING,client.state.value.phase)
        }
    }
    @Test fun missingAudioTimesOutWithoutLosingTranscript()=runBlocking {
        runClient(responseTimeoutMs=150) { client,socket,_ ->
            socket.event("{\"setupComplete\":{}}" );delay(80)
            socket.event("{\"serverContent\":{\"inputTranscription\":{\"text\":\"Dos noches\"}}}")
            delay(350); assertEquals(VoicePhase.RECONNECTING,client.state.value.phase)
            assertTrue(client.state.value.transcript.any { it.text.contains("Dos noches") })
        }
    }
    @Test fun rapidSettingsChangesPersistWithoutFalseFailure()=runBlocking {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as android.app.Application
        val vm=withContext(Dispatchers.Main) { com.locospanish.ai.ui.LocoViewModel(app) }
        val repo=com.locospanish.ai.data.LocalRepository(app.locoStore)
        val original=repo.settings.first()
        try {
            delay(500)
            withContext(Dispatchers.Main) {
                repeat(20) { vm.update(vm.settings.value.copy(speed=.6f + it*.02f)) }
            }
            delay(700)
            assertFalse(vm.notice.value.contains("Nie udało się zapisać ustawień"))
            assertEquals(.98f,repo.settings.first().speed,.001f)
        } finally {
            repo.saveSettings(original)
            withContext(Dispatchers.Main) { vm.viewModelScope.cancel() }
        }
    }
    @Test fun keyIsEncryptedAndCanBeRemoved() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val store=AccessTokenStore(context)
        // Test APK is installed only on the test emulator, never on the user's phone.
        try { store.save("FAKE_TEST_KEY"); assertEquals("FAKE_TEST_KEY",AccessTokenStore(context).read())
            assertFalse(context.getSharedPreferences("gemini_access",0).getString("value","")!!.contains("FAKE_TEST_KEY"))
        } finally { store.save("") }
        assertEquals("",store.read())
    }
}




