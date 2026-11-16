package kr.yeaan.bus.data.remote

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import retrofit2.http.*

/** Public read endpoints observed on bus.andong.go.kr, 2026-09-08. No private login API used. */
interface AndongApi {
    @GET("api/bus/stops") suspend fun stops(): JsonArray
    @GET("api/hp/busRoute") suspend fun routes(): JsonArray
    @GET("api/bus/selectBusStopList") suspend fun arrivals(@Query("stopId") stopId: String, @Query("locale") locale: String = "ko"): JsonArray
    @GET("api/gnss/all") suspend fun locations(): JsonArray
    @GET("api/bus/routes/{routeId}/stops") suspend fun routeStops(@Path("routeId") route: String): JsonArray
    @GET("api/bus/selectBusVertex") suspend fun vertices(@Query("routeId") route: String): JsonArray
}
interface TagoApi {
    @GET("BusSttnInfoInqireService/getCtyCodeList") suspend fun cities(@QueryMap params: Map<String, String>): JsonObject
    @GET("BusSttnInfoInqireService/getSttnNoList") suspend fun stops(@QueryMap params: Map<String, String>): JsonObject
    @GET("BusRouteInfoInqireService/getRouteNoList") suspend fun routes(@QueryMap params: Map<String, String>): JsonObject
    @GET("ArvlInfoInqireService/getSttnAcctoArvlPrearngeInfoList") suspend fun arrivals(@QueryMap params: Map<String, String>): JsonObject
    @GET("BusLcInfoInqireService/getRouteAcctoBusLcList") suspend fun locations(@QueryMap params: Map<String, String>): JsonObject
    @GET("BusRouteInfoInqireService/getRouteAcctoThrghSttnList") suspend fun routeStops(@QueryMap params: Map<String, String>): JsonObject
}
