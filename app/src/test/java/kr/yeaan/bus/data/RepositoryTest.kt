package kr.yeaan.bus.data

import com.google.gson.JsonArray
import com.google.gson.Gson
import com.google.gson.JsonParser
import kr.yeaan.bus.data.local.*
import kr.yeaan.bus.data.model.*
import kr.yeaan.bus.data.remote.*
import kr.yeaan.bus.data.repository.BusRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException

class MemoryDao : BusDao {
    val stops = MutableStateFlow<Map<String, SavedStopEntity>>(emptyMap())
    val cache = mutableMapOf<String, CacheEntity>()
    val positions = mutableMapOf<String, PositionEntity>()
    override fun favorites() = stops.map { it.values.filter { row -> row.favorite } }
    override fun recent() = stops.map { it.values.filter { row -> row.viewedAt > 0 }.sortedByDescending { row -> row.viewedAt }.take(12) }
    override suspend fun saved(key: String) = stops.value[key]
    override suspend fun save(entity: SavedStopEntity) { stops.value += entity.key to entity }
    override suspend fun cache(key: String) = cache[key]
    override suspend fun cache(entity: CacheEntity) { cache[entity.key] = entity }
    override suspend fun positions(entities: List<PositionEntity>) { entities.forEach { positions[it.key] = it } }
    override suspend fun positionsAfter(after: Long) = positions.values.filter { it.observedAt > after }
    override suspend fun pruneCache(before: Long) { cache.entries.removeAll { it.value.savedAt < before } }
    override suspend fun prunePositions(before: Long) { positions.entries.removeAll { it.value.observedAt < before } }
    override suspend fun pruneHistory() {}
}
class FakeBis : AndongApi {
    var calls = 0
    var failure: Exception? = null
    var stopRows = JsonArray()
    var stopFailure: Exception? = null
    override suspend fun stops(): JsonArray { stopFailure?.let { throw it }; return stopRows }
    override suspend fun routes() = JsonArray()
    override suspend fun arrivals(stopId: String, locale: String): JsonArray {
        calls++; failure?.let { throw it }; return JsonArray()
    }
    override suspend fun locations() = JsonArray()
    override suspend fun routeStops(route: String) = JsonArray()
    override suspend fun vertices(route: String) = JsonArray()
}
class RepositoryTest {
    private val stop = Stop("bis-id", "터미널", "540533", Point(36.57349,128.67566), City.ANDONG, Provider.BIS)
    private fun tago(server: MockWebServer, key: String = "test"): TagoSource {
        val api = Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(TagoApi::class.java)
        return TagoSource(api, key)
    }
    private fun bisWithStops() = FakeBis().apply {
        stopRows = JsonParser.parseString("""[{"stopId":"shared","stopName":"터미널 앞","serviceId":"100","lat":36.5,"lng":128.7}]""").asJsonArray
    }
    @Test fun unifiedSearchPreservesBothRegionsAndRanksExactMatchFirst() = runTest {
        MockWebServer().use { server ->
            val yecheon = Stop("shared", "터미널", "200", Point(36.6, 128.5), City.YECHEON, Provider.TAGO)
            val dao = MemoryDao().apply {
                cache(CacheEntity("search:stops:YECHEON:터미널", Gson().toJson(listOf(yecheon)), 100_000))
            }
            val result = BusRepository(AndongSource(bisWithStops()), tago(server), dao, clock = { 100_000 }).searchStops("터미널")
            assertEquals(listOf(City.YECHEON, City.ANDONG), result.items.map { it.city })
            assertEquals(2, result.items.map { it.key }.distinct().size)
            assertTrue(result.warnings.isEmpty())
        }
    }
    @Test fun unifiedRouteSearchKeepsSameNumberFromDifferentRegions() = runTest {
        MockWebServer().use { server ->
            val dao = MemoryDao().apply {
                cache(CacheEntity("bis:routes", """[{"routeId":"shared","routeNum":"101","stStationNm":"안동역","edStationNm":"시청"}]""", 100_000))
                cache(CacheEntity("search:routes:YECHEON:101", Gson().toJson(listOf(Route("shared", "101", "예천역", "터미널", City.YECHEON, Provider.TAGO))), 100_000))
            }
            val result = BusRepository(AndongSource(FakeBis()), tago(server), dao, clock = { 100_000 }).searchRoutes("101")
            assertEquals(setOf(City.ANDONG, City.YECHEON), result.items.map { it.city }.toSet())
            assertEquals(2, result.items.size)
            assertTrue(result.warnings.isEmpty())
        }
    }
    @Test fun missingTagoKeyDoesNotHideBisSearchResults() = runTest {
        MockWebServer().use { server ->
            val result = BusRepository(AndongSource(bisWithStops()), tago(server, ""), MemoryDao()).searchStops("터미널")
            assertEquals(1, result.items.size)
            assertEquals(City.ANDONG, result.items.single().city)
            assertTrue(result.warnings.single().startsWith("예천 조회 불가"))
            assertEquals(0, server.requestCount)
        }
    }
    @Test fun bothRegionsFailWithExplicitWarningsInsteadOfNoResults() = runTest {
        MockWebServer().use { server ->
            val result = BusRepository(AndongSource(FakeBis()), tago(server, ""), MemoryDao()).searchStops("터미널")
            assertTrue(result.items.isEmpty())
            assertEquals(2, result.warnings.size)
        }
    }
    @Test fun unifiedNearbyUsesDistanceAcrossRegionalBoundary() = runTest {
        MockWebServer().use { server ->
            val yecheon = Stop("near", "가까운 정류장", "200", Point(36.501, 128.7), City.YECHEON, Provider.TAGO)
            val dao = MemoryDao().apply {
                cache(CacheEntity("tago:all:YECHEON", Gson().toJson(listOf(yecheon)), 100_000))
            }
            val result = BusRepository(AndongSource(bisWithStops()), tago(server), dao, clock = { 100_000 }).nearby(Point(36.501, 128.7))
            assertEquals(listOf(City.YECHEON, City.ANDONG), result.items.map { it.city })
            assertTrue(result.warnings.isEmpty())
        }
    }
    @Test fun unifiedSearchPropagatesCancellation() = runTest {
        MockWebServer().use { server ->
            val bis = FakeBis().apply { stopFailure = CancellationException("query changed") }
            val repository = BusRepository(AndongSource(bis), tago(server, ""), MemoryDao())
            try { repository.searchStops("역"); fail("Expected cancellation") } catch (_: CancellationException) {}
            assertEquals(0, server.requestCount)
        }
    }
    @Test fun successfulEmptyBisDoesNotTriggerFallbackAndCallsAreDeduplicated() = runTest {
        MockWebServer().use { server ->
            val bis = FakeBis()
            val repository = BusRepository(AndongSource(bis), tago(server), MemoryDao(), clock = { 100_000 })
            assertTrue(repository.refresh(stop).arrivals.isEmpty())
            repository.refresh(stop)
            assertEquals(1, bis.calls)
            assertEquals(0, server.requestCount)
        }
    }
    @Test fun failedNetworkReturnsExplicitlyStaleCache() = runTest {
        MockWebServer().use { server ->
            var now = 100_000L
            val bis = FakeBis()
            val repository = BusRepository(AndongSource(bis), tago(server, ""), MemoryDao(), clock = { now })
            repository.refresh(stop)
            now += 5000
            bis.failure = IOException("offline")
            val old = repository.refresh(stop)
            assertTrue(old.cached)
            assertEquals(100_000L, old.fetchedAt)
            assertNotNull(old.message)
        }
    }
    @Test fun cancellationDoesNotTriggerFallback() = runTest {
        MockWebServer().use { server ->
            val bis = FakeBis().apply { failure = CancellationException("background") }
            val repository = BusRepository(AndongSource(bis), tago(server), MemoryDao())
            try { repository.refresh(stop); fail("Expected cancellation") } catch (_: CancellationException) {}
            assertEquals(0, server.requestCount)
        }
    }
    @Test fun fallbackMapsRealNodeIdInsteadOfInventingIt() = runTest {
        MockWebServer().use { server ->
            fun body(content: String) = MockResponse().setBody("""{"response":{"header":{"resultCode":"00"},"body":{"items":{"item":$content},"totalCount":1}}}""")
            server.enqueue(body("""{"citycode":37040,"cityname":"안동시"}"""))
            server.enqueue(body("""{"nodeid":"ADB_verified","nodenm":"터미널","nodeno":"540533","gpslati":36.57349,"gpslong":128.67566}"""))
            server.enqueue(MockResponse().setBody("""{"response":{"header":{"resultCode":"00"},"body":{"items":"","totalCount":0}}}"""))
            val bis = FakeBis().apply { failure = IOException("BIS down") }
            val result = BusRepository(AndongSource(bis), tago(server), MemoryDao()).refresh(stop)
            assertEquals(Provider.TAGO, result.provider)
            server.takeRequest(); server.takeRequest()
            assertEquals("ADB_verified", server.takeRequest().requestUrl!!.queryParameter("nodeId"))
        }
    }
    @Test fun duplicateBisDirectoryEntriesDoNotCrashSearchLists() = runTest {
        MockWebServer().use { server ->
            val bis = FakeBis().apply {
                stopRows = JsonParser.parseString("""[{"stopId":1,"stopName":"역","serviceId":"100","lat":36.5,"lng":128.7},{"stopId":1,"stopName":"역","serviceId":"100","lat":36.5,"lng":128.7}]""").asJsonArray
            }
            val repository = BusRepository(AndongSource(bis), tago(server), MemoryDao())
            assertEquals(1, repository.searchStops(City.ANDONG, "역").size)
        }
    }
    @Test fun favoriteSurvivesRecentView() = runTest {
        MockWebServer().use { server ->
            val dao = MemoryDao()
            val repository = BusRepository(AndongSource(FakeBis()), tago(server), dao, clock = { 100_000 })
            repository.toggleFavorite(stop)
            repository.record(stop)
            assertTrue(dao.saved(stop.key)!!.favorite)
            assertEquals(100_000L, dao.saved(stop.key)!!.viewedAt)
            repository.toggleFavorite(stop)
            assertFalse(dao.saved(stop.key)!!.favorite)
        }
    }
}
