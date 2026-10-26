package com.locospanish.ai.realtime

import com.locospanish.ai.model.Settings

/** A vocal cue inside the same model utterance; never a second playback or a canned clip. */
object SpokenLaughter {
 private val laugh=Regex("\\b(?:ha|he|hi|hah|heh|(?:ha){2,}|(?:he){2,}|(?:hi){2,})\\b",RegexOption.IGNORE_CASE)
 fun add(s:Settings,roast:String,verdict:String,turn:Int):String {
  if(!SpokenRoastPolicy.enabled(s) || s.personality.roast<80 || verdict!="ERROR" || roast.isBlank() || laugh.containsMatchIn(roast)) return roast
  val cue=listOf("Ha, ha!","Heh!","Ha!","He, he!")[Math.floorMod(turn,4)]
  return "$roast $cue"
 }
}
