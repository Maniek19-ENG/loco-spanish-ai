package com.locospanish.ai

import com.locospanish.ai.tutor.*
import org.junit.Assert.*
import org.junit.Test

class LessonReserveTest {
    @Test fun everyScenarioHasNewSingleWordsAfterItsInitialVocabulary() {
        for(scenario in TopicLessons.plans.keys) {
            val known=TopicLessons.vocabulary(scenario).map { it.es }
            val next=LessonReserve.next(scenario,known)
            assertNotNull("Brak zapasu: $scenario",next)
            assertTrue(LessonTurnPolicy.singleWord(next!!.es))
            assertTrue(next.pl.isNotBlank())
            assertFalse(known.map(AssessmentPolicy::key).contains(AssessmentPolicy.key(next.es)))
            assertTrue(LessonReserve.words(scenario).all { LessonTurnPolicy.singleWord(it.es) && it.pl.isNotBlank() })
        }
    }
    @Test fun exhaustedReserveNeverRepeatsKnownWordsOrChangesUnknownTopic() {
        val words=LessonReserve.words("hotel")
        assertNull(LessonReserve.next("hotel",words.map { it.es }))
        assertNull(LessonReserve.next("unknown-topic",emptyList()))
        assertNotEquals(LessonReserve.next("hotel",emptyList()),LessonReserve.next("daily",emptyList()))
    }
}
