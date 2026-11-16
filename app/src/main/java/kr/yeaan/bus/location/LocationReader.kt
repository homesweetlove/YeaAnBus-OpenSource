package kr.yeaan.bus.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import android.os.CancellationSignal
import kr.yeaan.bus.data.model.Point
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object LocationReader {
    suspend fun current(context: Context): Point = withTimeout(20_000) {
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!coarse) throw IllegalStateException("위치 권한을 허용하면 가까운 정류장을 찾을 수 있습니다.")
        val manager = context.getSystemService(LocationManager::class.java)
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val provider = when {
            fine && manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> throw IllegalStateException("기기의 위치 서비스를 켜 주세요.")
        }
        suspendCancellableCoroutine { continuation ->
            val cancellation = CancellationSignal()
            continuation.invokeOnCancellation { cancellation.cancel() }
            LocationManagerCompat.getCurrentLocation(manager, provider, cancellation, ContextCompat.getMainExecutor(context)) { location ->
                if (continuation.isActive) {
                    if (location != null) continuation.resume(Point(location.latitude, location.longitude))
                    else continuation.resumeWithException(IllegalStateException("현재 위치를 찾지 못했습니다. 잠시 후 다시 시도해 주세요."))
                }
            }
        }
    }
}
