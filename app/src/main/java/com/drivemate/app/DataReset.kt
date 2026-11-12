package com.drivemate.app

import android.content.SharedPreferences

/**
 * Every key in the "driving" preferences is either user data removed by delete-all or a setting kept on purpose.
 * DataResetTest scans the sources, so a newly stored key fails the build until it is listed here.
 */
object DataReset {
    val LAST_TRIP = listOf("lastSessionId", "lastCount", "lastMoments", "lastContexts", "lastGapMs", "lastInsufficient", "lastInterrupted", "lastRoute")

    val ALL = LAST_TRIP + listOf(
        "garden", "gardenBroken", "sessions", "reviewed", "goalDraft",
        "destinationName", "destinationLat", "destinationLon",
        "pendingCount", "pendingSamples", "pendingGapMs", "sessionStart", "pendingMoments", "pendingRoute", "pendingSessionId", "recording",
        "liveLat", "liveLon", "locationAt", "bearing", "liveSpeed", "signalAt", "vehicleState", "gearLabel",
        "navDestination", "navLat", "navLon", "navDistance", "navSeconds", "navAt", "navStatus", "navInstruction",
        "turnDistance", "thenInstruction", "thenDistance", "tbtAt",
        // Written only by earlier versions; removed so no past coaching, signal or limit value survives.
        "goalSession", "goalCompletedSession", "lastResponse", "leaves", "totalCount", "feedback", "coachTitle", "coachDetail", "coachKind", "coachAt",
        "brakeLabel", "turnLabel", "steering", "extraAt", "speedLimit", "limitAt",
    )

    /** Preferences, not records: voice on/off, the recording pause choice and the voice engine status. */
    val KEPT = listOf("voiceEnabled", "recordingPaused", "voiceStatus")

    fun wipeAll(storage: SharedPreferences): Boolean = storage.edit().apply { ALL.forEach { remove(it) } }.commit()
}
