package com.locospanish.ai
import com.locospanish.ai.tutor.*
import org.junit.Assert.*
import org.junit.Test
class WordAttemptPolicyTest {
    @Test fun everySingleWordInTheActualScenarioCurriculumPassesWithAndWithoutItsArticle() {
        val words=TopicLessons.plans.keys.flatMap { TopicLessons.vocabulary(it) }.filter { LessonTurnPolicy.singleWord(it.es) }.distinctBy { it.es }
        assertTrue(words.size>30)
        for(word in words) for(answer in listOf(word.es,AssessmentPolicy.display(word.es))) {
            val grade=WordAttemptPolicy.evaluate(word,answer,Assessment("ERROR",answer,"otra palabra","Zmyślona odmowa modelu"))
            assertEquals("${word.es} / $answer","CORRECT",grade.verdict)
            assertTrue("${word.es} / $answer",WordAttemptPolicy.canAdvance(word,answer,grade))
        }
    }
    @Test fun correctlyAnsweredWordSurvivesSpacingAndShortAnswerFrames() {
        val target=LessonWord("la reserva","rezerwacja")
        for(answer in listOf("La   reserva.","Es una reserva.","La palabra es reserva.","Se dice reserva.")) {
            val result=WordAttemptPolicy.evaluate(target,answer,Assessment("ERROR",answer,"la llave","Zmyślona odmowa"))
            assertEquals(answer,"CORRECT",result.verdict)
            assertTrue(answer,WordAttemptPolicy.canAdvance(target,answer,result))
        }
    }
    @Test fun exactCorrectPhraseDoesNotInheritTheModelsFalseError() {
        for(term in listOf("buenos días","hasta luego","la cuenta, por favor")) {
            val result=WordAttemptPolicy.evaluate(LessonWord(term,"znaczenie"),term,Assessment("ERROR",term,"otra palabra","Zmyślona odmowa"))
            assertEquals(term,"CORRECT",result.verdict)
        }
    }
    @Test fun answerFramesDoNotTurnNegationOrOtherWordsIntoCorrectAnswers() {
        val target=LessonWord("la reserva","rezerwacja")
        for(answer in listOf("No es una reserva.","Es el reserva.","Es un gato.","¿Cómo se dice reserva?")) {
            val result=WordAttemptPolicy.evaluate(target,answer,Assessment("ERROR",answer,"la reserva","Inna odpowiedź"))
            assertNotEquals(answer,"CORRECT",result.verdict)
        }
    }
    @Test fun definiteAndIndefiniteArticlesDoNotMakeCorrectWordsWrong() {
        for((target,answers) in listOf(
            "un café" to listOf("el café","un café","cafe"),
            "una bebida" to listOf("la bebida","una bebida","bebida"),
            "el perro" to listOf("un perro","el perro","perro"),
            "la habitación" to listOf("una habitación","la habitación","el cuarto","un cuarto"),
            "los amigos" to listOf("unos amigos","los amigos","amigos")
        )) for(answer in answers) {
            val grade=WordAttemptPolicy.evaluate(LessonWord(target,"test"),answer,Assessment("ERROR",answer,"inventado","zmyślona poprawka"),false)
            assertEquals("$target / $answer","CORRECT",grade.verdict)
        }
    }
    @Test fun matchingActualTranscriptWinsOverUncertainModelAssessment() {
        for(verdict in listOf("ERROR","UNCERTAIN")) {
            val grade=WordAttemptPolicy.evaluate(LessonWord("la reserva","rezerwacja"),"reserva",Assessment(verdict,"inne słowo","gato","zmyślona poprawka"),false)
            assertEquals("CORRECT",grade.verdict)
            assertTrue(WordAttemptPolicy.canAdvance(LessonWord("la reserva","rezerwacja"),"reserva",grade))
        }
    }
    @Test fun genuineArticleErrorsAndDistinctSpanishWordsAreStillRejected() {
        for((target,answer) in listOf("un café" to "la café","una bebida" to "el bebida","la habitación" to "la cuarto"))
            assertEquals("ERROR",WordAttemptPolicy.evaluate(LessonWord(target,"test"),answer,Assessment("CORRECT",answer)).verdict)
        val year=LessonWord("el año","rok")
        assertFalse(WordAttemptPolicy.canAdvance(year,"ano",WordAttemptPolicy.evaluate(year,"ano",Assessment("CORRECT","ano"))))
    }
    @Test fun punctuationAndNumericTranscriptionDoNotCreateFalseErrors() {
        for((target,answer) in listOf("la habitación" to "¿Habitación?","un café" to "«El café»","treinta" to "30")) {
            val result=WordAttemptPolicy.evaluate(LessonWord(target,"test"),answer,Assessment("ERROR",answer,"inventado","test"),false)
            assertEquals("$target / $answer","CORRECT",result.verdict)
        }
    }
    private val word=LessonWord("el perro","pies")
    private fun correct(text:String)=Assessment("CORRECT",text)
    @Test fun silenceAndAcknowledgmentNeverPassWordTask() {
        for(text in listOf("","tak","ok","dobrze","nie wiem","powtórz","jak powiedzieć pies")) {
            val grade=WordAttemptPolicy.evaluate(word,text,correct(text))
            assertFalse(WordAttemptPolicy.canAdvance(word,text,grade))
        }
    }
    @Test fun anotherCorrectSpanishWordDoesNotPassTarget() {
        val grade=WordAttemptPolicy.evaluate(word,"gato",correct("gato"))
        assertEquals("ERROR",grade.verdict);assertEquals("el perro",grade.correction)
        assertFalse(WordAttemptPolicy.canAdvance(word,"gato",grade))
    }
    @Test fun actualCorrectAnswerAndRegionalVariantCanPass() {
        for(text in listOf("perro","el perro","un perro")) assertTrue(WordAttemptPolicy.canAdvance(word,text,correct(text)))
        val room=LessonWord("la habitación","pokój")
        assertTrue(WordAttemptPolicy.canAdvance(room,"cuarto",correct("cuarto")))
    }
    @Test fun uncertainOrWrongPronunciationCannotPassEvenWithMatchingTranscript() {
        for(verdict in listOf("ERROR","UNCERTAIN")) assertFalse(WordAttemptPolicy.canAdvance(word,"perro",Assessment(verdict,"perro")))
    }
    @Test fun requestToChangeTopicIsNotVocabularyProof() {
        val text="Chcę teraz słowa o hotelu"
        assertTrue(WordAttemptPolicy.newTopic(text));assertFalse(WordAttemptPolicy.canAdvance(word,text,correct(text)))
    }
    @Test fun spellingOnlyTranscriptVariantsOfTheRightSoundAreCorrect() {
        for((target,answer) in listOf("la habitación" to "abitación","la cerveza" to "cerbeza","la llave" to "yave","la cerveza" to "serbesa","el vaso" to "baso","la cena" to "sena")) {
            val grade=WordAttemptPolicy.evaluate(LessonWord(target,"test"),answer,Assessment("ERROR",answer,"inventado","zmyślona poprawka"))
            assertEquals("$target / $answer","CORRECT",grade.verdict)
            assertTrue("$target / $answer",WordAttemptPolicy.canAdvance(LessonWord(target,"test"),answer,grade))
        }
    }
    @Test fun nearMissTranscriptIsUncertainNotAMockedError() {
        for((target,answer) in listOf("la habitación" to "habitacio","la reserva" to "reserbo","el pasaporte" to "pasaporta")) {
            val grade=WordAttemptPolicy.evaluate(LessonWord(target,"test"),answer,Assessment("UNCERTAIN",answer))
            assertEquals("$target / $answer","UNCERTAIN",grade.verdict)
            assertFalse(WordAttemptPolicy.canAdvance(LessonWord(target,"test"),answer,grade))
            // Gemini's own CORRECT does not credit a word that is not what the card says.
            val heard=WordAttemptPolicy.evaluate(LessonWord(target,"test"),answer,Assessment("CORRECT",target))
            assertNotEquals("$target / $answer","CORRECT",heard.verdict)
            assertFalse(WordAttemptPolicy.canAdvance(LessonWord(target,"test"),answer,heard))
        }
        // Even Gemini's own ERROR on a one-sound slip asks for another try instead of mocking (real session: beber heard as "deber").
        assertEquals("UNCERTAIN",WordAttemptPolicy.evaluate(LessonWord("el pasaporte","paszport"),"pasaporta",Assessment("ERROR","pasaporta","pasaporte","końcówka")).verdict)
        assertEquals("UNCERTAIN",WordAttemptPolicy.evaluate(LessonWord("beber","pić"),"deber",Assessment("ERROR","deber","beber","x")).verdict)
        assertEquals("CORRECT",WordAttemptPolicy.evaluate(LessonWord("beber","pić"),"beber curva",Assessment("ERROR","beber curva","beber","x")).verdict)
    }
    /** From a real session: the transcriber added "de" and the learner repeated the word; Gemini still said ERROR. */
    @Test fun strayFillerAndRepetitionAroundTheRightWordStillPass() {
        val tryOn=LessonWord("probar","przymierzać")
        for(text in listOf("de probar probar","probar probar","eee probar","de probar")) {
            val grade=WordAttemptPolicy.evaluate(tryOn,text,Assessment("ERROR",text,"probar","x"))
            assertEquals(text,"CORRECT",grade.verdict)
            assertTrue(text,WordAttemptPolicy.canAdvance(tryOn,text,grade))
        }
        for(text in listOf("profesora","Bargteheide","taco","próbować próbować kurwa szmaty"))
            assertNotEquals(text,"CORRECT",WordAttemptPolicy.evaluate(tryOn,text,Assessment("ERROR",text,"probar","x")).verdict)
    }
    @Test fun anotherLessonWordNeverPassesTheCard() {
        val today=LessonWord("hoy","dzisiaj")
        for(text in listOf("mañana","manana","mañana hoy","hoy mañana","Mañana.")) for(verdict in listOf("CORRECT","UNCERTAIN")) for(heard in listOf("","hoy")) {
            val grade=WordAttemptPolicy.evaluate(today,text,Assessment(verdict,text),false,heard)
            assertNotEquals("$text/$verdict/$heard","CORRECT",grade.verdict)
            assertFalse("$text/$verdict/$heard",WordAttemptPolicy.canAdvance(today,text,Assessment("CORRECT",text),heard))
        }
        assertEquals("CORRECT",WordAttemptPolicy.evaluate(today,"hoy",Assessment("CORRECT","hoy")).verdict)
    }
    @Test fun polishSpelledOrSplitTranscriptOfTheRightWordIsCorrect() {
        for((target,answer) in listOf("una noche" to "una nocze","una noche" to "lano cze","la llave" to "la jawe","la cerveza" to "serbesa","la habitación" to "abitasjon","el pollo" to "el pojo","yo" to "jo","la playa" to "la plaja")) {
            val grade=WordAttemptPolicy.evaluate(LessonWord(target,"test"),answer,Assessment("UNCERTAIN",answer))
            assertEquals("$target / $answer","CORRECT",grade.verdict)
            assertTrue("$target / $answer",WordAttemptPolicy.canAdvance(LessonWord(target,"test"),answer,grade))
        }
        // A different word is never credited, even when Gemini claims it heard the target.
        for(other in listOf("gato","buenos días","perro","la mesa","hola"))
            assertNotEquals(other,"CORRECT",WordAttemptPolicy.evaluate(LessonWord("una noche","noc"),other,Assessment("CORRECT",other),false,"una noche").verdict)
        assertNotEquals("CORRECT",WordAttemptPolicy.evaluate(LessonWord("una noche","noc"),"un gato",Assessment("UNCERTAIN","un gato")).verdict)
        assertNotEquals("CORRECT",WordAttemptPolicy.evaluate(LessonWord("la llave","klucz"),"nie wiem",Assessment("UNCERTAIN","nie wiem")).verdict)
    }
    @Test fun geminiHearingTheRightWordWinsOverAGarbledTranscript() {
        val night=LessonWord("una noche","jedna noc")
        for(garbled in listOf("no cze","nocze","una no che")) {
            val grade=WordAttemptPolicy.evaluate(night,garbled,Assessment("UNCERTAIN",garbled),false,"una noche")
            assertEquals(garbled,"CORRECT",grade.verdict)
            assertTrue(garbled,WordAttemptPolicy.canAdvance(night,garbled,grade,"una noche"))
        }
        // Without Gemini's certain hearing, or when the transcript is clearly another lesson word, nothing is credited.
        assertNotEquals("CORRECT",WordAttemptPolicy.evaluate(night,"la cerveza",Assessment("UNCERTAIN","la cerveza"),false,"una noche").verdict)
        assertNotEquals("CORRECT",WordAttemptPolicy.evaluate(night,"nie wiem",Assessment("UNCERTAIN","nie wiem"),false,"una noche").verdict)
    }
}
