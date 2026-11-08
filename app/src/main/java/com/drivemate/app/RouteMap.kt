package com.drivemate.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import org.json.JSONArray
import kotlin.math.cos

class RouteMap(context: Context, encoded: String) : View(context) {
    private val points = runCatching {
        val array = JSONArray(encoded)
        List(array.length()) { index ->
            val pair = array.getJSONArray(index)
            pair.getDouble(1) to pair.getDouble(0)
        }
    }.getOrDefault(emptyList())
    private data class Road(val major: Boolean, val points: List<Pair<Double, Double>>)

    private val hasRoadCoverage = points.isNotEmpty() &&
        points.all { (lon, lat) -> lon in 127.025..127.033 && lat in 37.495..37.502 }
    private val roads = if (hasRoadCoverage) runCatching {
        val data = context.assets.open("gangnam-roads.json").bufferedReader().use { JSONArray(it.readText()) }
        List(data.length()) { index ->
            val road = data.getJSONObject(index)
            val coords = road.getJSONArray("points")
            Road(road.getBoolean("major"), List(coords.length()) { i ->
                val pair = coords.getJSONArray(i)
                pair.getDouble(0) to pair.getDouble(1)
            })
        }
    }.getOrDefault(emptyList()) else emptyList()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density

    init {
        contentDescription = if (points.size >= 2) "차량 위치로 그린 최근 주행 경로" else "경로 데이터 부족"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width < 120 * density || height < 80 * density) return
        val w = width.toFloat()
        val h = height.toFloat()
        paint.color = Color.WHITE
        canvas.drawRoundRect(0f, 0f, w, h, 14 * density, 14 * density, paint)
        paint.color = Color.rgb(0, 44, 95)
        paint.textSize = 16 * density
        canvas.drawText(if (roads.isNotEmpty()) "최근 주행 경로 · 강남역 오프라인 지도" else "최근 주행 경로 · 도로 지도 미지원 지역", 14 * density, 24 * density, paint)
        if (points.distinct().size < 2) {
            paint.textSize = 13 * density
            canvas.drawText("정확한 위치 표본이 충분하지 않아요.", 14 * density, 50 * density, paint)
            return
        }
        val origin = points.first()
        val longitudeScale = cos(Math.toRadians(origin.second))
        val local = points.map { (lon, lat) -> (lon - origin.first) * longitudeScale to lat - origin.second }
        val minX = local.minOf { it.first }
        val maxX = local.maxOf { it.first }
        val minY = local.minOf { it.second }
        val maxY = local.maxOf { it.second }
        val pad = 42 * density
        val top = 38 * density
        val scale = minOf((w - 2 * pad) / (maxX - minX).coerceAtLeast(1e-8),
            (h - top - pad) / (maxY - minY).coerceAtLeast(1e-8))
        fun position(lon: Double, lat: Double): Pair<Float, Float> {
            val x = (lon - origin.first) * longitudeScale
            val y = lat - origin.second
            return (w / 2 + (x - (minX + maxX) / 2) * scale).toFloat() to
                ((h + top) / 2 - (y - (minY + maxY) / 2) * scale).toFloat()
        }
        val mapped = points.map { (lon, lat) -> position(lon, lat) }
        if (roads.isNotEmpty()) {
            canvas.save()
            canvas.clipRect(8 * density, top - 6 * density, w - 8 * density, h - 24 * density)
            paint.color = Color.rgb(232, 240, 232)
            canvas.drawRect(0f, top - 6 * density, w, h, paint)
            val roadPaths = roads.map { road ->
                road.major to Path().apply {
                    road.points.forEachIndexed { index, (lon, lat) ->
                        val (x, y) = position(lon, lat)
                        if (index == 0) moveTo(x, y) else lineTo(x, y)
                    }
                }
            }.sortedBy { it.first }
            paint.style = Paint.Style.STROKE
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeCap = Paint.Cap.ROUND
            for ((major, road) in roadPaths) {
                paint.strokeWidth = (if (major) 12 else 6) * density
                paint.color = Color.rgb(194, 203, 193)
                canvas.drawPath(road, paint)
            }
            for ((major, road) in roadPaths) {
                paint.strokeWidth = (if (major) 9 else 4) * density
                paint.color = if (major) Color.rgb(255, 230, 176) else Color.WHITE
                canvas.drawPath(road, paint)
            }
            paint.style = Paint.Style.FILL
            canvas.restore()
            paint.textSize = 10 * density
            paint.color = Color.rgb(64, 78, 73)
            canvas.drawText("© OpenStreetMap contributors · ODbL", 12 * density, h - 8 * density, paint)
        }
        val line = Path()
        mapped.forEachIndexed { index, (x, y) -> if (index == 0) line.moveTo(x, y) else line.lineTo(x, y) }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4 * density
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = Color.rgb(31, 116, 87)
        canvas.drawPath(line, paint)
        paint.style = Paint.Style.FILL
        val (startX, startY) = mapped.first()
        val (endX, endY) = mapped.last()
        canvas.drawCircle(startX, startY, 6 * density, paint)
        paint.color = Color.rgb(0, 44, 95)
        canvas.drawCircle(endX, endY, 6 * density, paint)
        paint.textSize = 12 * density
        canvas.drawText("출발", startX + 9 * density, startY, paint)
        canvas.drawText("도착", endX + 9 * density, endY, paint)
    }
}


