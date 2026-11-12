package com.drivemate.app

/** A reply is bound to both the goal and trip; only a completed matching trip may earn a leaf. */
internal data class VoiceReviewReply(val goalId:String,val tripId:String,val response:String) {
    fun complete(garden:Garden,lastTrip:String,sessions:Int,contexts:List<String>,now:Long):Garden? {
        if(tripId!=lastTrip || response !in listOf("GOOD","DIFFICULT"))return null
        return garden.reflect(goalId,response,tripId,sessions,contexts,now)
    }
}
