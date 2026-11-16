package kr.yeaan.bus.data.remote

import com.google.gson.JsonObject
import kr.yeaan.bus.data.model.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.net.URLDecoder

class TagoSource(private val api: TagoApi, key: String) {
    // Decode percent escapes once, preserving literal '+' in a Decoding key.
    // Retrofit then performs the single required URL encoding at the transport boundary.
    private val key = key.trim().let { value ->
        if ('%' in value) try { URLDecoder.decode(value.replace("+", "%2B"), "UTF-8") }
        catch (_: IllegalArgumentException) { "" } else value
    }
    private val cityCodes = mutableMapOf<City, String>()
    private val cityLock = Mutex()
    private fun params(vararg values: Pair<String, String>): Map<String, String> {
        if (key.isBlank() || key == "api키 삽입") throw ApiFailure("예천 조회와 TAGO 대체 조회에는 API 키 설정이 필요합니다.")
        return mapOf("serviceKey" to key, "_type" to "json", "numOfRows" to "200", "pageNo" to "1") + values
    }
    private suspend fun cityCode(city: City): String = cityLock.withLock {
        cityCodes[city] ?: TagoParser.items(request { api.cities(params()) }).firstOrNull {
            it.text("cityname").contains(city.label)
        }?.text("citycode")?.takeIf(String::isNotBlank)?.also { cityCodes[city] = it }
        ?: throw ApiFailure("TAGO에서 ${city.label} 지역의 제공 도시코드를 찾지 못했습니다.")
    }
    private suspend fun request(call: suspend () -> JsonObject): JsonObject = try {
        call()
    } catch (error: HttpException) {
        // Gateway authentication failures bypass Retrofit's success-body converter.
        // Only expose a known error code, never the body, URL, or credential.
        val body = error.response()?.errorBody()?.use { it.source().readUtf8( it.contentLength().takeIf { size -> size in 0..65_536 } ?: 0) }.orEmpty()
        throw TagoErrors.httpFailure(error.code(), body)
    }
    private suspend fun pages(query: Map<String, String>, request: suspend (Map<String, String>) -> JsonObject): List<JsonObject> {
        val all = mutableListOf<JsonObject>()
        for (page in 1..100) {
            val root = this.request { request(query + ("pageNo" to page.toString())) }
            val rows = TagoParser.items(root)
            all += rows
            if (rows.isEmpty() || all.size >= TagoParser.total(root) || rows.size < 200) return all
        }
        throw ApiFailure("조회 결과가 너무 많습니다. 검색어를 더 입력해 주세요.")
    }
    suspend fun stops(city: City, keyword: String): List<Stop> {
        val query = params("cityCode" to cityCode(city), "nodeNm" to keyword)
        return pages(query, api::stops).mapNotNull { stop(it, city) }.distinctBy { it.key }
    }
    private fun stop(row: JsonObject, city: City): Stop? {
        val point = Point(row.number("gpslati") ?: return null, row.number("gpslong") ?: return null)
        return if (row.text("nodeid").isBlank() || !point.valid) null
        else Stop(row.text("nodeid"), row.text("nodenm"), row.text("nodeno"), point, city, Provider.TAGO)
    }
    suspend fun routes(city: City, keyword: String): List<Route> =
        pages(params("cityCode" to cityCode(city), "routeNo" to keyword), api::routes).map {
            Route(it.text("routeid"), it.text("routeno"), it.text("startnodenm"), it.text("endnodenm"), city, Provider.TAGO)
        }.filter { it.id.isNotBlank() }.distinctBy { it.key }

    suspend fun arrivals(stop: Stop): List<Arrival> =
        pages(params("cityCode" to cityCode(stop.city), "nodeId" to stop.id), api::arrivals).mapIndexed { index, row ->
            val route = Route(row.text("routeid"), row.text("routeno"), "", "", stop.city, Provider.TAGO)
            Arrival("${route.id}:$index", route, apiSeconds = row.integer("arrtime")?.takeIf { it >= 0 },
                stopsAway = row.integer("arrprevstationcnt")?.takeIf { it >= 0 })
        }.filter { it.route.id.isNotBlank() }

    suspend fun routeStops(route: Route): List<Pair<Int, Stop>> =
        pages(params("cityCode" to cityCode(route.city), "routeId" to route.id), api::routeStops)
            .mapNotNull { row -> stop(row, route.city)?.let { (row.integer("nodeord") ?: 0) to it } }.sortedBy { it.first }

    suspend fun locations(route: Route, now: Long): List<Pair<Int?, BusLocation>> =
        pages(params("cityCode" to cityCode(route.city), "routeId" to route.id), api::locations).mapNotNull {
            val point = Point(it.number("gpslati") ?: return@mapNotNull null, it.number("gpslong") ?: return@mapNotNull null)
            val plate = it.text("vehicleno")
            if (plate.isBlank() || !point.valid) return@mapNotNull null
            it.integer("nodeord") to BusLocation(plate, route.id, route.number, point, now, nextStop = it.text("nodenm"), provider = Provider.TAGO)
        }
}
