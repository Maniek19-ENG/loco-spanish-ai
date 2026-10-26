package com.locospanish.ai
import com.locospanish.ai.realtime.GeminiWordPlanner
import com.locospanish.ai.model.Level
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
class WordPlannerTest {
 @Test fun plannerDecodesHttpResponseAndSendsApiKeyOnlyInHeader()=runBlocking {
  val http=OkHttpClient.Builder().addInterceptor { chain ->
   val request=chain.request()
   assertEquals("TEST_KEY",request.header("x-goog-api-key"))
   assertFalse(request.url.toString().contains("TEST_KEY"))
   val answer="""{"candidates":[{"content":{"parts":[{"thought":true,"text":"not vocabulary"},{"text":"{\"words\":[{\"es\":\"el balcón\",\"pl\":\"balkon\"}]}"}]}}]}"""
   Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(answer.toResponseBody()).build()
  }.build()
  assertEquals("el balcón",GeminiWordPlanner(http).generate("TEST_KEY","Hotel",Level.A1,emptyList()).single().es)
 }
 @Test fun httpQuotaAndEmptyResponsesFailInsteadOfCreatingPhantomWords()=runBlocking {
  for((status,body) in listOf(429 to "{}",200 to "{\"candidates\":[]}")) {
   val http=OkHttpClient.Builder().addInterceptor { chain -> Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("Test").body(body.toResponseBody()).build() }.build()
   assertTrue(runCatching { GeminiWordPlanner(http).generate("TEST_KEY","Hotel",Level.A1,emptyList()) }.isFailure)
  }
 }
 @Test fun filtersKnownWordsSentencesAndEmptyTranslationsOnA1() {
  val raw="""{"words":[{"es":"la habitación","pl":"pokój"},{"es":"Quiero una habitación","pl":"Chcę pokój"},{"es":"el minibar","pl":"minibar"},{"es":"el minibar","pl":"minibar"},{"es":"la persiana","pl":""}]}"""
  val words=GeminiWordPlanner.decode(raw,Level.A1,listOf("habitacion"))
  assertEquals(listOf("el minibar"),words.map { it.es })
 }
 @Test fun requestHasTypedSchemaTopicAndExclusions() {
  val body=GeminiWordPlanner.body("Hotel",Level.A1,listOf("cama","reserva"))
  assertEquals("application/json",body.getAsJsonObject("generationConfig").get("responseMimeType").asString)
  assertTrue(body.toString().contains("Hotel"));assertTrue(body.toString().contains("cama, reserva"))
  assertTrue(body.getAsJsonObject("generationConfig").has("responseJsonSchema"))
 }
 @Test fun shortPhrasesRemainAvailableAboveA1() {
  val raw="""{"words":[{"es":"Necesito una habitación","pl":"Potrzebuję pokoju"}]}"""
  assertEquals(1,GeminiWordPlanner.decode(raw,Level.A2,emptyList()).size)
  assertTrue(GeminiWordPlanner.decode(raw,Level.A1,emptyList()).isEmpty())
 }
}
