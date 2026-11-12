package com.drivemate.app
import org.junit.Assert.*
import org.junit.Test

class MovementHeadingTest {
    @Test fun derivesEastAndSouthFromMovementEvenWhenProviderReportsNorth() {
        val h=MovementHeading()
        h.sample(GeoPoint(37.5,127.0),1000,3f,10f,0f)
        assertEquals(90f,h.sample(GeoPoint(37.5,127.00012),2000,3f,10f,0f)!!,0.1f)
        assertEquals(180f,h.sample(GeoPoint(37.4999,127.00012),3000,3f,10f,null)!!,0.1f)
    }
    @Test fun missingBearingDoesNotInventNorthAndStationaryNoiseHoldsDirection() {
        val h=MovementHeading()
        assertNull(h.sample(GeoPoint(37.5,127.0),1000,3f,0f,null))
        assertEquals(270f,h.sample(GeoPoint(37.5,126.99988),2000,3f,10f,null)!!,0.1f)
        assertEquals(270f,h.sample(GeoPoint(37.50002,126.99987),3000,3f,0f,0f)!!,0.1f)
    }
    @Test fun staleFixesAndGpsJumpsCannotTurnTheArrow() {
        val h=MovementHeading()
        h.sample(GeoPoint(37.5,127.0),1000,3f,10f,90f)
        assertEquals(90f,h.sample(GeoPoint(38.0,127.0),2000,3f,10f,0f)!!,0.1f)
        assertEquals(90f,h.sample(GeoPoint(38.0,127.1),1500,3f,10f,0f)!!,0.1f)
    }
    @Test fun shortMovementAccumulatesUntilDirectionIsReliable() {
        val h=MovementHeading()
        h.sample(GeoPoint(37.5,127.0),1000,5f,2f,null)
        assertNull(h.sample(GeoPoint(37.5,127.00001),2000,5f,2f,null))
        assertEquals(90f,h.sample(GeoPoint(37.5,127.00006),3000,5f,2f,null)!!,0.1f)
    }
    @Test fun restoredCourseHoldsWhileParkedAndYieldsToRealMovement() {
        val h=MovementHeading(-90f)
        assertEquals(270f,h.sample(GeoPoint(37.5,127.0),1000,3f,0f,null)!!,0.1f)
        h.sample(GeoPoint(37.5,127.0),2000,3f,10f,null)
        assertEquals(0f,h.sample(GeoPoint(37.50012,127.0),3000,3f,10f,null)!!,0.1f)
        assertNull(MovementHeading(Float.NaN).heading)
    }
}
