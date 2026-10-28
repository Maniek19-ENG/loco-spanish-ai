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
    @org.junit.Test fun everyLevelGetsItsOwnStartingMaterialInEveryScenario() {
        for(scenario in com.locospanish.ai.model.Scenarios.all.map { it.id }) {
            val a1=com.locospanish.ai.tutor.LessonTurnPolicy.next(scenario,null,emptyList(),emptyList(),wordsOnly=true,preferScenario=true)!!
            org.junit.Assert.assertTrue("$scenario A1 ${a1.es}",com.locospanish.ai.tutor.LessonTurnPolicy.singleWord(a1.es))
            val a2=com.locospanish.ai.tutor.LessonTurnPolicy.next(scenario,null,emptyList(),emptyList(),preferScenario=true,level=com.locospanish.ai.model.Level.A2)!!
            org.junit.Assert.assertFalse("$scenario A2 ${a2.es}",com.locospanish.ai.tutor.LessonTurnPolicy.singleWord(a2.es))
            for(level in listOf(com.locospanish.ai.model.Level.B1,com.locospanish.ai.model.Level.B2,com.locospanish.ai.model.Level.C1,com.locospanish.ai.model.Level.C2)) {
                val b=com.locospanish.ai.tutor.LessonTurnPolicy.next(scenario,null,emptyList(),emptyList(),preferScenario=true,level=level)!!
                org.junit.Assert.assertTrue("$scenario $level ${b.es}",b.es.split(' ').size>=4)
            }
        }
    }
    @org.junit.Test fun aSentenceSaidInCloseWordsCountsButAnotherSentenceDoesNot() {
        val target=com.locospanish.ai.tutor.LessonWord("Reservé una habitación para tres noches","Zarezerwowałem pokój na trzy noce")
        org.junit.Assert.assertTrue(com.locospanish.ai.tutor.WordAttemptPolicy.matches(target,"Reservé una habitación para tres noches"))
        org.junit.Assert.assertTrue(com.locospanish.ai.tutor.WordAttemptPolicy.matches(target,"Pues reservé habitación para tres noches"))
        org.junit.Assert.assertFalse(com.locospanish.ai.tutor.WordAttemptPolicy.matches(target,"Quiero una cerveza muy fría"))
    }
}
