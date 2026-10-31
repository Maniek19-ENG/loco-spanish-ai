package com.locospanish.ai

import com.locospanish.ai.model.Settings
import com.locospanish.ai.realtime.*
import org.junit.Assert.*
import org.junit.Test

class SecondListenAndVarietyTest {
    @Test fun recordedAnswerIsAValidWavAndTheVerdictIsRead() {
        val wav=SpokenWordCheck.wav(ByteArray(3200))
        assertEquals("RIFF",String(wav.copyOfRange(0,4)));assertEquals("WAVE",String(wav.copyOfRange(8,12)));assertEquals(3244,wav.size)
        assertEquals(true to "beber",SpokenWordCheck.decode("{\"said_target\":true,\"heard\":\"beber\"}"))
        assertNull(SpokenWordCheck.decode("nie json"))
        val tape=MicTape(6400)
        tape.add(ByteArray(3200),100);tape.add(ByteArray(3200),200);tape.add(ByteArray(3200),300)
        assertEquals(6400,tape.since(0).size);assertEquals(3200,tape.since(250).size)
    }
    /** From a real session: every riposte ended with the same "¡Pedazo de burro!" and opened with "Co ty, kurwa". */
    @Test fun lastOpeningsAndShoutsAreBannedAndANewShoutIsGiven() {
        val recent=listOf("Co ty, kurwa, włoską operę śpiewasz? ¡Pedazo de burro! Jak powiesz po hiszpańsku „pić”?","Co ty kurwa bredzisz o zakrętach? ¡Pedazo de burro!")
        val brief=SpokenRoastPolicy.mustHave(Settings(),"deber","beber",recent,3)
        assertTrue(brief.contains("¡Pedazo de burro!"));assertTrue(brief.contains("Co ty, kurwa,"))
        val given=Regex("okrzyk tym razem: (¡[^!]+!)").find(brief)!!.groupValues[1]
        assertNotEquals("¡Pedazo de burro!",given)
        assertNotEquals(given,Regex("okrzyk tym razem: (¡[^!]+!)").find(SpokenRoastPolicy.mustHave(Settings(),"deber","beber",recent,4))!!.groupValues[1])
    }
}
