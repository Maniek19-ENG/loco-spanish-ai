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
        assertTrue(prompt.contains("assess_user_turn"))
        assertTrue(prompt.contains("maksymalnie 2 sekundy"))
        assertTrue(prompt.contains("czterema ostatnimi wypowiedziami"))
        assertTrue(GeminiProtocol.setup(Settings(personality = p), "daily", "").toString().contains("assess_user_turn"))
        val light = PersonalityEngine.instructions(p.copy(profanity = Profanity.LIGHT), true)
        assertTrue(light.contains("cholera")); assertFalse(light.contains("kurwa"))
    }
    @Test fun openingIsFreeAndReconnectDoesNotRestartLesson() {
        val p = PersonalityEngine.preset(Preset.ROAST)
        val fresh = PersonalityEngine.opening(p, "Kawiarnia", false)
        assertTrue(fresh.contains("inną od zapisanych w pamięci"))
        assertTrue(fresh.contains("zapytać o cokolwiek"))
        assertFalse(fresh.contains("Hola, me llamo"))
        val legacy = PersonalityEngine.opening(p, "Kawiarnia", false, legacyBehavior = true)
        assertTrue(legacy.contains("mocnym polskim przekleństwem"))
        val reconnect = PersonalityEngine.opening(p, "Kawiarnia", true)
        assertTrue(reconnect.contains("Nie witaj ponownie"))
    }
    @Test fun correctionZeroAndGlobalOffOverrideOtherPersonalityValues() {
        val p = PersonalityEngine.preset(Preset.ROAST)
        for (settings in listOf(Settings(autoCorrection = false, personality = p), Settings(personality = p.copy(correction = 0)))) {
            assertTrue(GeminiProtocol.prompt(settings, "daily", "").contains("Nie przerywaj automatycznie"))
        }
    }
    @Test fun polishSpanishLessonKeepsNeonAssessmentAndPractice() {
        val setup = GeminiProtocol.setup(Settings(), "cafe", "").toString()
        assertTrue(setup.contains("polski–hiszpański"))
        assertTrue(setup.contains("assess_user_turn"))
        assertTrue(setup.contains("ODEGRAJ ROZMÓWCĘ"))
        assertFalse(setup.contains("poproś o jedną krótką próbę"))
        assertTrue(setup.contains("bez zdradzania odpowiedzi"))
        val mild = GeminiProtocol.prompt(Settings(personality = PersonalityEngine.preset(Preset.TEACHER)), "cafe", "")
        assertFalse(mild.contains("pendejo"))
        assertFalse(mild.contains("kurwa"))
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


