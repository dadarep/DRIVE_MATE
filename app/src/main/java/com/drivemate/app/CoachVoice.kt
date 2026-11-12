package com.drivemate.app

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import java.util.Locale

class CoachVoice(context:Context) {
    private val app=context.applicationContext
    private val prefs=context.getSharedPreferences("driving",Context.MODE_PRIVATE)
    private val tts=TextToSpeech(app,Mode.ON_DEVICE)
    private val handler=Handler(Looper.getMainLooper())
    private var ready=false
    private var closed=false
    private var completion: ((Boolean)->Unit)?=null
    private var speaking=false
    private val speechTimeout=Runnable{finishSpeech(false)}
    private fun finishSpeech(success:Boolean){
        handler.removeCallbacks(speechTimeout);speaking=false
        val done=completion;completion=null;CoachAudio.releaseSoon();done?.invoke(success)
    }
    private val onLoss:()->Unit={stop()}
    private fun finished(success:Boolean){handler.post{finishSpeech(success)}}
    private val events=object:EventListener {
        override fun onStart(){prefs.edit().putString("voiceStatus","재생 중").apply();Log.i("DriveMateVoice","playback started")}
        override fun onReady()=Unit
        override fun onDone(){prefs.edit().putString("voiceStatus","준비됨").apply();Log.i("DriveMateVoice","playback completed");finished(true)}
        override fun onStop(){prefs.edit().putString("voiceStatus","준비됨").apply();finished(false)}
        override fun onUpdatedRms(rms:Double)=Unit
        override fun onError(errorType:ErrorType){prefs.edit().putString("voiceStatus","음성 연결 확인").apply();Log.w("DriveMateVoice","$errorType");finished(false)}
    }
    fun start(){
        prefs.edit().putString("voiceStatus","준비 중").apply()
        CoachAudio.addLossListener(onLoss)
        runCatching { tts.initialize(object:OnInitializeListener {
            override fun onSuccess(){
                if(closed)return
                runCatching {
                    tts.addEventListener(events);tts.setLocale(Locale.KOREA)
                    tts.setAudioAttributes(CoachAudio.attributes(AudioAttributes.CONTENT_TYPE_SPEECH))
                    ready=true;prefs.edit().putString("voiceStatus","준비됨").apply();Log.i("DriveMateVoice","ON_DEVICE initialized")
                }.onFailure {Log.w("DriveMateVoice","configure",it)}
            }
            override fun onFailure(errorType:ErrorType){prefs.edit().putString("voiceStatus","음성 연결 확인").apply();Log.w("DriveMateVoice","initialize $errorType")}
        }) }.onFailure {prefs.edit().putString("voiceStatus","음성 연결 확인").apply();Log.w("DriveMateVoice","initialize",it)}
    }
    // Focus is held up to 10s and released early when playback ends.
    fun speak(text:String,onComplete:((Boolean)->Unit)?=null): Boolean {
        if(!ready||closed||speaking||!prefs.getBoolean("voiceEnabled",false))return false
        if(!CoachAudio.hold(app,10000)){Log.i("DriveMateVoice","skipped without focus");return false}
        speaking=true;completion=onComplete
        val accepted=runCatching {tts.speak(text)}.onFailure{Log.w("DriveMateVoice","speak",it);CoachAudio.abandon()}.isSuccess
        if(accepted)handler.postDelayed(speechTimeout,15000) else {speaking=false;completion=null}
        return accepted
    }
    fun stop(){runCatching{tts.stop()};finishSpeech(false);CoachAudio.abandon()}
    fun close(){closed=true;ready=false;handler.removeCallbacksAndMessages(null);completion=null;speaking=false;CoachAudio.removeLossListener(onLoss);runCatching{tts.stop();tts.removeEventListener(events);tts.release()};CoachAudio.abandon()}
}
