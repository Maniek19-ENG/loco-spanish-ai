package com.locospanish.ai

import com.locospanish.ai.tutor.*
import org.junit.Assert.*
import org.junit.Test

class ArticleConsistencyTest {
    private val articles=setOf("el","la","los","las","un","una","unos","unas")
    /** A noun always appears with its article on the card and in the voice; a verb, adjective or adverb never does. */
    @Test fun everyBankNounGetsItsArticleAndOtherWordsStayBare() {
        assertEquals("la cama",TeachingForm.complete(LessonWord("cama","łóżko")).es)
        assertEquals("el agua",TeachingForm.complete(LessonWord("agua","woda")).es)
        assertEquals("los zapatos",TeachingForm.complete(LessonWord("zapatos","buty")).es)
        assertEquals("la estación",TeachingForm.complete(LessonWord("estación","stacja")).es)
        for(bare in listOf("pagar","libre","aquí","hoy","beber")) assertEquals(bare,TeachingForm.complete(LessonWord(bare,"x")).es)
        // The same word reached from any path has the same form.
        val all=TopicLessons.plans.keys.flatMap { TopicLessons.vocabulary(it)+LessonReserve.words(it) }.map { TeachingForm.complete(it) }
        all.groupBy { AssessmentPolicy.key(it.es) }.forEach { (key,forms) -> assertEquals(key,1,forms.map { it.es.substringBefore(' ').lowercase().takeIf { a -> a in articles } }.distinct().size) }
    }
}
