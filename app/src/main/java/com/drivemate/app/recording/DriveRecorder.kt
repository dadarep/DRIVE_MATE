package com.drivemate.app.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import org.json.JSONArray
import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.property.CarPropertyManager
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log

class DriveRecorder : Service() {
    private lateinit var extra: com.drivemate.app.PleosSignals
    private val coach = com.drivemate.app.CoachEngine()
    private var movementHeading = com.drivemate.app.MovementHeading()
    private var seatbeltWarning = false
    private var rapidAcceleration = false
    private lateinit var voice: com.drivemate.app.CoachVoice
    private lateinit var voiceReview: com.drivemate.app.VoiceReflection
    private val reviewTiming=com.drivemate.app.VoiceReviewTiming()
    private var lastReviewAttempt=0L
    private val earcons by lazy { com.drivemate.app.Earcons(this) }
    private var lastExtraLog = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val storage by lazy { getSharedPreferences("driving", MODE_PRIVATE) }
    private var car: Car? = null
    private var properties: CarPropertyManager? = null
    private var restrictions: CarUxRestrictionsManager? = null
    private var recording = false
    private var candidates = 0
    private var previousSpeed: Float? = null
    private var previousTime = 0L
    private var lastCandidate = 0L
    private var lastDrop = 0L
    private var braking = false
    private var gapStart = 0L
    private var gapMs = 0L
    private var validSamples = 0
    private var sessionStart = 0L
    private var parkedSince = 0L
    private var status = ""
    private lateinit var locations: LocationManager
    private var lastLocationRequest = 0L
    private var route = JSONArray()
    private val locationListener = LocationListener { point ->
        if (android.os.SystemClock.elapsedRealtimeNanos() - point.elapsedRealtimeNanos in 0..15000000000L && point.hasAccuracy() && point.accuracy <= 50f) {
            storage.edit().putString("liveLat", point.latitude.toString()).putString("liveLon", point.longitude.toString())
                .putLong("locationAt", point.elapsedRealtimeNanos/1000000).apply()
            val course=movementHeading.sample(com.drivemate.app.GeoPoint(point.latitude,point.longitude),point.elapsedRealtimeNanos/1000000,point.accuracy,
                if(point.hasSpeed())point.speed else storage.getFloat("liveSpeed",Float.NaN).takeIf{it.isFinite()},
                if(point.hasBearing() && (!point.hasBearingAccuracy() || point.bearingAccuracyDegrees <= 30f)) point.bearing else null)
            storage.edit().apply { if(course != null)putFloat("bearing",course) else remove("bearing") }.apply()
        }
        if (recording && point.hasAccuracy() && point.accuracy <= 50f && route.length() < 3600) {
            route.put(JSONArray().put(point.latitude).put(point.longitude))
            if (route.length() % 10 == 0) storage.edit().putString("pendingRoute", route.toString()).apply()
        }
    }
    private var sessionId = ""
    private val moments = mutableListOf<com.drivemate.app.Garden.Moment>()
    private fun kph(metersPerSecond: Float) = kotlin.math.round(metersPerSecond * 3.6f).toInt()
    private val poll = object : Runnable {
        override fun run() {
            update()
            handler.postDelayed(this, 250)
        }
    }

