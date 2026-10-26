package com.locospanish.ai.voice

import android.content.Context
import com.locospanish.ai.R
import com.locospanish.ai.model.Settings
import com.locospanish.ai.realtime.SpokenRoastPolicy

/** Verified mistakes get the original complete laugh before the teacher replies. */
class RiposteLaughter(context:Context) {
 private val clip by lazy { context.resources.openRawResource(R.raw.mocking_laugh).use { it.readBytes() }.drop(44).toByteArray() }
 private val errorOwners=mutableSetOf<String>()
 fun error(settings:Settings,owner:String?,verdict:String,help:Boolean):ByteArray? {
  if(owner.isNullOrBlank() || verdict!="ERROR" || help || !SpokenRoastPolicy.enabled(settings) || settings.personality.roast<80 || !errorOwners.add(owner)) return null
  return clip.copyOf()
 }
 private var replies=0
 private var lastReply=-3
 private var lastOwner:String?=null
 fun reset() { replies=0;lastReply=-3;lastOwner=null;errorOwners.clear() }
 fun pcm(settings:Settings,owner:String?,verdict:String,reply:String,realJoke:Boolean,help:Boolean):ByteArray? {
  if(owner==null || owner in errorOwners || verdict!="ERROR" || owner==lastOwner || reply.isBlank()) return null
  lastOwner=owner;replies++
  if(!SpokenRoastPolicy.enabled(settings) || settings.personality.roast<80 || help || !realJoke || replies-lastReply<2) return null
  // Written syllables do not establish whether the model made an audible laugh.
  lastReply=replies
  val source=clip
  val start=if((replies/2)%2==1) 57600 else 0
  val end=if(start==0) 46080 else source.size
  if(source.size<end || end<=start) return null
  val pcm=source.copyOfRange(start,end)
  // Fade both boundaries so a clipped chuckle cannot click in the speech queue.
  val frames=pcm.size/2
  for(i in 0 until frames) {
   val fade=minOf(1f,i/240f,(frames-1-i)/240f).coerceAtLeast(0f)
   val sample=((pcm[i*2].toInt() and 255) or (pcm[i*2+1].toInt() shl 8)).toShort().toInt()
   val value=(sample*fade).toInt()
   pcm[i*2]=value.toByte();pcm[i*2+1]=(value shr 8).toByte()
  }
  return pcm
 }
}
