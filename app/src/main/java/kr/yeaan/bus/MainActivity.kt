package kr.yeaan.bus

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kr.yeaan.bus.notification.CommuteService
import kr.yeaan.bus.widget.FavoriteWidget
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.yeaan.bus.location.LocationReader
import kr.yeaan.bus.ui.*
import kr.yeaan.bus.ui.components.Notice
import kr.yeaan.bus.ui.screens.*
import kr.yeaan.bus.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: BusViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            YeaAnTheme(settings) { BusApp(vm) }
        }
    }
}
@Composable
private fun BusApp(vm: BusViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val view = LocalView.current
    val lightBars = MaterialTheme.colorScheme.background.luminance() > 0.5f
    SideEffect {
        (context as? android.app.Activity)?.let { activity ->
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = lightBars
            }
        }
    }
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.any { it }) {
            vm.locating()
            scope.launch {
                try { vm.located(LocationReader.current(context)) }
                catch (e: kotlinx.coroutines.TimeoutCancellationException) { vm.report("위치 확인이 지연됩니다. 정류장 검색도 이용할 수 있어요.") }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { vm.report(e.message ?: "현재 위치를 확인할 수 없습니다.") }
            }
        } else vm.report("위치 권한 없이도 정류장과 노선을 검색할 수 있습니다.")
    }
    val commuteActive by CommuteService.active.collectAsStateWithLifecycle()
    fun startCommute() {
        if (state.favorites.isEmpty()) { vm.report("즐겨찾기 정류장을 먼저 등록해 주세요."); return }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            vm.report("휴대폰 설정에서 예안버스 알림을 허용해 주세요."); return
        }
        try { ContextCompat.startForegroundService(context, Intent(context, CommuteService::class.java)) }
        catch (_: Exception) { vm.report("실시간 보기를 시작하지 못했습니다. 잠시 후 다시 시도해 주세요.") }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCommute() else vm.report("잠금화면 버스 정보에는 알림 권한이 필요합니다.")
    }
    LaunchedEffect(owner, state.page == Page.HOME, state.favorites.map { it.key }) {
        if (state.page == Page.HOME && state.favorites.isNotEmpty())
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.monitorFavorites() }
    }
    // Search does not consume live arrival requests; Home/arrival/map share one foreground loop.
    val monitoredStop = state.selectedStop.takeIf { state.page in setOf(Page.HOME, Page.ARRIVAL, Page.MAP) }
    LaunchedEffect(owner, monitoredStop?.key) {
        val stop = monitoredStop ?: return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.monitor(stop) }
    }
    BackHandler(enabled = state.page != Page.HOME) { vm.back() }
    Scaffold(containerColor = Paper, bottomBar = {
        BottomBar(state.page) { page -> if (page == Page.MAP) vm.map() else vm.page(page) }
    }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            state.error?.let { Notice(it, true) }
            Box(Modifier.weight(1f)) {
                when (state.page) {
                    Page.HOME -> HomeScreen(state, { vm.page(Page.SEARCH) },
                        { launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                        { vm.openStop(it) }, vm::favorite, { vm.map(it) })
                    Page.SEARCH -> SearchScreen(state, vm::query, vm::mode, { vm.openStop(it) }, vm::favorite, vm::openRoute, vm::back)
                    Page.ARRIVAL -> BusArrivalScreen(state, vm::back, vm::favorite, vm::refresh, vm::map)
                    Page.MAP -> MapScreen(state, vm::back, { vm.page(Page.SEARCH) }, vm::position, settings.keepMapAwake, { vm.openStop(it, navigate = false) }, vm::map, vm::loadMapStops)
                    Page.USER -> UserScreen(state, settings, vm::updateSettings, { vm.openStop(it) }, vm::favorite,
                        commuteActive, {
                            if (commuteActive) context.stopService(Intent(context, CommuteService::class.java))
                            else if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else startCommute()
                        }, { size ->
                            val manager = AppWidgetManager.getInstance(context)
                            if (manager.isRequestPinAppWidgetSupported)
                                manager.requestPinAppWidget(ComponentName(context, FavoriteWidget.providers()[size]), null, null)
                            else vm.report("홈 화면을 길게 누른 뒤 위젯 목록에서 예안버스를 추가해 주세요.")
                        })
                }
            }
        }
    }
}


private class NavTab(val page: Page, val label: String, val active: ImageVector, val idle: ImageVector)

@Composable
private fun BottomBar(current: Page, onSelect: (Page) -> Unit) {
    val tabs = listOf(
        NavTab(Page.HOME, "홈", Icons.Filled.Home, Icons.Outlined.Home),
        NavTab(Page.SEARCH, "검색", Icons.Filled.Search, Icons.Outlined.Search),
        NavTab(Page.MAP, "지도", Icons.Filled.Map, Icons.Outlined.Map),
        NavTab(Page.USER, "사용자", Icons.Filled.Person, Icons.Outlined.PersonOutline)
    )
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        HorizontalDivider(color = Line)
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).selectableGroup()) {
            tabs.forEach { tab ->
                val selected = current == tab.page || (tab.page == Page.HOME && current == Page.ARRIVAL)
                val tint = if (selected) Navy else Muted
                Column(Modifier.weight(1f).heightIn(min = 64.dp)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab.page) })
                    .padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(if (selected) tab.active else tab.idle, null, tint = tint)
                    Spacer(Modifier.height(3.dp))
                    Text(tab.label, color = tint, style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
    }
}
