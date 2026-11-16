package kr.yeaan.bus.algorithm

import kotlin.math.*

class ArrivalEstimator {
    fun seconds(
        distanceMeters: Double?, recentSpeed: Double?, routeAverageSpeed: Double = 6.0,
        stopsAway: Int? = null, apiSeconds: Int? = null
    ): Int? {
        val api = apiSeconds?.takeIf { it >= 0 }?.toDouble()
        val distance = distanceMeters?.takeIf { it.isFinite() && it >= 0 }
        if (distance == null) return api?.roundToInt()
        val average = routeAverageSpeed.coerceIn(2.0, 20.0)
        val speed = recentSpeed?.takeIf { it.isFinite() && it > 0.8 }
            ?.let { it.coerceIn(1.0, 35.0) * 0.65 + average * 0.35 } ?: (average * 0.65)
        val dwell = ((stopsAway ?: 1) - 1).coerceAtLeast(0) * 16.0
        val estimate = distance / speed + dwell + if (distance > 100) 8.0 else 0.0
        // API includes traffic/dispatch knowledge. Bound our correction to +/-35%.
        return if (api != null) (api * 0.7 + estimate.coerceIn(api * 0.65, api * 1.35) * 0.3).roundToInt()
        else estimate.roundToInt().coerceAtLeast(0)
    }
}
object ArrivalText {
    fun time(seconds: Int?, stale: Boolean = false): String = when {
        stale -> "정보 갱신 대기"
        seconds == null -> "도착 정보 없음"
        seconds <= 35 -> "곧 도착합니다"
        else -> "${ceil(seconds / 60.0).toInt()}분 후"
    }
    fun distance(meters: Double?): String = when {
        meters == null -> "위치 정보 대기"
        meters < 1000 -> "약 ${(meters / 10).roundToInt() * 10}m 남음"
        else -> "약 ${"%.1f".format(java.util.Locale.KOREA, meters / 1000)}km 남음"
    }
}
