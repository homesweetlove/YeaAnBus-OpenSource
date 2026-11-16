package kr.yeaan.bus.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kr.yeaan.bus.YeaAnBusApp
import kr.yeaan.bus.algorithm.*
import kr.yeaan.bus.data.model.*
import kr.yeaan.bus.data.repository.userMessage
import kr.yeaan.bus.ui.settings.UserSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

enum class Page { HOME, SEARCH, ARRIVAL, MAP, USER }
enum class SearchMode { STOPS, ROUTES }
data class UiState(
    val page: Page = Page.HOME,
    val query: String = "", val searchMode: SearchMode = SearchMode.STOPS,
    val stops: List<Stop> = emptyList(), val routes: List<Route> = emptyList(),
    val favoriteArrivals: List<FavoriteArrival> = emptyList(),
    val favorites: List<Stop> = emptyList(), val recent: List<Stop> = emptyList(),
    val nearby: List<Stop> = emptyList(), val userPoint: Point? = null,
    val selectedStop: Stop? = null, val selectedBusId: String? = null,
    val snapshot: Snapshot? = null, val arrivals: List<ArrivalDisplay> = emptyList(),
    val mapStops: List<Stop> = emptyList(), val mapStopsLoading: Boolean = false,
    val mapStopsNotice: String? = null, val mapPathApproximate: Boolean = false,
    val mapRouteLoading: Boolean = false,
    val routeDetail: RouteDetail? = null, val mapPath: List<Point> = emptyList(),
    val searching: Boolean = false, val loading: Boolean = false, val locating: Boolean = false,
    val error: String? = null, val searchError: String? = null,
    val searchNotice: String? = null, val nearbyNotice: String? = null,
    val intervalMs: Long = 30_000, val updatedSecondsAgo: Long? = null
)

