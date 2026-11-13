package com.drivemate.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import java.util.Locale

/** Vehicle microphone is acquired for a single bounded reply, after TTS has completed. */
internal class CoachListener(context: Context) {
    private val stt=SpeechToText(context.applicationContext,Mode.ON_DEVICE)
    private val handler=Handler(Looper.getMainLooper())
    var ready=false; private set
    private var closed=false
    private var answer: ((String?)->Unit)?=null
    private val timeout=Runnable{finish(null)}
    private val listener=object:ResultListener {
        override fun onReady()=Unit
        override fun onStartedRecognition(){Log.i("DriveMateSTT","listening (ON_DEVICE)");handler.post{if(answer!=null){handler.removeCallbacks(timeout);handler.postDelayed(timeout,REPLY_MS)}}}
        override fun onUpdated(stt:String,completed:Boolean){if(completed)handler.post{finish(stt)}}
        override fun onUpdatedEpdData(on:Long,off:Long)=Unit
        override fun onUpdatedRms(rms:Double)=Unit
        override fun onEndedRecognition(){handler.post{if(answer!=null)handler.postDelayed({finish(null)},200)}}
        override fun onError(errorType:ErrorType){Log.w("DriveMateSTT","$errorType");handler.post{finish(null)}}
    }
    fun start(){
        runCatching{stt.initialize(object:OnInitializeListener{
            override fun onSuccess(){handler.post{
                if(!closed)runCatching{stt.addListener(listener);stt.setLocale(Locale.KOREA);ready=true;Log.i("DriveMateSTT","ON_DEVICE initialized")}
                    .onFailure{Log.w("DriveMateSTT","configure",it)}
            }}
            override fun onFailure(errorType:ErrorType){Log.w("DriveMateSTT","initialize $errorType")}
        })}.onFailure{Log.w("DriveMateSTT","initialize",it)}
    }
    fun listen(result:(String?)->Unit): Boolean {
        if(!ready||closed||answer!=null)return false
        answer=result
        return runCatching{stt.request();handler.postDelayed(timeout,STARTUP_MS+REPLY_MS);true}.getOrElse{answer=null;Log.w("DriveMateSTT","request",it);false}
    }
    private fun finish(text:String?){
        val callback=answer?:return
        answer=null;handler.removeCallbacks(timeout);runCatching{stt.stop()};callback(text)
    }
    fun cancel(){answer=null;handler.removeCallbacksAndMessages(null);runCatching{stt.stop()}}
    fun close(){closed=true;cancel();ready=false;runCatching{stt.removeListener(listener);stt.release()}}
    private companion object { const val STARTUP_MS=12000L; const val REPLY_MS=8000L }
}
