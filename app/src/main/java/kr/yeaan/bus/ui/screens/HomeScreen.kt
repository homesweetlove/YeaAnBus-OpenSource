package kr.yeaan.bus.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.yeaan.bus.algorithm.Geo
import kr.yeaan.bus.data.model.ArrivalDisplay
import kr.yeaan.bus.data.model.FavoriteArrival
import kr.yeaan.bus.data.model.Stop
import kr.yeaan.bus.ui.UiState
import kr.yeaan.bus.ui.components.ArrivalRow
import kr.yeaan.bus.ui.components.BoardPanel
import kr.yeaan.bus.ui.components.EtaValue
import kr.yeaan.bus.ui.components.Notice
import kr.yeaan.bus.ui.components.PlateSize
import kr.yeaan.bus.ui.components.RoutePlate
import kr.yeaan.bus.ui.components.SectionTitle
import kr.yeaan.bus.ui.components.StopRow
import kr.yeaan.bus.ui.theme.Board
import kr.yeaan.bus.ui.theme.Line
import kr.yeaan.bus.ui.theme.Muted
import kr.yeaan.bus.ui.theme.Navy
import kr.yeaan.bus.widget.FavoriteText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private class BoardLine(val number: String, val to: String, val minutes: Int?, val soon: Boolean, val label: String?)

/** Same freshness rules as the widget text: a countdown only while the answer is recent. */
private fun boardLines(row: FavoriteArrival, now: Long): List<BoardLine> {
    val snapshot = row.snapshot ?: return emptyList()
    val clock = SimpleDateFormat("HH:mm", Locale.KOREA)
    val fresh = !snapshot.cached && now - snapshot.fetchedAt <= 90_000
    return snapshot.arrivals.sortedBy { it.apiSeconds ?: Int.MAX_VALUE }.take(3).map { bus ->
        val to = bus.route.to.takeIf { it.isNotBlank() }?.let { "$it 방면" } ?: "시내버스"
        val target = bus.apiSeconds?.let { snapshot.fetchedAt + it * 1000L }
        if (target != null && fresh && target >= now) {
            val left = target - now
            if (left < 60_000) BoardLine(bus.route.number, to, null, true, null)
            else BoardLine(bus.route.number, to, ((left + 59_999) / 60_000).toInt(), false, null)
        } else if (target != null) {
            BoardLine(bus.route.number, to, null, false, "${clock.format(Date(target))} 도착 예상")
        } else {
            BoardLine(bus.route.number, to, null, false,
                bus.stopsAway?.let { "${it}개 정류장 전" } ?: bus.status.ifBlank { "시간 미제공" })
        }
    }
}

private fun boardMessage(row: FavoriteArrival?): String? {
    if (row == null) return "도착 정보를 불러오는 중…"
    val snapshot = row.snapshot ?: return row.error ?: "도착 정보 조회 중"
    if (snapshot.arrivals.isEmpty()) return if (snapshot.cached) "이전 조회에 도착 정보 없음" else "현재 제공되는 도착 정보 없음"
    return null
}

