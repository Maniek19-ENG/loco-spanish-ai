package com.locospanish.ai
import com.locospanish.ai.tutor.*
import com.locospanish.ai.model.*
import com.locospanish.ai.realtime.GeminiProtocol
import com.google.gson.*
import org.junit.Assert.*
import org.junit.Test
class AssessmentPolicyTest {
    private fun args(text:String,verdict:String="CORRECT",correction:String="",kind:String="GRAMMAR") = GeminiProtocol.json("heard_text" to text,"evidence" to text,"confidence" to "CERTAIN","verdict" to verdict,"correction" to correction,"explanation" to "Uzasadnienie","error_kind" to kind)
    @Test fun definiteAgeErrorCannotBePraisedAsCorrect() {
        for(text in listOf("Yo soy 31 años.","Soy veinte años.")) {
            val result=AssessmentPolicy.evaluate(text,args(text))
            assertEquals("ERROR",result.verdict);assertTrue(result.correction.startsWith("Tengo"))
        }
    }
    @Test fun validAgeAndOptionalPronounCannotGetFalseCorrections() {
        assertEquals("CORRECT",AssessmentPolicy.evaluate("Yo tengo 31 años.",args("Yo tengo 31 años.","ERROR","Soy 31 años.")).verdict)
        assertEquals("CORRECT",AssessmentPolicy.evaluate("Yo quiero un café.",args("Yo quiero un café.","ERROR","Quiero un café.")).verdict)
        assertEquals("CORRECT",AssessmentPolicy.evaluate("Quisiera un café, por favor.",args("Quisiera un café, por favor.","ERROR","Quiero un café.")).verdict)
    }
    @Test fun digitAndSpokenNumberRepresentTheSameHeardSentence() {
        assertEquals("CORRECT",AssessmentPolicy.evaluate("Tengo treinta y un años.",args("Tengo 31 años.")).verdict)
    }
    @Test fun apocopeIsNotMistakenForACorrectSpokenNumber() {
        val text="Tengo treinta y uno años."
        assertEquals("ERROR",AssessmentPolicy.evaluate(text,args(text)).verdict)
        assertEquals("Tengo treinta y un años.",AssessmentPolicy.evaluate(text,args(text)).correction)
    }
    @Test fun uncertainOrDifferentHeardPhraseDoesNotGetInventedGrade() {
        assertEquals("UNCERTAIN",AssessmentPolicy.evaluate("Tengo una reserva.",args("Tengo una mesa.","ERROR","Tengo una reserva.")).verdict)
        val low=args("Quiero una habitación.").apply { addProperty("confidence","UNCERTAIN") }
        assertEquals("UNCERTAIN",AssessmentPolicy.evaluate("Quiero una habitación.",low).verdict)
        assertEquals("UNCERTAIN",AssessmentPolicy.evaluate("",args("soy 31 años","ERROR","Tengo 31 años")).verdict)
    }
    @Test fun clearAgreementErrorsCannotBePraisedAsCorrect() {
        for(text in listOf("Yo tiene una reserva.","Quiero un habitación.","Yo quiere una café."))
            assertEquals("ERROR",AssessmentPolicy.evaluate(text,args(text)).verdict)
        assertEquals("Yo tengo una habitación.",AssessmentPolicy.evaluate("Yo tiene un habitación.",args("Yo tiene un habitación.")).correction)
    }
    @Test fun errorRequiresAConcreteCorrectionAndAnExplanation() {
        assertEquals("UNCERTAIN",AssessmentPolicy.evaluate("Hablo ayer.",args("Hablo ayer.","ERROR")).verdict)
        val valid=args("Yo tiene una reserva.","ERROR","Yo tengo una reserva.")
        assertEquals("ERROR",AssessmentPolicy.evaluate("Yo tiene una reserva.",valid).verdict)
    }
    @Test fun spellingAloneCannotProveAPronunciationError() {
        assertEquals("UNCERTAIN",AssessmentPolicy.evaluate("Café.",args("Café.","ERROR","Café.","PRONUNCIATION")).verdict)
    }
    @Test fun enyeIsNotCollapsedIntoNWhenCheckingPronunciation() {
        val text="Tengo 31 anos."
        val error=args(text,"ERROR","Tengo 31 años.","PRONUNCIATION")
        assertEquals("ERROR",AssessmentPolicy.evaluate(text,error).verdict)
        assertNotEquals(AssessmentPolicy.key("año"),AssessmentPolicy.key("ano"))
    }
    @Test fun onlyWordsActuallySaidBecomeProgress() {
        val text="Quiero un café.";val fields=args(text)
        fields.add("words",GeminiProtocol.json("items" to listOf(
            GeminiProtocol.json("term" to "el café","translation" to "kawa","evidence" to text,"known" to true),
            GeminiProtocol.json("term" to "la leche","translation" to "mleko","evidence" to "Quiero leche.","known" to true)
        )).get("items"))
        val learned=AssessmentPolicy.vocabulary(fields,AssessmentPolicy.evaluate(text,fields),"u")
        assertEquals(1,learned.size);assertEquals("known_word",learned.single().kind);assertEquals("café",learned.single().term)
    }
    @Test fun knowledgeSurvivesAnotherScenarioAndAppearsInMemory() {
        val event=LearningEvent("vocab:u:cafe","known_word","el café","","kawa","Quiero un café.")
        val library=Library(listOf(Session("s",1,scenarioId="cafe",level=Level.A1,learning=listOf(event))))
        assertEquals(listOf("el café"),WordKnowledge.mastered(library))
        assertTrue(PromptBuilder.memory(library,"hotel",Level.A2).contains("ZNANE_SLOWA: el café"))
        assertEquals(listOf("el café"),com.locospanish.ai.repository.ProgressCalculator.calculate(library).masteredWords)
    }
    @Test fun twoGenuineUsesCountButDuplicateEventDoesNot() {
        val event=LearningEvent("vocab:u:cafe","word","café","","kawa","café")
        fun library(events:List<LearningEvent>)=Library(listOf(Session("s",1,scenarioId="free",level=Level.A1,learning=events)))
        assertTrue(WordKnowledge.mastered(library(listOf(event,event))).isEmpty())
        assertEquals(listOf("café"),WordKnowledge.mastered(library(listOf(event,event.copy(id="vocab:v:cafe")))))
    }
}
