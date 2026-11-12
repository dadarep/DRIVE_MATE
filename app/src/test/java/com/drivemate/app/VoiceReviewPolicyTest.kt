package com.drivemate.app

import org.junit.Assert.*
import org.junit.Test

class VoiceReviewPolicyTest {
    @Test fun koreanAnswersHandleSpacingNegationAndDeferral(){
        assertEquals("GOOD",VoiceReviewAnswer.parse("잘 됐어!"))
        assertEquals("DIFFICULT",VoiceReviewAnswer.parse("잘 안됐어"))
        assertEquals("DIFFICULT",VoiceReviewAnswer.parse("어려웠어"))
        assertEquals("GOOD",VoiceReviewAnswer.parse("안 어려웠어"))
        assertEquals("LATER",VoiceReviewAnswer.parse("나중에 할게"))
        assertNull(VoiceReviewAnswer.parse("잘됐지만 어려웠어"))
        assertNull(VoiceReviewAnswer.parse("오늘 점심 뭐 먹지"))
    }
    @Test fun offersRequireStableLowSpeedAndAreDeferredDuringManeuvers(){
        val timing=VoiceReviewTiming()
        assertFalse(timing.eligible(60000,60000,10f,0f,true,true))
        assertTrue(timing.eligible(72000,72000,10f,0f,true,true))
        assertFalse(timing.eligible(73000,73000,10f,2f,true,true))
        assertFalse(timing.eligible(74000,74000,10f,0f,true,false))
        assertFalse(timing.eligible(90000,90000,20f,0f,true,true))
    }
    @Test fun midTripQuestionWaitsFiveMinutesEvenAfterSteadyStretch(){
        val timing=VoiceReviewTiming()
        timing.eligible(60000,60000,10f,0f,false,true)
        assertFalse(timing.eligible(100000,100000,10f,0f,false,true))
        assertTrue(timing.eligible(300000,300000,10f,0f,false,true))
    }
    @Test fun aVoiceReplyEarnsOneLeafOnlyAfterItsOwnTripAndGoal(){
        val g=Garden().withGoal("goal","천천히 출발하기","before",4)
        val r=VoiceReviewReply("goal","trip","DIFFICULT")
        assertNull(r.complete(g,"before",4,emptyList(),1))
        assertNull(r.complete(g,"trip",4,emptyList(),1))
        assertNull(r.complete(g.withGoal("other","다른 목표","before",4),"trip",5,emptyList(),1))
        val after=r.complete(g,"trip",5,emptyList(),1)!!
        assertEquals(1,after.leaves)
        assertNull(r.complete(after,"trip",5,emptyList(),2))
        assertNull(VoiceReviewReply("goal","trip","LATER").complete(g,"trip",5,emptyList(),1))
    }
}
