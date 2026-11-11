package com.drivemate.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle

/** Public, read-only SDK calls. Failed capabilities remain unknown, never zero. */
class PleosSignals(context: Context) {
    private val vehicle = Vehicle(context.applicationContext)
    private val handler = Handler(Looper.getMainLooper())
    private var closed = false
    private var brakeSupported = false
    private var turnSupported = false
    private var angleSupported = false
    var brake: Boolean? = null; private set
    var turn: String? = null; private set
    var angle: Float? = null; private set
    private var brakeAt = 0L
    private var turnAt = 0L
    private var angleAt = 0L
    private fun failed(name: String, e: Exception) { Log.w("DriveMateSignals", "$name: ${e.message}") }
    fun start() {
        runCatching { vehicle.initialize() }.onFailure { Log.w("DriveMateSignals", "initialize", it) }
        handler.postDelayed({ check() }, 1500)
    }
    private fun check() {
        if (closed) return
        runCatching {
            vehicle.getBrake().checkBrakePedalStatusCapabilities(
                onComplete = { config -> brakeSupported = true; Log.i("DriveMateSignals", "brake capability=$config") },
                onFailed = { failed("brake capability", it) })
            vehicle.getTurnSignal().checkTurnSignalStateCapabilities(
                onComplete = { config -> turnSupported = true; Log.i("DriveMateSignals", "turn capability=$config") },
                onFailed = { failed("turn capability", it) })
            vehicle.getSteeringWheel().checkSteeringWheelAngleCapabilities(
                onComplete = { config, bounds -> angleSupported = true; Log.i("DriveMateSignals", "angle capability=$config $bounds") },
                onFailed = { failed("angle capability", it) })
        }.onFailure { Log.w("DriveMateSignals", "capabilities", it) }
        handler.post(poll)
    }
    private val poll = object : Runnable {
        override fun run() {
            if (closed) return
            runCatching {
                if (brakeSupported) vehicle.getBrake().getCurrentBrakePedalStatus(
                    onComplete = { brake = it; brakeAt = SystemClock.elapsedRealtime() },
                    onFailed = { brake = null; failed("brake read", it) })
                if (turnSupported) vehicle.getTurnSignal().getCurrentTurnSignalState(
                    onComplete = { turn = it?.toString(); turnAt = SystemClock.elapsedRealtime() },
                    onFailed = { turn = null; failed("turn read", it) })
                if (angleSupported) vehicle.getSteeringWheel().getCurrentSteeringWheelAngle(
                    onComplete = { angle = it?.takeIf { v -> v.isFinite() }; angleAt = SystemClock.elapsedRealtime() },
                    onFailed = { angle = null; failed("angle read", it) })
            }.onFailure { Log.w("DriveMateSignals", "read", it) }
            val now = SystemClock.elapsedRealtime()
            if (now - brakeAt > 2500) brake = null
            if (now - turnAt > 2500) turn = null
            if (now - angleAt > 2500) angle = null
            handler.postDelayed(this, 500)
        }
    }
    fun close() { closed = true; handler.removeCallbacksAndMessages(null); runCatching { vehicle.release() } }
}
