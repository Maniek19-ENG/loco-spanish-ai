package com.locospanish.ai.voice
import kotlin.math.*
/** RMS from little-endian signed PCM16, excluding bytes not accepted by the player. */
object PcmLevel {
 fun measure(bytes:ByteArray,offset:Int=0,length:Int=bytes.size-offset):Float {
  val end=(offset+length).coerceAtMost(bytes.size);var sum=0.0;var count=0;var i=offset
  while(i+1<end) { val sample=((bytes[i].toInt() and 255) or (bytes[i+1].toInt() shl 8)).toShort().toDouble()/32768.0;sum+=sample*sample;count++;i+=2 }
  if(count==0) return 0f
  return ((sqrt(sum/count)-.006)/.22).coerceIn(0.0,1.0).pow(.55).toFloat()
 }
}
