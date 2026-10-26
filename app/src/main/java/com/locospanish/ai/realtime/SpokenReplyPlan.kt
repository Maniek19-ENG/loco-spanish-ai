package com.locospanish.ai.realtime

import com.locospanish.ai.tutor.*

/** Only a transcript prefix matching a known, checked script may be played early. */
object SpokenReplyPlan {
 fun script(roast:String,target:LessonWord?,verdict:String,reason:String,help:Boolean,reviewFinished:Boolean,question:String=LessonTurnPolicy.question(target)):String {
  if(roast.isBlank() || target==null || help || reviewFinished) return ""
  val correction=if(verdict=="ERROR") reason.substringBefore('.').trim().take(120).takeIf { it.isNotBlank() }?.plus(". ").orEmpty() else ""
  return "$roast $correction${question}".trim()
 }
 private fun normal(text:String)=AssessmentPolicy.normal(text).replace(Regex("\\b(?:ha|he|hi|hah|heh|(?:ha){2,}|(?:he){2,}|(?:hi){2,})\\b")," ").replace(Regex("\\s+")," ").trim()
 fun compatible(script:String,transcript:String):Boolean {
  if(script.isBlank() || transcript.isBlank()) return false
  val expected=normal(script);val actual=normal(transcript)
  return actual.isNotBlank() && (expected.startsWith(actual) || actual==expected)
 }
 fun ready(script:String,transcript:String):Boolean = compatible(script,transcript) && normal(transcript).split(' ').size>=4
}
