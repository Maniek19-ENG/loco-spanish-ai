package com.locospanish.ai
import com.google.gson.*
import com.locospanish.ai.model.*
import com.locospanish.ai.realtime.*
import org.junit.Assert.*
import org.junit.Test

class ConnectionContractTest {
 @Test fun setupExactlyMatchesThePreviouslyWorkingConnectionContract() {
  for(level in Level.entries) for(voice in listOf("Kore","Puck","marin")) {
   val settings=Settings(level=level,voice=voice)
   assertEquals(KnownWorkingConnection.setup(settings,"hotel","pamięć"),GeminiProtocol.setup(settings,"hotel","pamięć"))
  }
 }
 private fun checkNoBlankEnums(value:JsonElement) {
  if(value.isJsonArray) value.asJsonArray.forEach(::checkNoBlankEnums)
  if(value.isJsonObject) value.asJsonObject.entrySet().forEach { (key,item) ->
   if(key=="enum") assertTrue("Blank enum value in connection/material schema",item.asJsonArray.none { it.asString.isBlank() })
   checkNoBlankEnums(item)
  }
 }
 @Test fun liveAndDownloadedMaterialDoNotSendTheNewBlankArticleEnums() {
  checkNoBlankEnums(GeminiProtocol.setup(Settings(),"hotel",""))
  val body=GeminiWordPlanner.body("Hotel",Level.A1,emptyList())
  checkNoBlankEnums(body)
  val required=body.getAsJsonObject("generationConfig").getAsJsonObject("responseJsonSchema").getAsJsonObject("properties").getAsJsonObject("words").getAsJsonObject("items").getAsJsonArray("required").map { it.asString }
  assertEquals(listOf("es","pl"),required)
  assertTrue(body.toString().contains("rodzajnikiem"))
 }
 @Test fun theWorkingTeachWordArgumentsCarryTheCompleteNounWithoutExtraRequiredFields() {
  val declarations=GeminiProtocol.setup(Settings(),"hotel","").getAsJsonObject("setup").getAsJsonArray("tools")[0].asJsonObject.getAsJsonArray("functionDeclarations")
  val teach=declarations.first { it.asJsonObject.get("name").asString=="teach_word" }.asJsonObject.getAsJsonObject("parameters")
  assertEquals(listOf("term","translation"),teach.getAsJsonArray("required").map { it.asString })
  assertFalse(teach.getAsJsonObject("properties").has("article"))
 }
}
