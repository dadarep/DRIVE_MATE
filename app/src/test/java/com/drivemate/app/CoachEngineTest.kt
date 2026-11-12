package com.drivemate.app

import org.junit.Assert.*
import org.junit.Test

class CoachEngineTest {
    @Test fun decelerationRequiresAnObservationWindow() {
        val engine=CoachEngine()
        assertNull(engine.sample(20000,20f))
        assertNull(engine.sample(20250,19f))
        assertNull(engine.sample(20500,18f))
        assertEquals("deceleration",engine.sample(20750,17f)?.kind)
        assertNull(engine.sample(21000,15f))
    }
    @Test fun disconnectedSamplesDoNotBecomeAnAccelerationEvent() {
        val engine=CoachEngine()
        engine.sample(20000,20f)
        assertNull(engine.sample(24000,0f))
        assertNull(engine.acceleration)
    }
    @Test fun gentleAccelerationDoesNotTriggerANote() {
        val engine=CoachEngine()
        for(i in 0..20)assertNull(engine.sample(20000+i*250L,5f+i*.2f))
    }
    @Test fun steadyNoteDoesNotRepeatOrDelaySpeedChange() {
        val engine=CoachEngine()
        var t=20000L
        val kinds=mutableListOf<String>()
        repeat(240){engine.sample(t,14f)?.let{kinds+=it.kind};t+=250}
        assertEquals(listOf("steady"),kinds)
        t+=5000
        engine.sample(t,14f);engine.sample(t+250,13f);engine.sample(t+500,11f)
        assertEquals("deceleration",engine.sample(t+750,9f)?.kind)
    }
    @Test fun notesUseObservationWording() {
        val engine=CoachEngine()
        engine.sample(20000,5f);engine.sample(20250,6f);engine.sample(20500,7f)
        val advice=engine.sample(20750,8f)
        assertEquals("acceleration",advice?.kind)
        assertFalse(advice!!.title.contains("주의"))
        assertFalse(advice.title.contains("좋아요"))
    }
}
