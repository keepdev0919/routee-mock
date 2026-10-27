package com.keepdev.routeemock

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLng(val lat: Double, val lng: Double)

/** 경로 한 지점, [ele]는 GPX 고도 (m) */
data class RoutePoint(val lat: Double, val lng: Double, val ele: Double)

/** 한 번 보낼 가짜 위치 */
data class Fix(
    val lat: Double,
    val lng: Double,
    val altitude: Double,
    val accuracy: Float,
    val verticalAccuracy: Float,
    val speed: Float,
    val bearing: Float,
)

/** 경로 끝에 도착했을 때, 위치 모방자의 반복 옵션과 같음 */
enum class LoopMode { STOP, REVERSE, RESTART }

/** 재생 설정, 바꾸면 다음 틱부터 반영 */
data class MockSettings(
    val speedKmh: Float = 60f,
    val loopMode: LoopMode = LoopMode.STOP,
    val accuracy: Float = 5f,
    val verticalAccuracy: Float = 3f,
)

/**
 * 경로를 따라 1초마다 가짜 위치를 만든다. 화면과 서비스가 메인 스레드에서 공유.
 */
class RouteEngine {
    var route: List<RoutePoint> = emptyList()
        set(value) {
            field = value
            cumulative = cumulativeDistances(value)
            restart()
        }

    var settings = MockSettings()
    var playing = false
    var traveled = 0.0
        private set

    private var cumulative: List<Double> = emptyList()
    private var forward = true

    val totalMeters: Double get() = cumulative.lastOrNull() ?: 0.0
    val finished: Boolean get() = route.size >= 2 && forward && traveled >= totalMeters

    fun restart() {
        traveled = 0.0
        forward = true
    }

    /** [dtSeconds]만큼 진행하고 보낼 위치를 돌려준다 */
    fun tick(dtSeconds: Double): Fix? {
        if (route.isEmpty()) return null
        val speedMs = settings.speedKmh / 3.6
        if (playing && route.size >= 2) advance(speedMs * dtSeconds)

        val (point, bearing) = positionAt(traveled)
        return Fix(
            lat = point.lat,
            lng = point.lng,
            altitude = point.ele,
            accuracy = settings.accuracy,
            verticalAccuracy = settings.verticalAccuracy,
            speed = if (playing) speedMs.toFloat() else 0f,
            bearing = if (forward) bearing else (bearing + 180f) % 360f,
        )
    }

    private fun advance(meters: Double) {
        if (forward) {
            traveled += meters
            if (traveled < totalMeters) return
            when (settings.loopMode) {
                LoopMode.STOP -> { traveled = totalMeters; playing = false }
                LoopMode.REVERSE -> { traveled = totalMeters; forward = false }
                LoopMode.RESTART -> traveled = 0.0
            }
        } else {
            traveled -= meters
            if (traveled > 0.0) return
            traveled = 0.0
            forward = true
        }
    }

    /** 지금 위치와 진행 방향, 고도는 앞뒤 지점 사이를 보간 */
    private fun positionAt(meters: Double): Pair<RoutePoint, Float> {
        if (route.size == 1) return route[0] to 0f
        val i = cumulative.indexOfFirst { it >= meters }.let { if (it <= 0) 1 else it }
        val a = route[i - 1]
        val b = route[i]
        val segment = cumulative[i] - cumulative[i - 1]
        val f = if (segment <= 0.0) 0.0 else ((meters - cumulative[i - 1]) / segment).coerceIn(0.0, 1.0)
        val point = RoutePoint(
            lat = a.lat + (b.lat - a.lat) * f,
            lng = a.lng + (b.lng - a.lng) * f,
            ele = a.ele + (b.ele - a.ele) * f,
        )
        return point to bearing(a, b)
    }
}

private const val EARTH_RADIUS = 6_371_008.8

fun distanceMeters(a: RoutePoint, b: RoutePoint): Double {
    val lat1 = Math.toRadians(a.lat)
    val lat2 = Math.toRadians(b.lat)
    val dLat = lat2 - lat1
    val dLng = Math.toRadians(b.lng - a.lng)
    val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLng / 2) * sin(dLng / 2)
    return 2 * EARTH_RADIUS * atan2(sqrt(h), sqrt(max(0.0, 1 - h)))
}

private fun bearing(a: RoutePoint, b: RoutePoint): Float {
    val lat1 = Math.toRadians(a.lat)
    val lat2 = Math.toRadians(b.lat)
    val dLng = Math.toRadians(b.lng - a.lng)
    val y = sin(dLng) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
    return ((Math.toDegrees(atan2(y, x)) + 360) % 360).toFloat()
}

private fun cumulativeDistances(points: List<RoutePoint>): List<Double> {
    if (points.isEmpty()) return emptyList()
    val out = ArrayList<Double>(points.size)
    out.add(0.0)
    for (i in 1 until points.size) out.add(out[i - 1] + distanceMeters(points[i - 1], points[i]))
    return out
}
