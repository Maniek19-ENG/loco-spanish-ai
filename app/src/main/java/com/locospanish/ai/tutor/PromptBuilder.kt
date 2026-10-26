package com.locospanish.ai.tutor

import com.locospanish.ai.model.*

object PromptBuilder {
    fun memory(library: Library, scenario: String? = null, level: Level? = null): String {
        val sessions = library.sessions.filter { (scenario == null || it.scenarioId == scenario) && (level == null || it.level == level) }
        val recent = sessions.filter { it.transcript.any { t -> t.role == "user" && t.text.isNotBlank() } || it.learning.isNotEmpty() }.asReversed().take(2)
        return buildString {
            val known=WordKnowledge.mastered(library).asReversed().take(40)
            if(known.isNotEmpty()) {
                appendLine("ZNANE_SLOWA: "+known.joinToString("; ").take(600))
                appendLine("Uczeń już użył tych słów poprawnie. Nie odpytuj z nich w kółko, ucz nowych słów i rozwijaj nowe sytuacje. Powtórka tylko na prośbę lub po rzeczywistym błędzie.")
            }
            val used=WordKnowledge.confirmed(library).filter { word -> word !in known }.asReversed().take(20)
            if(used.isNotEmpty()) appendLine("JUŻ_POPRAWNIE_UŻYTE (nie odpytuj znowu o znaczenie): "+used.joinToString("; ").take(300))
            sessions.flatMap { it.learning }.filter { it.kind == "repetition" && it.id.startsWith("scenario:") }
                .distinctBy { it.term }.takeLast(8).forEach { appendLine("PRACTICADA: ${it.term.take(90)}") }
            if(sessions.any { it.transcript.any { t -> t.role == "user" && t.text.isNotBlank() } || it.learning.isNotEmpty() }) {
                appendLine("KONTYNUACJA_LEKCJI")
                appendLine("Zapisane próby ucznia: " + sessions.sumOf { s -> s.transcript.count { it.role == "user" && it.text.isNotBlank() } })
                appendLine("Ostatni etap:")
                recent.firstOrNull()?.transcript?.filter { it.text.isNotBlank() }?.takeLast(6)?.forEach {
                    appendLine("${it.role}: ${it.text.take(300)}")
                }
                appendLine("Ostatnio używane słowa i konstrukcje:")
                sessions.asReversed().flatMap { it.learning.asReversed() }.distinctBy { it.kind + it.term.lowercase() }.take(6)
                    .forEach { appendLine("${it.kind}: ${it.term.take(65)} → ${it.correction.take(90)}") }
                appendLine("Najpierw wznów ostatnie zadanie lub przejdź do kolejnego kroku; nie zaczynaj całego scenariusza ponownie.")
            }
            appendLine("Ostatnio ćwiczone błędy:")
            sessions.asReversed().flatMap { it.learning.asReversed() }
                .filter { it.kind in setOf("grammar", "difficult_word", "pronunciation") }
                .distinctBy { it.kind + it.term.lowercase() }.take(6)
                .forEach { appendLine("${it.kind}: ${it.term.take(80)} → ${it.correction.take(120)}") }
            recent.forEach { session -> session.transcript.filter { it.text.isNotBlank() }.takeLast(3).forEach {
                appendLine("${it.role}: ${it.text.take(160)}")
            } }
        }.take(2400)
    }
}

