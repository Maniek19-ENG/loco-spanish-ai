package com.locospanish.ai.voice

data class SystemVoiceChoice(val name:String,val language:String,val country:String,val network:Boolean,val installed:Boolean,val quality:Int,val latency:Int)
object SystemVoicePolicy {
    fun choose(voices:List<SystemVoiceChoice>,language:String,country:String):String? = voices
        .filter { it.language==language && (language!="es" || it.country==country) && it.installed }
        .sortedWith(compareBy<SystemVoiceChoice> { it.network }.thenByDescending { it.quality }.thenBy { it.latency }.thenBy { it.name })
        .firstOrNull()?.name
}
