package com.keepdev.routeemock

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.location.LocationCompat
import androidx.core.location.altitude.AltitudeConverterCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 화면에 보여줄 서비스 상태 */
data class MockStatus(
    val running: Boolean = false,
    val route: MockRoute? = null,
    val playing: Boolean = false,
    val traveledMeters: Double = 0.0,
    val lastFix: Fix? = null,
    val maxAltitude: Double = 0.0,
    val sending: Boolean = true,
    val error: String? = null,
)

/** 화면과 서비스가 같이 쓰는 엔진과 상태 */
object MockController {
    val engine = RouteEngine()
    private val _status = MutableStateFlow(MockStatus())
    val status: StateFlow<MockStatus> = _status

    /** [route]를 처음부터 재생 */
    fun play(context: Context, route: MockRoute, settings: MockSettings) {
        engine.route = route.points
        engine.settings = settings
        engine.playing = true
        publish { MockStatus(running = it.running, route = route) }
        MockService.start(context)
    }

    internal fun publish(update: (MockStatus) -> MockStatus) {
        _status.value = update(_status.value)
    }
}

/**
 * 1초마다 엔진에서 위치를 받아 휴대폰 위치 경로(gps, network)와 구글 위치 서비스에 넣는다.
 * 루티 안드는 구글 위치 서비스로 받으므로 두 번째가 핵심.
 */
class MockService : Service() {
    // 화면도 메인 스레드에서 엔진을 바꾸므로 같은 스레드에서 돌린다
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loop: Job? = null
    private lateinit var locationManager: LocationManager
    private lateinit var fused: FusedLocationProviderClient

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        fused = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground()
        if (loop == null) startLoop()
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLoop() {
        val error = runCatching {
            PROVIDERS.forEach { addProvider(it) }
            fused.setMockMode(true)
        }.exceptionOrNull()
        if (error != null) {
            MockController.publish { it.copy(error = "모의 위치 앱으로 지정되지 않았어요: ${error.message}") }
            stopSelf()
            return
        }
        MockController.publish { it.copy(running = true, error = null) }

        loop = scope.launch {
            val engine = MockController.engine
            while (isActive) {
                val fix = engine.tick(1.0)
                if (fix != null) withContext(Dispatchers.IO) { push(fix) }
                MockController.publish {
                    it.copy(
                        playing = engine.playing,
                        traveledMeters = engine.traveled,
                        lastFix = fix ?: it.lastFix,
                        maxAltitude = maxOf(it.maxAltitude, fix?.altitude ?: 0.0),
                        sending = fix != null,
                    )
                }
                delay(1_000)
            }
        }
    }

    /**
     * 진짜 GPS처럼 고도를 WGS84 타원체 기준으로 보낸다 (경로 고도는 해수면 기준이라 지오이드 높이를 더함).
     * 지오이드 모델을 디스크에서 읽으므로 IO에서 부름
     */
    @SuppressLint("MissingPermission")
    private fun push(fix: Fix) {
        val ellipsoidAltitude = fix.altitude + Geoid.heightAt(this, fix.lat, fix.lng)
        PROVIDERS.forEach { provider ->
            runCatching { locationManager.setTestProviderLocation(provider, fix.toLocation(provider, ellipsoidAltitude)) }
        }
        runCatching { fused.setMockLocation(fix.toLocation("fused", ellipsoidAltitude)) }
    }

    private fun addProvider(name: String) {
        runCatching { locationManager.removeTestProvider(name) }
        @Suppress("DEPRECATION")
        locationManager.addTestProvider(
            name, false, false, false, false, true, true, true,
            android.location.Criteria.POWER_LOW, android.location.Criteria.ACCURACY_FINE,
        )
        locationManager.setTestProviderEnabled(name, true)
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        loop?.cancel()
        scope.cancel()
        runCatching { fused.setMockMode(false) }
        PROVIDERS.forEach { runCatching { locationManager.removeTestProvider(it) } }
        MockController.engine.playing = false
        MockController.publish { it.copy(running = false, playing = false, route = null) }
        super.onDestroy()
    }

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "모의 위치", NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, MockService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("루티 모의위치 실행 중")
            .setContentText("가짜 위치를 보내고 있어요")
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "끄기", stop).build())
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL = "mock"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "stop"
        private val PROVIDERS = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)

        fun start(context: Context) {
            context.startForegroundService(Intent(context, MockService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MockService::class.java))
        }

        /**
         * 남아 있는 가짜 위치를 모두 걷어낸다. 앱이 재생 중에 강제 종료됐거나
         * 다른 모의 위치 앱(위치 모방자 등)이 만든 "test" 경로가 남은 경우용
         */
        @SuppressLint("MissingPermission")
        fun resetToReal(context: Context) {
            stop(context)
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            (PROVIDERS + LEFTOVER_PROVIDERS).forEach { runCatching { manager.removeTestProvider(it) } }
            runCatching { LocationServices.getFusedLocationProviderClient(context).setMockMode(false) }
        }

        private val LEFTOVER_PROVIDERS = listOf(LocationManager.PASSIVE_PROVIDER, "fused", "test")
    }
}

/** 한 지점의 지오이드 높이 N (타원체 고도 = 해수면 고도 + N), 한국은 약 +25m */
private object Geoid {
    private val cache = HashMap<Long, Double>()

    fun heightAt(context: Context, lat: Double, lng: Double): Double {
        // 지오이드는 완만해서 약 1km 격자로 묶어 캐시
        val key = (lat * 100).toLong() * 100_000 + (lng * 100).toLong()
        return cache.getOrPut(key) {
            // 타원체 고도 0인 위치를 해수면 기준으로 바꾸면 MSL = -N
            val probe = Location("geoid").apply {
                latitude = lat
                longitude = lng
                altitude = 0.0
            }
            runCatching {
                AltitudeConverterCompat.addMslAltitudeToLocation(context, probe)
                -LocationCompat.getMslAltitudeMeters(probe)
            }.getOrDefault(0.0)
        }
    }
}

private fun Fix.toLocation(provider: String, ellipsoidAltitude: Double) = Location(provider).apply {
    latitude = lat
    longitude = lng
    altitude = ellipsoidAltitude
    accuracy = this@toLocation.accuracy
    verticalAccuracyMeters = verticalAccuracy
    speed = this@toLocation.speed
    bearing = this@toLocation.bearing
    time = System.currentTimeMillis()
    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
}
