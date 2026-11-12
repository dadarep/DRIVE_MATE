package com.drivemate.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * Goal, reflection and reward state kept as one JSON value so a reflection, its goal completion and
 * its reward are written together. Rewards are derived from reflections keyed by goal id, so one goal
 * can never earn two leaves. Pre-ID versions keep their leaf count as [legacyLeaves] only.
 */
data class Garden(
    val goal: Goal? = null,
    val reflections: List<Reflection> = emptyList(),
    val legacyLeaves: Int = 0,
) {
    data class Goal(val id: String, val text: String, val baseSessionId: String, val baseSession: Int)
    data class Reflection(
        val goalId: String, val goalText: String, val afterSessionId: String,
        val response: String, val contexts: List<String>, val completedAt: Long,
    )

    val leaves get() = legacyLeaves + reflections.distinctBy { it.goalId }.size

    fun due(sessions: Int) = goal != null && sessions > goal.baseSession

    /** At most one active goal; replacing it keeps nothing of the old one because it was never reflected on. */
    fun withGoal(id: String, text: String, sessionId: String, sessions: Int) =
        copy(goal = Goal(id, text.trim().take(MAX_GOAL_LENGTH), sessionId, sessions))

    /** Null when the goal is not active, not yet due, or already rewarded. */
    fun reflect(goalId: String, response: String, sessionId: String, sessions: Int, contexts: List<String>, now: Long): Garden? {
        val active = goal ?: return null
        if (active.id != goalId || !due(sessions) || reflections.any { it.goalId == goalId }) return null
        return copy(goal = null, reflections = reflections + Reflection(goalId, active.text, sessionId, response, contexts, now))
    }

    /** Deleting a trip also removes reflections made after it, and with them their leaves. */
    fun withoutSession(sessionId: String, sessionsAfter: Int) = copy(
        reflections = reflections.filterNot { it.afterSessionId == sessionId },
        goal = goal?.let { if (it.baseSession > sessionsAfter) it.copy(baseSession = sessionsAfter) else it },
    )

    fun toJson(): String = JSONObject().apply {
        goal?.let { put("goal", JSONObject().put("id", it.id).put("text", it.text).put("baseSessionId", it.baseSessionId).put("baseSession", it.baseSession)) }
        put("reflections", JSONArray().apply {
            reflections.forEach { r ->
                put(JSONObject().put("goalId", r.goalId).put("goalText", r.goalText).put("afterSessionId", r.afterSessionId)
                    .put("response", r.response).put("contexts", JSONArray(r.contexts)).put("completedAt", r.completedAt))
            }
        })
        put("legacyLeaves", legacyLeaves)
    }.toString()

    companion object {
        const val MAX_GOAL_LENGTH = 40
        val PRESET_GOALS = listOf(
            "앞차와의 간격 변화를 일찍 살펴보기",
            "신호가 바뀌기 전에 미리 속도 줄이기",
            "출발할 때 천천히 가속하기",
            "정지선 앞에서 여유 있게 멈추기",
        )

        fun fromJson(value: String): Garden {
            val o = JSONObject(value)
            val goal = o.optJSONObject("goal")?.let { Goal(it.getString("id"), it.getString("text"), it.getString("baseSessionId"), it.getInt("baseSession")) }
            val list = o.optJSONArray("reflections") ?: JSONArray()
            val reflections = List(list.length()) { i ->
                val r = list.getJSONObject(i)
                val contexts = r.optJSONArray("contexts") ?: JSONArray()
                Reflection(r.getString("goalId"), r.getString("goalText"), r.getString("afterSessionId"), r.getString("response"),
                    List(contexts.length()) { contexts.getString(it) }, r.getLong("completedAt"))
            }
            return Garden(goal, reflections, o.optInt("legacyLeaves", 0))
        }

        /** Up to [limit] candidates with the largest speed drop, shown in trip order. */
        fun topMoments(moments: List<Moment>, limit: Int = 3) =
            moments.sortedByDescending { it.fromKph - it.toKph }.take(limit).sortedBy { it.offsetMs }
    }

    data class Moment(val offsetMs: Long, val fromKph: Int, val toKph: Int) {
        fun toJson(): JSONObject = JSONObject().put("t", offsetMs).put("from", fromKph).put("to", toKph)
        companion object {
            fun listFromJson(value: String?): List<Moment> {
                val a = runCatching { JSONArray(value ?: "[]") }.getOrDefault(JSONArray())
                return List(a.length()) { i -> a.getJSONObject(i).let { Moment(it.getLong("t"), it.getInt("from"), it.getInt("to")) } }
            }
            fun listToJson(list: List<Moment>) = JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()
        }
    }
}
