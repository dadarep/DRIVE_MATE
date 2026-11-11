package com.drivemate.app

import android.content.Context
import android.media.AudioAttributes
import android.util.Log
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import java.util.Locale

class CoachVoice(context:Context) {
    private val prefs=context.getSharedPreferences("driving",Context.MODE_PRIVATE)
    private val tts=TextToSpeech(context.applicationContext,Mode.ON_DEVICE)
    private var ready=false
    private var closed=false
    private val events=object:EventListener {
        override fun onStart(){prefs.edit().putString("voiceStatus","재생 중").apply();Log.i("DriveMateVoice","playback started")}
        override fun onReady()=Unit
        override fun onDone(){prefs.edit().putString("voiceStatus","준비됨").apply();Log.i("DriveMateVoice","playback completed")}
        override fun onStop()=Unit
        override fun onUpdatedRms(rms:Double)=Unit
        override fun onError(errorType:ErrorType){prefs.edit().putString("voiceStatus","음성 연결 확인").apply();Log.w("DriveMateVoice","$errorType")}
    }
    fun start(){
        prefs.edit().putString("voiceStatus","준비 중").apply()
        runCatching { tts.initialize(object:OnInitializeListener {
            override fun onSuccess(){
                if(closed)return
                runCatching {
                    tts.addEventListener(events);tts.setLocale(Locale.KOREA)
                    tts.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    ready=true;prefs.edit().putString("voiceStatus","준비됨").apply();Log.i("DriveMateVoice","ON_DEVICE initialized")
                }.onFailure {Log.w("DriveMateVoice","configure",it)}
            }
            override fun onFailure(errorType:ErrorType){prefs.edit().putString("voiceStatus","음성 연결 확인").apply();Log.w("DriveMateVoice","initialize $errorType")}
        }) }.onFailure {prefs.edit().putString("voiceStatus","음성 연결 확인").apply();Log.w("DriveMateVoice","initialize",it)}
    }
    fun speak(text:String){if(ready&&!closed&&prefs.getBoolean("voiceEnabled",true))runCatching {tts.speak(text)}.onFailure{Log.w("DriveMateVoice","speak",it)}}
    fun stop(){runCatching{tts.stop()}}
    fun close(){closed=true;ready=false;runCatching{tts.stop();tts.removeEventListener(events);tts.release()}}
}
