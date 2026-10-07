package com.example.todoapplication.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.todoapplication.R
import com.example.todoapplication.data.local.AppDatabase
import com.example.todoapplication.data.local.PlaceEntity
import com.example.todoapplication.data.local.TaskLocationEntity
import com.example.todoapplication.data.notifications.Notifications
import com.example.todoapplication.di.ServiceLocator
import com.example.todoapplication.ui.navigation.AppIntents
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume
import com.example.todoapplication.i18n.str

/*
 * [TẦNG DATA · VỊ TRÍ] Nhắc việc theo địa điểm ("khi đến công ty", "khi rời nhà") bằng Geofencing API của
 * Google Play services: hệ thống tự theo dõi ra/vào vùng (tiết kiệm pin hơn app tự đọc GPS) và đánh thức app
 * qua BroadcastReceiver. Cần quyền vị trí CHÍNH XÁC + vị trí NỀN (Android 10+, người dùng cấp riêng).
 * Địa điểm và nhắc theo địa điểm chỉ lưu trên máy (không đồng bộ) — vị trí là dữ liệu nhạy cảm.
 */

object LocationTrigger {
    const val ENTER = "ENTER"
    const val EXIT = "EXIT"
}

class PlaceRepository(private val db: AppDatabase, private val onChanged: () -> Unit) {
    private val dao = db.placeDao()

    fun observePlaces(): Flow<List<PlaceEntity>> = dao.observeAll()
    fun observeForTask(taskId: String): Flow<TaskLocationEntity?> = dao.observeForTask(taskId)

    suspend fun addPlace(name: String, latitude: Double, longitude: Double, radiusMeters: Int): PlaceEntity {
        val place = PlaceEntity(UUID.randomUUID().toString(), name.trim().take(40), latitude, longitude, radiusMeters.coerceIn(100, 2000))
        dao.upsert(place)
        onChanged()
        return place
    }

    suspend fun deletePlace(id: String) {
        dao.delete(id) // nhắc theo địa điểm này tự xóa theo (CASCADE)
        onChanged()
    }

    suspend fun setTaskLocation(taskId: String, placeId: String, trigger: String) {
        dao.setTaskLocation(TaskLocationEntity(taskId, placeId, trigger))
        onChanged()
    }

    suspend fun clearTaskLocation(taskId: String) {
        dao.clearTaskLocation(taskId)
        onChanged()
    }
}

object LocationReminders {
    private const val TAG = "LocationReminders"
    private const val REQUEST_CODE = 9201
    /** Geofencing cho phép tối đa 100 vùng mỗi app. */
    private const val MAX_FENCES = 100

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var lastSignature: String? = null

    fun hasForegroundPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasBackgroundPermission(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST_CODE, Intent(context, GeofenceReceiver::class.java),
        // Geofencing gắn dữ liệu sự kiện vào Intent → bắt buộc MUTABLE
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )

    /**
     * Đăng ký lại geofence theo các nhắc theo địa điểm của việc còn chờ. Gọi sau mọi thay đổi (đặt / bỏ nhắc,
     * xóa địa điểm, hoàn thành việc), khi mở app và khi khởi động lại máy (hệ thống xóa geofence lúc reboot).
     * Bỏ qua nếu tập vùng không đổi so với lần trước.
     */
    fun sync(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        scope.launch {
            val reminders = runCatching { ServiceLocator.database.placeDao().activeReminders() }.getOrDefault(emptyList())
            val fences = reminders.distinctBy { it.placeId + "|" + it.trigger }.take(MAX_FENCES)
            val signature = fences.joinToString { "${it.placeId}|${it.trigger}|${it.latitude},${it.longitude},${it.radiusMeters}" }
            if (!force && signature == lastSignature) return@launch
            val client = LocationServices.getGeofencingClient(app)
            client.removeGeofences(pendingIntent(app))
            lastSignature = signature
            if (fences.isEmpty() || !hasForegroundPermission(app) || !hasBackgroundPermission(app)) return@launch

            val geofences = fences.map {
                Geofence.Builder()
                    .setRequestId(it.placeId + "|" + it.trigger)
                    .setCircularRegion(it.latitude, it.longitude, it.radiusMeters.toFloat())
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(
                        if (it.trigger == LocationTrigger.EXIT) Geofence.GEOFENCE_TRANSITION_EXIT else Geofence.GEOFENCE_TRANSITION_ENTER
                    )
                    .build()
            }
            val request = GeofencingRequest.Builder()
                .setInitialTrigger(0) // không báo ngay khi đang ở sẵn trong vùng
                .addGeofences(geofences)
                .build()
            @SuppressLint("MissingPermission") // đã kiểm tra quyền ở trên
            val task = client.addGeofences(request, pendingIntent(app))
            task.addOnFailureListener {
                Log.w(TAG, "addGeofences failed: ${it.message}")
                lastSignature = null
            }
        }
    }

    /** Vị trí hiện tại (một lần, độ chính xác cao) — để lưu "Nhà", "Công ty". Chưa có quyền / lỗi → null. */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(context: Context): android.location.Location? {
        if (!hasForegroundPermission(context)) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        val token = CancellationTokenSource()
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { token.cancel() }
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        }
    }
}

/** Hệ thống báo vào / ra vùng → hiện thông báo cho từng việc gắn với địa điểm đó. */
class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        val trigger = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> LocationTrigger.ENTER
            Geofence.GEOFENCE_TRANSITION_EXIT -> LocationTrigger.EXIT
            else -> return
        }
        val ids = event.triggeringGeofences.orEmpty().map { it.requestId }.toSet()
        val app = context.applicationContext
        val pending = goAsync()
        scope.launch {
            try {
                if (!ServiceLocator.sessionManager.isLoggedIn()) return@launch
                ServiceLocator.database.placeDao().activeReminders()
                    .filter { it.trigger == trigger && (it.placeId + "|" + it.trigger) in ids }
                    .forEach { r ->
                        notify(app, r.taskId, r.title, str(if (trigger == LocationTrigger.ENTER) R.string.location_arrived else R.string.location_left, r.placeName))
                    }
            } finally {
                pending.finish()
            }
        }
    }

    private fun notify(context: Context, taskId: String, title: String, place: String) {
        Notifications.ensureChannels(context)
        if (!Notifications.canPost(context)) return
        val id = taskId.hashCode() xor 0x10CA_0000
        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_task)
            .setContentTitle(place)
            .setContentText(title)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(Notifications.openApp(context, id, AppIntents.openTask(taskId)))
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
