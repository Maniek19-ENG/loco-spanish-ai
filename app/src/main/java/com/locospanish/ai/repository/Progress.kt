package com.locospanish.ai.repository

import com.locospanish.ai.model.*
import java.time.*
import com.locospanish.ai.tutor.WordKnowledge
import com.locospanish.ai.tutor.AssessmentPolicy

data class Progress(val seconds: Long, val todaySeconds: Long, val sessions: Int, val streak: Int,
    val words: List<String>, val errors: List<LearningEvent>, val difficult: List<Pair<String, Int>>,
    val completed: Set<String>, val repetitions: Int, val practiceTurns: Int = 0, val masteredWords: List<String> = emptyList())
object ProgressCalculator {
    fun calculate(library: Library, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Progress {
        val sessions = library.sessions.filter { it.durationSeconds > 0 || it.transcript.any { t -> t.role == "user" && t.text.isNotBlank() } || it.learning.isNotEmpty() }
        fun date(s: Session) = Instant.ofEpochMilli(s.startedAt).atZone(zone).toLocalDate()
        val days = sessions.map(::date).toSet()
        var day = if (today in days) today else today.minusDays(1)
        var streak = 0
        while (day in days) { streak++; day = day.minusDays(1) }
        val events = sessions.flatMap { it.learning }.distinctBy { it.id }
        val errors = events.filter { it.kind in setOf("grammar", "pronunciation") }
        return Progress(sessions.sumOf { it.durationSeconds }, sessions.filter { date(it) == today }.sumOf { it.durationSeconds },
            sessions.size, streak, events.filter { it.kind in setOf("word","known_word","repetition") }.map { it.term.lowercase() }.distinctBy(AssessmentPolicy::key),
            errors, events.filter { it.kind == "difficult_word" }.groupingBy { it.term.lowercase() }.eachCount()
                .toList().sortedByDescending { it.second }, sessions.filter { it.completed }.map { it.scenarioId }.toSet(),
            events.count { it.kind == "repetition" }, sessions.sumOf { s -> s.transcript.count { t -> t.role == "user" && t.text.isNotBlank() } }, WordKnowledge.mastered(library))
    }
}
