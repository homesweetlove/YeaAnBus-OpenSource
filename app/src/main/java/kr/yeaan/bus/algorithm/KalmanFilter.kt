package kr.yeaan.bus.algorithm

/** Scalar Kalman filter for GPS-derived velocity (m/s); variance uses squared m/s. */
class KalmanFilter(private val processNoise: Double = 1.5, private val measurementNoise: Double = 6.0) {
    private var estimate: Double? = null
    private var variance = 10.0
    fun update(measurement: Double, seconds: Double): Double {
        if (estimate == null) { estimate = measurement; return measurement }
        variance += processNoise * seconds.coerceIn(0.0, 60.0)
        val gain = variance / (variance + measurementNoise)
        estimate = estimate!! + gain * (measurement - estimate!!)
        variance *= 1 - gain
        return estimate!!
    }
    fun reset() { estimate = null; variance = 10.0 }
}