@Composable
private fun DashedLine(color: Color) {
    Canvas(Modifier.fillMaxWidth().padding(vertical = 14.dp).height(1.dp)) {
        drawLine(color, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
    }
}

@Composable
private fun BoardStop(stop: Stop, row: FavoriteArrival?, onStop: () -> Unit, onFavorite: () -> Unit) {
    val board = Board
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "도착 정보 열기", onClick = onStop)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stop.name, color = board.ink, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${stop.city.label} · ${stop.code.ifBlank { stop.id }}", color = board.muted, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onFavorite) { Icon(Icons.Filled.Star, "${stop.name} 즐겨찾기 해제", tint = board.signal) }
        }
        val message = boardMessage(row)
        if (row == null || message != null) {
            Text(message ?: "도착 정보 조회 중", Modifier.padding(top = 8.dp, bottom = 6.dp), color = board.muted, style = MaterialTheme.typography.bodyMedium)
        } else boardLines(row, System.currentTimeMillis()).forEachIndexed { index, line ->
            val first = index == 0
            Row(Modifier.fillMaxWidth().heightIn(min = if (first) 64.dp else 44.dp), verticalAlignment = Alignment.CenterVertically) {
                RoutePlate(line.number, if (first) PlateSize.MEDIUM else PlateSize.SMALL, onBoard = true)
                Spacer(Modifier.width(12.dp))
                Text(line.to, Modifier.weight(1f), color = board.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = if (first) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(10.dp))
                EtaValue(line.minutes, line.soon, line.label, big = first,
                    color = if (first) board.signal else board.ink, labelColor = board.muted)
            }
        }
        Text(row?.let { FavoriteText.updated(it) } ?: "자동 갱신 준비 중", Modifier.padding(top = 4.dp),
            color = board.muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun HomeScreen(state: UiState, onSearch: () -> Unit,
               onLocate: () -> Unit, onStop: (Stop) -> Unit, onFavorite: (Stop) -> Unit,
               onBus: (ArrivalDisplay) -> Unit) {
    val favorites = state.favorites.take(3)
    val nearby = state.nearby.take(4)
    val recent = state.recent.take(5)
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("예안버스", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                Text("안동 · 예천", color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(18.dp))
            val shape = RoundedCornerShape(16.dp)
            Row(Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface).border(1.dp, Line, shape)
                .clickable(onClick = onSearch).heightIn(min = 54.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Search, null, tint = Navy)
                Spacer(Modifier.width(12.dp))
                Text("정류장 또는 버스 번호 검색", color = Muted, style = MaterialTheme.typography.bodyLarge)
            }
            SectionTitle("내 정류장", "${favorites.size}/3곳")
        }
        item {
            BoardPanel {
                if (favorites.isEmpty()) {
                    Icon(Icons.Outlined.StarOutline, null, tint = Board.signal, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("자주 타는 정류장을 걸어 두세요", color = Board.ink, style = MaterialTheme.typography.titleMedium)
                    Text("정류장 옆의 별을 누르면 도착 시간을 여기에서 바로 볼 수 있어요.",
                        Modifier.padding(top = 4.dp, end = 4.dp), color = Board.muted, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onSearch, shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Board.signal, contentColor = Board.onSignal)) {
                        Text("정류장 찾기")
                    }
                } else favorites.forEachIndexed { index, stop ->
                    if (index > 0) DashedLine(Board.line)
                    BoardStop(stop, state.favoriteArrivals.firstOrNull { it.stop.key == stop.key }, { onStop(stop) }, { onFavorite(stop) })
                }
            }
        }
        item {
            SectionTitle("가까운 정류장")
            Button(onClick = onLocate, enabled = !state.locating, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.primary)) {
                if (state.locating) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Icon(Icons.Outlined.MyLocation, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (state.locating) "현재 위치 확인 중" else "내 위치로 정류장 찾기")
            }
            if (nearby.isNotEmpty()) Spacer(Modifier.height(10.dp))
        }
        itemsIndexed(nearby, key = { _, stop -> "near:${stop.key}" }) { index, stop ->
            val distance = state.userPoint?.let { Geo.distance(it, stop.point).toInt() }
            StopRow(stop, state.favorites.any { it.key == stop.key }, { onStop(stop) }, { onFavorite(stop) },
                distance?.let { "${stop.city.label} · 약 ${it}m · ${stop.code}" }, index, nearby.size)
        }
        state.nearbyNotice?.let { message -> item { Notice(message) } }
        if (state.selectedStop != null) {
            val coming = state.arrivals.filter { it.seconds != null }.take(3)
            item {
                SectionTitle("지금 오는 버스", "전체 보기") { onStop(state.selectedStop) }
                Text(state.selectedStop.name, Modifier.padding(bottom = 10.dp), style = MaterialTheme.typography.bodyMedium, color = Muted)
                if (state.loading && state.arrivals.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            itemsIndexed(coming, key = { _, row -> "bus:${row.arrival.id}" }) { index, row ->
                ArrivalRow(row, index, coming.size) { onBus(row) }
            }
            if (!state.loading && coming.isEmpty()) item {
                Text("현재 안내 중인 도착 정보가 없습니다.", Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
        }
        if (recent.isNotEmpty()) {
            item { SectionTitle("최근 조회") }
            itemsIndexed(recent, key = { _, stop -> "recent:${stop.key}" }) { index, stop ->
                StopRow(stop, state.favorites.any { it.key == stop.key }, { onStop(stop) }, { onFavorite(stop) }, null, index, recent.size)
            }
        }
        item {
            Text("예천 · 안동 실시간 대중교통", Modifier.padding(top = 28.dp), style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}
