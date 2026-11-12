package com.drivemate.app

/** Whether a trip has enough valid speed samples to report a deceleration count at all. */
object TripQuality {
    const val MIN_SAMPLES = 20

    fun insufficient(validSamples: Int, gapMs: Long, durationMs: Long) =
        validSamples < MIN_SAMPLES || (durationMs > 0 && gapMs * 2 > durationMs)

    fun gapLabel(gapMs: Long): String? {
        val seconds = gapMs / 1000
        if (seconds < 3) return null
        return if (seconds >= 60) "${seconds / 60}분 ${seconds % 60}초" else "${seconds}초"
    }
}
