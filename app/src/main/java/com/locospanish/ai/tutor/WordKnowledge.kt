package com.locospanish.ai.tutor
import com.locospanish.ai.model.*
object WordKnowledge {
    private val catalog by lazy { TopicLessons.plans.keys.flatMap { TopicLessons.vocabulary(it) }.associateBy { AssessmentPolicy.key(it.es) } }
    fun lookup(term:String)=catalog[AssessmentPolicy.key(term)]
    fun confirmed(library: Library): List<String> = library.sessions.flatMap { it.learning }
        .filter { it.kind in setOf("known_word","word","repetition") && it.evidence.isNotBlank() && AssessmentPolicy.contains(it.evidence,it.term) }
        .distinctBy { AssessmentPolicy.key(it.term) }.map { it.term }
    fun reviewWords(library:Library):List<LessonWord> = library.sessions.flatMap { it.learning }.asReversed()
        .filter { it.kind in setOf("known_word","word","repetition") && it.evidence.isNotBlank() && AssessmentPolicy.contains(it.evidence,it.term) }
        .distinctBy { AssessmentPolicy.key(it.term) }.mapNotNull { event ->
            val entry=catalog[AssessmentPolicy.key(event.term)]
            val meaning=entry?.pl ?: event.explanation.trim().takeIf { it.isNotBlank() && it.length<=100 }
            meaning?.let { LessonWord(event.term,it) }
        }
    fun recentComments(library:Library)=library.sessions.flatMap { it.transcript }.filter { it.role=="assistant" && !it.id.startsWith("word:") && it.text.isNotBlank() }.takeLast(24).map { it.text.take(900) }
    fun mastered(library: Library): List<String> {
        val events=library.sessions.flatMap { it.learning }.filter { it.kind in setOf("known_word","word","repetition") && it.evidence.isNotBlank() && AssessmentPolicy.contains(it.evidence,it.term) }.distinctBy { it.id }
        return events.groupBy { AssessmentPolicy.key(it.term) }.values.filter { group ->
            group.any { it.kind == "known_word" } || group.distinctBy { it.id }.size >= 2
        }.map { it.last().term }
    }
}