    override fun onCreate() {
        super.onCreate()
        extra = com.drivemate.app.PleosSignals(this); extra.start()
        voice = com.drivemate.app.CoachVoice(this); voice.start()
        voiceReview=com.drivemate.app.VoiceReflection(this,voice);voiceReview.start()
        storage.edit().remove("voiceReviewStatus").apply()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("driving", "주행 기록", NotificationManager.IMPORTANCE_LOW))
        val notification = notification()
        val canLocate = checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= 34) {
            val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                (if (canLocate) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
            startForeground(1, notification, type)
        } else if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, notification, if (canLocate) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
        } else {
            startForeground(1, notification)
        }
        // A parked car keeps facing the same way, so the last course survives a service restart.
        movementHeading = com.drivemate.app.MovementHeading(if(storage.contains("bearing")) storage.getFloat("bearing",Float.NaN) else null)
        locations = getSystemService(LocationManager::class.java)
        if (!paused()) startLocation()
        recording = storage.getBoolean("recording", false)
        seatbeltWarning=storage.getBoolean("pendingSeatbeltWarning",false)
        rapidAcceleration=storage.getBoolean("pendingRapidAcceleration",false)
        candidates = storage.getInt("pendingCount", 0)
        if (recording) {
            val now = SystemClock.elapsedRealtime()
            gapMs = storage.getLong("pendingGapMs", 0)
            validSamples = storage.getInt("pendingSamples", 0)
            sessionStart = storage.getLong("sessionStart", now).takeIf { it <= now } ?: now
            // Time while the service was dead is a gap too; elapsedRealtime restarts after reboot.
            gapStart = storage.getLong("signalAt", 0).takeIf { it in 1..now } ?: now
            sessionId = storage.getString("pendingSessionId", null) ?: java.util.UUID.randomUUID().toString()
            moments += com.drivemate.app.Garden.Moment.listFromJson(storage.getString("pendingMoments", null))
            route = runCatching { JSONArray(storage.getString("pendingRoute", "[]")) }.getOrDefault(JSONArray())
            Log.i("DriveMate", "session restored id=$sessionId moments=${moments.size} route=${route.length()}")
        }
        publish("unknown")
        runCatching {
            car = Car.createCar(this)
            properties = car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
            restrictions = car?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
        }.onFailure { Log.w("DriveMate", "차량 서비스 연결 실패", it) }
        handler.post(poll)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A plain start also follows a permission grant, so location may only now be allowed.
        if (intent?.action == null && !paused()) startLocation()
        if(intent?.action == "VOICE_CHECK") {
            if (earcons.play(com.drivemate.app.Earcons.Cue.READY))
                handler.postDelayed({ voice.speak("드라이브 메이트가 함께할게요.") }, earcons.durationMs(com.drivemate.app.Earcons.Cue.READY).toLong())
        }
        if(intent?.action == "VOICE_REVIEW") offerVoiceReview(manual=true)
        if(intent?.action == "VOICE_REVIEW_STOP") voiceReview.cancel()
        if(intent?.action == "VOICE_STOP") {voiceReview.cancel();voice.stop()}
        if(intent?.action == "PAUSE") {
            voiceReview.cancel();voice.stop()
            if (recording) saveSession(interrupted = true)
            locations.removeUpdates(locationListener)
            storage.edit().remove("liveLat").remove("liveLon").remove("locationAt").apply()
            Log.i("DriveMate", "recording paused")
        }
        if(intent?.action == "RESUME") { startLocation(); Log.i("DriveMate", "recording resumed") }
        // A plain start can follow a notification grant, so the notice is reposted then too.
        if(intent?.action == null || intent.action == "PAUSE" || intent.action == "RESUME") getSystemService(NotificationManager::class.java).notify(1, notification())
        return START_STICKY
    }
    /** One synchronous write, so the trip and the session count can never be saved separately. False means retry later. */
    private fun saveSession(interrupted: Boolean): Boolean {
        val duration = SystemClock.elapsedRealtime() - sessionStart
        val insufficient = com.drivemate.app.TripQuality.insufficient(validSamples, gapMs, duration)
        val reminder=com.drivemate.app.TripReminder.line(seatbeltWarning,candidates,rapidAcceleration,insufficient)
        val saved = storage.edit()
            .putString("lastReminder",reminder)
            .putBoolean("recording", false)
            .putString("lastSessionId", sessionId)
            .putInt("lastCount", candidates)
            .putString("lastMoments", com.drivemate.app.Garden.Moment.listToJson(com.drivemate.app.Garden.topMoments(moments)))
            .remove("lastContexts")
            .putLong("lastGapMs", gapMs)
            .putBoolean("lastInsufficient", insufficient)
            .putBoolean("lastInterrupted", interrupted)
            .putString("lastRoute", route.toString())
            .putInt("sessions", storage.getInt("sessions", 0) + 1)
            .putBoolean("reviewed", false)
            .remove("pendingMoments").remove("pendingRoute").remove("pendingSessionId")
            .remove("pendingSeatbeltWarning").remove("pendingRapidAcceleration")
            .commit()
        if (!saved) {
            Log.w("DriveMate", "session save failed, retrying")
            return false
        }
        recording = false
        Log.i("DriveMate", "session saved id=$sessionId count=$candidates moments=${moments.size} samples=$validSamples gap=${gapMs}ms insufficient=$insufficient interrupted=$interrupted")
        if (!interrupted) {
            earcons.play(com.drivemate.app.Earcons.Cue.TRIP_END)
            announceReminder(sessionId,reminder)
        }
        return true
    }
    private fun announceReminder(savedSession: String, text: String, attempt: Int = 0) {
        handler.postDelayed({
            val parked=read(VehiclePropertyIds.GEAR_SELECTION)==VehicleGear.GEAR_PARK &&
                (read(VehiclePropertyIds.PERF_VEHICLE_SPEED) as? Float)?.let{kotlin.math.abs(it)<0.05f}==true &&
                restrictions?.currentCarUxRestrictions?.isRequiresDistractionOptimization==false
            if(parked && !paused() && storage.getString("lastSessionId",null)==savedSession && storage.getBoolean("voiceEnabled",false)) {
                if(voice.speak(text){done->if(done)handler.postDelayed({offerVoiceReview()},400)}) Log.i("DriveMateReminder",text)
                else if(attempt<5) announceReminder(savedSession,text,attempt+1)
            }
        },if(attempt==0)earcons.durationMs(com.drivemate.app.Earcons.Cue.TRIP_END).toLong()+150 else 500)
    }
    private fun readGarden()=runCatching{com.drivemate.app.Garden.fromJson(storage.getString("garden","{}")!!)}.getOrNull()
    private fun offerVoiceReview(manual:Boolean=false) {
        if(paused() || !storage.getBoolean("voiceEnabled",false) || !storage.getBoolean("voiceReviewEnabled",false))return
        val garden=readGarden()?:return
        val goal=garden.goal?:return
        val trip=if(recording)sessionId else storage.getString("lastSessionId",null)?:return
        if(!recording && (storage.getString("vehicleState","")!="parked" || !garden.due(storage.getInt("sessions",0))))return
        if(!manual && storage.getString("voiceReviewAskedSession",null)==trip)return
        if(!voiceReview.ready){if(manual)storage.edit().putString("voiceReviewStatus","음성 인식을 준비 중이에요 · 잠시 후 다시 눌러 주세요").apply();return}
        if(com.drivemate.app.CoachAudio.priorityBusy(this))return
        voiceReview.ask(goal,trip,recording){response->
            val currentTrip=if(recording)sessionId else storage.getString("lastSessionId",null)
            if(currentTrip!=trip || readGarden()?.goal?.id!=goal.id)return@ask
            val reply=org.json.JSONObject().put("goal",goal.id).put("trip",trip).put("response",response)
            if(storage.edit().putString("pendingVoiceReview",reply.toString()).commit()) {
                val saved=if(storage.getString("vehicleState","")=="parked")applyVoiceReview() else false
                voice.speak(if(saved)"회고를 저장했어요. 새 잎이 돋았어요." else "기억해 둘게요. 주차 후 반영할게요.")
                Log.i("DriveMateVoiceReview","answer accepted: $response; parked=$saved")
            }
        }
    }
    private fun maybeOfferDrivingReview(now:Long,speed:Float) {
        if(!storage.getBoolean("voiceReviewEnabled",false) || !storage.getBoolean("voiceEnabled",false) || readGarden()?.goal==null){reviewTiming.reset();return}
        if(storage.getString("voiceReviewAskedSession",null)==sessionId || voiceReview.busy)return
        val near=runCatching {
            if(now-storage.getLong("locationAt",0) !in 0..15000)return@runCatching false
            val lat=storage.getString("liveLat",null)?.toDoubleOrNull()?:return@runCatching false
            val lon=storage.getString("liveLon",null)?.toDoubleOrNull()?:return@runCatching false
            val destLat=storage.getString("destinationLat",null)?.toDoubleOrNull()?:return@runCatching false
            val destLon=storage.getString("destinationLon",null)?.toDoubleOrNull()?:return@runCatching false
            val distance=FloatArray(1);Location.distanceBetween(lat,lon,destLat,destLon,distance);distance[0]<=300
        }.getOrDefault(false)
        val nativeMatches=runCatching{
            val destination=com.drivemate.app.Destination("",com.drivemate.app.GeoPoint(storage.getString("destinationLat",null)!!.toDouble(),storage.getString("destinationLon",null)!!.toDouble()))
            com.drivemate.app.matchesNativeDestination(destination,storage.getString("navLat",null)?.toDoubleOrNull(),storage.getString("navLon",null)?.toDoubleOrNull())
        }.getOrDefault(false)
        val closeTurn=nativeMatches && now-storage.getLong("tbtAt",0) in 0..15000 && storage.getFloat("turnDistance",9999f)<300
        val quiet=extra.brake==false && extra.turn=="OFF" && extra.angle?.let{kotlin.math.abs(it)<8f}==true && !closeTurn && !com.drivemate.app.CoachAudio.priorityBusy(this)
        if(reviewTiming.eligible(now,now-sessionStart,speed,coach.acceleration,near,quiet) && now-lastReviewAttempt>5000) {
            lastReviewAttempt=now;offerVoiceReview()
        }
    }
    private fun applyVoiceReview():Boolean {
        if(storage.getString("vehicleState","")!="parked" || paused())return false
        val raw=storage.getString("pendingVoiceReview",null)?:return false
        val garden=readGarden()?:return false
        val parsed=runCatching{org.json.JSONObject(raw)}.getOrNull()?:return false
        val reply=com.drivemate.app.VoiceReviewReply(parsed.optString("goal"),parsed.optString("trip"),parsed.optString("response"))
        val lastTrip=storage.getString("lastSessionId","")?:""
        if(reply.tripId!=lastTrip || storage.getBoolean("lastInterrupted",false)) {
            storage.edit().remove("pendingVoiceReview").apply();return false
        }
        val contexts=runCatching{org.json.JSONObject(storage.getString("lastContexts","{}")!!)}.getOrDefault(org.json.JSONObject())
        val next=reply.complete(garden,lastTrip,storage.getInt("sessions",0),contexts.keys().asSequence().map{contexts.getString(it)}.toList(),System.currentTimeMillis())
        if(next==null){storage.edit().remove("pendingVoiceReview").apply();return false}
        return storage.edit().putString("garden",next.toJson()).putBoolean("reviewed",true).remove("pendingVoiceReview")
            .putString("voiceReviewStatus","회고 완료 · 잎 1장이 자랐어요").commit()
    }
    // The choice lives in preferences so it survives app restarts and service recovery.
    private fun paused() = storage.getBoolean("recordingPaused", false)
    private fun notification() = Notification.Builder(this, "driving")
        .setSmallIcon(android.R.drawable.ic_menu_compass)
        .setContentTitle(if (paused()) "DRIVE MATE 기록 중지됨" else "DRIVE MATE 주행 기록")
        .setContentText(if (paused()) "속도·위치를 기록하지 않아요" else "주행하면 속도 변화와 경로를 이 기기에 기록해요")
        .setOngoing(true).build()
    private fun startLocation() {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        lastLocationRequest=SystemClock.elapsedRealtime()
        runCatching {
            locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener)
            // A stationary emulator may have a fresh provider fix without delivering a new callback.
            locations.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.takeIf {
                it.elapsedRealtimeNanos/1000000 > storage.getLong("locationAt",0)
            }?.let { locationListener.onLocationChanged(it) }
        }.onFailure { Log.w("DriveMate", "위치 수신 시작 실패", it) }
    }
    override fun onBind(intent: Intent?): IBinder? = null

    private fun publish(value: String) {
        // Delete-all also removes this key, so it is rewritten even when the state itself did not change.
        if (value == status && storage.contains("vehicleState")) return
        status = value
        storage.edit().putString("vehicleState", value).apply()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::extra.isInitialized) extra.close()
        if (::voiceReview.isInitialized) voiceReview.close()
        if (::voice.isInitialized) voice.close()
        publish("unknown")
        if (::locations.isInitialized) locations.removeUpdates(locationListener)
        car?.disconnect()
        super.onDestroy()
    }
    // Pleos user builds block VHAL injection, so debug builds expose a file switch:
    // run-as com.drivemate.app touch files/simulate_signal_loss
    private val signalLossFlag by lazy {
        java.io.File(filesDir, "simulate_signal_loss").takeIf { applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 }
    }
    private fun signalLossSimulated() = signalLossFlag?.exists() == true
    private fun read(id: Int): Any? = runCatching {
        properties?.getProperty<Any>(id, 0)?.takeIf { it.status == 0 }?.value
    }.getOrNull()

    private fun update() {
        val locationNow=SystemClock.elapsedRealtime()
        if(!paused() && locationNow-lastLocationRequest>=15000 && locationNow-storage.getLong("locationAt",0)>15000) startLocation()
        val speed = (read(VehiclePropertyIds.PERF_VEHICLE_SPEED) as? Float)?.takeIf { it.isFinite() }
        val gear = read(VehiclePropertyIds.GEAR_SELECTION) as? Int
        val restricted = runCatching {
            restrictions?.currentCarUxRestrictions?.isRequiresDistractionOptimization
        }.getOrNull()
        if (speed == null || gear == null || restricted == null || signalLossSimulated()) {
            previousSpeed = null
            coach.reset();reviewTiming.reset();voiceReview.cancel()
            storage.edit().remove("liveSpeed").apply()
            parkedSince = 0L
            if (recording && gapStart == 0L) gapStart = SystemClock.elapsedRealtime()
            publish("unknown")
            return
        }
        val nowTick = SystemClock.elapsedRealtime()
        if (gapStart != 0L) {
            if (recording) {
                gapMs += nowTick - gapStart
                storage.edit().putLong("pendingGapMs", gapMs).apply()
                Log.i("DriveMate", "signal gap closed total=${gapMs}ms")
            }
            gapStart = 0L
        }
        storage.edit().putFloat("liveSpeed",speed).putLong("signalAt",nowTick)
            .putString("gearLabel",when(gear){VehicleGear.GEAR_PARK -> "P";VehicleGear.GEAR_DRIVE -> "D";VehicleGear.GEAR_REVERSE -> "R";else -> "N"})
            .apply()
        // Brake, turn signal and steering are logged for accuracy checks only, never shown or coached.
        if(nowTick-lastExtraLog>10000){Log.i("DriveMateSignals","live brake=${extra.brake} turn=${extra.turn} angle=${extra.angle} speed=$speed");lastExtraLog=nowTick}
        val paused = paused()
        if (paused || !storage.getBoolean("voiceEnabled",false) || !storage.getBoolean("voiceReviewEnabled",false)) voiceReview.cancel()
        if (voiceReview.busy && (readGarden()?.goal?.id!=voiceReview.goalId || (voiceReview.listening && com.drivemate.app.CoachAudio.priorityBusy(this,false)) ||
            (voiceReview.driving && gear==VehicleGear.GEAR_DRIVE && (speed>13.9f || extra.brake==true || extra.turn?.let{it!="OFF"}==true || kotlin.math.abs(extra.angle?:0f)>8f)) ||
            (!voiceReview.driving && (gear!=VehicleGear.GEAR_PARK || kotlin.math.abs(speed)>0.05f))))voiceReview.cancel()
        if (paused && recording) saveSession(interrupted = true)
        if(gear == VehicleGear.GEAR_DRIVE && speed >= 0 && !paused) {
            // No earcon while driving; only the short spoken note when the user turned voice on.
            coach.sample(nowTick,speed)?.let { advice ->
                Log.i("DriveMateCoach",advice.kind+": "+advice.title)
                if(advice.kind=="acceleration") {rapidAcceleration=true;storage.edit().putBoolean("pendingRapidAcceleration",true).apply()}
                if(voiceReview.busy) {
                    if(advice.kind!="steady")voiceReview.cancel()
                } else voice.speak(advice.title)
            }
        } else if(gear==VehicleGear.GEAR_PARK && kotlin.math.abs(speed)<0.05f && !paused)coach.observeStopped(nowTick) else coach.reset()
        val parkedNow = gear == VehicleGear.GEAR_PARK && kotlin.math.abs(speed) < 0.05f && !restricted
        if (!parkedNow) parkedSince = 0L else if (parkedSince == 0L) parkedSince = nowTick
        // All three parking conditions must hold for 2s before the screen unlocks or the trip is saved.
        val parked = parkedNow && nowTick - parkedSince >= 2000L
        val moving = gear == VehicleGear.GEAR_DRIVE || gear == VehicleGear.GEAR_REVERSE
        val absSpeed = kotlin.math.abs(speed)
        if (!parked) {
            // While paused, speed only drives the parking lock; no session, sample or route is kept.
            if (moving && absSpeed > 0.05f && !recording && !paused) {
                recording = true
                candidates = 0
                seatbeltWarning=extra.seatbeltWarning==true
                rapidAcceleration=false
                storage.edit().putBoolean("pendingSeatbeltWarning",seatbeltWarning).putBoolean("pendingRapidAcceleration",false).apply()
                route = JSONArray()
                moments.clear()
                gapMs = 0L; gapStart = 0L; validSamples = 0
                sessionStart = SystemClock.elapsedRealtime()
                reviewTiming.reset()
                sessionId = java.util.UUID.randomUUID().toString()
                storage.edit().putBoolean("recording", true).putInt("pendingCount", 0).putString("pendingSessionId", sessionId)
                    .remove("pendingMoments").remove("pendingRoute")
                    .putLong("pendingGapMs", 0).putInt("pendingSamples", 0).putLong("sessionStart", sessionStart).apply()
                lastCandidate = 0
                braking = false
                Log.i("DriveMate", "session started gear=$gear")
            }
            if(recording && moving && extra.seatbeltWarning==true && !seatbeltWarning) {
                seatbeltWarning=true;storage.edit().putBoolean("pendingSeatbeltWarning",true).apply()
            }
            if (recording && moving) {
                val speed = absSpeed
                val now = SystemClock.elapsedRealtime()
                val elapsed = now - previousTime
                val last = previousSpeed
                if (last != null && elapsed in 100L..750L) {
                    validSamples++
                    if (validSamples <= com.drivemate.app.TripQuality.MIN_SAMPLES || validSamples % 20 == 0) storage.edit().putInt("pendingSamples", validSamples).apply()
                }
                if (last != null && last - speed > 0.2f) lastDrop = now
                // One braking maneuver spans several samples; count it once until slowing has paused for 2s.
                if (braking && now - lastDrop > 2000L) braking = false
                if (last != null && elapsed in 100L..750L &&
                    last - speed >= 1.5f && (last - speed) * 1000f / elapsed >= 3.0f &&
                    now - lastCandidate >= 1500L && !braking
                ) {
                    braking = true
                    candidates++
                    moments += com.drivemate.app.Garden.Moment(now - sessionStart, kph(last), kph(speed))
                    storage.edit().putInt("pendingCount", candidates).putString("pendingMoments", com.drivemate.app.Garden.Moment.listToJson(moments)).apply()
                    lastCandidate = now
                    Log.i("DriveMate", "deceleration candidate count=$candidates")
                } else if (braking && last != null && last - speed > 0.2f && moments.isNotEmpty()) {
                    // The moment ends where this braking maneuver ends, not at the first sample that crossed the threshold.
                    moments[moments.lastIndex] = moments.last().copy(toKph = kph(speed))
                    storage.edit().putString("pendingMoments", com.drivemate.app.Garden.Moment.listToJson(moments)).apply()
                }
                previousSpeed = speed
                previousTime = now
            } else {
                previousSpeed = null
            }
            if(recording && gear==VehicleGear.GEAR_DRIVE)maybeOfferDrivingReview(nowTick,speed)
            publish(if (parkedNow) "settling" else "locked")
            return
        }
        previousSpeed = null
        if (recording && !saveSession(interrupted = false)) {
            publish("settling")
            return
        }
        publish("parked")
        applyVoiceReview()
    }
}


