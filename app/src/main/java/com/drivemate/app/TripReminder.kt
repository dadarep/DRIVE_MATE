package com.drivemate.app

/** One actionable reminder; unknown telemetry never becomes a negative claim about the driver. */
internal object TripReminder {
    fun line(seatbeltWarning: Boolean, suddenSlowing: Int, rapidAcceleration: Boolean, insufficient: Boolean): String = when {
        seatbeltWarning -> "운전 시작 전 안전벨트, 잊지 마세요."
        !insufficient && suddenSlowing > 0 -> "다음 운전엔 앞 상황을 미리 살피고, 여유 있게 속도를 줄여 주세요."
        !insufficient && rapidAcceleration -> "다음 출발은 천천히, 부드럽게 가속해 주세요."
        else -> "다음 운전도 앞차와의 간격, 여유 있게 유지해 주세요."
    }
}
