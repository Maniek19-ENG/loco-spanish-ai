package com.locospanish.ai

import com.locospanish.ai.model.Level
import com.locospanish.ai.tutor.*
import org.junit.Assert.*
import org.junit.Test

class LevelFallbackTest {
    /** A finished scene bank never leaves the learner waiting on a silent model for the next card. */
    @Test fun everyLevelAlwaysHasANextCard() {
        for(level in Level.values()) for(scenario in TopicLessons.plans.keys) {
            val bank=if(level==Level.A1) LessonReserve.words(scenario) else LevelPhrases.words(scenario,level)
            val all=bank.map { it.es }
            // Everything in the scene known: another scene of the same level follows.
            val next=LevelPhrases.anyNext(scenario,level,all,emptyList())
            assertNotNull("$level/$scenario",next)
            assertFalse("$level/$scenario",next!!.es in all)
            // Everything taught but not mastered: the scene repeats, not the card just shown.
            val repeat=LevelPhrases.anyNext(scenario,level,emptyList(),TopicLessons.plans.keys.flatMap { s -> if(level==Level.A1) LessonReserve.words(s).map { it.es } else LevelPhrases.words(s,level).map { it.es } },all.first())
            assertNotNull("$level/$scenario repeat",repeat)
            assertNotEquals(all.first(),repeat!!.es)
        }
    }
}
