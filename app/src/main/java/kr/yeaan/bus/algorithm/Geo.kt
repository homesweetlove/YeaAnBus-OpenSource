package kr.yeaan.bus.algorithm

import kr.yeaan.bus.data.model.Point
import kotlin.math.*

object Geo {
    private const val R = 6371000.0
    fun distance(a: Point, b: Point): Double {
        val p = Math.toRadians(b.lat - a.lat)
        val l = Math.toRadians(b.lon - a.lon)
        val h = sin(p / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(l / 2).pow(2)
        return 2 * R * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
    fun interpolate(a: Point, b: Point, fraction: Double): Point {
        val t = fraction.coerceIn(0.0, 1.0)
        return Point(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
    }
    fun move(a: Point, north: Double, east: Double) = Point(
        a.lat + Math.toDegrees(north / R),
        a.lon + Math.toDegrees(east / (R * cos(Math.toRadians(a.lat)).coerceAtLeast(0.01)))
    )
    fun bearing(a: Point, b: Point): Double = atan2(
        (b.lon - a.lon) * cos(Math.toRadians(a.lat)), b.lat - a.lat
    )
}

/** Ordered road vertices. Off-route or ambiguous loop matches are deliberately rejected. */
class RoutePath(val points: List<Point>) {
    private val cumulative = DoubleArray(points.size).also { out ->
        for (i in 1 until points.size) out[i] = out[i - 1] + Geo.distance(points[i - 1], points[i])
    }
    data class Projection(val progress: Double, val error: Double)
    fun project(p: Point): Projection? {
        if (points.size < 2) return null
        var best: Projection? = null
        val candidates = mutableListOf<Projection>()
        for (i in 1 until points.size) {
            val a = points[i - 1]; val b = points[i]
            val scale = cos(Math.toRadians(p.lat))
            val dx = (b.lon - a.lon) * scale; val dy = b.lat - a.lat
            val d = dx * dx + dy * dy
            val t = if (d == 0.0) 0.0 else (((p.lon - a.lon) * scale * dx + (p.lat - a.lat) * dy) / d).coerceIn(0.0, 1.0)
            val error = Geo.distance(p, Geo.interpolate(a, b, t))
            val candidate = Projection(cumulative[i - 1] + (cumulative[i] - cumulative[i - 1]) * t, error)
            if (error <= 100) candidates += candidate
            if (best == null || error < best.error) best = candidate
        }
        val nearest = best?.takeIf { it.error <= 100 } ?: return null
        // At a loop/crossing there can be two equally close, widely separated route positions.
        if (candidates.any { abs(it.progress - nearest.progress) > 150 && it.error <= nearest.error + 8 }) return null
        return nearest
    }
    fun pointAt(progress: Double): Point? {
        if (points.size < 2) return null
        val s = progress.coerceIn(0.0, cumulative.last())
        val found = cumulative.binarySearch(s)
        val i = (if (found >= 0) found else -found - 1).coerceIn(1, points.lastIndex)
        val length = cumulative[i] - cumulative[i - 1]
        return Geo.interpolate(points[i - 1], points[i], if (length <= 0) 0.0 else (s - cumulative[i - 1]) / length)
    }
    fun remaining(bus: Point, stop: Point): Double? {
        val a = project(bus) ?: return null
        val b = project(stop) ?: return null
        return (b.progress - a.progress).takeIf { it >= 0 }
    }
}
