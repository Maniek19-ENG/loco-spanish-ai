package com.locospanish.ai.tutor

import com.locospanish.ai.model.*

/** A short evidence and dialogue summary crosses the network, never the complete archive. */
object PromptBuilder {
    fun memory(library: Library): String = buildString {
        val recent = library.sessions.asReversed().take(3)
        appendLine("Ostatnio ćwiczone błędy:")
        recent.flatMap { it.learning.asReversed() }
            .filter { it.kind in setOf("grammar", "difficult_word", "pronunciation") }
            .distinctBy { it.kind + it.term.lowercase() }.take(6)
            .forEach { appendLine("${it.kind}: ${it.term.take(80)} → ${it.correction.take(120)}") }
        appendLine("Tło z poprzednich rozmów (tylko pamięć, nie wracaj do tego z własnej inicjatywy):")
        recent.take(2).forEach { session ->
            session.transcript.filter { it.role == "user" && it.text.isNotBlank() }.takeLast(4).forEach {
                appendLine("uczeń: ${it.text.take(160)}")
            }
        }
        appendLine("Już użyte wypowiedzi LOCO (nie powtarzaj ich, zmień otwarcie, żarty i przykłady):")
        recent.flatMap { s -> s.transcript.filter { it.role != "user" && it.text.isNotBlank() }.takeLast(5) }
            .forEach { appendLine("LOCO: ${it.text.take(90)}") }
    }.take(2400)
}
