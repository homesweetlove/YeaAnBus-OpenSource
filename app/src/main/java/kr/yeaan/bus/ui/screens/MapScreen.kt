package kr.yeaan.bus.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kr.yeaan.bus.algorithm.ArrivalText
import kr.yeaan.bus.data.model.*
import kr.yeaan.bus.ui.UiState
import kr.yeaan.bus.ui.components.*
import kr.yeaan.bus.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraPosition
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.NaverMapSdk
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.OverlayImage
import com.naver.maps.map.overlay.PolylineOverlay

@Composable
fun MapScreen(state: UiState, onBack: () -> Unit, onSearch: () -> Unit,
              position: (BusLocation, Long) -> Point, keepAwake: Boolean = false,
              onStop: (Stop) -> Unit, onBus: (ArrivalDisplay?) -> Unit, onRetryStops: () -> Unit) {
    val stop = state.selectedStop
    val context = LocalContext.current
    val accentArgb = MaterialTheme.colorScheme.primary.toArgb()
    val boardArgb = Board.panel.toArgb()
    val signalArgb = Board.signal.toArgb()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val current by rememberUpdatedState(state)
    val currentPosition by rememberUpdatedState(position)
    val currentOnStop by rememberUpdatedState(onStop)
    val currentOnBus by rememberUpdatedState(onBus)
    val initialPoint = remember { stop?.point ?: state.userPoint ?: Point(36.5684, 128.7294) }
    val map = remember(context) {
        MapView(context).apply {
            onCreate(null)
            contentDescription = "안동·예천 정류장과 버스 위치 지도"
        }
    }
    var naverMap by remember(map) { mutableStateOf<NaverMap?>(null) }
    var mapError by remember(map) { mutableStateOf<String?>(null) }
    val markers = remember(map) { mutableMapOf<String, Marker>() }
    DisposableEffect(map, keepAwake) {
        map.keepScreenOn = keepAwake
        onDispose { map.keepScreenOn = false }
    }
    DisposableEffect(map, lifecycle) {
        var started = false
        var resumed = false
        var disposed = false
        fun syncLifecycle() {
            val shouldStart = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            val shouldResume = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (resumed && !shouldResume) { map.onPause(); resumed = false }
            if (started && !shouldStart) { map.onStop(); started = false }
            if (!started && shouldStart) { map.onStart(); started = true }
            if (!resumed && shouldResume) { map.onResume(); resumed = true }
        }
        val observer = LifecycleEventObserver { _, _ ->
            syncLifecycle()
        }
        val sdk = NaverMapSdk.getInstance(context)
        val authListener = NaverMapSdk.OnAuthFailedListener { error ->
            map.post {
                if (!disposed) mapError = when (error) {
                    is NaverMapSdk.QuotaExceededException -> "지도 이용 한도에 도달했거나 Dynamic Map이 활성화되지 않았습니다."
                    else -> "지도 인증을 확인해 주세요. Client ID와 Android 패키지 등록을 확인해야 합니다."
                }
            }
        }
        sdk.onAuthFailedListener = authListener
        map.getMapAsync { ready ->
            if (!disposed) {
                ready.cameraPosition = CameraPosition(LatLng(initialPoint.lat, initialPoint.lon), 14.0)
                ready.minZoom = 5.0
                ready.maxZoom = 19.0
                ready.uiSettings.isZoomControlEnabled = false
                naverMap = ready
            }
        }
        lifecycle.addObserver(observer)
        val memoryCallbacks = object : android.content.ComponentCallbacks {
            override fun onConfigurationChanged(config: android.content.res.Configuration) = Unit
            override fun onLowMemory() { map.onLowMemory() }
        }
        context.registerComponentCallbacks(memoryCallbacks)
        syncLifecycle()
        onDispose {
            disposed = true
            lifecycle.removeObserver(observer)
            context.unregisterComponentCallbacks(memoryCallbacks)
            sdk.onAuthFailedListener = null
            markers.values.forEach { it.map = null }
            markers.clear()
            if (resumed) map.onPause()
            if (started) map.onStop()
            map.onDestroy()
        }
    }
    DisposableEffect(naverMap, state.mapPath, state.mapPathApproximate, accentArgb) {
        val line = if (naverMap != null && state.mapPath.size > 1) PolylineOverlay().apply {
            coords = state.mapPath.map { LatLng(it.lat, it.lon) }
            color = accentArgb
            width = (4 * context.resources.displayMetrics.density).toInt()
            if (state.mapPathApproximate) setPattern(12, 8)
            this.map = naverMap
        } else null
        onDispose { line?.map = null }
    }
    // Only attach markers in the visible area; every stop remains available when panning.
    DisposableEffect(naverMap, state.mapStops, stop?.key, accentArgb) {
        val ready = naverMap
        val stopMarkers = mutableMapOf<String, Marker>()
        val icon = markerIcon(context, false, accentArgb, android.graphics.Color.WHITE)
        val stops = (state.mapStops + listOfNotNull(stop)).distinctBy { it.key }
        fun updateStops() {
            if (ready == null) return
            val visible = stops.filter { it.point.valid && ready.contentBounds.contains(LatLng(it.point.lat, it.point.lon)) }
            val keys = visible.map { it.key }.toSet()
            stopMarkers.keys.filter { it !in keys }.toList().forEach { stopMarkers.remove(it)?.map = null }
            visible.forEach { item ->
                stopMarkers.getOrPut(item.key) {
                    Marker().apply {
                        this.position = LatLng(item.point.lat, item.point.lon)
                        this.icon = icon
                        anchor = PointF(0.5f, 0.5f)
                        captionText = item.name
                        captionMinZoom = 15.0
                        zIndex = if (item.key == stop?.key) 10 else 0
                        setOnClickListener { currentOnStop(item); true }
                        this.map = ready
                    }
                }
            }
        }
        val listener = NaverMap.OnCameraIdleListener { updateStops() }
        ready?.addOnCameraIdleListener(listener)
        updateStops()
        onDispose {
            ready?.removeOnCameraIdleListener(listener)
            stopMarkers.values.forEach { it.map = null }
        }
    }
    LaunchedEffect(naverMap, stop?.key) {
        stop?.point?.takeIf { it.valid }?.let { point ->
            naverMap?.moveCamera(CameraUpdate.scrollTo(LatLng(point.lat, point.lon)))
        }
    }
    LaunchedEffect(naverMap, lifecycle, boardArgb, signalArgb) {
        val ready = naverMap ?: return@LaunchedEffect
        val busIcon = markerIcon(context, true, boardArgb, signalArgb)
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                val now = System.currentTimeMillis()
                val rows = current.arrivals.filter {
                    current.selectedBusId == null || it.arrival.id == current.selectedBusId
                }.mapNotNull { it.arrival.location }.distinctBy { it.key }
                val keys = rows.map { it.key }.toSet()
                markers.keys.filter { it !in keys }.toList().forEach { key ->
                    markers.remove(key)?.map = null
                }
                rows.forEach { fix ->
                    val point = currentPosition(fix, now).takeIf { it.valid } ?: fix.point
                    if (!point.valid) {
                        markers.remove(fix.key)?.map = null
                        return@forEach
                    }
                    val marker = markers.getOrPut(fix.key) {
                        Marker().apply {
                            // Naver validates coordinates when attaching: a new marker starts at NaN.
                            this.position = LatLng(point.lat, point.lon)
                            icon = busIcon
                            anchor = PointF(0.5f, 0.5f)
                            setOnClickListener {
                                current.arrivals.firstOrNull { it.arrival.location?.key == fix.key }?.let { currentOnBus(it) }
                                true
                            }
                            zIndex = 20
                            this.map = ready
                        }
                    }
                    marker.position = LatLng(point.lat, point.lon)
                    marker.captionText = "${fix.number}번"
                    marker.subCaptionText = if (now - fix.observedAt > 60_000) "마지막 수신 위치" else fix.nextStop
                    marker.alpha = if (now - fix.observedAt > 60_000 || current.snapshot?.cached == true) 0.45f else 1f
                }
                delay(33) // Only marker rendering runs at 30 fps; this never performs an API call.
            }
        }
    }
    val focused = if (state.selectedBusId != null)
        state.arrivals.firstOrNull { it.arrival.id == state.selectedBusId }
    else state.arrivals.firstOrNull { it.point != null }
    val others = if (focused != null) state.arrivals.filter { it.arrival.id != focused.arrival.id } else state.arrivals
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "뒤로") }
            Column(Modifier.weight(1f)) {
                Text(stop?.name ?: "정류장 지도", style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(stop?.let { "정류장 ${it.code}" } ?: "안동·예천 · 정류장을 눌러 버스를 확인하세요", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, "정류장 검색") }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            key(map) { AndroidView(factory = { map }, modifier = Modifier.fillMaxSize()) }
            Column(Modifier.align(Alignment.TopEnd).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MapButton(Icons.Outlined.LocationOn, "정류장 위치로 이동") {
                    naverMap?.moveCamera(CameraUpdate.scrollTo(LatLng((stop?.point ?: initialPoint).lat, (stop?.point ?: initialPoint).lon)))
                }
                if (focused?.point != null) MapButton(Icons.Outlined.DirectionsBus, "버스 위치로 이동") {
                    val point = focused.point
                    naverMap?.moveCamera(CameraUpdate.scrollTo(LatLng(point.lat, point.lon)))
                }
            }
            mapError?.let { message ->
                Text(message, Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 12.dp, end = 76.dp)
                    .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small).padding(12.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        HorizontalDivider(color = Line)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)
            .background(MaterialTheme.colorScheme.surface), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
            item {
                if (state.mapStopsLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.mapStopsNotice?.let { message ->
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetryStops) { Text("정류장 다시 불러오기") }
                }
                if (stop == null) {
                    Text("정류장을 선택해 주세요", style = MaterialTheme.typography.titleMedium)
                    Text("지도를 움직이면 해당 위치의 정류장이 표시됩니다.", color = Muted, style = MaterialTheme.typography.bodySmall)
                } else {
                    if (focused != null) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RoutePlate(focused.arrival.route.number, PlateSize.LARGE)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(arrivalDirection(focused), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(if (state.selectedBusId != null) "${arrivalNote(focused)} · 선택한 버스" else arrivalNote(focused),
                                    style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.width(10.dp))
                            EtaText(focused.seconds, focused.stale, big = true, color = Ink, labelColor = Muted)
                        }
                        approachProgress(focused.arrival.stopsAway, focused.distanceMeters)?.let { progress ->
                            ApproachStrip(progress, Line, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, Ink,
                                Modifier.padding(top = 12.dp, bottom = 4.dp))
                        }
                    }
                    if (state.mapRouteLoading) Text("노선 경로를 불러오는 중…", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = Muted)
                    else if (state.selectedBusId != null) {
                        Text(when {
                            state.mapPath.size < 2 -> "제공되는 노선 경로가 없습니다."
                            state.mapPathApproximate -> "점선은 정류장 순서를 연결한 선입니다. 실제 도로 경로와 다를 수 있어요."
                            else -> "선은 선택한 버스의 운행 노선입니다."
                        }, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = Muted)
                        Text(if (focused?.point == null) "연결된 차량 GPS가 없습니다."
                            else "버스는 수신 사이의 추정 위치로 움직입니다. 흐린 아이콘은 이전 위치입니다.",
                            style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("도착 예정 버스 ${state.arrivals.size}대", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        if (state.selectedBusId != null) TextButton(onClick = { onBus(null) }) { Text("전체 버스") }
                    }
                    if (state.loading && state.arrivals.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else if (state.arrivals.isEmpty()) Text("현재 제공되는 도착 정보가 없습니다.", color = Muted)
                }
            }
            itemsIndexed(others, key = { _, row -> row.arrival.id }) { index, row ->
                ArrivalRow(row, index, others.size, inset = false) { onBus(row) }
            }
        }
    }
}

