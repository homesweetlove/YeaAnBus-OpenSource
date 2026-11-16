package kr.yeaan.bus.algorithm

import kr.yeaan.bus.data.model.*
import org.junit.Assert.*
import org.junit.Test

class AlgorithmTest {
    private val t = 1_800_000_000_000L
    private val p = Point(36.57349, 128.67566)
    private fun fix(point: Point = p, time: Long = t, speed: Double? = 10.0) =
        BusLocation("test", "route", "110", point, time, speed, 90.0, provider = Provider.BIS)
    @Test fun distanceIsInMeters() { assertEquals(111.2, Geo.distance(Point(36.0, 128.0), Point(36.001, 128.0)), 1.0) }
    @Test fun emptyPredictorHasNoInventedPosition() { assertNull(BusPredictor().position(t)) }
    @Test fun predictionMovesUsingSpeedAndTime() {
        val predictor = BusPredictor()
        predictor.observe(fix(), t)
        assertEquals(50.0, Geo.distance(p, predictor.position(t + 5000)!!), 1.0)
    }
    @Test fun extrapolationIsBoundedDuringOutage() {
        val predictor = BusPredictor()
        predictor.observe(fix(), t)
        assertEquals(predictor.position(t + 12_000), predictor.position(t + 180_000))
        assertTrue(predictor.isStale(t + 61_000))
    }
    @Test fun stationaryBusDoesNotDrift() {
        val predictor = BusPredictor()
        predictor.observe(fix(speed = 0.0), t)
        assertEquals(p, predictor.position(t + 10_000))
    }
    @Test fun duplicateAndOutOfOrderGpsAreIgnored() {
        val predictor = BusPredictor()
        predictor.observe(fix(), t)
        predictor.observe(fix(Geo.move(p, 100.0, 100.0), t - 1000), t + 1000)
        assertEquals(50.0, Geo.distance(p, predictor.position(t + 5000)!!), 1.0)
    }
    @Test fun impossibleGpsJumpIsRejected() {
        val predictor = BusPredictor()
        predictor.observe(fix(), t)
        predictor.observe(fix(Point(37.5, 127.0), t + 3000), t + 3000)
        assertTrue(Geo.distance(p, predictor.position(t + 4000)!!) < 100)
    }
    @Test fun correctionStartsAtRenderedPositionThenConverges() {
        val predictor = BusPredictor()
        predictor.observe(fix(), t)
        val before = predictor.position(t + 5000)!!
        val real = Geo.move(p, 12.0, 45.0)
        predictor.observe(fix(real, t + 5000), t + 5000)
        assertEquals(0.0, Geo.distance(before, predictor.position(t + 5000)!!), .001)
        assertTrue(Geo.distance(Geo.move(real, 0.0, 15.0), predictor.position(t + 6500)!!) < 2.0)
    }
    @Test fun invalidPointDoesNotPoisonPrediction() {
        val predictor = BusPredictor()
        predictor.observe(fix(Point(Double.NaN, 128.0)), t)
        assertNull(predictor.position(t))
    }
    @Test fun roadPredictionFollowsCorner() {
        val path = RoutePath(listOf(p, Geo.move(p, 0.0, 50.0), Geo.move(p, 100.0, 50.0)))
        val predictor = BusPredictor()
        predictor.path = path
        predictor.observe(fix(), t)
        assertTrue(Geo.distance(Geo.move(p, 50.0, 50.0), predictor.position(t + 10_000)!!) < 2)
    }
    @Test fun roadRejectsOffRouteProjection() {
        val path = RoutePath(listOf(p, Geo.move(p, 1000.0, 0.0)))
        assertNull(path.project(Geo.move(p, 0.0, 500.0)))
        assertNull(path.remaining(Geo.move(p, 800.0, 0.0), p))
    }
    @Test fun refreshThresholdsAndSpeedLookahead() {
        assertEquals(30_000L, RefreshPolicy.intervalMillis(5000.0, 900))
        assertEquals(10_000L, RefreshPolicy.intervalMillis(1800.0, 400))
        assertEquals(5_000L, RefreshPolicy.intervalMillis(450.0, 100))
        assertEquals(3_000L, RefreshPolicy.intervalMillis(100.0, 30))
        assertEquals(5_000L, RefreshPolicy.intervalMillis(600.0, 200, 12.0))
        assertEquals(30_000L, RefreshPolicy.intervalMillis(null, null))
    }
    @Test fun failuresOverrideFastPolling() {
        assertEquals(60_000L, RefreshPolicy.intervalMillis(30.0, 5, failures = 1))
        assertEquals(120_000L, RefreshPolicy.intervalMillis(30.0, 5, failures = 20))
    }
    @Test fun estimatorHandlesStoppedBusAndMissingData() {
        val estimator = ArrivalEstimator()
        assertNull(estimator.seconds(null, null))
        assertEquals(180, estimator.seconds(null, null, apiSeconds = 180))
        assertTrue(estimator.seconds(500.0, 0.0)!! > estimator.seconds(500.0, 10.0)!!)
    }
    @Test fun dwellAndCorrectionAreBounded() {
        val estimator = ArrivalEstimator()
        assertTrue(estimator.seconds(800.0, 8.0, stopsAway = 5)!! > estimator.seconds(800.0, 8.0, stopsAway = 1)!!)
        assertTrue(estimator.seconds(10_000.0, 1.0, apiSeconds = 180)!! in 161..199)
    }
    @Test fun unknownHeadingDoesNotInventNorthboundMovement() {
        val predictor = BusPredictor()
        predictor.observe(fix().copy(bearing = null), t)
        assertEquals(p, predictor.position(t + 5000))
    }
    @Test fun loopCrossingIsNotAssignedToArbitraryRouteProgress() {
        val path = RoutePath(listOf(p, Geo.move(p, 1000.0, 0.0), Geo.move(p, 1000.0, 1000.0), p))
        assertNull(path.project(p))
    }
    @Test fun userLabelsDoNotImplyArrivalFromUnknownData() {
        assertEquals("도착 정보 없음", ArrivalText.time(null))
        assertEquals("정보 갱신 대기", ArrivalText.time(3, true))
        assertEquals("3분 후", ArrivalText.time(150))
        assertEquals("약 420m 남음", ArrivalText.distance(421.0))
    }
}