@OptIn(FlowPreview::class)
class BusViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as YeaAnBusApp).repository
    private val preferences = application.getSharedPreferences("user-settings", android.content.Context.MODE_PRIVATE)
    private val mutableSettings = MutableStateFlow(UserSettings.read(preferences))
    val settings: StateFlow<UserSettings> = mutableSettings.asStateFlow()
    fun updateSettings(value: UserSettings) {
        value.save(preferences)
        mutableSettings.value = value
    }
    private val mutable = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = mutable.asStateFlow()
    private val search = MutableStateFlow(SearchMode.STOPS to "")
    private val refreshSignal = Channel<Unit>(Channel.CONFLATED)
    private val predictors = mutableMapOf<String, BusPredictor>()
    private val averageSpeeds = mutableMapOf<String, Double>()
    private val estimator = ArrivalEstimator()
    private var detailJob: Job? = null
    private var mapJob: Job? = null
    private var mapStopsJob: Job? = null
    private var nearbyJob: Job? = null
    private var previousPage = Page.HOME
    private var mapReturnPage = Page.HOME

    init {
        viewModelScope.launch { repository.favorites.collect { value ->
            mutable.update { it.copy(favorites = value, favoriteArrivals = it.favoriteArrivals.filter { row -> value.any { stop -> stop.key == row.stop.key } }) }
            kr.yeaan.bus.widget.FavoriteWidget.publish(getApplication(), value.take(3).map { FavoriteArrival(it) })
            kr.yeaan.bus.widget.FavoriteWidget.refresh(getApplication())
        } }
        viewModelScope.launch { repository.recent.collect { value -> mutable.update { it.copy(recent = value) } } }
        viewModelScope.launch {
            repository.lastLocations().forEach { fix ->
                predictors.getOrPut(fix.key) { BusPredictor() }.observe(fix, System.currentTimeMillis())
            }
        }
        viewModelScope.launch {
            search.debounce(350).collectLatest { (mode, query) ->
                if (query.isBlank()) {
                    mutable.update { it.copy(stops = emptyList(), routes = emptyList(), searching = false, searchError = null, searchNotice = null) }
                    return@collectLatest
                }
                mutable.update { it.copy(searching = true, searchError = null, searchNotice = null) }
                try {
                    if (mode == SearchMode.STOPS) {
                        val result = repository.searchStops(query.trim())
                        mutable.update { it.copy(stops = result.items, routes = emptyList(), searchNotice = result.warnings.takeIf { it.isNotEmpty() }?.joinToString("\n")) }
                    } else {
                        val result = repository.searchRoutes(query.trim())
                        mutable.update { it.copy(routes = result.items, stops = emptyList(), searchNotice = result.warnings.takeIf { it.isNotEmpty() }?.joinToString("\n")) }
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { mutable.update { it.copy(searchError = userMessage(e), stops = emptyList(), routes = emptyList()) } }
                finally { mutable.update { it.copy(searching = false) } }
            }
        }
    }
    fun page(page: Page) { mutable.update { it.copy(page = page, error = null) } }
    fun back() {
        if (state.value.routeDetail != null && state.value.page == Page.SEARCH) {
            detailJob?.cancel(); mutable.update { it.copy(routeDetail = null) }
        } else page(when (state.value.page) { Page.ARRIVAL -> previousPage; Page.MAP -> mapReturnPage; else -> Page.HOME })
    }
    fun query(value: String) {
        detailJob?.cancel()
        mutable.update { it.copy(query = value, routeDetail = null, searchError = null, searchNotice = null, stops = emptyList(), routes = emptyList()) }
        search.value = state.value.searchMode to value
    }
    fun mode(mode: SearchMode) {
        detailJob?.cancel()
        mutable.update { it.copy(searchMode = mode, routeDetail = null, stops = emptyList(), routes = emptyList(), searchNotice = null) }
        search.value = mode to state.value.query
    }
    fun openStop(stop: Stop, navigate: Boolean = true) {
        if (state.value.selectedStop?.key == stop.key) {
            if (navigate) { previousPage = state.value.page.takeIf { it == Page.HOME || it == Page.SEARCH || it == Page.USER } ?: Page.HOME; page(Page.ARRIVAL) }
            refresh()
            return
        }
        if (navigate) previousPage = state.value.page.takeIf { it == Page.HOME || it == Page.SEARCH || it == Page.USER } ?: Page.HOME
        mapJob?.cancel()
        mutable.update { it.copy(
            selectedStop = stop, snapshot = null, arrivals = emptyList(), selectedBusId = null, mapPath = emptyList(), mapPathApproximate = false, mapRouteLoading = false,
            page = if (navigate) Page.ARRIVAL else it.page, error = null, loading = true, updatedSecondsAgo = null
        ) }
        viewModelScope.launch { safely { repository.record(stop) } }
    }
    suspend fun monitorFavorites() {
        while (currentCoroutineContext().isActive) {
            try {
                val rows = repository.favoriteArrivals()
                mutable.update { it.copy(favoriteArrivals = rows) }
                kr.yeaan.bus.widget.FavoriteWidget.publish(getApplication(), rows)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { report(userMessage(e)) }
            delay(if (settings.value.dataSaver) 60_000 else 30_000)
        }
    }
    fun favorite(stop: Stop) { viewModelScope.launch { safely { repository.toggleFavorite(stop) } } }
    fun refresh() { refreshSignal.trySend(Unit) }
    fun report(message: String) { mutable.update { it.copy(error = message, locating = false) } }
    fun locating() { mutable.update { it.copy(locating = true, error = null) } }
    fun located(point: Point) {
        nearbyJob?.cancel()
        nearbyJob = viewModelScope.launch {
            try {
                val result = repository.nearby(point)
                mutable.update { it.copy(nearby = result.items, userPoint = point, locating = false,
                    nearbyNotice = result.warnings.takeIf { it.isNotEmpty() }?.joinToString("\n"),
                    error = if (result.items.isEmpty() && result.warnings.isEmpty()) "주변 3km 안에 정류장이 없습니다. 검색으로 찾아보세요." else null) }
                result.items.firstOrNull()?.let { openStop(it, navigate = false) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { report(userMessage(e)) }
        }
    }
    fun openRoute(route: Route) {
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            mutable.update { it.copy(searching = true, searchError = null) }
            try {
                val detail = repository.routeDetail(route)
                mutable.update { it.copy(routeDetail = detail) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(searchError = userMessage(e)) } }
            finally { mutable.update { it.copy(searching = false) } }
        }
    }
    fun loadMapStops() {
        if (mapStopsJob?.isActive == true) return
        mapStopsJob = viewModelScope.launch {
            mutable.update { it.copy(mapStopsLoading = true, mapStopsNotice = null) }
            try {
                val result = repository.mapStops()
                mutable.update { it.copy(mapStops = result.items,
                    mapStopsNotice = result.warnings.takeIf { it.isNotEmpty() }?.joinToString("\n")) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(mapStopsNotice = userMessage(e)) } }
            finally { mutable.update { it.copy(mapStopsLoading = false) } }
        }
    }
    fun map(arrival: ArrivalDisplay? = null) {
        if (state.value.page != Page.MAP) mapReturnPage = state.value.page
        if (state.value.mapStops.isEmpty()) loadMapStops()
        mutable.update { it.copy(page = Page.MAP, selectedBusId = arrival?.arrival?.id, mapPath = emptyList(), mapPathApproximate = false, mapRouteLoading = arrival != null, error = null) }
        mapJob?.cancel()
        val route = arrival?.arrival?.route ?: return
        mapJob = viewModelScope.launch {
            try {
                val detail = repository.routeDetail(route)
                val path = detail.path.takeIf { it.size > 1 }?.let(::RoutePath)
                predictors.filterKeys { key -> key.startsWith("${route.provider.name}:${route.id}:") }.values.forEach { it.path = path }
                val road = detail.path.filter { it.valid }
                val approximate = road.size < 2
                mutable.update { it.copy(mapPath = if (approximate) detail.stops.map { stop -> stop.point }.filter { point -> point.valid } else road,
                    mapPathApproximate = approximate) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { report(userMessage(e)) }
            finally { mutable.update { it.copy(mapRouteLoading = false) } }
        }
    }
    fun position(location: BusLocation, now: Long): Point =
        predictors[location.key]?.position(now) ?: location.point

    /** Owned by repeatOnLifecycle(STARTED), so screen-off/background cancels HTTP and both loops. */
    suspend fun monitor(stop: Stop) = coroutineScope {
        launch {
            while (isActive) {
                render(System.currentTimeMillis())
                delay(1000)
            }
        }
        var failures = 0
        while (isActive) {
            mutable.update { it.copy(loading = true) }
            try {
                val snapshot = repository.refresh(stop)
                if (state.value.selectedStop?.key != stop.key) return@coroutineScope
                val now = System.currentTimeMillis()
                snapshot.arrivals.mapNotNull { it.location }.distinctBy { it.key }.forEach { location ->
                    val predictor = predictors.getOrPut(location.key) { BusPredictor() }
                    predictor.observe(location, now)
                    if (predictor.speedMps > 1) {
                        val old = averageSpeeds[location.routeId] ?: 6.0
                        averageSpeeds[location.routeId] = old * 0.9 + predictor.speedMps * 0.1
                    }
                }
                val currentKeys = snapshot.arrivals.mapNotNull { it.location?.key }.toSet()
                predictors.keys.retainAll(currentKeys)
                failures = if (snapshot.cached) failures + 1 else 0
                mutable.update { it.copy(snapshot = snapshot, error = null) }
                render(now)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                failures++
                mutable.update { it.copy(error = userMessage(e)) }
            } finally { mutable.update { it.copy(loading = false) } }
            val completedAt = android.os.SystemClock.elapsedRealtime()
            while (isActive) {
                val displays = state.value.arrivals.filterNot { it.stale }
                val interval = settings.value.refreshInterval(RefreshPolicy.intervalMillis(
                    displays.mapNotNull { it.distanceMeters }.minOrNull(),
                    displays.mapNotNull { it.seconds }.minOrNull(),
                    displays.maxOfOrNull { it.speedMps } ?: 0.0, failures
                ))
                mutable.update { it.copy(intervalMs = interval) }
                val remaining = interval - (android.os.SystemClock.elapsedRealtime() - completedAt)
                if (remaining <= 0) break
                // Re-evaluate predicted approach every second, without an HTTP request.
                val requested = withTimeoutOrNull(minOf(1000L, remaining)) { refreshSignal.receive(); true } ?: false
                if (requested) break
            }
        }
    }

    private fun render(now: Long) {
        val snapshot = state.value.snapshot ?: return
        val age = ((now - snapshot.fetchedAt) / 1000).coerceAtLeast(0)
        val rows = snapshot.arrivals.map { arrival ->
            val predictor = arrival.location?.let { predictors[it.key] }
            val gpsStale = predictor?.isStale(now) ?: false
            val stale = snapshot.cached || age > 60 || (gpsStale && arrival.apiSeconds == null)
            val point = arrival.location?.let { position(it, now) }
            val distance = if (point != null && !gpsStale)
                predictor?.path?.remaining(point, snapshot.stop.point) ?: (Geo.distance(point, snapshot.stop.point) * 1.18)
            else null
            val seconds = if (stale) null else estimator.seconds(
                distance, predictor?.speedMps, averageSpeeds[arrival.route.id] ?: 6.0,
                arrival.stopsAway, arrival.apiSeconds?.let { (it - age.toInt()).coerceAtLeast(0) }
            )
            ArrivalDisplay(arrival, seconds, distance, point, predictor?.speedMps ?: 0.0, stale)
        }.sortedWith(compareBy<ArrivalDisplay> { it.seconds == null }.thenBy { it.seconds }.thenBy { it.arrival.route.number })
        mutable.update { it.copy(arrivals = rows, updatedSecondsAgo = age) }
    }
    private suspend fun safely(block: suspend () -> Unit) {
        try { block() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { report(userMessage(e)) }
    }
}
