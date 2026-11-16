package kr.yeaan.bus.data.repository

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kr.yeaan.bus.algorithm.Geo
import kr.yeaan.bus.data.local.*
import kr.yeaan.bus.data.model.*
import kr.yeaan.bus.data.remote.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BusRepository(
    private val bis: AndongSource, private val tago: TagoSource,
    private val dao: BusDao, private val gson: Gson = Gson(),
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val refreshLock = Mutex()
    private val savedLock = Mutex()
    private val favoriteLock = Mutex()
    private val favoriteMemory = mutableMapOf<String, Snapshot>()
    private val snapshotMemory = mutableMapOf<String, Snapshot>()
    private val routeMemory = mutableMapOf<String, RouteDetail>()
    private val tagoRouteStops = mutableMapOf<String, List<Pair<Int, Stop>>>()
    val favorites = dao.favorites().map { rows -> rows.mapNotNull { decode<Stop>(it.payload) } }
    val recent = dao.recent().map { rows -> rows.mapNotNull { decode<Stop>(it.payload) } }

    private inline fun <reified T> decode(json: String): T? = runCatching {
        gson.fromJson<T>(json, object : TypeToken<T>() {}.type)
    }.getOrNull()
    private suspend inline fun <T> attempt(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }

    private suspend fun bisDirectory(routes: Boolean): String {
        val key = if (routes) "bis:routes" else "bis:stops"
        val cached = dao.cache(key)
        if (cached != null && clock() - cached.savedAt < 86_400_000) return cached.payload
        return attempt {
            val data = if (routes) bis.api.routes() else bis.api.stops()
            if (data.size() == 0) throw ApiFailure("안동 BIS 검색 데이터가 비어 있습니다.")
            val json = data.toString()
            dao.cache(CacheEntity(key, json, clock()))
            json
        }.getOrElse { error ->
            cached?.takeIf { clock() - it.savedAt < 7 * 86_400_000L }?.payload ?: throw error
        }
    }
    private suspend fun <T> acrossRegions(request: suspend (City) -> List<T>): RegionalResult<T> = coroutineScope {
        val results = City.entries.map { city -> async { city to attempt { request(city) } } }.awaitAll()
        RegionalResult(
            items = results.flatMap { it.second.getOrDefault(emptyList()) },
            warnings = results.mapNotNull { (city, result) ->
                result.exceptionOrNull()?.let { "${city.label} 조회 불가 · ${userMessage(it)}" }
            }
        )
    }
    suspend fun searchStops(query: String): RegionalResult<Stop> {
        if (query.isBlank()) return RegionalResult(emptyList())
        val result = acrossRegions { searchStops(it, query.trim()) }
        return result.copy(items = result.items.distinctBy { it.key }
            .sortedWith(compareBy<Stop> { it.name != query.trim() }.thenBy { it.name }.thenBy { it.city.label }.thenBy { it.code }))
    }
    suspend fun searchRoutes(query: String): RegionalResult<Route> {
        if (query.isBlank()) return RegionalResult(emptyList())
        val result = acrossRegions { searchRoutes(it, query.trim()) }
        return result.copy(items = result.items.distinctBy { it.key }
            .sortedWith(compareBy<Route> { it.number != query.trim() }.thenBy { it.number.toIntOrNull() ?: Int.MAX_VALUE }
                .thenBy { it.number }.thenBy { it.city.label }.thenBy { it.from }))
    }
    suspend fun nearby(point: Point): RegionalResult<Stop> {
        val result = acrossRegions { nearby(point, it) }
        return result.copy(items = result.items.distinctBy { it.key }.sortedBy { Geo.distance(point, it.point) }.take(12))
    }
    suspend fun searchStops(city: City, query: String): List<Stop> {
        if (query.isBlank()) return emptyList()
        if (city == City.ANDONG) {
            val primary = attempt { bis.stops(JsonParser.parseString(bisDirectory(false)).asJsonArray)
                .filter { it.name.contains(query, true) || it.code.contains(query) }.distinctBy { it.key } }
            if (primary.isSuccess) return primary.getOrThrow()
        }
        val key = "search:stops:${city.name}:$query"
        return cachedStops(key) { tago.stops(city, query) }
    }
    private suspend fun cachedStops(key: String, request: suspend () -> List<Stop>): List<Stop> {
        val cached = dao.cache(key)
        if (cached != null && clock() - cached.savedAt < 86_400_000) decode<List<Stop>>(cached.payload)?.let { return it }
        return attempt {
            request().also { dao.cache(CacheEntity(key, gson.toJson(it), clock())) }
        }.getOrElse { error -> cached?.takeIf { clock() - it.savedAt < 7 * 86_400_000L }?.let { decode<List<Stop>>(it.payload) } ?: throw error }
    }
    suspend fun searchRoutes(city: City, query: String): List<Route> {
        if (query.isBlank()) return emptyList()
        if (city == City.ANDONG) {
            val primary = attempt { bis.routes(JsonParser.parseString(bisDirectory(true)).asJsonArray)
                .filter { it.number.contains(query, true) || it.from.contains(query) || it.to.contains(query) } }
            if (primary.isSuccess) return primary.getOrThrow()
        }
        val key = "search:routes:${city.name}:$query"
        val cached = dao.cache(key)
        if (cached != null && clock() - cached.savedAt < 86_400_000) decode<List<Route>>(cached.payload)?.let { return it }
        return attempt { tago.routes(city, query).also { dao.cache(CacheEntity(key, gson.toJson(it), clock())) } }
            .getOrElse { error -> cached?.takeIf { clock() - it.savedAt < 7 * 86_400_000L }?.let { decode<List<Route>>(it.payload) } ?: throw error }
    }
    suspend fun mapStops(): RegionalResult<Stop> {
        val result = acrossRegions { allStops(it) }
        return result.copy(items = result.items.filter { it.point.valid }.distinctBy { it.key })
    }
    private suspend fun allStops(city: City): List<Stop> =
        if (city == City.ANDONG) attempt {
            bis.stops(JsonParser.parseString(bisDirectory(false)).asJsonArray)
        }.getOrElse { cachedStops("tago:all:${city.name}") { tago.stops(city, "") } }
        else cachedStops("tago:all:${city.name}") { tago.stops(city, "") }

    suspend fun nearby(point: Point, city: City): List<Stop> =
        allStops(city).distinctBy { it.key }.filter { Geo.distance(point, it.point) <= 3000 }
            .sortedBy { Geo.distance(point, it.point) }.take(12)
    suspend fun record(stop: Stop) = savedLock.withLock {
        val old = dao.saved(stop.key)
        dao.save(SavedStopEntity(stop.key, gson.toJson(stop), old?.favorite ?: false, clock()))
        dao.pruneHistory()
    }
    suspend fun toggleFavorite(stop: Stop) = savedLock.withLock {
        val old = dao.saved(stop.key)
        if (old?.favorite != true && dao.favorites().first().size >= 3)
            throw ApiFailure("즐겨찾기는 최대 3곳까지 등록할 수 있어요. 기존 정류장의 별을 해제한 뒤 추가해 주세요.")
        dao.save(SavedStopEntity(stop.key, gson.toJson(stop), !(old?.favorite ?: false), old?.viewedAt ?: 0))
    }

    /** Match code + GPS first; otherwise require a unique, very close same-name stop.
     * Never synthesize a TAGO node ID from an Andong ID (opposite directions can share names). */
    private fun matchStop(source: Stop, candidates: List<Stop>): Stop? {
        val near = candidates.distinctBy { it.key }.filter { Geo.distance(source.point, it.point) <= 45 }
        return near.singleOrNull { source.code.isNotBlank() && source.code == it.code }
            ?: near.singleOrNull { it.name.replace(" ", "") == source.name.replace(" ", "") }
    }
    suspend fun refresh(stop: Stop): Snapshot = refreshLock.withLock {
        snapshotMemory[stop.key]?.takeIf { clock() - it.fetchedAt < 3000 }?.let { return@withLock it }
        val result = attempt {
            val value = if (stop.city == City.ANDONG) {
                val primary = attempt {
                    val bisStop = if (stop.provider == Provider.BIS) stop
                    else matchStop(stop, bis.stops(JsonParser.parseString(bisDirectory(false)).asJsonArray))
                        ?: throw ApiFailure("안동 BIS 정류장 연결 정보를 찾지 못했습니다.")
                    bisSnapshot(bisStop).copy(stop = stop)
                }
                primary.getOrElse {
                    val fallbackStop = if (stop.provider == Provider.TAGO) stop
                    else matchStop(stop, tago.stops(stop.city, stop.name))
                        ?: throw ApiFailure("안동 BIS 연결 실패 · TAGO에서 같은 정류장을 확인하지 못했습니다.")
                    tagoSnapshot(fallbackStop).copy(stop = stop, message = "안동 BIS 연결이 원활하지 않아 TAGO로 조회했습니다.")
                }
            } else tagoSnapshot(stop)
            dao.cache(CacheEntity("arrival:${stop.key}", gson.toJson(value), value.fetchedAt))
            val locations = value.arrivals.mapNotNull { it.location }.distinctBy { it.key }
            if (locations.isNotEmpty()) dao.positions(locations.map { PositionEntity(it.key, gson.toJson(it), it.observedAt) })
            dao.prunePositions(clock() - 86_400_000)
            dao.pruneCache(clock() - 7 * 86_400_000L)
            snapshotMemory[stop.key] = value
            value
        }
        result.getOrElse { error ->
            val old = dao.cache("arrival:${stop.key}")?.let { decode<Snapshot>(it.payload) }
            if (old != null && clock() - old.fetchedAt < 600_000)
                old.copy(cached = true, message = userMessage(error) + " 마지막 조회 정보를 표시합니다.")
            else throw error
        }
    }
    private suspend fun bisSnapshot(stop: Stop): Snapshot {
        val rows = bis.api.arrivals(stop.id)
        val hasVehicle = rows.objects().any { it.text("plateNo").isNotBlank() }
        val gps = attempt { if (hasVehicle) bis.locations(bis.api.locations(), clock()) else emptyList() }
        return Snapshot(stop, bis.arrivals(rows, gps.getOrDefault(emptyList())), clock(), Provider.BIS,
            message = if (gps.isFailure) "차량 GPS 연결 지연 · 도착 안내는 계속 확인할 수 있습니다." else null)
    }
    private suspend fun tagoSnapshot(stop: Stop): Snapshot {
        val arrivals = tago.arrivals(stop)
        val tracked = arrivals.filter { it.apiSeconds != null }.sortedBy { it.apiSeconds }.map { it.route }.distinctBy { it.key }.take(4)
        val positions = mutableMapOf<String, BusLocation>()
        for (route in tracked) {
            attempt {
                val stops = tagoRouteStops[route.key] ?: tago.routeStops(route).also { tagoRouteStops[route.key] = it }
                val target = stops.singleOrNull { it.second.id == stop.id }?.first ?: return@attempt
                val gps = tago.locations(route, clock())
                for (arrival in arrivals.filter { it.route.id == route.id }) {
                    val count = arrival.stopsAway ?: continue
                    val position = gps.singleOrNull { it.first == target - count }?.second ?: continue
                    positions[arrival.id] = position
                }
            }
        }
        return Snapshot(stop, arrivals.map { it.copy(location = positions[it.id]) }, clock(), Provider.TAGO)
    }
    suspend fun routeDetail(route: Route): RouteDetail {
        routeMemory[route.key]?.let { return it }
        val key = "route:${route.key}"
        val cached = dao.cache(key)
        if (cached != null && clock() - cached.savedAt < 86_400_000) decode<RouteDetail>(cached.payload)?.let {
            routeMemory[route.key] = it; return it
        }
        val result = if (route.provider == Provider.BIS) {
            val stops = bis.stops(bis.api.routeStops(route.id))
            val path = attempt { bis.vertices(bis.api.vertices(route.id)) }.getOrDefault(emptyList())
            RouteDetail(route, stops, path)
        } else {
            val stops = tagoRouteStops[route.key] ?: tago.routeStops(route).also { tagoRouteStops[route.key] = it }
            // Stop coordinates are not road geometry: do not animate along straight stop-to-stop segments.
            RouteDetail(route, stops.map { it.second }, emptyList())
        }
        routeMemory[route.key] = result
        dao.cache(CacheEntity(key, gson.toJson(result), clock()))
        return result
    }
    /** Arrival-only requests: the dashboard never downloads vehicle GPS or route geometry. */
    suspend fun favoriteArrivals(): List<FavoriteArrival> = favoriteLock.withLock {
        favorites.first().take(3).map { stop ->
            val key = "favorite-arrival:${stop.key}"
            val cached = favoriteMemory[stop.key] ?: dao.cache(key)?.let { decode<Snapshot>(it.payload) }
            if (cached != null && clock() - cached.fetchedAt < 30_000) FavoriteArrival(stop, cached)
            else {
                val result = attempt {
                    val rows = if (stop.provider == Provider.BIS) bis.arrivals(bis.api.arrivals(stop.id), emptyList())
                        else tago.arrivals(stop)
                    Snapshot(stop, rows, clock(), stop.provider).also {
                        favoriteMemory[stop.key] = it
                        dao.cache(CacheEntity(key, gson.toJson(it), it.fetchedAt))
                    }
                }
                result.fold({ FavoriteArrival(stop, it) }, { error ->
                    FavoriteArrival(stop, cached?.copy(cached = true), userMessage(error))
                })
            }
        }
    }
    suspend fun lastLocations(): List<BusLocation> = dao.positionsAfter(clock() - 60_000).mapNotNull { decode<BusLocation>(it.payload) }
}
fun userMessage(error: Throwable): String = when (error) {
    is ApiFailure -> error.message ?: "버스 정보를 불러올 수 없습니다."
    is java.net.UnknownHostException -> "인터넷 연결을 확인해 주세요."
    is java.net.SocketTimeoutException -> "응답이 지연되고 있습니다. 잠시 후 다시 확인해 주세요."
    is retrofit2.HttpException -> "버스 정보 서버에 연결할 수 없습니다. (${error.code()})"
    else -> "버스 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요."
}
