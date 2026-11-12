package com.drivemate.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/** Short synthesized cues for parked moments only; nothing plays while driving. Kept under 0.5s and soft. */
class Earcons(private val context: Context) {
    enum class Cue(val notes: List<Pair<Double, Int>>, val gap: Int) {
        DESTINATION(listOf(1174.7 to 0, 1480.0 to 1, 1760.0 to 2), 70),
        TRIP_END(listOf(784.0 to 0, 1046.5 to 1), 180),
        READY(listOf(1318.5 to 0), 0)
    }
    private val prefs = context.getSharedPreferences("driving", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private val rate = 44100
    private val cache = mutableMapOf<Cue, ShortArray>()

    fun durationMs(cue: Cue) = cue.gap * (cue.notes.size - 1) + 320

    /** Returns false when the cue was skipped, so follow-up speech should be skipped too. */
    fun play(cue: Cue): Boolean {
        if (!prefs.getBoolean("voiceEnabled", false)) return false
        // Hold past the cue so speech that follows it keeps the same focus instead of re-ducking.
        if (!CoachAudio.hold(context, durationMs(cue) + 600L)) return false
        return runCatching {
            val pcm = cache.getOrPut(cue) { render(cue) }
            val track = AudioTrack.Builder()
                .setAudioAttributes(CoachAudio.attributes(AudioAttributes.CONTENT_TYPE_SONIFICATION))
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size * 2).build()
            track.write(pcm, 0, pcm.size); track.play()
            handler.postDelayed({ runCatching { track.release() } }, pcm.size * 1000L / rate + 200)
        }.onFailure { Log.w("DriveMateEarcon", "play $cue", it) }.isSuccess
    }

    private fun render(cue: Cue): ShortArray {
        val noteLength = (0.32 * rate).toInt()
        val total = (cue.gap * (cue.notes.size - 1) * rate / 1000) + noteLength
        val mix = DoubleArray(total)
        for ((frequency, index) in cue.notes) {
            val start = cue.gap * index * rate / 1000
            for (i in 0 until noteLength) {
                val t = i.toDouble() / rate
                val attack = min(1.0, t / 0.006)
                val decay = exp(-t / 0.11)
                val tone = sin(2 * PI * frequency * t) + 0.28 * sin(2 * PI * frequency * 2 * t) * exp(-t / 0.05) + 0.08 * sin(2 * PI * frequency * 3 * t) * exp(-t / 0.03)
                mix[start + i] += tone * attack * decay
            }
        }
        val peak = mix.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1e-6)
        return ShortArray(total) { (mix[it] / peak * 0.32 * Short.MAX_VALUE).toInt().toShort() }
    }
}
