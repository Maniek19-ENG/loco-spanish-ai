package com.locospanish.ai
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.locospanish.ai.model.Settings
import com.locospanish.ai.voice.RiposteLaughter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RiposteLaughterTest {
 @Test fun everyVerifiedMistakeGetsTheCompleteOriginalLaughOnceBeforeAnyReply() {
  val laughter=RiposteLaughter(InstrumentationRegistry.getInstrumentation().targetContext)
  val first=laughter.error(Settings(),"first","ERROR",false)!!
  assertEquals(96000,first.size);assertTrue(first.any { it.toInt()!=0 })
  assertNull(laughter.error(Settings(),"first","ERROR",false))
  assertNull(laughter.pcm(Settings(),"first","ERROR","Kurwa, kot przejął recepcję.",true,false))
  assertTrue(first.contentEquals(laughter.error(Settings(),"second","ERROR",false)!!))
  assertNull(laughter.error(Settings(),"good","CORRECT",false))
  assertNull(laughter.error(Settings(),"uncertain","UNCERTAIN",false))
  assertNull(laughter.error(Settings(),"help","ERROR",true))
  assertNull(laughter.error(Settings(personality=Settings().personality.copy(roastMode=false)),"off","ERROR",false))
  laughter.reset();assertNotNull(laughter.error(Settings(),"first","ERROR",false))
 }
 @Test fun actualPcmReachesPlaybackFallbackWithoutAnotherApiRequest() {
  val laughter=RiposteLaughter(InstrumentationRegistry.getInstrumentation().targetContext)
  val pcm=laughter.pcm(Settings(),"wrong-1","ERROR","Kurwa, kot dostał klucz, a ty śpisz na recepcji.",true,false)
  assertNotNull(pcm);assertTrue(pcm!!.size in 24000..48000);assertTrue(pcm.any { it.toInt()!=0 })
  assertNull(laughter.pcm(Settings(),"wrong-1","ERROR","Ta sama odpowiedź.",true,false))
  assertNull(laughter.pcm(Settings(),"wrong-2","ERROR","Kolejna riposta.",true,false))
  val next=laughter.pcm(Settings(),"wrong-3","ERROR","Kolejna świeża riposta.",true,false)
  assertNotNull(next);assertFalse(pcm.contentEquals(next))
 }
 @Test fun helpAndJokeOnlyAndDisabledRoastNeverGetAFixedLaugh() {
  val laughter=RiposteLaughter(InstrumentationRegistry.getInstrumentation().targetContext)
  assertNull(laughter.pcm(Settings(),"help","UNCERTAIN","To znaczy rezerwacja.",true,true))
  assertNull(laughter.pcm(Settings(),"no-joke","ERROR","Powtórz słowo.",false,false))
  assertNull(laughter.pcm(Settings(personality=Settings().personality.copy(roastMode=false)),"off","ERROR","Riposta.",true,false))
  laughter.reset()
  assertNotNull(laughter.pcm(Settings(),"already-laughing","ERROR","Ha ha, kot dostał klucz!",true,false))
 }
}
