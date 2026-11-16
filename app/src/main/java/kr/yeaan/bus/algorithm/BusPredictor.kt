package kr.yeaan.bus.algorithm

import kr.yeaan.bus.data.model.BusLocation
import kr.yeaan.bus.data.model.Point
import kotlin.math.*

/** One instance per provider/route/vehicle. Times are epoch millis, supplied by callers for testing. */
class BusPredictor {
    private var last: BusLocation? = null
    private var correctionFrom: Point? = null
    private var receivedAt = 0L
    private var heading: Double? = null
    private val speedFilter = KalmanFilter()
    var speedMps: Double = 0.0
        private set
    private var progress: Double? = null
    var path: RoutePath? = null
        set(value) {
            field = value
            progress = last?.let { value?.project(it.point)?.progress }
        }

    fun observe(sample: BusLocation, now: Long) {
        if (!sample.point.valid || sample.observedAt > now + 5000) return
        val previous = last
        if (previous != null && sample.observedAt <= previous.observedAt) return
        val rendered = position(now)
        val dt = previous?.let { (sample.observedAt - it.observedAt) / 1000.0 } ?: 0.0
        val moved = previous?.let { Geo.distance(it.point, sample.point) } ?: 0.0
        // Reject a single physically impossible GPS jump; retain the last credible fix.
        if (previous != null && dt in 0.1..60.0 && moved > max(120.0, dt * 45.0)) return
        val measured = sample.speedMps?.takeIf { it.isFinite() && it in 0.0..40.0 }
            ?: if (dt in 1.0..90.0) moved / dt else 0.0
        speedMps = if (measured < 0.8) { speedFilter.reset(); 0.0 }
            else speedFilter.update(measured.coerceIn(0.0, 35.0), dt).coerceIn(0.0, 35.0)
        heading = sample.bearing?.takeIf { it in 0.0..360.0 }?.let(Math::toRadians)
            ?: if (previous != null && moved > 5) Geo.bearing(previous.point, sample.point) else heading
        correctionFrom = rendered?.takeIf { Geo.distance(it, sample.point) < 500 }
        receivedAt = now
        last = sample
        progress = path?.project(sample.point)?.progress
    }

    fun position(now: Long): Point? {
        val fix = last ?: return null
        // Dead reckoning is bounded. Without a road shape stop at 12 s; on a road at 30 s.
        val horizon = if (path == null) 12.0 else 30.0
        val seconds = ((now - fix.observedAt) / 1000.0).coerceIn(0.0, horizon)
        val routeProgress = progress
        val predicted = if (routeProgress != null)
            path?.pointAt(routeProgress + seconds * speedMps) ?: fix.point
        else heading?.let { Geo.move(fix.point, cos(it) * seconds * speedMps, sin(it) * seconds * speedMps) } ?: fix.point
        val t = ((now - receivedAt) / 1500.0).coerceIn(0.0, 1.0)
        // Smoothstep has zero endpoint velocity, avoiding a hard jump at the next observation.
        return correctionFrom?.let { Geo.interpolate(it, predicted, t * t * (3 - 2 * t)) } ?: predicted
    }
    fun isStale(now: Long) = last?.let { now - it.observedAt > 60_000 } ?: true
}