@Composable
private fun MapButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    FilledIconButton(onClick = onClick, modifier = Modifier.shadow(4.dp, shape).size(48.dp), shape = shape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface)) {
        Icon(icon, label)
    }
}

/** Stop: an accent ring. Bus: a dark plate with the amber bus, matching the arrival board. */
private fun markerIcon(context: android.content.Context, bus: Boolean, base: Int, mark: Int): OverlayImage {
    val density = context.resources.displayMetrics.density
    val size = (if (bus) 40 * density else 24 * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.scale(size / 40f, size / 40f)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    if (bus) {
        paint.color = android.graphics.Color.WHITE
        canvas.drawRoundRect(1f, 1f, 39f, 39f, 12f, 12f, paint)
        paint.color = base
        canvas.drawRoundRect(3f, 3f, 37f, 37f, 10f, 10f, paint)
        paint.color = mark
        canvas.drawRoundRect(11f, 9f, 29f, 29f, 3f, 3f, paint)
        canvas.drawRect(12f, 28f, 15f, 32f, paint)
        canvas.drawRect(25f, 28f, 28f, 32f, paint)
        paint.color = base
        canvas.drawRect(13f, 13f, 27f, 21f, paint)
        canvas.drawCircle(15f, 25f, 1.5f, paint)
        canvas.drawCircle(25f, 25f, 1.5f, paint)
    } else {
        paint.color = mark
        canvas.drawCircle(20f, 20f, 19f, paint)
        paint.color = base
        canvas.drawCircle(20f, 20f, 15f, paint)
        paint.color = mark
        canvas.drawCircle(20f, 20f, 7f, paint)
    }
    return OverlayImage.fromBitmap(bitmap)
}
