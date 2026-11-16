package kr.yeaan.bus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.yeaan.bus.data.model.Route
import kr.yeaan.bus.data.model.Stop
import kr.yeaan.bus.ui.SearchMode
import kr.yeaan.bus.ui.UiState
import kr.yeaan.bus.ui.components.EmptyState
import kr.yeaan.bus.ui.components.Notice
import kr.yeaan.bus.ui.components.PlateSize
import kr.yeaan.bus.ui.components.RoutePlate
import kr.yeaan.bus.ui.components.SectionTitle
import kr.yeaan.bus.ui.components.Segmented
import kr.yeaan.bus.ui.components.StopRow
import kr.yeaan.bus.ui.components.groupShape
import kr.yeaan.bus.ui.theme.Line
import kr.yeaan.bus.ui.theme.Muted
import kr.yeaan.bus.ui.theme.Navy

/** One stop on the route diagram: the line and its dot are drawn behind the row. */
@Composable
private fun RouteStopRow(stop: Stop, index: Int, count: Int, favorite: Boolean, onClick: () -> Unit, onFavorite: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val terminal = index == 0 || index == count - 1
    Row(Modifier.fillMaxWidth().clip(groupShape(index, count)).background(surface).clickable(onClick = onClick)
        .drawBehind {
            val x = 30.dp.toPx()
            val y = size.height / 2
            if (index > 0) drawLine(accent, Offset(x, 0f), Offset(x, y), strokeWidth = 3.dp.toPx())
            if (index < count - 1) drawLine(accent, Offset(x, y), Offset(x, size.height), strokeWidth = 3.dp.toPx())
            drawCircle(accent, (if (terminal) 9.dp else 6.dp).toPx(), Offset(x, y))
            drawCircle(surface, (if (terminal) 4.dp else 3.dp).toPx(), Offset(x, y))
        }
        .heightIn(min = 60.dp).padding(start = 58.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stop.name, style = MaterialTheme.typography.bodyLarge, fontWeight = if (terminal) FontWeight.Bold else FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${index + 1}번째 정류장 · ${stop.code}", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        IconButton(onClick = onFavorite) {
            Icon(if (favorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                if (favorite) "${stop.name} 즐겨찾기 해제" else "${stop.name} 즐겨찾기 추가", tint = if (favorite) Navy else Muted)
        }
    }
}

@Composable
private fun RouteResultRow(route: Route, index: Int, count: Int, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(groupShape(index, count)).background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 68.dp).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            RoutePlate(route.number, PlateSize.MEDIUM)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("${route.from} → ${route.to}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(route.city.label, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
        if (index < count - 1) HorizontalDivider(Modifier.padding(start = 16.dp), color = Line)
    }
}

@Composable
fun SearchScreen(state: UiState, onQuery: (String) -> Unit, onMode: (SearchMode) -> Unit,
                 onStop: (Stop) -> Unit, onFavorite: (Stop) -> Unit,
                 onRoute: (Route) -> Unit, onBack: () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("검색", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            Text("안동 · 예천 통합", style = MaterialTheme.typography.bodyMedium, color = Muted)
        }
        OutlinedTextField(state.query, onQuery, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = Line, focusedLeadingIconColor = Navy, unfocusedLeadingIconColor = Navy),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            placeholder = { Text("정류장 이름 또는 버스 번호") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = if (state.query.isNotEmpty()) ({ IconButton(onClick = { onQuery("") }) { Icon(Icons.Outlined.Close, "검색어 지우기") } }) else null)
        Segmented(SearchMode.entries, state.searchMode, { if (it == SearchMode.STOPS) "정류장" else "버스 노선" }, onMode,
            Modifier.padding(vertical = 12.dp))
        if (state.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.searchError?.let { Notice(it, true) }
        state.searchNotice?.let { Notice(it) }
        val detail = state.routeDetail
        if (detail != null) {
            Row(Modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "검색 결과로") }
                RoutePlate(detail.route.number, PlateSize.LARGE)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("${detail.route.city.label} · 정류장 ${detail.stops.size}곳", style = MaterialTheme.typography.bodySmall, color = Muted)
                    Text("${detail.route.from} → ${detail.route.to}", style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                itemsIndexed(detail.stops, key = { index, _ -> "route-stop:$index" }) { index, stop ->
                    RouteStopRow(stop, index, detail.stops.size, state.favorites.any { it.key == stop.key }, { onStop(stop) }, { onFavorite(stop) })
                }
                if (detail.stops.isEmpty()) item { EmptyState("정류장 정보가 없습니다", "다른 노선을 선택해 주세요.") }
            }
        } else if (state.query.isBlank()) {
            EmptyState("어디로 가시나요?", "안동역, 예천터미널 또는 버스 번호를 검색하세요.", Icons.Outlined.Search)
            if (state.recent.isNotEmpty()) {
                SectionTitle("최근 조회")
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    itemsIndexed(state.recent, key = { _, stop -> stop.key }) { index, stop ->
                        StopRow(stop, state.favorites.any { it.key == stop.key }, { onStop(stop) }, { onFavorite(stop) }, null, index, state.recent.size)
                    }
                }
            }
        } else {
            val count = if (state.searchMode == SearchMode.STOPS) state.stops.size else state.routes.size
            Text(if (state.searchMode == SearchMode.STOPS) "검색 결과 ${count}곳" else "검색 결과 ${count}개",
                style = MaterialTheme.typography.bodyMedium, color = Muted, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                if (state.searchMode == SearchMode.STOPS) itemsIndexed(state.stops, key = { _, stop -> stop.key }) { index, stop ->
                    StopRow(stop, state.favorites.any { it.key == stop.key }, { onStop(stop) }, { onFavorite(stop) }, null, index, state.stops.size)
                } else itemsIndexed(state.routes, key = { _, route -> route.key }) { index, route ->
                    RouteResultRow(route, index, state.routes.size) { onRoute(route) }
                }
                if (count == 0 && !state.searching && state.searchError == null && state.searchNotice == null) item {
                    EmptyState("검색 결과가 없습니다", "정류장 이름이나 버스 번호를 다시 확인해 주세요.", Icons.Outlined.SearchOff)
                }
            }
        }
    }
}
