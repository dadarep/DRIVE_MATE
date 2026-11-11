package com.drivemate.app

import org.junit.Assert.*
import org.junit.Test

class CoachEngineTest {
    @Test fun decelerationRequiresAnObservationWindow() {
        val engine=CoachEngine()
        assertNull(engine.sample(20000,20f,null,null,null))
        assertNull(engine.sample(20250,19f,null,null,null))
        assertNull(engine.sample(20500,18f,null,null,null))
        val advice=engine.sample(20750,17f,true,null,null)
        assertEquals("deceleration",advice?.kind)
        assertTrue(advice!!.detail.contains("브레이크"))
        assertNull(engine.sample(21000,15f,true,null,null))
    }
    @Test fun disconnectedSamplesDoNotBecomeAnAccelerationEvent() {
        val engine=CoachEngine()
        engine.sample(20000,20f,null,null,null)
        assertNull(engine.sample(24000,0f,null,null,null))
        assertNull(engine.acceleration)
    }
    @Test fun gentleAccelerationDoesNotTriggerWarning() {
        val engine=CoachEngine()
        for(i in 0..20)assertNull(engine.sample(20000+i*250L,5f+i*.2f,null,null,null))
    }
    @Test fun unknownSteeringAndLimitNeverTriggerTheirWarnings() {
        val engine=CoachEngine()
        for(i in 0..8)assertNull(engine.sample(20000+i*250L,20f,null,null,null))
    }
    @Test fun knownSpeedLimitCanTriggerShortAdvice() {
        assertEquals("limit",CoachEngine().sample(20000,20f,null,null,50)?.kind)
    }
}
