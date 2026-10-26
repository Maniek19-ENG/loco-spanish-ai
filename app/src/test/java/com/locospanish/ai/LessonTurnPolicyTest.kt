package com.locospanish.ai

import com.locospanish.ai.tutor.*
import org.junit.Assert.*
import org.junit.Test

class LessonTurnPolicyTest {
    @Test fun actualWordErrorGetsCorrectReferenceBeforeMovingOn() {
        val next=LessonTurnPolicy.next("hotel",LessonWord("la llave","klucz"),listOf("habitación"),listOf("la habitación"),true,true,"la habitación.")
        assertEquals("la habitación",next?.es);assertEquals("pokój",next?.pl)
    }
    @Test fun a1RejectsSentencesAndKeepsWordLessonOpen() {
        assertNull(LessonTurnPolicy.next("hotel",LessonWord("Quiero una habitación","Chcę pokój"),emptyList(),emptyList(),false,true))
        assertEquals("la habitación",LessonTurnPolicy.next("hotel",LessonWord("la habitación","pokój"),emptyList(),emptyList(),false,true)?.es)
        val settings=com.locospanish.ai.model.Settings()
        val config=com.locospanish.ai.realtime.GeminiProtocol.setup(settings,"hotel","")
        val declarations=config.getAsJsonObject("setup").getAsJsonArray("tools")[0].asJsonObject.getAsJsonArray("functionDeclarations")
        assertFalse(declarations.any { it.asJsonObject.get("name").asString == "complete_scenario" })
        assertTrue(LessonTurnPolicy.instruction(null,"hotel").contains("Nie kończ lekcji"))
    }
    @Test fun skipsKnownAndAlreadyTaughtWordsWithoutRepeatingLastWord() {
        val words=TopicLessons.get("hotel").words
        assertEquals(words[2],LessonTurnPolicy.next("hotel",words[0],listOf(words[0].es),listOf(words[1].es)))
        assertNull(LessonTurnPolicy.next("hotel",words.last(),words.map { it.es },emptyList()))
    }
    @Test fun requestedDomainNeverFallsBackToOldHotelWords() {
        assertNull(LessonTurnPolicy.next("hotel",LessonWord("perro","pies"),listOf("perro"),emptyList(),false))
        assertEquals("gato",LessonTurnPolicy.next("hotel",LessonWord("gato","kot"),emptyList(),emptyList(),false)?.es)
        val instruction=LessonTurnPolicy.instruction(null,"zwierzęta")
        assertTrue(instruction.contains("zwierzęta"));assertTrue(instruction.contains("JEDNO INNE"))
    }
    @Test fun missingOrLongProposalIsNotForcedOnBeginner() {
        assertEquals("la reserva",LessonTurnPolicy.next("hotel",LessonWord("",""),emptyList(),emptyList())?.es)
        assertNull(LessonTurnPolicy.next("hotel",LessonWord("Quisiera cambiar mi reserva para mañana","rezerwacja"),emptyList(),emptyList(),false,wordsOnly=true))
    }
    @Test fun knownAccentAndArticleVariantsAreExcluded() {
        assertNotEquals("la habitación",LessonTurnPolicy.next("hotel",LessonWord("la habitación","pokój"),listOf("habitación"),emptyList())?.es)
    }
}
