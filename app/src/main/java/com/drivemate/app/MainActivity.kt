package com.drivemate.app

import android.app.Activity
import android.content.Intent
import com.drivemate.app.recording.DriveRecorder
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val storage by lazy { getSharedPreferences("driving", MODE_PRIVATE) }
    private val speedPermission = "android.car.permission.CAR_SPEED"
    private var rendered = ""
    private var mascotMotion: ObjectAnimator? = null
    private val garden by lazy { assets.open("garden.png").use { BitmapFactory.decodeStream(it) } }
    private val poll = object : Runnable {
        override fun run() {
            update()
            handler.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            show("Pleos Connect에서 실행해 주세요")
            return
        }
        val permissions = arrayOf(
            speedPermission,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (permissions.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) {
            requestPermissions(permissions, 1)
        } else {
            startRecorder()
        }
        handler.post(poll)
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code == 1 && checkSelfPermission(speedPermission) == PackageManager.PERMISSION_GRANTED) startRecorder()
    }

    private fun startRecorder() {
        startForegroundService(Intent(this, DriveRecorder::class.java))
    }

    private fun update() {
        when (storage.getString("vehicleState", "unknown")) {
            "locked" -> {
                show("주행 기록 중", "주차 후 피드백을 확인할 수 있어요.")
                return
            }
            "parked" -> Unit
            else -> {
                show("차량 신호 확인 중", "속도 권한과 차량 연결을 확인해 주세요.")
                return
            }
        }
        val last = storage.getInt("lastCount", 0)
        val sessions = storage.getInt("sessions", 0)
        if (sessions == 0) {
            show("드라이빙 정원", "차량이 D단에서 움직이면 자동으로 주행을 기록해요.")
            return
        }
        val goalSession = storage.getInt("goalSession", 0)
        val completed = storage.getInt("goalCompletedSession", 0)
        val leaves = storage.getInt("leaves", 0)
        val goal = "앞차와의 간격 변화를 일찍 살펴보기"
        val summary = "급감속 후보 ${last}건 · 누적 ${sessions}회 주행 · 첫 잎 ${leaves}개"
        when {
            goalSession == 0 && completed == sessions ->
                show("첫 잎이 돋았어요", "${summary}\n이전 목표를 돌아본 기록이 정원에 남았어요.")
            goalSession == 0 ->
                show("오늘의 주행 회고", "${summary}\n다음 목표 · ${goal}", "목표 저장하고 씨앗 심기") {
                    if (!sameParkedSession(sessions) || storage.getInt("goalSession", 0) != 0) return@show
                    storage.edit().putInt("goalSession", sessions).apply()
                    Log.i("DriveMate", "goal planted at session=$sessions")
                    update()
                }
            sessions <= goalSession ->
                show("씨앗을 심었어요", "${goal}\n다음 주행을 마치면 이전 목표를 돌아볼 수 있어요.")
            else ->
                show("지난 목표는 어땠나요?", "${goal}\n${summary}", "어려웠어요", "잘됐어요") { response ->
                    if (!sameParkedSession(sessions) || storage.getInt("goalSession", 0) != goalSession) return@show
                    storage.edit().remove("goalSession").putInt("goalCompletedSession", sessions)
                        .putInt("leaves", leaves + 1).putString("lastGoalResponse", response).apply()
                    Log.i("DriveMate", "goal reviewed from=$goalSession at=$sessions leaves=${leaves + 1}")
                    update()
                }
        }
    }

    private fun sameParkedSession(sessions: Int) =
        storage.getString("vehicleState", "") == "parked" && storage.getInt("sessions", 0) == sessions

    private fun show(title: String, detail: String = "", button: String? = null, alternate: String? = null, action: (String) -> Unit = {}) {
        val key = listOf(title, detail, button, alternate).joinToString("|")
        if (rendered == key) return
        rendered = key
        mascotMotion?.cancel()
        val parkedScreen = title == "드라이빙 정원" || title == "씨앗을 심었어요" || button != null || title == "첫 잎이 돋았어요"
        val panel = LinearLayout(this).apply {
            orientation = if (parkedScreen) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(36), dp(12), dp(36), dp(12))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.WHITE, Color.rgb(228, 246, 236))
            )
        }
        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (parkedScreen) Gravity.CENTER_VERTICAL else Gravity.CENTER
        }
        panel.addView(copy, if (parkedScreen) {
            LinearLayout.LayoutParams(0, -1, 1.15f)
        } else {
            LinearLayout.LayoutParams(-1, -1)
        })
        fun label(value: String, size: Float, color: Int, bold: Boolean = false) {
            copy.addView(TextView(this).apply {
                text = value
                textSize = size
                setTextColor(color)
                if (bold) typeface = Typeface.DEFAULT_BOLD
                gravity = if (parkedScreen) Gravity.START else Gravity.CENTER
                setPadding(0, dp(4), 0, dp(4))
            })
        }
        val navy = Color.rgb(0, 44, 95)
        val green = Color.rgb(32, 101, 74)
        label(if (parkedScreen) "DRIVE MATE   /   드라이빙 정원" else "DRIVE MATE", 16f, green, true)
        label(title, 34f, navy, true)
        if (detail.isNotEmpty()) label(detail, 20f, navy)
        if (parkedScreen) label("Pleos 차량 신호 · P단 / 속도 0 / 주행 제한 해제", 15f, green, true)
        for (choice in listOfNotNull(button, alternate)) copy.addView(Button(this).apply {
            text = choice
            textSize = 17f
            setTextColor(Color.WHITE)
            setPadding(dp(18), 0, dp(18), 0)
            minHeight = dp(48)
            background = GradientDrawable().apply {
                setColor(green)
                cornerRadius = dp(14).toFloat()
            }
            setOnClickListener { action(choice) }
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(12) })
        if (parkedScreen) {
            val sprouted = title == "첫 잎이 돋았어요"

            val mascot = ImageView(this).apply {
                setImageBitmap(garden)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = if (sprouted) "첫 잎과 함께 기뻐하는 정원 캐릭터" else "정원 캐릭터"
                alpha = if (title == "잎이 살짝 처졌어요") 0.82f else 1f
            }
            val visual = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            panel.addView(visual, LinearLayout.LayoutParams(0, -1, 1f))
            visual.addView(mascot, LinearLayout.LayoutParams(-1, 0, 1f))
            visual.addView(RouteMap(this, storage.getString("lastRoute", "[]") ?: "[]"),
                LinearLayout.LayoutParams(-1, 0, 1f))
            mascotMotion = ObjectAnimator.ofPropertyValuesHolder(
                mascot,
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, -dp(if (sprouted) 13 else 5).toFloat()),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, if (sprouted) 1.04f else 1.02f)
            ).apply {
                duration = if (sprouted) 650 else 1300
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                start()
            }
        }
        setContentView(panel)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onStop() {
        mascotMotion?.pause()
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        mascotMotion?.resume()
    }

    override fun onDestroy() {
        mascotMotion?.cancel()
        handler.removeCallbacksAndMessages(null)

        super.onDestroy()
    }
}









