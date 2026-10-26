package com.locospanish.ai
import com.locospanish.ai.model.*
import com.locospanish.ai.realtime.GeminiProtocol
import com.locospanish.ai.tutor.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test
class TopicLessonsTest {
    @Test fun beginnersStartWithWordsAndConcreteTeaching() {
        val s=Settings(level=Level.A1)
        val prompt=GeminiProtocol.prompt(s,"hotel","")
        assertTrue(prompt.contains("NAJPIERW SŁOWA"))
        assertTrue(prompt.contains("la habitación = pokój"))
        assertTrue(prompt.contains("Odpowiedź jednym słowem wystarczy"))
        assertTrue(prompt.contains("Zakazane puste pytania"))
        assertTrue(GeminiProtocol.startCommand(s,"hotel").contains("Nie zaczynaj od zdania"))
        assertFalse(TopicLessons.instructions("cafe",Level.A1,"").contains("la habitación = pokój"))
        assertFalse(GeminiProtocol.prompt(s.copy(level=Level.B1),"hotel","").contains("NAJPIERW SŁOWA"))
    }
    @Test fun everyScenarioHasAnActualWordPlan() {
        assertEquals(Scenarios.all.map { it.id }.toSet(),TopicLessons.plans.keys)
        TopicLessons.plans.values.forEach { assertEquals(6,it.words.size); assertTrue(it.words.all { w -> w.es.isNotBlank() && w.pl.isNotBlank() }) }
    }
    @Test fun hotelDoesNotReuseFoodExamplesAndStartsWithBooking() {
        val s=Settings().forDialog()
        val prompt=GeminiProtocol.prompt(s,"hotel","")
        assertTrue(prompt.contains("Temat jest otwartą dziedziną")); assertTrue(prompt.contains("Hotel"))
        assertFalse(prompt.contains("Café — kawa")); assertFalse(prompt.contains("pepino"))
        assertTrue(GeminiProtocol.startCommand(s,"hotel").contains("Temat jest otwartą dziedziną"))
        assertTrue(GeminiProtocol.startCommand(s,"cafe").contains("Temat jest otwartą dziedziną"))
    }
    @Test fun onlyConfirmedScenarioPracticeAdvancesTheLessonAfterReopen() {
        val event=LearningEvent("scenario:hotel:u:la reserva","repetition","la reserva","","","Tengo una reserva")
        val session=Session("s",1,1,"hotel",Level.A1,learning=listOf(event),transcript=listOf(Transcript("u","user","Tengo una reserva")))
        val memory=PromptBuilder.memory(Library(listOf(session)),"hotel",Level.A1)
        assertTrue(memory.contains("PRACTICADA: la reserva"))
        assertTrue(TopicLessons.instructions("hotel",Level.A1,memory).contains("nie gotowym scenariuszem"))
        assertTrue(TopicLessons.demonstrated("Jak powiedzieć reserva?","hotel").isEmpty())
        assertEquals(listOf("la reserva"),TopicLessons.demonstrated("Tengo una reserva","hotel").map { it.es })
    }
    @Test fun pronunciationAndScopeAreAlsoAppliedInFallbackMode() {
        for(previous in listOf(false,true)) {
            val setup=GeminiProtocol.setup(Settings(previousVersionMode=previous),"hotel","").getAsJsonObject("setup")
            val prompt=setup.getAsJsonObject("systemInstruction").getAsJsonArray("parts")[0].asJsonObject.get("text").asString
            assertTrue(prompt.contains("Temat jest otwartą dziedziną")); assertTrue(prompt.contains("WYMOWA — WZORZEC"))
            assertFalse(setup.getAsJsonObject("generationConfig").getAsJsonObject("speechConfig").has("languageCode"))
        }
        val dir=File("build/topic-verification").apply { mkdirs() }
        for(id in listOf("hotel","cafe","taxi","electronics")) File(dir,"$id.json").writeText(GeminiProtocol.json(
            "config" to GeminiProtocol.setup(Settings().forDialog(),id,""),
            "command" to GeminiProtocol.startCommand(Settings().forDialog(),id),
            "topic" to TopicLessons.instructions(id,Level.A1,""),
            "guide" to GeminiProtocol.turnGuidance(Settings().forDialog(),"CORRECT")
        ).toString())
    }
}

