package com.drivemate.app

import kotlin.math.abs

/**
 * Speed-only observations, not a safety score. Brake, steering, turn signal and speed limit
 * stay out until their permission and accuracy are verified. Thresholds are prototype heuristics.
 */
class CoachEngine {
    data class Advice(val title: String, val kind: String)
    private val samples = ArrayDeque<Pair<Long, Float>>()
    private var lastAdvice = -60000L
    private var lastChange = -60000L
    private var lastSteady = -600000L
    private var steadySince = 0L
    private var stoppedSince: Long? = null
    private var launchAt: Long? = null
    var acceleration: Float? = null
        private set
    fun reset() { samples.clear(); acceleration = null; steadySince = 0; stoppedSince=null;launchAt=null }
    fun observeStopped(now:Long) {
        samples.clear();samples.addLast(now to 0f);acceleration=null;steadySince=0;launchAt=null
        if(stoppedSince==null)stoppedSince=now
    }
    fun sample(now: Long, speed: Float): Advice? {
        if (samples.lastOrNull()?.let { now - it.first > 1500 } == true) reset()
        if(speed < 0.8f) {
            if(stoppedSince==null)stoppedSince=now
            launchAt=null
        } else if(stoppedSince!=null) {
            if(now-stoppedSince!!>=1500)launchAt=now
            stoppedSince=null
        }
        samples.addLast(now to speed)
        while (samples.size > 2 && now - samples.elementAt(1).first >= 1000) samples.removeFirst()
        val first = samples.first()
        val dt = now - first.first
        acceleration = if (dt in 700..1500) (speed - first.second) * 1000 / dt else null
        val a = acceleration
        if (a != null && abs(a) < 0.7 && speed > 3) {
            if (steadySince == 0L) steadySince = now
        } else steadySince = 0
        // A steady-flow note must never delay a speed-change note, and repeating it quickly turns it into noise.
        val changeReady = now - lastChange >= 15000 && now - lastAdvice >= 4000
        // A short departure allowance needs an observed stop. It never excuses very abrupt launches.
        val launching=launchAt?.let{now-it in 0..3000 && speed<8.33f}==true
        val accelerationLimit=if(launching)4.0f else 2.5f
        val advice = when {
            !changeReady -> null
            a != null && a < -3 -> Advice("속도가 빠르게 줄었어요", "deceleration")
            a != null && a > accelerationLimit -> Advice(if(launching)"출발할 때 속도가 빠르게 올랐어요" else "속도가 빠르게 올랐어요", "acceleration")
            else -> null
        } ?: if (steadySince > 0 && now - steadySince > 12000 && now - lastAdvice >= 15000 && now - lastSteady >= 120000)
            Advice("일정한 속도가 이어지고 있어요", "steady") else null
        if (advice != null) {
            lastAdvice = now; steadySince = 0
            if (advice.kind == "steady") lastSteady = now else lastChange = now
        }
        return advice
    }
}
