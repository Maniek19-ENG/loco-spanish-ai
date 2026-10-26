package com.locospanish.ai

import com.locospanish.ai.model.*
import com.locospanish.ai.realtime.GeminiProtocol
import com.locospanish.ai.repository.ProgressCalculator
import com.locospanish.ai.tutor.PromptBuilder
import org.junit.Assert.*
import org.junit.Test

class LessonResumeTest {
    @Test fun reconnectNeverIncludesFirstLessonOpeningInEitherMode() {
        for(previous in listOf(false,true)) for(level in Level.entries) {
            val command=GeminiProtocol.startCommand(Settings(level=level,previousVersionMode=previous),"cafe",true)
            assertTrue(command.contains("Kontynuuj lekcję"))
            assertFalse(command.contains("Zacznij od"))
        }
    }
    @Test fun memoryResumesActualPracticeAndIgnoresEmptyFailedRestart() {
        val real=Session("real",1,0,"cafe",Level.A1,transcript=listOf(
            Transcript("u","user","un café con leche"),Transcript("a","assistant","Teraz poproś o rachunek: la cuenta")))
        val failed=Session("failed",2,0,"cafe",Level.A1,transcript=listOf(Transcript("intro","assistant","Zaczynamy od café")))
        val other=Session("other",3,30,"taxi",Level.A2,transcript=listOf(Transcript("o","user","OTHER_SCENARIO")))
        val memory=PromptBuilder.memory(Library(listOf(real,failed,other)),"cafe",Level.A1)
        assertTrue(memory.contains("KONTYNUACJA_LEKCJI")); assertTrue(memory.contains("la cuenta"))
        assertFalse(memory.contains("Zaczynamy od café")); assertFalse(memory.contains("OTHER_SCENARIO"))
        val progress=ProgressCalculator.calculate(Library(listOf(real)))
        assertEquals(1,progress.sessions); assertEquals(1,progress.practiceTurns)
        assertEquals(0,progress.repetitions); assertTrue(progress.words.isEmpty())
    }
}
