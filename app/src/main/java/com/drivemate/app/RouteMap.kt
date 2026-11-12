package com.drivemate.app

import android.content.Context
import android.graphics.*
import android.util.LruCache
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*
import org.json.JSONArray

class RouteMap(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var center = GeoPoint(37.499, 127.029)
    private var zoom = 16
    private var follow = true
    var current: GeoPoint? = null; private set
    var destination: Destination? = null; private set
    var route: List<GeoPoint> = emptyList(); private set
    var onPick: ((GeoPoint) -> Unit)? = null
    var tileState: ((String) -> Unit)? = null
    private var heading = 0f
    private val u = max(1.5f, context.resources.displayMetrics.density)
    private val tileSize = 256.0 * u
    private val tiles = object : LruCache<String, Bitmap>(24 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }
    private val pending = ConcurrentHashMap.newKeySet<String>()
    private val failedAt = ConcurrentHashMap<String, Long>()
    private val cacheDir = File(context.cacheDir, "osm-map-tiles").apply { mkdirs() }
    private var detached = false
    private val roads = runCatching {
        val data = JSONArray(context.assets.open("gangnam-roads.json").bufferedReader().use { it.readText() })
        List(data.length()) { i -> val r = data.getJSONObject(i); val points = r.getJSONArray("points")
            r.getBoolean("major") to List(points.length()) { j -> val p = points.getJSONArray(j); GeoPoint(p.getDouble(1),p.getDouble(0)) }
        }
    }.getOrDefault(emptyList())
    private val sortedRoads = roads.sortedBy { it.first }
    private val tileRect = RectF()
    init { contentDescription = "도로 지도. 현재 위치와 목적지, 탐색된 경로 표시" }
    private val gesture = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onScroll(a: MotionEvent?, b: MotionEvent, dx: Float, dy: Float): Boolean {
            follow = false; val p = world(center); center = geo(p.first + dx, p.second + dy)
            invalidate(); return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean { zoomBy(1); return true }
        override fun onLongPress(e: MotionEvent) {
            val p = world(center); onPick?.invoke(geo(p.first + e.x - width/2, p.second + e.y - height/2))
        }
    })
    var interactive = true
    override fun onTouchEvent(event: MotionEvent): Boolean = interactive && gesture.onTouchEvent(event)
    override fun onAttachedToWindow() { super.onAttachedToWindow(); detached = false }
    override fun onDetachedFromWindow() { detached = true; super.onDetachedFromWindow() }
    fun locate(point: GeoPoint?, bearing: Float = 0f) {
        current = point; heading = bearing
        if (follow && point != null && route.isEmpty()) center = point
        invalidate()
    }
    fun setDestination(value: Destination?) { destination = value; invalidate() }
    var preview: GeoPoint? = null
        set(value) { field = value; invalidate() }
    fun setRoute(points: List<GeoPoint>) { route = points; fit(); invalidate() }
    fun recenter() { follow = true; current?.let { center = it }; zoom = 16; invalidate() }
    fun zoomBy(amount: Int) { zoom = (zoom + amount).coerceIn(10,18); follow = false; invalidate() }
    private fun world(p: GeoPoint): Pair<Double,Double> {
        val size = tileSize * (1 shl zoom); val s = sin(Math.toRadians(p.lat.coerceIn(-85.05,85.05)))
        return (p.lon + 180)/360*size to (0.5 - ln((1+s)/(1-s))/(4*PI))*size
    }
    private fun geo(x: Double,y: Double): GeoPoint {
        val size=tileSize*(1 shl zoom)
        return GeoPoint(Math.toDegrees(atan(sinh(PI*(1-2*y/size)))),x/size*360-180)
    }
    private fun position(p: GeoPoint): Pair<Float,Float> {
        val c=world(center); val v=world(p)
        return (v.first-c.first+width/2).toFloat() to (v.second-c.second+height/2).toFloat()
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (destination != null) fit()
    }
    fun fit() {
        val points = route.ifEmpty { listOfNotNull(current,destination?.point) }
        if (width == 0) return
        if (points.size < 2) { destination?.let { center = it.point; zoom = 15; follow = false; invalidate() }; return }
        center = GeoPoint((points.minOf { it.lat }+points.maxOf { it.lat })/2,(points.minOf { it.lon }+points.maxOf { it.lon })/2)
        zoom=17
        while (zoom > 10) {
            val a=world(GeoPoint(points.minOf{it.lat},points.minOf{it.lon})); val b=world(GeoPoint(points.maxOf{it.lat},points.maxOf{it.lon}))
            if (abs(a.first-b.first)<width*0.65 && abs(a.second-b.second)<height*0.50) break
            zoom--
        }
        if (zoom == 10) destination?.let { center = it.point; zoom = 14 }
        follow=false
    }
    private fun tile(z:Int,x:Int,y:Int): Bitmap? {
        if (x !in 0 until (1 shl z) || y !in 0 until (1 shl z)) return null
        val key="$z-$x-$y"; tiles.get(key)?.let{return it}
        if (System.currentTimeMillis()-(failedAt[key]?:0L)<30000 || !pending.add(key)) return null
        MapData.io.execute {
            val result=runCatching {
                val file=File(cacheDir,"$key.png")
                val bytes=if(file.exists()) file.readBytes() else MapData.request("https://tile.openstreetmap.org/$z/$x/$y.png").also{file.writeBytes(it)}
                BitmapFactory.decodeByteArray(bytes,0,bytes.size) ?: error("지도 이미지 형식 오류")
            }
            result.onSuccess { tiles.put(key,it) }.onFailure { failedAt[key]=System.currentTimeMillis() }
            pending.remove(key)
            post { if (!detached) { tileState?.invoke(if(result.isSuccess) "지도 연결됨" else "지도 연결 확인 · 저장된 도로 표시"); invalidate() } }
        }; return null
    }
    var night = false
        set(value) { field = value; invalidate() }
    private val nightTiles = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(-1f,0f,0f,0f,255f, 0f,-1f,0f,0f,255f, 0f,0f,-1f,0f,255f, 0f,0f,0f,1f,0f)).apply {
        postConcat(ColorMatrix(floatArrayOf(-0.574f,1.43f,0.144f,0f,0f, 0.426f,0.43f,0.144f,0f,0f, 0.426f,1.43f,-0.856f,0f,0f, 0f,0f,0f,1f,0f)))
        postConcat(ColorMatrix().apply { setScale(0.85f,0.88f,0.95f,1f) })
    })
    override fun onDraw(c:Canvas) {
        super.onDraw(c); c.drawColor(if(night) Color.rgb(30,36,44) else Color.rgb(235,239,239))
        for((major,points) in sortedRoads) {
            paint.style=Paint.Style.STROKE; paint.strokeWidth=(if(major) 9f else 4f)*u
            paint.color=if(night) (if(major) Color.rgb(120,100,60) else Color.rgb(70,78,90)) else if(major) Color.rgb(246,218,158) else Color.WHITE
            c.drawPath(path(points),paint)
        }
        paint.style=Paint.Style.FILL; paint.isFilterBitmap=true
        paint.colorFilter=if(night) nightTiles else null
        val w=world(center);val left=w.first-width/2;val top=w.second-height/2
        for(x in floor(left/tileSize).toInt()..floor((left+width)/tileSize).toInt()) for(y in floor(top/tileSize).toInt()..floor((top+height)/tileSize).toInt()) {
            tile(zoom,x,y)?.let{tileRect.set((x*tileSize-left).toFloat(),(y*tileSize-top).toFloat(),(x*tileSize-left+tileSize).toFloat(),(y*tileSize-top+tileSize).toFloat());c.drawBitmap(it,null,tileRect,paint)}
        }
        paint.colorFilter=null
        if(route.size>1) {
            paint.style=Paint.Style.STROKE;paint.strokeCap=Paint.Cap.ROUND;paint.strokeJoin=Paint.Join.ROUND
            paint.strokeWidth=11f*u;paint.color=if(night) Color.rgb(12,16,22) else Color.WHITE;c.drawPath(path(route),paint)
            paint.strokeWidth=7f*u;paint.color=if(night) Color.rgb(90,145,255) else Color.rgb(31,96,247);c.drawPath(path(route),paint);paint.style=Paint.Style.FILL
        }
        destination?.let { d ->
            val(x,y)=position(d.point);paint.color=Color.rgb(241,79,79);c.drawCircle(x,y-14*u,12*u,paint)
            c.drawPath(Path().apply{moveTo(x-8*u,y-7*u);lineTo(x,y+5*u);lineTo(x+8*u,y-7*u);close()},paint)
            paint.color=Color.WHITE;c.drawCircle(x,y-14*u,4*u,paint)
            paint.textSize=16f*u;paint.typeface=Typeface.DEFAULT_BOLD
            val label=d.name.take(18);val tw=paint.measureText(label)
            paint.color=if(night) Color.rgb(27,33,43) else Color.WHITE
            paint.setShadowLayer(4*u,0f,u,Color.argb(60,0,0,0))
            c.drawRoundRect(x-tw/2-12*u,y-62*u,x+tw/2+12*u,y-32*u,10*u,10*u,paint)
            paint.clearShadowLayer()
            paint.color=if(night) Color.rgb(236,240,246) else Color.rgb(49,59,76);c.drawText(label,x-tw/2,y-41*u,paint)
        }
        preview?.let { p ->
            val(x,y)=position(p);paint.color=Color.argb(60,31,96,247);c.drawCircle(x,y,24*u,paint)
            paint.color=Color.rgb(31,96,247);c.drawCircle(x,y-14*u,12*u,paint)
            c.drawPath(Path().apply{moveTo(x-8*u,y-7*u);lineTo(x,y+5*u);lineTo(x+8*u,y-7*u);close()},paint)
            paint.color=Color.WHITE;c.drawCircle(x,y-14*u,4*u,paint)
        }
        current?.let { p ->
            val(x,y)=position(p);paint.color=Color.argb(35,35,100,255);c.drawCircle(x,y,29*u,paint)
            paint.color=Color.WHITE;c.drawCircle(x,y,17*u,paint)
            c.save();c.rotate(heading,x,y);paint.color=Color.rgb(31,96,247)
            c.drawPath(Path().apply{moveTo(x,y-12*u);lineTo(x+9*u,y+9*u);lineTo(x,y+5*u);lineTo(x-9*u,y+9*u);close()},paint);c.restore()
        }
        paint.typeface=Typeface.DEFAULT;paint.textSize=9f*u;paint.color=if(night) Color.argb(210,27,33,43) else Color.argb(210,255,255,255)
        val credit="© OpenStreetMap contributors · 경로 OSRM";val cw=paint.measureText(credit)
        c.drawRect(width-cw-16*u,height-16*u,width.toFloat(),height.toFloat(),paint);paint.color=if(night) Color.rgb(162,174,190) else Color.rgb(91,104,115)
        c.drawText(credit,width-cw-8*u,height-5*u,paint)
    }
    private fun path(points:List<GeoPoint>)=Path().apply { points.forEachIndexed { i,p -> val(x,y)=position(p);if(i==0)moveTo(x,y)else lineTo(x,y) } }
}
