package com.locospanish.ai
import com.locospanish.ai.model.*
import com.locospanish.ai.realtime.GeminiProtocol
import com.locospanish.ai.tutor.AssessmentPolicy
import java.io.File
import org.junit.Test
class AssessmentExportTest {
    @Test fun exportExactProtocolAndExecutableAssessmentForLiveVerification() {
        val dir=File("build/assessment-verification").apply { mkdirs() }
        val settings=Settings(personality=com.locospanish.ai.personality.PersonalityEngine.preset(Preset.FRIEND).copy(correction=100),tiktokStyle=false).withFilmStyle(true)
        File(dir,"spanish-word.json").writeText(com.locospanish.ai.voice.SpanishWordSpeech.setup(settings.voice).toString())
        File(dir,"config.json").writeText(GeminiProtocol.setup(settings,"free","").toString())
        val classes=listOf(AssessmentBridge::class.java,AssessmentPolicy::class.java,kotlin.Unit::class.java,com.google.gson.Gson::class.java)
        File(dir,"classpath.txt").writeText(classes.map { File(it.protectionDomain.codeSource.location.toURI()).absolutePath }.distinct().joinToString(File.pathSeparator))
        File(dir,"cases.json").writeText(GeminiProtocol.json("cases" to listOf(
            GeminiProtocol.json("text" to "Yo soy 31 años.","expected" to "ERROR"),
            GeminiProtocol.json("text" to "Yo tengo 31 años.","expected" to "CORRECT"),
            GeminiProtocol.json("text" to "Tengo treinta y un años.","expected" to "CORRECT"),
            GeminiProtocol.json("text" to "Quisiera un café, por favor.","expected" to "CORRECT"),
            GeminiProtocol.json("text" to "Quiero un habitación.","expected" to "ERROR"),
            GeminiProtocol.json("text" to "Quiero una habitación tranquila.","expected" to "CORRECT"),
            GeminiProtocol.json("text" to "Necesito otra habitación.","expected" to "CORRECT"),
            GeminiProtocol.json("text" to "Yo tiene una reserva.","expected" to "ERROR"),
            GeminiProtocol.json("text" to "Tengo una reserva.","expected" to "CORRECT")
        )).toString())
    }
}
