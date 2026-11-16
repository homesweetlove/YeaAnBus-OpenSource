package kr.yeaan.bus.notification

import android.content.Context
import androidx.work.*
import kr.yeaan.bus.YeaAnBusApp
import kr.yeaan.bus.algorithm.ArrivalText
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/** On-demand preview hook. WorkManager periodic work has a 15 minute minimum and cannot
 * guarantee a five-minute arrival alert. Foreground polling stays in the ViewModel.
 * This worker returns eligibility, but never silently registers a repeating notification. */
class ArrivalCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = (applicationContext as YeaAnBusApp).repository
        val stopJson = inputData.getString("stop") ?: return Result.failure()
        val routeId = inputData.getString("routeId") ?: return Result.failure()
        val stop = runCatching { com.google.gson.Gson().fromJson(stopJson, kr.yeaan.bus.data.model.Stop::class.java) }.getOrNull()
            ?: return Result.failure()
        return try {
            val snapshot = repository.refresh(stop)
            val eta = snapshot.arrivals.filter { it.route.id == routeId }.mapNotNull { it.apiSeconds }.minOrNull()
            Result.success(workDataOf(
                "eligible" to (!snapshot.cached && eta != null && eta in 0..300),
                "etaSeconds" to (eta ?: -1), "message" to ArrivalText.time(eta, snapshot.cached)
            ))
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { if (runAttemptCount < 2) Result.retry() else Result.failure() }
    }
}
object ArrivalWork {
    fun enqueueCheck(context: Context, stop: kr.yeaan.bus.data.model.Stop, routeId: String) {
        val request = OneTimeWorkRequestBuilder<ArrivalCheckWorker>()
            .setInputData(workDataOf("stop" to com.google.gson.Gson().toJson(stop), "routeId" to routeId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork("arrival:${stop.key}:$routeId", ExistingWorkPolicy.KEEP, request)
    }
}
