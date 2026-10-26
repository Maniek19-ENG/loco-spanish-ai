package com.locospanish.ai.realtime

import android.content.Context
import com.locospanish.ai.model.Level
import com.locospanish.ai.tutor.LessonWord
import com.locospanish.ai.tutor.AssessmentPolicy
import java.security.MessageDigest

/** Downloaded material only. Mastery is recorded separately by LocalRepository. */
class LessonWordCache(context:Context) {
    private val preferences=context.getSharedPreferences("downloaded-vocabulary-v1",Context.MODE_PRIVATE)
    private fun key(topic:String,level:Level)=MessageDigest.getInstance("SHA-256").digest((level.name+"|"+topic).toByteArray()).joinToString("") { "%02x".format(it) }
    fun read(topic:String,level:Level,excluded:Collection<String>):List<LessonWord> = runCatching {
        GeminiWordPlanner.decode(preferences.getString(key(topic,level),null) ?: return emptyList(),level,excluded,limit=120)
    }.getOrDefault(emptyList())
    fun save(topic:String,level:Level,words:List<LessonWord>) {
        val combined=(words+read(topic,level,emptyList())).distinctBy { AssessmentPolicy.key(it.es) }.take(120)
        preferences.edit().putString(key(topic,level),GeminiProtocol.json("words" to combined).toString()).apply()
    }
}
