package com.drivemate.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

internal class VoiceReflection(context:Context,private val voice:CoachVoice) {
    private val prefs=context.getSharedPreferences("driving",Context.MODE_PRIVATE)
    private val listener=CoachListener(context)
    private val handler=Handler(Looper.getMainLooper())
    private var generation=0
    var busy=false;private set
    var driving=false;private set
    var goalId:String?=null;private set
    var listening=false;private set
    val ready get()=listener.ready
    fun start()=listener.start()
    fun ask(goal:Garden.Goal,trip:String,whileDriving:Boolean,onAnswer:(String)->Unit): Boolean {
        if(busy||!ready)return false
        val token=++generation;busy=true;driving=whileDriving;goalId=goal.id
        val prompt="오늘 연습 목표는 어땠나요? 잘됐어, 어려웠어, 나중에로 답해 주세요."
        val accepted=voice.speak(prompt){completed->
            if(token==generation){
                if(!completed){Log.i("DriveMateVoiceReview","question playback interrupted");end("주차 후 버튼으로 돌아볼 수 있어요")}
                else {
                    CoachAudio.abandon()
                    handler.postDelayed({if(token==generation){
                        status("듣는 중 · 짧게 답해 주세요")
                        listening=true
                        Log.i("DriveMateVoiceReview","microphone requested")
                        if(!listener.listen{text->
                            if(token==generation){
                                val answer=text?.let(VoiceReviewAnswer::parse)
                                end(if(answer=="GOOD"||answer=="DIFFICULT")"답변을 기억했어요" else "주차 후 버튼으로 돌아볼 수 있어요")
                                if(answer=="GOOD"||answer=="DIFFICULT")onAnswer(answer)
                            }
                        })end("음성 인식을 확인해 주세요 · 버튼으로도 회고할 수 있어요")
                    }},350)
                }
            }
        }
        if(!accepted){busy=false;return false}
        prefs.edit().putString("voiceReviewAskedSession",trip).apply()
        status("질문 중 · 듣고 나서 답해 주세요")
        Log.i("DriveMateVoiceReview","question accepted driving=$whileDriving")
        return true
    }
    private fun status(text:String){prefs.edit().putString("voiceReviewStatus",text).apply()}
    private fun end(text:String){busy=false;listening=false;status(text)}
    fun cancel(){
        if(!busy)return
        Log.i("DriveMateVoiceReview","conversation cancelled")
        generation++;busy=false;listening=false;handler.removeCallbacksAndMessages(null);listener.cancel();voice.stop()
        status("주차 후 버튼으로 돌아볼 수 있어요")
    }
    fun close(){cancel();listener.close()}
}
