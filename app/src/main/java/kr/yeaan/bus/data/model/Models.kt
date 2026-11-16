package kr.yeaan.bus.data.model

data class Point(val lat: Double, val lon: Double) {
    val valid: Boolean get() = lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0 && !(lat == 0.0 && lon == 0.0)
}
enum class City(val label: String) { ANDONG("안동"), YECHEON("예천") }
enum class Provider(val label: String) { BIS("안동 BIS"), TAGO("TAGO") }
data class Stop(
    val id: String, val name: String, val code: String,
    val point: Point, val city: City, val provider: Provider
) { val key: String get() = "${provider.name}:${city.name}:$id" }
data class Route(
    val id: String, val number: String, val from: String, val to: String,
    val city: City, val provider: Provider
) { val key: String get() = "${provider.name}:${city.name}:$id" }
data class BusLocation(
    val id: String, val routeId: String, val number: String, val point: Point,
    val observedAt: Long, val speedMps: Double? = null, val bearing: Double? = null,
    val nextStop: String = "", val provider: Provider
) { val key: String get() = "${provider.name}:$routeId:$id" }
data class Arrival(
    val id: String, val route: Route, val vehicleId: String? = null,
    val apiSeconds: Int? = null, val stopsAway: Int? = null,
    val location: BusLocation? = null, val status: String = ""
)
data class Snapshot(
    val stop: Stop, val arrivals: List<Arrival>, val fetchedAt: Long,
    val provider: Provider, val cached: Boolean = false, val message: String? = null
)
data class ArrivalDisplay(
    val arrival: Arrival, val seconds: Int?, val distanceMeters: Double?,
    val point: Point?, val speedMps: Double, val stale: Boolean
)
data class RouteDetail(val route: Route, val stops: List<Stop>, val path: List<Point>)

/** A failed region must not discard the other region's usable results. */
data class RegionalResult<T>(val items: List<T>, val warnings: List<String> = emptyList())

data class FavoriteArrival(val stop: Stop, val snapshot: Snapshot? = null, val error: String? = null)
