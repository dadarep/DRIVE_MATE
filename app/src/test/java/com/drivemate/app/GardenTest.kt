package com.drivemate.app

import org.junit.Assert.*
import org.junit.Test

class GardenTest {
    private val seeded = Garden().withGoal("g1", "출발할 때 천천히 가속하기", "s3", 3)

    @Test fun goalIsNotDueInTheSessionItWasSaved() {
        assertFalse(seeded.due(3))
        assertNull(seeded.reflect("g1", "GOOD", "s3", 3, emptyList(), 0))
        assertTrue(seeded.due(4))
    }
    @Test fun reflectingTwiceOnTheSameGoalGivesOneLeaf() {
        val once = seeded.reflect("g1", "DIFFICULT", "s4", 4, listOf("SIGNAL"), 10)!!
        assertEquals(1, once.leaves)
        assertNull(once.goal)
        assertNull(once.reflect("g1", "GOOD", "s4", 4, emptyList(), 11))
        val replayed = once.copy(goal = seeded.goal)
        assertNull(replayed.reflect("g1", "GOOD", "s4", 4, emptyList(), 12))
    }
    @Test fun replacedGoalCannotBeReflectedWithTheOldId() {
        val replaced = seeded.withGoal("g2", "정지선 앞에서 여유 있게 멈추기", "s3", 3)
        assertNull(replaced.reflect("g1", "GOOD", "s4", 4, emptyList(), 0))
        assertNotNull(replaced.reflect("g2", "GOOD", "s4", 4, emptyList(), 0))
    }
    @Test fun legacyLeavesStaySeparateFromGoalRewards() {
        val g = Garden(legacyLeaves = 2).withGoal("g1", "목표", "s1", 1).reflect("g1", "GOOD", "s2", 2, emptyList(), 0)!!
        assertEquals(3, g.leaves)
    }
    @Test fun deletingATripRemovesItsReflectionAndLeaf() {
        val reflected = seeded.reflect("g1", "GOOD", "s4", 4, emptyList(), 0)!!.withGoal("g2", "다음 목표", "s4", 4)
        val deleted = reflected.withoutSession("s4", 3)
        assertEquals(0, deleted.leaves)
        assertEquals(3, deleted.goal!!.baseSession)
    }
    @Test fun survivesJsonRoundTrip() {
        val g = seeded.reflect("g1", "GOOD", "s4", 4, listOf("SIGNAL", "UNSURE"), 99)!!.withGoal("g2", "다음", "s4", 4).copy(legacyLeaves = 1)
        assertEquals(g, Garden.fromJson(g.toJson()))
    }
    @Test fun goalTextIsTrimmedAndBounded() {
        assertEquals(Garden.MAX_GOAL_LENGTH, Garden().withGoal("g", "  " + "가".repeat(60), "s", 0).goal!!.text.length)
    }
    @Test fun topMomentsKeepsTheThreeLargestDropsInTripOrder() {
        val m = listOf(Garden.Moment(1, 40, 30), Garden.Moment(2, 50, 10), Garden.Moment(3, 30, 25), Garden.Moment(4, 60, 20), Garden.Moment(5, 35, 10))
        assertEquals(listOf(2L, 4L, 5L), Garden.topMoments(m).map { it.offsetMs })
    }
    @Test fun reboundCannotTurnADecelerationMomentIntoAnIncrease() {
        val moment = Garden.Moment(4000, 18, 0)
        assertEquals(moment, moment.withEndSpeed(22))
    }
    @Test fun continuedBrakingRetainsTheLowestSpeedAndOriginalStart() {
        val moment = Garden.Moment(7000, 50, 30).withEndSpeed(10).withEndSpeed(20)
        assertEquals(Garden.Moment(7000, 50, 10), moment)
    }
}
