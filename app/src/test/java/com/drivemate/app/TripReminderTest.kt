package com.drivemate.app
import org.junit.Assert.*
import org.junit.Test

class TripReminderTest {
    @Test fun observedSeatbeltWarningHasPriority() {
        assertEquals("운전 시작 전 안전벨트, 잊지 마세요.",TripReminder.line(true,3,true,false))
    }
    @Test fun choosesOneActionFromThisTrip() {
        assertTrue(TripReminder.line(false,1,true,false).contains("속도를 줄여"))
        assertTrue(TripReminder.line(false,0,true,false).contains("가속"))
    }
    @Test fun insufficientOrUneventfulTripsDoNotInventAProblem() {
        assertEquals(TripReminder.line(false,0,false,false),TripReminder.line(false,9,true,true))
        assertFalse(TripReminder.line(false,0,false,false).contains("안전벨트"))
    }
}
