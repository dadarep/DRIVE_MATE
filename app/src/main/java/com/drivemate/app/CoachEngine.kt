package com.drivemate.app

import kotlin.math.abs

/** Observations, not a safety score. Thresholds are prototype heuristics. */
class CoachEngine {
    data class Advice(val title: String, val detail: String, val kind: String)
    private val samples = ArrayDeque<Pair<Long, Float>>()
    private var lastAdvice = 0L
    private var steadySince = 0L
    private var lastTurn: String? = null
    var acceleration: Float? = null
        private set
    fun reset() { samples.clear(); acceleration = null; steadySince = 0 }
    fun sample(now: Long, speed: Float, brake: Boolean?, steering: Float?, limit: Int?, turn: String? = null): Advice? {
        if (samples.lastOrNull()?.let { now - it.first > 1500 } == true) reset()
        samples.addLast(now to speed)
        while (samples.size > 2 && now - samples.elementAt(1).first >= 1000) samples.removeFirst()
        val first = samples.first()
        val dt = now - first.first
        acceleration = if (dt in 700..1500) (speed - first.second) * 1000 / dt else null
        val a = acceleration
        if (a != null && abs(a) < 0.7 && speed > 3) {
            if (steadySince == 0L) steadySince = now
        } else steadySince = 0
        val turnChanged = turn != null && turn != "OFF" && turn != "UNKNOWN" && turn != lastTurn
        lastTurn = turn
        if (now - lastAdvice < 15000) return null
        val advice = when {
            limit != null && limit > 0 && speed * 3.6 > limit + 3 -> Advice("제한속도를 확인해 주세요", "현재 도로의 제한속도는 ${limit}km/h예요.", "limit")
            a != null && a < -3 -> Advice("속도가 빠르게 줄었어요", if (brake == true) "브레이크 입력이 함께 감지됐어요. 여유가 생기면 부드럽게 감속해 보세요." else "주변 상황을 먼저 살피고, 여유 있게 속도를 조절해 주세요.", "deceleration")
            a != null && a > 2.5 -> Advice("조금 더 부드럽게 출발해 볼까요?", "속도가 빠르게 올라갔어요. 천천히 가속해 주세요.", "acceleration")
            steering != null && abs(steering) > 80 && speed > 8.3 -> Advice("커브에서는 속도를 여유 있게", "핸들 회전과 차량 속도를 함께 확인했어요.", "curve")
            turnChanged && speed > 3 -> Advice("방향을 바꾸기 전, 주변을 살펴요", "방향지시등이 켜졌어요. 주변 차량과 보행자를 확인해 주세요.", "signal")
            steadySince > 0 && now - steadySince > 12000 -> Advice("부드러운 흐름, 좋아요", "일정한 속도 흐름이 이어지고 있어요.", "steady")
            else -> null
        }
        if (advice != null) { lastAdvice = now; steadySince = 0 }
        return advice
    }
}
