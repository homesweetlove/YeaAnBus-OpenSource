package kr.yeaan.bus.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.yeaan.bus.data.model.ArrivalDisplay
import kr.yeaan.bus.data.model.Stop
import kr.yeaan.bus.ui.UiState
import kr.yeaan.bus.ui.components.ApproachStrip
import kr.yeaan.bus.ui.components.ArrivalRow
import kr.yeaan.bus.ui.components.BoardPanel
import kr.yeaan.bus.ui.components.EmptyState
import kr.yeaan.bus.ui.components.EtaText
import kr.yeaan.bus.ui.components.Notice
import kr.yeaan.bus.ui.components.PlateSize
import kr.yeaan.bus.ui.components.RoutePlate
import kr.yeaan.bus.ui.components.SectionTitle
import kr.yeaan.bus.ui.components.approachProgress
import kr.yeaan.bus.ui.components.arrivalDirection
import kr.yeaan.bus.ui.components.arrivalNote
import kr.yeaan.bus.ui.components.groupShape
import kr.yeaan.bus.ui.theme.Board
import kr.yeaan.bus.ui.theme.Muted
import kr.yeaan.bus.ui.theme.Navy

private const val WaitingPreview = 3

@Composable
fun BusArrivalScreen(state: UiState, onBack: () -> Unit, onFavorite: (Stop) -> Unit,
                     onRefresh: () -> Unit, onMap: (ArrivalDisplay?) -> Unit) {
    val stop = state.selectedStop ?: return
    val favorite = state.favorites.any { it.key == stop.key }
    val live = state.arrivals.filter { it.seconds != null }
    val next = live.firstOrNull()
    val following = live.drop(1)
    val waiting = state.arrivals.filter { it.seconds == null }
    var showAllWaiting by rememberSaveable(stop.key) { mutableStateOf(false) }
    val waitingShown = if (showAllWaiting) waiting else waiting.take(WaitingPreview)
    val waitingHasMore = waiting.size > waitingShown.size
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "뒤로") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onMap(null) }) {
                    Icon(Icons.Outlined.Map, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("지도 보기")
                }
                IconButton(onClick = { onFavorite(stop) }) {
                    Icon(if (favorite) Icons.Filled.Star else Icons.Outlined.StarOutline, "즐겨찾기 변경", tint = if (favorite) Navy else Muted)
                }
            }
            Text("${stop.city.label} · 정류장 ${stop.code.ifBlank { stop.id }}", Modifier.padding(top = 8.dp),
                color = Muted, style = MaterialTheme.typography.bodyMedium)
            Text(stop.name, Modifier.padding(top = 2.dp, bottom = 16.dp), style = MaterialTheme.typography.headlineMedium)
            BoardPanel {
                val board = Board
                if (next != null) {
                    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(onClickLabel = "지도에서 보기") { onMap(next) },
                        verticalAlignment = Alignment.CenterVertically) {
                        RoutePlate(next.arrival.route.number, PlateSize.LARGE, onBoard = true)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(arrivalDirection(next), color = board.ink, style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(arrivalNote(next), color = board.muted, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(10.dp))
                        EtaText(next.seconds, next.stale, big = true, color = board.signal, labelColor = board.muted)
                    }
                    approachProgress(next.arrival.stopsAway, next.distanceMeters)?.let { progress ->
                        Spacer(Modifier.height(16.dp))
                        ApproachStrip(progress, board.line, board.signal, board.onSignal, board.ink, Modifier.padding(end = 4.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                } else {
                    Text(if (state.loading) "도착 정보를 확인하고 있어요" else "지금 오고 있는 버스가 없어요",
                        color = board.ink, style = MaterialTheme.typography.titleMedium)
                    Text("운행 시간이나 정보 제공 상태에 따라 달라질 수 있어요.", Modifier.padding(top = 4.dp),
                        color = board.muted, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(state.snapshot?.provider?.label ?: "연결 중", color = board.muted, style = MaterialTheme.typography.bodySmall)
                    Text(state.updatedSecondsAgo?.let { if (it < 2) " · 방금 업데이트" else " · ${it}초 전 업데이트" } ?: " · 도착 정보 확인 중",
                        Modifier.weight(1f), color = board.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = onRefresh, enabled = !state.loading,
                        colors = ButtonDefaults.textButtonColors(contentColor = board.ink, disabledContentColor = board.muted)) {
                        Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("새로고침")
                    }
                }
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp))
            state.snapshot?.message?.let { message ->
                Spacer(Modifier.height(8.dp))
                Notice(message)
            }
        }
        if (following.isNotEmpty()) {
            item { SectionTitle("이어서 오는 버스", "${following.size}대") }
            itemsIndexed(following, key = { _, row -> "live:${row.arrival.id}" }) { index, row ->
                ArrivalRow(row, index, following.size) { onMap(row) }
            }
        }
        if (waiting.isNotEmpty()) {
            val rows = waitingShown.size + if (waitingHasMore) 1 else 0
            item { SectionTitle("도착 시간 미정", "${waiting.size}대") }
            itemsIndexed(waitingShown, key = { _, row -> "wait:${row.arrival.id}" }) { index, row ->
                ArrivalRow(row, index, rows) { onMap(row) }
            }
            if (waitingHasMore) item {
                Row(Modifier.fillMaxWidth().clip(groupShape(rows - 1, rows)).background(MaterialTheme.colorScheme.surface)
                    .clickable { showAllWaiting = true }.heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    Text("나머지 ${waiting.size - waitingShown.size}대 모두 보기", color = Navy, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Outlined.ExpandMore, null, tint = Navy, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        if (state.arrivals.isEmpty() && !state.loading) item {
            EmptyState("도착 정보가 없습니다", "운행 시간이나 정보 제공 상태에 따라\n도착 예정 버스가 표시되지 않을 수 있습니다.")
        }
        item {
            Text("도착 시간과 남은 거리는 교통 상황에 따라 달라질 수 있습니다.\n차량 위치는 마지막 수신 정보를 바탕으로 잠시 추정됩니다.",
                Modifier.padding(top = 24.dp), style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}
