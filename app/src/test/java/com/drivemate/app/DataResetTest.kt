package com.drivemate.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataResetTest {
    private val written: Set<String> by lazy {
        val put = Regex("""\.put(?:String|Int|Long|Float|Boolean|StringSet)\(\s*"(\w+)"""")
        File("src/main/java").walkTopDown().filter { it.extension == "kt" }
            .flatMap { file -> put.findAll(file.readText()).map { it.groupValues[1] } }.toSet()
    }

    @Test
    fun sourcesAreScanned() {
        assertTrue("expected stored keys, found $written", written.containsAll(listOf("sessions", "garden", "liveLat", "recordingPaused")))
    }

    @Test
    fun everyStoredKeyIsDeletedOrDeliberatelyKept() {
        val unclassified = written - DataReset.ALL.toSet() - DataReset.KEPT.toSet()
        assertEquals("add these keys to DataReset.ALL or DataReset.KEPT", emptySet<String>(), unclassified)
    }

    @Test
    fun keptKeysAreOnlySettings() {
        assertEquals(emptySet<String>(), DataReset.ALL.toSet() intersect DataReset.KEPT.toSet())
        assertEquals(setOf("voiceEnabled", "recordingPaused", "voiceStatus"), DataReset.KEPT.toSet())
    }

    @Test
    fun recordsAndProgressAreDeleted() {
        val required = listOf(
            "pendingMoments", "pendingRoute", "pendingSessionId", "recording",
            "liveLat", "liveLon", "locationAt", "liveSpeed", "signalAt", "vehicleState",
            "sessions", "garden", "lastSessionId", "lastMoments", "lastContexts", "lastRoute", "destinationLat",
        )
        assertTrue(DataReset.ALL.containsAll(required))
    }
}
