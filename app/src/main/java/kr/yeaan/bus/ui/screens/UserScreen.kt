package kr.yeaan.bus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kr.yeaan.bus.BuildConfig
import kr.yeaan.bus.data.model.Stop
import kr.yeaan.bus.ui.UiState
import kr.yeaan.bus.ui.components.EtaValue
import kr.yeaan.bus.ui.components.Panel
import kr.yeaan.bus.ui.components.PlateSize
import kr.yeaan.bus.ui.components.RoutePlate
import kr.yeaan.bus.ui.components.SectionTitle
import kr.yeaan.bus.ui.components.Segmented
import kr.yeaan.bus.ui.components.StopRow
import kr.yeaan.bus.ui.settings.Accent
import kr.yeaan.bus.ui.settings.Appearance
import kr.yeaan.bus.ui.settings.TextSize
import kr.yeaan.bus.ui.settings.UserSettings
import kr.yeaan.bus.ui.theme.Ink
import kr.yeaan.bus.ui.theme.Line
import kr.yeaan.bus.ui.theme.Muted
import kr.yeaan.bus.ui.theme.Navy
import kr.yeaan.bus.ui.theme.appColors
import kr.yeaan.bus.ui.theme.boardColors

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun UserScreen(state: UiState, settings: UserSettings, onSettings: (UserSettings) -> Unit,
               onStop: (Stop) -> Unit, onFavorite: (Stop) -> Unit,
               commuteActive: Boolean, onCommute: () -> Unit, onAddWidget: (Int) -> Unit) {
    var showThemes by rememberSaveable { mutableStateOf(false) }
    if (showThemes) {
        ModalBottomSheet(onDismissRequest = { showThemes = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.background) {
            val dark = settings.appearance == Appearance.DARK || (settings.appearance == Appearance.SYSTEM && isSystemInDarkTheme())
            val board = boardColors(dark)
            LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text("테마 고르기", style = MaterialTheme.typography.headlineMedium)
                    Text("버스 번호판과 버튼 색이 바로 바뀌어요.", Modifier.padding(top = 2.dp, bottom = 6.dp), color = Muted)
                }
                items(Accent.entries.toList()) { accent ->
                    val palette = appColors(accent, dark)
                    val selected = accent == settings.accent
                    val shape = MaterialTheme.shapes.medium
                    Row(Modifier.fillMaxWidth().clip(shape).background(palette.surface)
                        .border(2.dp, if (selected) palette.primary else palette.surface, shape)
                        .selectable(selected, role = Role.RadioButton, onClick = { onSettings(settings.copy(accent = accent)) })
                        .padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        // A small arrival board in this theme's colors.
                        Row(Modifier.clip(RoundedCornerShape(14.dp)).background(board.panel).padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(width = 38.dp, height = 24.dp).background(palette.primary, RoundedCornerShape(7.dp)))
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.size(width = 18.dp, height = 8.dp).background(board.signal, RoundedCornerShape(4.dp)))
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(accent.label, Modifier.weight(1f), color = palette.onSurface, style = MaterialTheme.typography.titleMedium)
                        if (selected) Icon(Icons.Outlined.Check, "선택됨", tint = palette.primary)
                    }
                }
                item {
                    Button(onClick = { showThemes = false }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 54.dp),
                        shape = RoundedCornerShape(16.dp)) { Text("선택 완료") }
                }
            }
        }
    }
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp)) {
        item {
            Text("나의 버스 생활", style = MaterialTheme.typography.headlineMedium)
            Text("즐겨찾기 ${state.favorites.size}곳 · 최근 조회 ${state.recent.size}곳", Modifier.padding(top = 2.dp),
                color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
        item {
            SectionTitle("화면")
            Panel {
                Row(Modifier.fillMaxWidth().clickable(onClickLabel = "테마 고르기") { showThemes = true }.heightIn(min = 64.dp)
                    .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("테마 변경", style = MaterialTheme.typography.bodyLarge)
                        Text(settings.accent.label, color = Navy, style = MaterialTheme.typography.labelLarge)
                    }
                    Box(Modifier.size(22.dp).background(Navy, CircleShape))
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Outlined.ChevronRight, null, tint = Muted)
                }
                HorizontalDivider(Modifier.padding(start = 16.dp), color = Line)
                SettingBlock("화면 모드", "휴대폰 설정에 맞추거나 직접 고르세요") {
                    Segmented(Appearance.entries, settings.appearance, { it.label }, { onSettings(settings.copy(appearance = it)) })
                }
                HorizontalDivider(Modifier.padding(start = 16.dp), color = Line)
                SettingBlock("글자 크기", "휴대폰의 글자 크기 설정에 더해서 적용돼요") {
                    Segmented(TextSize.entries, settings.textSize, { it.label }, { onSettings(settings.copy(textSize = it)) })
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RoutePlate("235", PlateSize.SMALL)
                        Spacer(Modifier.width(10.dp))
                        Text("미리보기", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Muted)
                        EtaValue(3, false, null, big = false, color = Ink, labelColor = Muted)
                    }
                }
            }
        }
        item {
            SectionTitle("출퇴근 도착 정보")
            Panel {
                SettingBlock("잠금화면에서 바로 확인", "즐겨찾기 최대 3곳을 약 30초 간격으로 갱신합니다. 데이터 절약 시 60초이며, 1시간 뒤 자동 종료됩니다. 휴대폰의 절전·알림 설정에 따라 갱신과 잠금화면 표시가 제한될 수 있어요.") {
                    Button(onClick = onCommute, enabled = commuteActive || state.favorites.isNotEmpty(),
                        modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                        Text(if (commuteActive) "실시간 보기 종료" else "출퇴근 실시간 보기 시작")
                    }
                    Text("알림을 펼치면 정류장별 버스를 볼 수 있습니다.", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                HorizontalDivider(Modifier.padding(start = 16.dp), color = Line)
                SettingBlock("홈 화면 위젯", "즐겨찾기 3곳의 예상 도착 시각을 표시합니다. 평소에는 약 15분 이상 간격으로 갱신하며, 실시간 보기 중에는 알림과 함께 갱신합니다. 위젯의 새로고침으로 직접 조회할 수도 있어요.") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("소형 · 1곳", "중형 · 2곳", "대형 · 3곳").forEachIndexed { index, label ->
                            OutlinedButton(onClick = { onAddWidget(index) }, shape = RoundedCornerShape(14.dp)) { Text(label) }
                        }
                    }
                    Text("배경은 투명합니다. 위젯의 설정에서 정류장과 글자색을 바꿀 수 있어요.", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
        }
        item {
            SectionTitle("이용 설정")
            Panel {
                SwitchRow("데이터 절약", "자동 버스 조회를 최소 30초 간격으로 줄입니다. 도착 안내 갱신이 느려질 수 있어요.",
                    settings.dataSaver) { onSettings(settings.copy(dataSaver = it)) }
                HorizontalDivider(Modifier.padding(start = 16.dp), color = Line)
                SwitchRow("지도 화면 켜짐 유지", "지도를 보는 동안 화면이 자동으로 꺼지지 않습니다. 배터리 사용량이 늘 수 있어요.",
                    settings.keepMapAwake) { onSettings(settings.copy(keepMapAwake = it)) }
            }
        }
        if (state.favorites.isNotEmpty()) {
            item { SectionTitle("내 즐겨찾기") }
            itemsIndexed(state.favorites, key = { _, stop -> stop.key }) { index, stop ->
                StopRow(stop, true, { onStop(stop) }, { onFavorite(stop) }, null, index, state.favorites.size)
            }
        }
        item {
            Spacer(Modifier.height(28.dp))
            Text("예안버스 ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelLarge, color = Navy)
            Text("안동 BIS · TAGO 버스정보 / NAVER 지도\n도착 정보는 운행 상황과 제공기관에 따라 달라집니다.",
                Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

@Composable
private fun SettingBlock(title: String, description: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        content()
    }
}

@Composable
private fun SwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.semantics { contentDescription = title })
    }
}
