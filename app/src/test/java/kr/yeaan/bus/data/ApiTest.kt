package kr.yeaan.bus.data

import com.google.gson.JsonParser
import kr.yeaan.bus.data.remote.*
import kr.yeaan.bus.data.model.*
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ApiTest {
    private fun json(value: String) = JsonParser.parseString(value).asJsonObject
    @Test fun tagoAcceptsSingletonArrayAndEmpty() {
        val head = "\"header\":{\"resultCode\":\"00\"},\"body\":"
        assertEquals(1, TagoParser.items(json("{\"response\":{$head{\"items\":{\"item\":{\"nodeid\":\"a\"}}}}}")).size)
        assertEquals(2, TagoParser.items(json("{\"response\":{$head{\"items\":{\"item\":[{},{}]}}}}")).size)
        assertTrue(TagoParser.items(json("{\"response\":{$head{\"items\":\"\"}}}")).isEmpty())
    }
    @Test fun apiErrorDoesNotBecomeEmptySuccess() {
        assertThrows(ApiFailure::class.java) { TagoParser.items(json("{\"response\":{\"header\":{\"resultCode\":\"30\"}}}")) }
        assertThrows(ApiFailure::class.java) { TagoParser.items(json("{}")) }
    }
    @Test fun missingKeyFailsBeforeNetworkCall() = runTest {
        MockWebServer().use { server ->
            val api = Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(TagoApi::class.java)
            try { TagoSource(api, "").stops(City.YECHEON, "터미널"); fail("Expected key error") } catch (_: ApiFailure) {}
            assertEquals(0, server.requestCount)
        }
    }
    @Test fun tagoKeyIsEncodedExactlyOnceAndCityIsDiscovered() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"response":{"header":{"resultCode":"00"},"body":{"items":{"item":{"citycode":37300,"cityname":"예천군"}},"totalCount":1}}}"""))
            server.enqueue(MockResponse().setBody("""{"response":{"header":{"resultCode":"00"},"body":{"items":"","totalCount":0}}}"""))
            val api = Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(TagoApi::class.java)
            assertTrue(TagoSource(api, "a+b/c=").stops(City.YECHEON, "터미널").isEmpty())
            val first = server.takeRequest()
            assertEquals("a+b/c=", first.requestUrl!!.queryParameter("serviceKey"))
            val second = server.takeRequest()
            assertEquals("37300", second.requestUrl!!.queryParameter("cityCode"))
            assertEquals("터미널", second.requestUrl!!.queryParameter("nodeNm"))
        }
    }
    @Test fun bisGpsUsesKoreanTimeAndRejectsOldUpdates() {
        MockWebServer().use { server ->
            val api = Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(AndongApi::class.java)
            val source = AndongSource(api)
            val data = JsonParser.parseString("""[{"carNumber":"test","latitude":36.5,"longitude":128.7,"speed":36,"angle":90,"upTime":"2026-09-08 09:00:00"}]""").asJsonArray
            val time = java.time.Instant.parse("2026-09-08T00:00:00Z").toEpochMilli()
            val fix = source.locations(data, time).single()
            assertEquals(time, fix.observedAt)
            assertEquals(10.0, fix.speedMps!!, 0.0)
            assertTrue(source.locations(data, time + 180_000).isEmpty())
        }
    }
    @Test fun bisTimetableIsNeverShownAsArrivingNow() {
        MockWebServer().use { server ->
            val api = Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(AndongApi::class.java)
            val rows = JsonParser.parseString("""[{"routeId":1,"routeName":"순환2","plateNo":"A","provideType":"10시 00분 출발 예정"}]""").asJsonArray
            val fix = BusLocation("A", "", "순환2", Point(36.5,128.7), 0, provider = Provider.BIS)
            val arrival = AndongSource(api).arrivals(rows, listOf(fix)).single()
            assertNull(arrival.apiSeconds)
            assertNull(arrival.location)
        }
    }
    @Test fun bisDoesNotMatchOppositeDirectionByRouteNumber() {
        MockWebServer().use { server ->
            val api = Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(AndongApi::class.java)
            val source = AndongSource(api)
            val rows = JsonParser.parseString("""[{"routeId":1,"routeName":"110","plateNo":"A","provideType":"3분 20초","rstop":"2정류장 전","via":"출발 -> 도착"}]""").asJsonArray
            val wrong = BusLocation("B", "", "110", Point(36.5,128.7), 0, provider = Provider.BIS)
            val arrival = source.arrivals(rows, listOf(wrong)).single()
            assertNull(arrival.location)
            assertEquals(200, arrival.apiSeconds)
            assertEquals(2, arrival.stopsAway)
            assertEquals("도착", arrival.route.to)
        }
    }
}
