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
    private val onLoss:()->Unit={runCatching{tts.stop()}}
    private fun finished(){handler.post{CoachAudio.releaseSoon()}}
    private val events=object:EventListener {
        override fun onStart(){prefs.edit().putString("voiceStatus","재생 중").apply();Log.i("DriveMateVoice","playback started")}
        override fun onReady()=Unit
        override fun onDone(){prefs.edit().putString("voiceStatus","준비됨").apply();Log.i("DriveMateVoice","playback completed");finished()}
        override fun onStop(){prefs.edit().putString("voiceStatus","준비됨").apply();finished()}
        override fun onUpdatedRms(rms:Double)=Unit
        override fun onError(errorType:ErrorType){prefs.edit().putString("voiceStatus","음성 연결 확인").apply();Log.w("DriveMateVoice","$errorType");finished()}
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
    fun speak(text:String){
        if(!ready||closed||!prefs.getBoolean("voiceEnabled",false))return
        if(!CoachAudio.hold(app,10000)){Log.i("DriveMateVoice","skipped without focus");return}
        runCatching {tts.speak(text)}.onFailure{Log.w("DriveMateVoice","speak",it);CoachAudio.abandon()}
    }
    fun stop(){runCatching{tts.stop()};CoachAudio.abandon()}
    fun close(){closed=true;ready=false;CoachAudio.removeLossListener(onLoss);runCatching{tts.stop();tts.removeEventListener(events);tts.release()};CoachAudio.abandon()}
}
