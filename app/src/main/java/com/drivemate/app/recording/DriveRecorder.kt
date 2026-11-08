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
    private var status = ""
    private lateinit var locations: LocationManager
    private var route = JSONArray()
    private val locationListener = LocationListener { point ->
        if (recording && point.hasAccuracy() && point.accuracy <= 50f && route.length() < 3600) {
            route.put(JSONArray().put(point.latitude).put(point.longitude))
        }
    }
    private var smoothSince = 0L
    private val poll = object : Runnable {
        override fun run() {
            update()
            handler.postDelayed(this, 250)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("driving", "주행 기록", NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(this, "driving")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("DRIVE MATE 주행 기록")
            .setOngoing(true).build()
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
        locations = getSystemService(LocationManager::class.java)
        if (canLocate) {
            runCatching {
                locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 2f, locationListener)
            }.onFailure { Log.w("DriveMate", "위치 수신 시작 실패", it) }
        }
        recording = storage.getBoolean("recording", false)
        candidates = storage.getInt("pendingCount", 0)
        publish("unknown")
        runCatching {
            car = Car.createCar(this)
            properties = car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
            restrictions = car?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
        }.onFailure { Log.w("DriveMate", "차량 서비스 연결 실패", it) }
        handler.post(poll)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    private fun publish(value: String) {
        if (value == status) return
        status = value
        storage.edit().putString("vehicleState", value).apply()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        publish("unknown")
        if (::locations.isInitialized) locations.removeUpdates(locationListener)
        car?.disconnect()
        super.onDestroy()
    }
    private fun read(id: Int): Any? = runCatching {
        properties?.getProperty<Any>(id, 0)?.takeIf { it.status == 0 }?.value
    }.getOrNull()

    private fun update() {
        val speed = (read(VehiclePropertyIds.PERF_VEHICLE_SPEED) as? Float)?.takeIf { it.isFinite() }
        val gear = read(VehiclePropertyIds.GEAR_SELECTION) as? Int
        val restricted = runCatching {
            restrictions?.currentCarUxRestrictions?.isRequiresDistractionOptimization
        }.getOrNull()
        if (speed == null || gear == null || restricted == null) {
            previousSpeed = null
            smoothSince = 0
            publish("unknown")
            return
        }
        val parked = gear == VehicleGear.GEAR_PARK && kotlin.math.abs(speed) < 0.05f && !restricted
        if (!parked) {
            if (gear == VehicleGear.GEAR_DRIVE && speed > 0.05f && !recording) {
                recording = true
                candidates = 0
                route = JSONArray()
                storage.edit().putBoolean("recording", true).putInt("pendingCount", 0).remove("feedback").apply()
                lastCandidate = 0
                Log.i("DriveMate", "session started")
            }
            if (recording && gear == VehicleGear.GEAR_DRIVE && speed >= 0f) {
                val now = SystemClock.elapsedRealtime()
                val elapsed = now - previousTime
                val last = previousSpeed
                if (last != null && elapsed in 100L..750L &&
                    last - speed >= 1.5f && (last - speed) * 1000f / elapsed >= 3.0f &&
                    now - lastCandidate >= 1500L
                ) {
                    candidates++
                    storage.edit().putInt("pendingCount", candidates).putString("feedback", "급감속 후보 감지").apply()
                    lastCandidate = now
                    Log.i("DriveMate", "deceleration candidate count=$candidates")
                }
                val steady = last != null && speed >= 2f && elapsed in 100L..750L &&
                    kotlin.math.abs(last - speed) * 1000f / elapsed <= 1.0f
                if (steady) {
                    if (smoothSince == 0L) smoothSince = now
                    if (now - smoothSince >= 3000L && storage.getString("feedback", "") != "속도가 안정적이었어요") {
                        storage.edit().putString("feedback", "속도가 안정적이었어요").apply()
                    }
                } else {
                    smoothSince = 0L
                }
                previousSpeed = speed
                previousTime = now
            } else {
                previousSpeed = null
            smoothSince = 0
            }
            publish("locked")
            return
        }
        previousSpeed = null
        if (recording) {
            recording = false
            storage.edit()
                .putBoolean("recording", false)
                .putInt("lastCount", candidates)
                .putString("lastRoute", route.toString())
                .putInt("totalCount", storage.getInt("totalCount", 0) + candidates)
                .putInt("sessions", storage.getInt("sessions", 0) + 1)
                .putBoolean("reviewed", false)
                .apply()
            Log.i("DriveMate", "session saved count=$candidates")
        }
        publish("parked")
    }
}


