package com.locospanish.ai

import com.locospanish.ai.model.*
import com.locospanish.ai.realtime.*
import org.junit.Assert.*
import org.junit.Test

class GeminiTest {
    @Test fun setupUsesLiveAudioAndMigratesOldVoice() {
        val config = GeminiProtocol.setup(Settings(voice="marin"),"daily","").getAsJsonObject("setup")
        assertEquals("models/gemini-3.8-live",config.get("model").asString)
        assertTrue(config.has("inputAudioTranscription")); assertTrue(config.has("outputAudioTranscription"))
        val generation = config.getAsJsonObject("generationConfig")
        assertEquals("AUDIO",generation.getAsJsonArray("responseModalities")[0].asString)
        assertEquals("Kore",generation.getAsJsonObject("speechConfig").getAsJsonObject("voiceConfig").getAsJsonObject("prebuiltVoiceConfig").get("voiceName").asString)
        assertFalse(config.toString().contains("apiKey"))
    }
    @Test fun promptRespectsRoastOffAndCorrectionOff() {
        val prompt=GeminiProtocol.prompt(Settings(autoCorrection=false,personality=Personality(roast=100,roastMode=false)),"maintenance","")
        assertTrue(prompt.contains("Nie roastuj")); assertTrue(prompt.contains("korekta: false")); assertTrue(prompt.contains("Maintenance"))
    }
    @Test fun deltasAccumulateAndTurnsStaySeparate() {
        val reducer=GeminiTranscript(); var state=VoiceState()
        fun input(text:String)=GeminiProtocol.json("inputTranscription" to GeminiProtocol.json("text" to text))
        state=reducer.apply(state,input("Hola")); state=reducer.apply(state,input(" amigo"))
        assertEquals("Hola amigo",state.transcript.single().text)
        state=reducer.apply(state,GeminiProtocol.json("turnComplete" to true))
        state=reducer.apply(state,input("Adiós")); assertEquals(2,state.transcript.size)
    }
    @Test fun interruptionMarksOnlyCurrentAssistant() {
        val reducer=GeminiTranscript()
        var state=reducer.apply(VoiceState(),GeminiProtocol.json("outputTranscription" to GeminiProtocol.json("text" to "Hola")))
        state=reducer.apply(state,GeminiProtocol.json("interrupted" to true))
        assertTrue(state.transcript.single().interrupted)
    }
    @Test fun quotaDoesNotExposeServerPayloadOrSuggestPayment() {
        val result=GeminiProtocol.problem(1008,"RESOURCE_EXHAUSTED key=secret")
        assertTrue(result.contains("limit")); assertFalse(result.contains("secret")); assertFalse(result.contains("kup"))
    }
    @Test fun freshInstallationRequiresFreeTierConfirmation() { assertFalse(Settings().freeTierConfirmed) }
}
