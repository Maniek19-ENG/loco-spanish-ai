package com.locospanish.ai
import com.locospanish.ai.model.*
import com.locospanish.ai.personality.PersonalityEngine
import com.locospanish.ai.realtime.GeminiProtocol
import org.junit.Assert.*
import org.junit.Test
class FilmStyleTest {
    @Test fun styleWorksWithEveryProfileAndPreservesCorrection100() {
        for(preset in Preset.entries) {
            val raw=Settings(personality=PersonalityEngine.preset(preset).copy(correction=100),tiktokStyle=false)
            val enabled=raw.withFilmStyle(true)
            assertEquals(preset,enabled.personality.preset);assertEquals(100,enabled.personality.correction)
            assertTrue(enabled.tiktokStyle);assertTrue(enabled.personality.roastMode)
            assertEquals(100,enabled.personality.roast);assertEquals(Profanity.STRONG,enabled.personality.profanity)
            assertEquals(enabled.personality,enabled.forDialog().personality)
            assertTrue(GeminiProtocol.prompt(enabled,"cafe","").contains("Korekta 100/100"))
        }
    }
    @Test fun adjustingCorrectionDoesNotDisableStyleOrResetAnotherValue() {
        val active=Settings().withFilmStyle(true)
        for(value in 0..100) {
            val adjusted=active.copy(personality=active.personality.copy(correction=value))
            assertTrue(adjusted.tiktokStyle);assertEquals(value,adjusted.forDialog().personality.correction)
            assertEquals(active.personality.roast,adjusted.forDialog().personality.roast)
        }
    }
    @Test fun switchingStyleOffKeepsNewCorrectionAndChosenProfile() {
        val original=Settings(personality=PersonalityEngine.preset(Preset.FRIEND),autoCorrection=false,tiktokStyle=false)
        val edited=original.withFilmStyle(true).copy(personality=original.withFilmStyle(true).personality.copy(preset=Preset.CUSTOM,correction=100))
        val disabled=edited.withFilmStyle(false)
        assertEquals(Preset.CUSTOM,disabled.personality.preset);assertEquals(100,disabled.personality.correction)
        assertEquals(original.personality.sarcasm,disabled.personality.sarcasm)
        assertFalse(disabled.autoCorrection);assertFalse(disabled.tiktokStyle)
    }
    @Test fun roastCanBeEnabledForTeacherWithoutKeepingZeroStrength() {
        val active=Settings(personality=PersonalityEngine.preset(Preset.TEACHER),tiktokStyle=false).withRoast(true)
        assertTrue(active.personality.roastMode);assertEquals(100,active.personality.roast)
        assertEquals(Preset.TEACHER,active.personality.preset)
    }
}
