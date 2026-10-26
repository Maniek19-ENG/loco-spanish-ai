package com.locospanish.ai.realtime

import com.google.gson.JsonParser
import com.locospanish.ai.model.Level
import com.locospanish.ai.tutor.*
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Creates typed teaching material independently of the Live model's spoken reply. */
class GeminiWordPlanner(private val http:Call.Factory=OkHttpClient.Builder().callTimeout(15,TimeUnit.SECONDS).build()) {
 companion object {
  const val MODEL="gemini-3.8-flash"
  fun body(topic:String,level:Level,excluded:Collection<String>):com.google.gson.JsonObject {
   val schema=GeminiProtocol.json("type" to "object","properties" to GeminiProtocol.json("words" to GeminiProtocol.json("type" to "array","minItems" to 1,"maxItems" to 12,"items" to GeminiProtocol.json("type" to "object","properties" to GeminiProtocol.json("es" to GeminiProtocol.json("type" to "string"),"pl" to GeminiProtocol.json("type" to "string")),"required" to listOf("es","pl")))),"required" to listOf("words"))
   val prompt="Przygotuj 6 NOWYCH haseł do nauki hiszpańskiego. Temat: $topic. Poziom: ${level.name}. es: poprawna hiszpańska pisownia, pl: krótkie znaczenie po polsku. Na A1 WYŁĄCZNIE pojedyncze słowo, KAŻDY rzeczownik z poprawnym rodzajnikiem określonym el/la/los/las w es. Czasowniki i przymiotniki bez rodzajnika, bez zdań. Na innych poziomach dopuszczalny krótki zwrot. Bez żartów, pytań i komentarzy. Nie używaj żadnego hasła z listy wykluczeń: "+excluded.joinToString(", ").take(18000)
   return GeminiProtocol.json("contents" to listOf(GeminiProtocol.json("role" to "user","parts" to listOf(GeminiProtocol.json("text" to prompt)))),"generationConfig" to GeminiProtocol.json("maxOutputTokens" to 2048,"thinkingConfig" to GeminiProtocol.json("thinkingLevel" to "low"),"responseMimeType" to "application/json","responseJsonSchema" to schema))
  }
  fun decode(raw:String,level:Level,excluded:Collection<String>,limit:Int=12):List<LessonWord> {
   val blocked=excluded.map(AssessmentPolicy::key).toSet()
   return JsonParser.parseString(raw).asJsonObject.getAsJsonArray("words").mapNotNull { item ->
    val word=item.asJsonObject;val es=word.str("es").trim();val pl=word.str("pl").trim()
    if(es.isBlank() || pl.isBlank() || es.length>160 || pl.length>180 || AssessmentPolicy.key(es) in blocked || (level==Level.A1 && !LessonTurnPolicy.singleWord(es))) null else TeachingForm.complete(LessonWord(es,pl),word.str("article"))
   }.distinctBy { AssessmentPolicy.key(it.es) }.take(limit)
  }
 }
 suspend fun generate(key:String,topic:String,level:Level,excluded:Collection<String>):List<LessonWord> = suspendCancellableCoroutine { cont ->
  val request=Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent").header("x-goog-api-key",key).post(body(topic,level,excluded).toString().toRequestBody("application/json".toMediaType())).build()
  val call=http.newCall(request);cont.invokeOnCancellation { call.cancel() }
  call.enqueue(object:Callback {
   override fun onFailure(call:Call,e:java.io.IOException) { if(cont.isActive) cont.resumeWithException(IllegalStateException("Nie udało się pobrać nowych słów.")) }
   override fun onResponse(call:Call,response:Response) {
    val result=runCatching { response.use {
     check(it.isSuccessful) { GeminiProtocol.problem(it.code) }
     val root=JsonParser.parseString(it.body.string()).asJsonObject
     val parts=root.getAsJsonArray("candidates")?.firstOrNull()?.asJsonObject?.getAsJsonObject("content")?.getAsJsonArray("parts")
     val text=parts?.filterNot { part -> part.asJsonObject.flag("thought") }?.joinToString("") { part -> part.asJsonObject.str("text") }.orEmpty()
     decode(text,level,excluded).also { words -> check(words.isNotEmpty()) { "Nie otrzymano nowego słowa." } }
    } }
    if(cont.isActive) result.fold({cont.resume(it)},{cont.resumeWithException(it)})
   }
  })
 }
}
