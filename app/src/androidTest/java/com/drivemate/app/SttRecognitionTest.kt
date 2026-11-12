package com.drivemate.app

import androidx.test.platform.app.InstrumentationRegistry
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Uses the actual on-device STT engine with supplied PCM. Never writes user preferences. */
class SttRecognitionTest {
    @Test fun koreanPcmIsRecognizedByTheRealPleosEngine() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val initialized=CountDownLatch(1);val completed=CountDownLatch(1)
        val failure=AtomicReference<String?>(null);val result=AtomicReference<String?>(null)
        lateinit var sdk:SpeechToText
        val listener=object:ResultListener {
            override fun onReady()=Unit
            override fun onStartedRecognition()=Unit
            override fun onUpdated(stt:String,done:Boolean){if(done){result.set(stt);completed.countDown()}}
            override fun onUpdatedEpdData(on:Long,off:Long)=Unit
            override fun onUpdatedRms(rms:Double)=Unit
            override fun onEndedRecognition()=Unit
            override fun onError(errorType:ErrorType){failure.set(errorType.toString());completed.countDown()}
        }
        instrumentation.runOnMainSync{
            sdk=SpeechToText(instrumentation.targetContext,Mode.ON_DEVICE)
            sdk.initialize(object:OnInitializeListener{
                override fun onSuccess(){sdk.addListener(listener);sdk.setLocale(Locale.KOREA);initialized.countDown()}
                override fun onFailure(errorType:ErrorType){failure.set(errorType.toString());initialized.countDown()}
            })
        }
        try {
            assertTrue("STT initialization timed out",initialized.await(20,TimeUnit.SECONDS))
            assertNull("STT initialization failed",failure.get())
            val pcm=instrumentation.context.assets.open("voice-review-good.pcm").use{it.readBytes()}
            instrumentation.runOnMainSync{sdk.sendAudio(ByteArray(16000)+pcm+ByteArray(32000));sdk.completeAudioSend()}
            assertTrue("STT result timed out",completed.await(25,TimeUnit.SECONDS))
            assertNull("STT recognition failed",failure.get())
            assertEquals("recognized: ${result.get()}","GOOD",result.get()?.let(VoiceReviewAnswer::parse))
        } finally {instrumentation.runOnMainSync{sdk.stop();sdk.removeListener(listener);sdk.release()}}
    }
}
