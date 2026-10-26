package com.locospanish.ai
import com.locospanish.ai.voice.PcmLevel
import org.junit.Assert.*
import org.junit.Test
class PcmLevelTest {
 private fun samples(value:Int)=ByteArray(960) { if(it%2==0) value.toByte() else (value shr 8).toByte() }
 @Test fun silenceIsZeroAndLoudnessIncreases() {
  assertEquals(0f,PcmLevel.measure(ByteArray(960)),0f)
  assertTrue(PcmLevel.measure(samples(1000))<PcmLevel.measure(samples(5000)))
  assertTrue(PcmLevel.measure(samples(5000))<PcmLevel.measure(samples(20000)))
  assertEquals(1f,PcmLevel.measure(samples(32767)),0f)
  assertEquals(PcmLevel.measure(samples(5000)),PcmLevel.measure(samples(-5000)),.001f)
 }
 @Test fun measuresOnlyWrittenRegionAndHandlesPartialSamples() {
  val bytes=samples(32767)+ByteArray(960)
  assertEquals(0f,PcmLevel.measure(bytes,960,960),0f)
  assertEquals(1f,PcmLevel.measure(bytes,0,960),0f)
  assertEquals(0f,PcmLevel.measure(byteArrayOf(99)),0f)
 }
}
