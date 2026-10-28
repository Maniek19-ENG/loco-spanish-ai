package com.locospanish.ai
import com.locospanish.ai.model.*
import com.locospanish.ai.realtime.SpokenRoastPolicy
import org.junit.Assert.*
import org.junit.Test
class SpokenRoastTest {
    private fun request(s: Settings = Settings(), user: String = "Quiero descansar", answer: String = "Dobrze", planned: String = "") =
        SpokenRoastPolicy.supplement(s,user,"CORRECT",answer,planned,"hotel","Poprzednia puenta o recepcji")
    @Test fun uncertaintyGetsHelpWithoutRiposteOrInventedError() {
        val text=com.locospanish.ai.realtime.GeminiProtocol.turnGuidance(Settings(),"UNCERTAIN")
        assertTrue(text.contains("bez riposty"));assertTrue(text.contains("Nie wymyślaj błędu"))
        assertTrue(text.contains("aktualnym słowie"));assertTrue(text.contains("szyderczo"))
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

    @Test fun addedSwearDoesNotAlwaysOpenWithTheSameWord() {
        val joke="Recepcjonista właśnie wpisał cię do księgi gości jako rekwizyt."
        val first=SpokenRoastPolicy.primary(Settings(),joke)
        assertTrue(first.startsWith("Kurwa,"))
        val second=SpokenRoastPolicy.primary(Settings(),joke,"ERROR",listOf(first))
        assertFalse(second.lowercase().startsWith("kurwa,"))
        assertTrue(Regex("kurw|pierdol|jeb|chuj",RegexOption.IGNORE_CASE).containsMatchIn(second))
        val third=SpokenRoastPolicy.primary(Settings(),joke,"ERROR",listOf(first,second))
        assertNotEquals(second,third)
    }
    @Test fun aRiposteOpeningLikeTheLastOnesIsTreatedAsARepeat() {
        val recent=listOf("Kurwa, twoja rezerwacja właśnie uciekła z hotelu przez okno.")
        assertEquals("",SpokenRoastPolicy.freshPrimary(Settings(),"Kurwa, twoja walizka zamówiła sobie osobny pokój z widokiem na basen.","ERROR",recent))
        assertTrue(SpokenRoastPolicy.freshPrimary(Settings(),"Recepcja dzwoni po ochronę, bo z tą walizką brzmisz jak pierdolony przemytnik ręczników.","ERROR",recent).isNotBlank())
    }
    @Test fun aDryReportOfTheMistakeIsNotAcceptedAsARiposte() {
        assertEquals("",SpokenRoastPolicy.freshPrimary(Settings(),"Kurwa, pomyliłeś słowo, to nie jest odpowiedź na rezerwację.","ERROR",emptyList()))
        assertTrue(SpokenRoastPolicy.freshPrimary(Settings(),"Kurwa, z takim gato recepcja da ci kuwetę zamiast klucza do pokoju.","ERROR",emptyList()).isNotBlank())
        assertTrue(com.locospanish.ai.realtime.ReplyDelivery.instruction(Settings(),"","ERROR",false,"").contains("wyśmiewa SKUTEK"))
    }
}
