package com.locospanish.ai.realtime

import com.google.gson.JsonParser
import com.locospanish.ai.model.Settings
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class GeminiPreview {
    private val http=OkHttpClient.Builder().callTimeout(40,TimeUnit.SECONDS).build()
    suspend fun generate(settings:Settings,key:String,scenario:String):String = suspendCancellableCoroutine { cont ->
        val body=GeminiProtocol.json("systemInstruction" to GeminiProtocol.json("parts" to listOf(GeminiProtocol.json("text" to GeminiProtocol.prompt(settings,scenario,"")))),
            "contents" to listOf(GeminiProtocol.json("role" to "user","parts" to listOf(GeminiProtocol.json("text" to "To próbka osobowości. Zareaguj w 2–4 zdaniach na błąd ucznia: Yo soy 31 años. Nie zapisuj postępów. Nie wywołuj narzędzi.")))),
            "generationConfig" to GeminiProtocol.json("maxOutputTokens" to 1024))
        val call=http.newCall(Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent")
            .header("x-goog-api-key",key).post(body.toString().toRequestBody("application/json".toMediaType())).build())
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object:Callback {
            override fun onFailure(call:Call,e:IOException) { if(cont.isActive) cont.resumeWithException(IOException("Nie udało się pobrać próbki. Sprawdź internet.")) }
            override fun onResponse(call:Call,response:Response) {
                val result=runCatching { response.use {
                    if(!it.isSuccessful) error(GeminiProtocol.problem(it.code))
                    val root=JsonParser.parseString(it.body.string()).asJsonObject
                    val parts=root.getAsJsonArray("candidates")?.firstOrNull()?.asJsonObject?.getAsJsonObject("content")?.getAsJsonArray("parts")
                    parts?.filterNot { p -> p.asJsonObject.flag("thought") }?.joinToString("") { p -> p.asJsonObject.str("text") }
                        ?.takeIf { text -> text.isNotBlank() } ?: error("Gemini nie zwróciło próbki. Spróbuj ponownie.")
                } }
                if(cont.isActive) result.fold({cont.resume(it)},{cont.resumeWithException(it)})
            }
        })
    }
}
