package com.locospanish.ai
import com.locospanish.ai.voice.*
import org.junit.Assert.*
import org.junit.Test

class PresentedAudioTest {
 private class Output:LiveAudio {
  val played=mutableListOf<Pair<Int,Boolean>>()
  override fun start(onInput:(ByteArray)->Unit,onError:()->Unit,onPlaying:(Boolean)->Unit) {}
  override fun enqueue(bytes:ByteArray):Boolean { played.add(bytes[0].toInt() to false);return true }
  override fun enqueueReference(bytes:ByteArray):Boolean { played.add(bytes[0].toInt() to true);return true }
  override fun mute(value:Boolean) {}
  override fun interrupt() {}
  override fun close() {}
 }
 @Test fun bothQuestionAndReferenceWaitForTheExactCard() {
  val output=Output();val audio=PresentedAudio(output)
  audio.task("la reserva","rezerwacja")
  assertTrue(audio.enqueue(byteArrayOf(1)));assertTrue(audio.enqueueReference(byteArrayOf(2)))
  assertTrue(output.played.isEmpty())
  audio.presented("la reserva","pokój");assertTrue(output.played.isEmpty())
  audio.presented("la reserva","rezerwacja")
  assertEquals(listOf(1 to false,2 to true),output.played)
 }
 @Test fun staleCardAndOldAudioCannotReleaseANewTask() {
  val output=Output();val audio=PresentedAudio(output)
  audio.task("la reserva","rezerwacja");audio.enqueue(byteArrayOf(1))
  audio.task("la habitación","pokój");audio.enqueue(byteArrayOf(3))
  audio.presented("la reserva","rezerwacja");assertTrue(output.played.isEmpty())
  audio.presented("la habitación","pokój");assertEquals(listOf(3 to false),output.played)
 }
 @Test fun interruptionDoesNotPlayTheBufferedQuestionLater() {
  val output=Output();val audio=PresentedAudio(output)
  audio.task("el agua","woda");audio.enqueue(byteArrayOf(1));audio.interrupt()
  audio.presented("el agua","woda");assertTrue(output.played.isEmpty())
 }
}
