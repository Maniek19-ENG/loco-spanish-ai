package com.locospanish.ai.tutor

import com.google.gson.JsonObject

/** The model interprets meaning; only decisions grounded in the real utterance are accepted. */
object DialogueIntent {
    enum class Kind { ANSWER, HELP, QUESTION, TOPIC, SKIP, CHAT }
    fun read(args:JsonObject,text:String,target:LessonWord?):Kind {
        if(LearnerIntent.questionInsteadOfTrial(text,target) || LearnerIntent.currentWordReference(text,target)) return Kind.HELP
        if(WordAttemptPolicy.newTopic(text)) return Kind.TOPIC
        val heard=args.get("heard_text")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        if(text.isBlank() || AssessmentPolicy.speech(text)!=AssessmentPolicy.speech(heard)) return Kind.ANSWER
        // An exact answer remains an answer even if the model mislabels its intent.
        if(target!=null && WordAttemptPolicy.directAnswer(target,text)) return Kind.ANSWER
        return runCatching { Kind.valueOf(args.get("intent")?.asString.orEmpty()) }.getOrDefault(Kind.ANSWER)
    }
    fun replyOnly(kind:Kind)=kind in setOf(Kind.HELP,Kind.QUESTION,Kind.CHAT)
}
