package com.locospanish.ai.voice

import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicInteger

/** A byte budget makes bursts independent of the provider's arbitrary packet size. */
class PcmOutputQueue(private val capacityBytes:Int=2_000_000) {
 data class Chunk(val generation:Int,val bytes:ByteArray,val reference:Boolean)
 val channel=Channel<Chunk>(Channel.UNLIMITED)
 private val pending=AtomicInteger(0)
 val pendingBytes:Int get()=pending.get()
 @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
 val isEmpty:Boolean get()=channel.isEmpty
 fun offer(generation:Int,bytes:ByteArray,reference:Boolean):Boolean {
  if(bytes.isEmpty() || bytes.size%2!=0 || bytes.size>capacityBytes) return false
  while(true) {
   val used=pending.get()
   if(used+bytes.size>capacityBytes) return false
   if(pending.compareAndSet(used,used+bytes.size)) break
  }
  if(channel.trySend(Chunk(generation,bytes,reference)).isSuccess) return true
  pending.addAndGet(-bytes.size);return false
 }
 fun release(chunk:Chunk) { pending.addAndGet(-chunk.bytes.size) }
 fun drain() { while(true) release(channel.tryReceive().getOrNull() ?: break) }
 fun close() { channel.close() }
}
