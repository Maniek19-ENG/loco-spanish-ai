package com.locospanish.ai
import com.locospanish.ai.model.*
import com.locospanish.ai.realtime.SpokenRoastPolicy
import org.junit.Assert.*
import org.junit.Test
class SpokenRoastTest {
    private fun request(s: Settings = Settings(), user: String = "Quiero descansar", answer: String = "Dobrze", planned: String = "") =
        SpokenRoastPolicy.supplement(s,user,"CORRECT",answer,planned,"hotel","Poprzednia puenta o recepcji")
    @Test fun uncertaintyKeepsMaximumRoastWithoutInventingError() {
        val text=com.locospanish.ai.realtime.GeminiProtocol.turnGuidance(Settings(),"UNCERTAIN")
        assertTrue(text.contains("kurwa"));assertTrue(text.contains("Nie wymyślaj błędu"))
        assertTrue(text.contains("aktualnym słowie"))
    }
    @Test fun correctTextAnswerNeverGetsInventedPronunciationCriticism() {
        assertEquals("",SpokenRoastPolicy.primary(Settings(),"Twoja wymowa jest fatalna, kurwa.","CORRECT"))
        assertEquals("",SpokenRoastPolicy.primary(Settings(),"Ten akcent boli.","UNCERTAIN"))
    }
    @Test fun primaryKeepsFreshModelJokeAndRespectsProfanitySettings() {
        val joke="Ten pokój czeka na ciebie dłużej niż recepcjonista na urlop."
        assertTrue(SpokenRoastPolicy.primary(Settings(),joke).contains(joke.replaceFirstChar { it.lowercase() }))
        assertTrue(SpokenRoastPolicy.primary(Settings(),joke).contains("Kurwa"))
        assertEquals(joke,SpokenRoastPolicy.primary(Settings(personality=Settings().personality.copy(profanity=Profanity.OFF)),joke))
        assertEquals("",SpokenRoastPolicy.primary(Settings(personality=Settings().personality.copy(roastMode=false)),joke))
    }
    @Test fun missingPunchlineUsesActualContextAndAvoidsOldJokes() {
        val text=request()
        assertTrue(text.contains("Quiero descansar")); assertTrue(text.contains("hotel"))
        assertTrue(text.contains("Poprzednia puenta o recepcji")); assertTrue(text.contains("Wymyśl"))
        assertTrue(text.contains("mocnego polskiego przekleństwa"))
    }
    @Test fun deliveredModelPunchlineDoesNotTriggerSecondTurn() {
        assertEquals("",request(answer="Kurwa, twoja walizka ma więcej planów niż ty! Dalej.",planned="Kurwa, twoja walizka ma więcej planów niż ty."))
        assertFalse(SpokenRoastPolicy.delivered("Dobrze", ""))
    }
    @Test fun maximumStyleDoesNotAcceptAnUnswearingNicknameAsFullDelivery() {
        assertTrue(request(answer="Ty marudo!",planned="Ty marudo!").contains("Wymyśl"))
        val lower=Settings(personality=Settings().personality.copy(roast=20))
        assertEquals("",request(lower,answer="Ty marudo!",planned="Ty marudo!"))
    }
    @Test fun characterSettingsControlSupplement() {
        assertEquals("",request(Settings(previousVersionMode=true)))
        assertEquals("",request(Settings(personality=Personality(roastMode=false))))
        assertTrue(request(Settings(personality=Settings().personality.copy(profanity=Profanity.OFF))).contains("Bez przekleństw"))
        assertFalse(SpokenRoastPolicy.gentle("stop"))
    }
    @Test fun differentLearnerMeaningChangesGenerationContext() {
        assertNotEquals(request(user="Quiero dormir"),request(user="Hay demasiado ruido"))
    }    @Test fun exportsActualSupplementRequestForLiveVerification() {
        val dir=java.io.File("build/topic-verification").apply { mkdirs() }
        java.io.File(dir,"supplement-template.txt").writeText(SpokenRoastPolicy.supplement(Settings(),"__USER__","__VERDICT__","__ANSWER__","","__TOPIC__","__RECENT__"))
    }

}
