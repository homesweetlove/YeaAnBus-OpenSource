package kr.yeaan.bus.notification

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import kr.yeaan.bus.MainActivity
import kr.yeaan.bus.R
import kr.yeaan.bus.YeaAnBusApp
import kr.yeaan.bus.widget.FavoriteText
import kr.yeaan.bus.widget.FavoriteWidget
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** User-started, bounded commute session. Never starts from boot or a background worker. */
class CommuteService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var deadline = 0L
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "출퇴근 버스 실시간", NotificationManager.IMPORTANCE_LOW).apply {
                description = "선택한 즐겨찾기 정류장의 도착 정보"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            })
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { stopSelf(); return START_NOT_STICKY }
        if (job?.isActive == true) return START_NOT_STICKY
        deadline = SystemClock.elapsedRealtime() + 60 * 60_000L
        startForeground(ID, notification("즐겨찾기 도착 정보를 불러오는 중…"))
        mutableActive.value = true
        job = scope.launch {
            try {
                withTimeout(60 * 60_000L) {
                    while (isActive && SystemClock.elapsedRealtime() < deadline) {
                        try {
                            val rows = (application as YeaAnBusApp).repository.favoriteArrivals()
                            if (rows.isEmpty()) break
                            FavoriteWidget.publish(this@CommuteService, rows)
                            val body = rows.joinToString("\n\n") {
                                "${it.stop.name}\n${FavoriteText.lines(it, relative = true)}\n${FavoriteText.updated(it)}"
                            }
                            getSystemService(NotificationManager::class.java).notify(ID, notification(body))
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) {
                            getSystemService(NotificationManager::class.java).notify(ID, notification("조회가 지연되고 있습니다. 다음 갱신을 기다려 주세요."))
                        }
                        val saver = getSharedPreferences("user-settings", MODE_PRIVATE).getBoolean("dataSaver", false)
                        delay(if (saver) 60_000L else 30_000L)
                    }
                }
            } finally { stopSelf() }
        }
        return START_NOT_STICKY
    }
    private fun notification(body: String): Notification {
        val open = PendingIntent.getActivity(this, 20, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 21, Intent(this, CommuteService::class.java).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_bus_notification)
            .setContentTitle("출퇴근 버스 · 최대 1시간")
            .setContentText(body.lineSequence().take(2).joinToString(" · "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, "실시간 보기 종료", stop).build()
    }
    override fun onTimeout(startId: Int, fgsType: Int) { stopSelf() }
    override fun onDestroy() {
        scope.cancel()
        mutableActive.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    companion object {
        private const val CHANNEL = "commute-arrivals"
        private const val ID = 3001
        const val STOP = "kr.yeaan.bus.STOP_COMMUTE"
        private val mutableActive = MutableStateFlow(false)
        val active = mutableActive.asStateFlow()
    }
}
