package kr.yeaan.bus.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import androidx.work.*
import kr.yeaan.bus.MainActivity
import kr.yeaan.bus.R
import kr.yeaan.bus.YeaAnBusApp
import kr.yeaan.bus.data.model.FavoriteArrival
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object FavoriteText {
    private fun time(value: Long) = SimpleDateFormat("HH:mm", Locale.KOREA).format(Date(value))
    fun updated(row: FavoriteArrival): String = row.snapshot?.let {
        val stamp = SimpleDateFormat("MM/dd HH:mm", Locale.KOREA).format(Date(it.fetchedAt))
        "$stamp 조회" + if (it.cached || System.currentTimeMillis() - it.fetchedAt > 90_000) " · 이전 정보, 새로고침 필요" else ""
    } ?: row.error ?: "조회 대기 중"
    fun lines(row: FavoriteArrival, relative: Boolean = false): String {
        val snapshot = row.snapshot ?: return row.error ?: "도착 정보 조회 중"
        if (snapshot.arrivals.isEmpty()) return if (snapshot.cached) "이전 조회에 도착 정보 없음" else "현재 제공되는 도착 정보 없음"
        val now = System.currentTimeMillis()
        return snapshot.arrivals.sortedBy { it.apiSeconds ?: Int.MAX_VALUE }.take(3).joinToString("\n") { bus ->
            val seconds = bus.apiSeconds
            val eta = if (seconds != null) {
                val target = snapshot.fetchedAt + seconds * 1000L
                if (relative && !snapshot.cached && now - snapshot.fetchedAt <= 90_000 && target >= now)
                    if (target - now < 60_000) "곧 도착" else "약 ${(target - now + 59_999) / 60_000}분"
                else "${time(target)} 도착 예상" + if (target < now) " · 재조회 필요" else ""
            } else bus.stopsAway?.let { "${it}개 정류장 전" } ?: bus.status.ifBlank { "시간 미제공" }
            "${bus.route.number}번 · $eta"
        }
    }
}

open class FavoriteWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context)
        schedule(context)
        refresh(context)
    }
    override fun onEnabled(context: Context) { schedule(context) }
    override fun onDisabled(context: Context) {
        if (ids(context).isNotEmpty()) return
        WorkManager.getInstance(context).cancelUniqueWork("favorite-widget-periodic")
        WorkManager.getInstance(context).cancelUniqueWork("favorite-widget-refresh")
    }
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val editor = preferences(context).edit()
        appWidgetIds.forEach { editor.remove("stops:$it").remove("dark:$it") }
        editor.apply()
    }
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == REFRESH) refresh(context)
    }
    companion object {
        private const val REFRESH = "kr.yeaan.bus.REFRESH_FAVORITES"
        fun preferences(context: Context) = context.getSharedPreferences("favorite-widget", Context.MODE_PRIVATE)
        fun providers() = listOf(FavoriteWidgetSmall::class.java, FavoriteWidgetMedium::class.java, FavoriteWidget::class.java)
        fun ids(context: Context) = providers().flatMap {
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, it)).toList()
        }.toIntArray()
        fun capacity(context: Context, id: Int): Int = when (AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider?.className) {
            FavoriteWidgetSmall::class.java.name -> 1
            FavoriteWidgetMedium::class.java.name -> 2
            else -> 3
        }
        fun publish(context: Context, rows: List<FavoriteArrival>) {
            preferences(context).edit().putString("rows", com.google.gson.Gson().toJson(rows)).apply()
            render(context)
        }
        fun render(context: Context) {
            val prefs = preferences(context)
            val rows = runCatching {
                com.google.gson.Gson().fromJson(prefs.getString("rows", "[]"), Array<FavoriteArrival>::class.java).toList()
            }.getOrDefault(emptyList())
            val manager = AppWidgetManager.getInstance(context)
            ids(context).forEach { id ->
                val count = capacity(context, id)
                val selected = prefs.getStringSet("stops:$id", null)
                val displayed = rows.filter { selected == null || it.stop.key in selected }.take(count)
                val dark = prefs.getBoolean("dark:$id", false)
                val text = widgetText(displayed, count, dark)
                val layout = when (count) { 1 -> R.layout.favorite_widget_small; 2 -> R.layout.favorite_widget_medium; else -> R.layout.favorite_widget }
                val views = RemoteViews(context.packageName, layout)
                views.setTextViewText(R.id.widget_arrivals, text)
                val secondary = if (dark) Color.rgb(63, 78, 93) else Color.rgb(205, 218, 232)
                listOf(R.id.widget_title, R.id.widget_refresh, R.id.widget_settings).forEach { views.setTextColor(it, secondary) }
                views.setOnClickPendingIntent(R.id.widget_arrivals, PendingIntent.getActivity(context, 10,
                    Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                views.setOnClickPendingIntent(R.id.widget_settings, PendingIntent.getActivity(context, id,
                    Intent(context, WidgetSettingsActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                views.setOnClickPendingIntent(R.id.widget_refresh, PendingIntent.getBroadcast(context, 11,
                    Intent(context, FavoriteWidget::class.java).setAction(REFRESH), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                manager.updateAppWidget(id, views)
            }
        }
        private fun widgetText(rows: List<FavoriteArrival>, capacity: Int, dark: Boolean): CharSequence {
            val nameColor = if (dark) Color.rgb(24, 35, 48) else Color.WHITE
            val routeColor = if (dark) Color.rgb(63, 78, 93) else Color.rgb(205, 218, 232)
            val etaColor = if (dark) Color.rgb(0, 78, 105) else Color.rgb(154, 237, 220)
            val result = SpannableStringBuilder()
            fun append(text: String, color: Int, size: Float, bold: Boolean = false) {
                val start = result.length
                result.append(text)
                result.setSpan(ForegroundColorSpan(color), start, result.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                result.setSpan(RelativeSizeSpan(size), start, result.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (bold) result.setSpan(StyleSpan(Typeface.BOLD), start, result.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (rows.isEmpty()) append("설정에서 즐겨찾기 정류장을 선택해 주세요.", routeColor, 1f)
            rows.forEachIndexed { index, row ->
                if (index > 0) result.append("\n\n")
                append(row.stop.name, nameColor, 1.35f, true)
                FavoriteText.lines(row).lineSequence().take(if (capacity == 3) 2 else 1).forEach { line ->
                    result.append("\n")
                    val split = line.indexOf(" · ")
                    if (split > 0 && line.substring(0, split).endsWith("번")) {
                        append(line.substring(0, split), routeColor, .95f)
                        append("  ", routeColor, 1f)
                        append(line.substring(split + 3), etaColor, 1.15f, true)
                    } else append(line, routeColor, 1f)
                }
                if (row.snapshot?.cached == true) {
                    result.append("\n")
                    append("이전 정보 · 새로고침 필요", routeColor, .85f)
                }
            }
            return result
        }
        fun refresh(context: Context) {
            if (ids(context).isEmpty()) return
            WorkManager.getInstance(context).enqueueUniqueWork("favorite-widget-refresh", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<FavoriteWidgetWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        }
        private fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("favorite-widget-periodic", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<FavoriteWidgetWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        }
    }
}
class FavoriteWidgetSmall : FavoriteWidget()
class FavoriteWidgetMedium : FavoriteWidget()

class FavoriteWidgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val rows = (applicationContext as YeaAnBusApp).repository.favoriteArrivals()
        FavoriteWidget.publish(applicationContext, rows)
        Result.success()
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { Result.failure() }
}
