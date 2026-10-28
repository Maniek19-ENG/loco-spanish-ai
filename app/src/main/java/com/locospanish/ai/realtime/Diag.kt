package com.locospanish.ai.realtime

import com.google.gson.JsonObject

/** In-memory trace of the live conversation, copied by the learner from the screen to report a stall. Never holds the access key. */
object Diag {
    private val lines = ArrayDeque<String>()
    private val start = System.currentTimeMillis()
    private var repeats = 0
    @Synchronized fun log(message: String) {
        val t = System.currentTimeMillis() - start
        val line = message.replace('\n', ' ').take(400)
        // Streamed audio arrives in many chunks; one line with a counter is enough.
        if (lines.isNotEmpty() && line == "IN audio" && lines.last().endsWith("IN audio" + if (repeats > 0) " x${repeats + 1}" else "")) {
            repeats++; lines.removeLast(); lines.addLast("${t / 1000}.${t % 1000 / 100} IN audio x${repeats + 1}"); return
        }
        repeats = 0
        lines.addLast("${t / 1000}.${t % 1000 / 100} $line")
        while (lines.size > 500) lines.removeFirst()
    }
    @Synchronized fun dump(): String = lines.joinToString("\n")
    @Synchronized fun clear() { lines.clear(); repeats = 0 }
    fun summary(e: JsonObject): String {
        val parts = mutableListOf<String>()
        e.getAsJsonObject("serverContent")?.let { c ->
            c.getAsJsonObject("inputTranscription")?.get("text")?.asString?.let { parts += "user:'$it'" }
            c.getAsJsonObject("outputTranscription")?.get("text")?.asString?.let { parts += "loco:'$it'" }
            if (c.has("modelTurn")) parts += "audio"
            listOf("turnComplete", "generationComplete", "interrupted").filter { c.get(it)?.asBoolean == true }.forEach { parts += it }
        }
        e.getAsJsonObject("toolCall")?.getAsJsonArray("functionCalls")?.forEach { call ->
            val fn = call.asJsonObject; val a = fn.getAsJsonObject("args")
            parts += "tool:${fn.get("name")?.asString}(${listOf("verdict", "confidence", "heard_text", "term", "kind").mapNotNull { k -> a?.get(k)?.takeIf { it.isJsonPrimitive }?.asString?.let { "$k=$it" } }.joinToString(",")})"
        }
        if (e.has("setupComplete")) parts += "setupComplete"
        if (e.has("goAway")) parts += "goAway"
        e.get("_connection_closed")?.let { parts += "closed:${it.asString} ${e.get("_reason")?.asString.orEmpty()}" }
        return "IN " + parts.ifEmpty { listOf(e.keySet().joinToString(",")) }.joinToString(" ")
    }
}
