package com.locospanish.ai.tutor

import com.locospanish.ai.model.*

/** A short evidence and dialogue summary crosses the network, never the complete archive. */
object PromptBuilder {
    fun memory(library: Library): String = buildString {
        library.sessions.asReversed().take(2).forEach { session ->
            session.transcript.filter { it.text.isNotBlank() }.takeLast(6).forEach {
                appendLine("${it.role}: ${it.text.take(320)}")
            }
        }
        appendLine("Ostatnio ćwiczone błędy:")
        library.sessions.asReversed().flatMap { it.learning.asReversed() }
            .filter { it.kind in setOf("grammar", "difficult_word", "pronunciation") }
            .distinctBy { it.kind + it.term.lowercase() }.take(6)
            .forEach { appendLine("${it.kind}: ${it.term.take(80)} → ${it.correction.take(120)}") }
    }.take(2400)
}
