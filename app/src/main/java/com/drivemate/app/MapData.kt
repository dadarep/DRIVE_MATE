package com.drivemate.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

data class GeoPoint(val lat: Double, val lon: Double)
data class Destination(val name: String, val point: GeoPoint, val address: String = "")
data class RoadRoute(val points: List<GeoPoint>, val meters: Double, val seconds: Double)

object MapData {
    val io = Executors.newFixedThreadPool(3)
    private val searchIo = Executors.newSingleThreadExecutor()
    private var lastSearch = 0L
    private val searches = mutableMapOf<String, List<Destination>>()
    fun request(url: String): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 12000
        connection.readTimeout = 15000
        connection.setRequestProperty("User-Agent", "DriveMate-Pleos-Prototype/0.2 (Android vehicle coaching)")
        try {
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            return connection.inputStream.use { it.readBytes() }
        } finally { connection.disconnect() }
    }
    fun route(from: GeoPoint, to: GeoPoint): RoadRoute {
        val data = JSONObject(String(request("https://router.project-osrm.org/route/v1/driving/${from.lon},${from.lat};${to.lon},${to.lat}?overview=full&geometries=geojson&steps=false"), Charsets.UTF_8))
        check(data.optString("code") == "Ok") { "도로 경로를 찾지 못했어요" }
        val route = data.getJSONArray("routes").getJSONObject(0)
        val coords = route.getJSONObject("geometry").getJSONArray("coordinates")
        check(coords.length() >= 2)
        return RoadRoute(List(coords.length()) { val p = coords.getJSONArray(it); GeoPoint(p.getDouble(1), p.getDouble(0)) }, route.getDouble("distance"), route.getDouble("duration"))
    }
    fun search(query: String, near: GeoPoint, callback: (Result<List<Destination>>) -> Unit) {
        searchIo.execute {
            callback(runCatching {
                searches[query] ?: run {
                    val wait = 1200 - (System.currentTimeMillis() - lastSearch)
                    if (wait > 0) Thread.sleep(wait)
                    lastSearch = System.currentTimeMillis()
                    // Search only needs a location bias; ~1km precision keeps the exact position on the device.
                    val url = "https://photon.komoot.io/api/?q=${URLEncoder.encode(query, "UTF-8")}&limit=5&lat=${"%.2f".format(java.util.Locale.US, near.lat)}&lon=${"%.2f".format(java.util.Locale.US, near.lon)}"
                    val features = JSONObject(String(request(url), Charsets.UTF_8)).getJSONArray("features")
                    List(features.length()) { i ->
                        val f = features.getJSONObject(i); val p = f.getJSONObject("properties")
                        val coords = f.getJSONObject("geometry").getJSONArray("coordinates")
                        Destination(p.optString("name", query), GeoPoint(coords.getDouble(1), coords.getDouble(0)),
                            listOf("city", "street", "housenumber").map { p.optString(it) }.filter { it.isNotBlank() }.joinToString(" "))
                    }.also { searches[query] = it }
                }
            })
        }
    }
    fun encode(points: List<GeoPoint>): String = JSONArray().apply {
        points.forEach { put(JSONArray().put(it.lat).put(it.lon)) }
    }.toString()
    fun decode(encoded: String): List<GeoPoint> = runCatching {
        val a = JSONArray(encoded)
        List(a.length()) { val p = a.getJSONArray(it); GeoPoint(p.getDouble(0), p.getDouble(1)) }
    }.getOrDefault(emptyList())
}
