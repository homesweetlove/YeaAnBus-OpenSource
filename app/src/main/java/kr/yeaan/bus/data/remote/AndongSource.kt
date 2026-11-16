package kr.yeaan.bus.data.remote

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kr.yeaan.bus.data.model.*
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AndongSource(val api: AndongApi) {
    fun stops(data: JsonArray): List<Stop> = data.objects().mapNotNull { row ->
        val point = Point(row.number("lat") ?: return@mapNotNull null, row.number("lng") ?: return@mapNotNull null)
        val id = row.text("stopId")
        if (!point.valid || id.isBlank()) null
        else Stop(id, row.text("stopName"), row.text("serviceId"), point, City.ANDONG, Provider.BIS)
    }
    fun routes(data: JsonArray): List<Route> = data.objects().mapNotNull {
        val id = it.text("routeId")
        if (id.isBlank()) null else Route(id, it.text("routeNum"), it.text("stStationNm"), it.text("edStationNm"), City.ANDONG, Provider.BIS)
    }.distinctBy { it.key }
    fun locations(data: JsonArray, now: Long): List<BusLocation> = data.objects().mapNotNull {
        val point = Point(it.number("latitude") ?: return@mapNotNull null, it.number("longitude") ?: return@mapNotNull null)
        val id = it.text("carNumber").ifBlank { it.text("carId").takeUnless { value -> value == "0" }.orEmpty() }
        val time = runCatching {
            LocalDateTime.parse(it.text("upTime"), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                .atZone(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli()
        }.getOrNull() ?: return@mapNotNull null
        if (!point.valid || id.isBlank() || time > now + 5000 || now - time > 120_000) return@mapNotNull null
        BusLocation(id, "", it.text("routeName"), point, time, it.number("speed")?.div(3.6),
            it.number("angle"), it.text("nextStopKorean"), Provider.BIS)
    }
    fun arrivals(rows: JsonArray, gps: List<BusLocation>): List<Arrival> = rows.objects().mapIndexedNotNull { index, row ->
        val routeId = row.text("routeId")
        if (routeId.isBlank()) return@mapIndexedNotNull null
        val via = row.text("via").split("->").map(String::trim)
        val route = Route(routeId, row.text("routeName"), via.firstOrNull().orEmpty(), via.lastOrNull().orEmpty(), City.ANDONG, Provider.BIS)
        val plate = row.text("plateNo")
        // Route number alone is ambiguous across directions. Match the actual license plate.
        val location = gps.singleOrNull { plate.isNotBlank() && it.id.replace(" ", "") == plate.replace(" ", "") }
            ?.copy(routeId = routeId, number = route.number)
        val status = row.text("provideType")
        val minutes = Regex("(\\d+)\\s*분").find(status)?.groupValues?.get(1)?.toIntOrNull()
        val seconds = Regex("(\\d+)\\s*초").find(status)?.groupValues?.get(1)?.toIntOrNull()
        // Timetables such as "10시 00분 출발 예정" are clock times, not a zero-minute ETA.
        val timetable = Regex("출발|운행.*종료|정보.*없|미운행|운행.*대기|[0-9]+\\s*시|[0-9]+:[0-9]{2}").containsMatchIn(status)
        val eta = when {
            timetable -> null
            "곧" in status || "잠시" in status || "진입" in status -> 25
            minutes != null -> minutes * 60 + (seconds ?: 0)
            seconds != null -> seconds
            else -> null
        }
        val stops = Regex("(\\d+)").find(row.text("rstop"))?.value?.toIntOrNull()
        Arrival("$routeId:$plate:$index", route, plate.takeIf(String::isNotBlank), eta, stops, location.takeUnless { timetable }, status)
    }
    fun vertices(rows: JsonArray): List<Point> = rows.objects().mapNotNull {
        val p = Point(it.number("lat") ?: return@mapNotNull null, it.number("lng") ?: return@mapNotNull null)
        p.takeIf(Point::valid)
    }
}
