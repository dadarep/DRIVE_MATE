package com.drivemate.app

import kotlin.math.abs

internal object VoiceReviewAnswer {
    fun parse(text: String): String? {
        val s=text.lowercase().replace(Regex("[^가-힣a-z]"),"")
        if(listOf("나중","그만","취소","패스","다음에").any{s.contains(it)})return "LATER"
        val good=listOf("잘됐","잘되","잘했","괜찮","수월","좋았","편했","안어려","어렵지않").any{s.contains(it)}
        val difficult=listOf("어려","어렵","힘들","긴장","못했","잘안","안됐","안되","괜찮지않").any{s.contains(it)} && !s.contains("안어려") && !s.contains("어렵지않")
        if(difficult && (s.contains("잘안")||s.contains("괜찮지않")))return "DIFFICULT"
        return when {good && !difficult->"GOOD";difficult && !good->"DIFFICULT";else->null}
    }
}

/** One offer per trip, only after a stable stretch. Distance is proximity, never an arrival event. */
internal class VoiceReviewTiming {
    private var steadySince: Long?=null
    fun eligible(now: Long, elapsed: Long, speed: Float, acceleration: Float?, near: Boolean, quiet: Boolean): Boolean {
        val steady=quiet && speed in 1.4f..13.9f && acceleration!=null && abs(acceleration)<0.5f
        if(!steady){steadySince=null;return false}
        if(steadySince==null)steadySince=now
        return elapsed>=60000 && now-steadySince!!>=12000 && (near || elapsed>=300000)
    }
    fun reset(){steadySince=null}
}
