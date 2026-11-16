package kr.yeaan.bus.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kr.yeaan.bus.YeaAnBusApp
import kr.yeaan.bus.ui.components.Panel
import kr.yeaan.bus.ui.components.Segmented
import kr.yeaan.bus.ui.theme.YeaAnTheme

class WidgetSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID || info?.provider?.className !in FavoriteWidget.providers().map { it.name }) {
            finish(); return
        }
        val prefs = FavoriteWidget.preferences(this)
        val capacity = FavoriteWidget.capacity(this, id)
        setContent {
            YeaAnTheme {
                val favorites by (application as YeaAnBusApp).repository.favorites.collectAsState(initial = null)
                var selected by remember { mutableStateOf(prefs.getStringSet("stops:$id", emptySet())!!.toSet()) }
                var dark by remember { mutableStateOf(prefs.getBoolean("dark:$id", false)) }
                LaunchedEffect(favorites) {
                    favorites?.let { stops -> selected = selected.intersect(stops.map { it.key }.toSet()) }
                }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)
                        .verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("위젯 설정", style = MaterialTheme.typography.headlineMedium)
                        Text("${when(capacity) { 1 -> "소형"; 2 -> "중형"; else -> "대형" }} · 정류장 최대 ${capacity}곳",
                            style = MaterialTheme.typography.titleMedium)
                        Text("이 위젯에 표시할 즐겨찾기를 선택하세요. 다른 위젯의 설정은 바뀌지 않습니다.")
                        if (favorites == null) CircularProgressIndicator()
                        else if (favorites!!.isEmpty()) Text("앱에서 정류장을 즐겨찾기에 추가한 뒤 위젯을 설정해 주세요.")
                        favorites?.takeIf { it.isNotEmpty() }?.let { stops ->
                            Panel {
                                stops.forEach { stop ->
                                    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        Checkbox(checked = stop.key in selected,
                                            enabled = stop.key in selected || selected.size < capacity,
                                            onCheckedChange = { checked -> selected = if (checked) selected + stop.key else selected - stop.key })
                                        Column(Modifier.weight(1f)) {
                                            Text(stop.name, style = MaterialTheme.typography.bodyLarge)
                                            Text("${stop.city.label} · ${stop.code}", style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                        Text("배경은 완전히 투명하게 표시됩니다.")
                        Text("배경화면에 맞는 글자색", style = MaterialTheme.typography.titleMedium)
                        Segmented(listOf(false, true), dark, { if (it) "어두운 글자" else "흰색 글자" }, { dark = it })
                        Text("정류장 이름과 버스 예상 도착 시각을 구분해 표시합니다. 추가 후 위젯의 설정 버튼으로 다시 바꿀 수 있어요.",
                            style = MaterialTheme.typography.bodySmall)
                        Button(modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), enabled = favorites != null && selected.isNotEmpty() && selected.size <= capacity,
                            onClick = {
                                prefs.edit().putStringSet("stops:$id", selected).putBoolean("dark:$id", dark).apply()
                                FavoriteWidget.render(this@WidgetSettingsActivity)
                                FavoriteWidget.refresh(this@WidgetSettingsActivity)
                                setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                                finish()
                            }) { Text("이 위젯에 적용 (${selected.size}곳)") }
                        TextButton(onClick = { finish() }) { Text("취소") }
                    }
                }
            }
        }
    }
}
