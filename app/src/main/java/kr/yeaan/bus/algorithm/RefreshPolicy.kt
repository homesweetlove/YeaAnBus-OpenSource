package kr.yeaan.bus.algorithm

object RefreshPolicy {
    fun intervalMillis(distanceMeters: Double?, etaSeconds: Int?, speedMps: Double = 0.0, failures: Int = 0): Long {
        if (failures > 0) return (30_000L * (1L shl failures.coerceIn(0, 3))).coerceAtMost(120_000L)
        val distance = distanceMeters?.takeIf { it.isFinite() && it >= 0 } ?: Double.MAX_VALUE
        val eta = etaSeconds?.takeIf { it >= 0 } ?: Int.MAX_VALUE
        val projected = distance - speedMps.coerceIn(0.0, 35.0) * 10
        return when {
            distance <= 120 || eta <= 35 -> 3_000
            projected <= 500 || eta <= 90 -> 5_000
            projected <= 2000 || eta <= 300 -> 10_000
            else -> 30_000
        }
    }
}
