package com.drivemate.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Earcons and speech share one transient, duckable focus on the notification context.
 * Pleos reserves the navigation context for the navigation app, and coaching must stay silent
 * when focus is refused or a call, emergency/safety sound or navigation prompt is playing.
 * All calls happen on the main thread.
 */
object CoachAudio {
    private val handler = Handler(Looper.getMainLooper())
    private var manager: AudioManager? = null
    private var request: AudioFocusRequest? = null
    private val lossListeners = mutableSetOf<() -> Unit>()
    private val release = Runnable { abandon() }
    // EMERGENCY, SAFETY, VEHICLE_STATUS and ANNOUNCEMENT are system usages 1000..1003 without public constants.
    private val yieldTo = setOf(
        AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, AudioAttributes.USAGE_VOICE_COMMUNICATION,
        AudioAttributes.USAGE_NOTIFICATION_RINGTONE, AudioAttributes.USAGE_ALARM, AudioAttributes.USAGE_ASSISTANT,
        1000, 1001, 1002, 1003)

    fun attributes(contentType: Int): AudioAttributes =
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(contentType).build()

    fun addLossListener(listener: () -> Unit) { lossListeners += listener }
    fun removeLossListener(listener: () -> Unit) { lossListeners -= listener }

    /** Holds focus for [ms] from now, requesting it first if needed. Returns false when playback must be skipped. */
    fun hold(context: Context, ms: Long): Boolean {
        val audio = manager ?: context.applicationContext.getSystemService(AudioManager::class.java).also { manager = it } ?: return false
        if (audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION || audio.mode == AudioManager.MODE_RINGTONE) {
            Log.i("DriveMateAudio", "skipped mode=${audio.mode}")
            return false
        }
        val busy = runCatching { audio.activePlaybackConfigurations.firstOrNull { it.audioAttributes.usage in yieldTo } }.getOrNull()
        if (busy != null) {
            Log.i("DriveMateAudio", "skipped active usage=${busy.audioAttributes.usage}")
            return false
        }
        if (request == null) {
            val next = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes(AudioAttributes.CONTENT_TYPE_SPEECH))
                .setOnAudioFocusChangeListener({ change ->
                    if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        Log.i("DriveMateAudio", "focus lost change=$change")
                        lossListeners.toList().forEach { it() }
                        abandon()
                    }
                }, handler).build()
            val result = audio.requestAudioFocus(next)
            Log.i("DriveMateAudio", "focus request usage=NOTIFICATION gain=TRANSIENT_MAY_DUCK result=$result")
            if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
            request = next
        }
        handler.removeCallbacks(release)
        handler.postDelayed(release, ms)
        return true
    }

    /** Shortens an existing hold without requesting focus again. */
    fun releaseSoon(ms: Long = 300L) {
        if (request == null) return
        handler.removeCallbacks(release)
        handler.postDelayed(release, ms)
    }

    fun abandon() {
        handler.removeCallbacks(release)
        request?.let { manager?.abandonAudioFocusRequest(it) }
        request = null
    }
}
