package com.keepdev.routeemock

import android.content.Context
import org.json.JSONObject

/** 앱에 담아 둔 산 경로 하나 */
data class MockRoute(
    val id: String,
    val name: String,
    val purpose: String,
    val distanceMeters: Int,
    val minEle: Int,
    val maxEle: Int,
    val points: List<RoutePoint>,
)

/** assets/routes.json, 만드는 법은 tools/build_routes.py */
object RouteCatalog {
    private var cache: Pair<String, List<MockRoute>>? = null

    /** 출처 표기와 경로 목록 */
    fun load(context: Context): Pair<String, List<MockRoute>> = cache ?: run {
        val json = JSONObject(context.assets.open("routes.json").bufferedReader().use { it.readText() })
        val routes = json.getJSONArray("routes")
        val list = (0 until routes.length()).map { i ->
            val r = routes.getJSONObject(i)
            val pts = r.getJSONArray("points")
            MockRoute(
                id = r.getString("id"),
                name = r.getString("name"),
                purpose = r.getString("purpose"),
                distanceMeters = r.getInt("distanceMeters"),
                minEle = r.getDouble("minEle").toInt(),
                maxEle = r.getDouble("maxEle").toInt(),
                points = (0 until pts.length()).map { j ->
                    val p = pts.getJSONArray(j)
                    RoutePoint(p.getDouble(0), p.getDouble(1), p.getDouble(2))
                },
            )
        }
        (json.getString("attribution") to list).also { cache = it }
    }
}
