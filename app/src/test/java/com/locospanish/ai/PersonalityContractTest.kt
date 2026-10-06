package com.locospanish.ai

import com.locospanish.ai.model.*
import com.locospanish.ai.personality.PersonalityEngine
import com.locospanish.ai.realtime.GeminiProtocol
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PersonalityContractTest {
    @Test fun profanityAndRoastAreIndependentAtEveryStrength() {
        for (strength in listOf(0, 10, 40, 65, 100)) {
            val p = Personality(roastMode = true, roast = strength, profanity = Profanity.OFF)
            val clean = PersonalityEngine.instructions(p, true)
            assertFalse(clean.contains("kurwa"))
            assertFalse(clean.contains("cholera"))
            val off = PersonalityEngine.instructions(p.copy(roastMode = false, profanity = Profanity.STRONG), true)
            assertTrue(off.contains("Nie roastuj"))
            assertTrue(off.contains("STRONG"))
            assertFalse(off.contains("wypadek komunikacyjny"))
        }
    }
    @Test fun maximumRoastHasConversationMechanicsButLightDoesNotContainStrongSwears() {
        val p = PersonalityEngine.preset(Preset.ROAST)
        val prompt = PersonalityEngine.instructions(p, true)
        assertTrue(prompt.contains("słyszalnym śmiechem w AUDIO"))
        assertTrue(prompt.contains("kontynuuj tę samą fikcyjną scenkę"))
        assertTrue(prompt.contains("Nie powtarzaj konstrukcji"))
        val light = PersonalityEngine.instructions(p.copy(profanity = Profanity.LIGHT), true)
        assertTrue(light.contains("cholera")); assertFalse(light.contains("kurwa"))
    }
    @Test fun openingStartsPracticeAndReconnectDoesNotRestartLesson() {
        val p = PersonalityEngine.preset(Preset.ROAST)
        val fresh = PersonalityEngine.opening(p, "Kawiarnia", false)
        assertTrue(fresh.contains("mocnym polskim przekleństwem"))
        assertTrue(fresh.contains("zapytać o co chce"))
        assertTrue(fresh.contains("jedno krótkie zadanie"))
        assertFalse(fresh.contains("Hola, me llamo"))
        val reconnect = PersonalityEngine.opening(p, "Kawiarnia", true)
        assertTrue(reconnect.contains("Nie witaj ponownie"))
    }
    @Test fun lessonUsesPolishAndSpanishWithRecall() {
        val prompt = GeminiProtocol.prompt(Settings(), "cafe", "")
        assertTrue(prompt.contains("polski–hiszpański"))
        assertTrue(prompt.contains("bez podpowiedzi"))
        assertFalse(prompt.contains("Nie wymuszaj powtórzenia"))
    }
    @Test fun correctionZeroAndGlobalOffOverrideOtherPersonalityValues() {
        val p = PersonalityEngine.preset(Preset.ROAST)
        for (settings in listOf(Settings(autoCorrection = false, personality = p), Settings(personality = p.copy(correction = 0)))) {
            assertTrue(GeminiProtocol.prompt(settings, "daily", "").contains("Nie przerywaj automatycznie"))
        }
    }
    @Test fun exportsProductionPromptsForManualProviderVerification() {
        val dir = File("build/personality-verification").apply { mkdirs() }
        val roast = PersonalityEngine.preset(Preset.ROAST)
        mapOf("teacher" to PersonalityEngine.preset(Preset.TEACHER), "roast" to roast,
            "clean-roast" to roast.copy(profanity = Profanity.OFF), "swearing-no-roast" to roast.copy(roastMode = false))
            .forEach { (name, personality) ->
                File(dir, "$name.json").writeText(GeminiProtocol.setup(Settings(personality = personality), "daily", "").toString())
            }
        File(dir, "live-free.json").writeText(GeminiProtocol.json(
            "config" to GeminiProtocol.setup(Settings(personality = roast), "free", ""),
            "command" to GeminiProtocol.startCommand(Settings(personality = roast), "free")
        ).toString())
        File(dir, "live-cafe.json").writeText(GeminiProtocol.json(
            "config" to GeminiProtocol.setup(Settings(personality = roast), "cafe", ""),
            "command" to GeminiProtocol.startCommand(Settings(personality = roast), "cafe")
        ).toString())
    }
}

