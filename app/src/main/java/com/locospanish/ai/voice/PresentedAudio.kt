package com.locospanish.ai.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/** Main-thread audio boundary: a task cannot be heard before its card has been drawn. */
class PresentedAudio(private val delegate:LiveAudio):LiveAudio by delegate {
 private data class Chunk(val bytes:ByteArray,val reference:Boolean)
 private val waiting=ArrayDeque<Chunk>()
 private var word=""
 private var meaning=""
 private var visible=false
 private val visibility=MutableStateFlow(false)
 private var size=0
 fun task(es:String,pl:String) {
  if(word==es && meaning==pl) return
  word=es;meaning=pl;visible=false;visibility.value=false;waiting.clear();size=0
 }
 fun hidden() { visible=false;visibility.value=false;waiting.clear();size=0;delegate.interrupt() }
 fun allowReplyWithoutTask() { if(word.isBlank()) { visible=true;visibility.value=true;flush() } }
 suspend fun awaitPresented(es:String,pl:String) { visibility.first { it && word==es && meaning==pl } }
 fun presented(es:String,pl:String) {
  if(word!=es || meaning!=pl || es.isBlank() || pl.isBlank()) return
  visible=true;visibility.value=true;flush()
 }
 private fun flush() {
  while(waiting.isNotEmpty()) {
   val chunk=waiting.removeFirst();size-=chunk.bytes.size
   if(chunk.reference) delegate.enqueueReference(chunk.bytes) else delegate.enqueue(chunk.bytes)
  }
 }
 private fun submit(bytes:ByteArray,reference:Boolean):Boolean {
  if(visible) return if(reference) delegate.enqueueReference(bytes) else delegate.enqueue(bytes)
  if(size+bytes.size>2*1024*1024) return false
  waiting.addLast(Chunk(bytes.copyOf(),reference));size+=bytes.size
  return true
 }
 override fun enqueue(bytes:ByteArray)=submit(bytes,false)
 override fun enqueueReference(bytes:ByteArray)=submit(bytes,true)
 override fun interrupt() { waiting.clear();size=0;delegate.interrupt() }
 override fun close() { waiting.clear();size=0;visible=false;visibility.value=false;delegate.close() }
}
