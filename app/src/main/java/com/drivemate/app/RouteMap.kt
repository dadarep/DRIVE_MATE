package com.drivemate.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.roundToInt

/** MapLibre owns projection and tile drawing; only observed GPS is used for the vehicle marker. */
class RouteMap(context: Context) : FrameLayout(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val camera = MapCameraPolicy()
    private val nativeView: MapView
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var roadsVisible = false
    private var failed = false
    private var destroyed = false
    private var started = false
    private var resumed = false
    private var loadGeneration = 0
    private var lastState = "도로 지도 연결 중"
    private var heading = 0f
    private var headingKnown = false
    private var lastRoadCheck = 0L
    private var pendingCamera = true
    private val notice: LinearLayout
    private val noticeText: TextView
    private val reload: TextView
    private val credit: TextView
    var current: GeoPoint? = null; private set
    var destination: Destination? = null; private set
    var route: List<GeoPoint> = emptyList(); private set
    var onPick: ((GeoPoint) -> Unit)? = null
    var tileState: ((String) -> Unit)? = null
        set(value) { field = value; value?.invoke(lastState) }
    var interactive = true
        set(value) {
            if (field && !value) recenter()
            field = value
            map?.uiSettings?.setAllGesturesEnabled(value)
            reload.visibility = if (value && failed) View.VISIBLE else View.GONE
        }
    var night = false
        set(value) {
            if (field == value) return
            field = value
            updateColors()
            if (map != null) loadStyle()
        }
    var preview: GeoPoint? = null
        set(value) { field = value?.takeIf(::validMapPoint); updatePointSources() }

    init {
        contentDescription = "벡터 도로 지도 · 현재 위치 추적과 목적지 경로"
        MapLibre.getInstance(context.applicationContext)
        nativeView = MapView(context, MapLibreMapOptions.createFromAttributes(context)
            .textureMode(true).compassEnabled(false).logoEnabled(false).attributionEnabled(false)
            .setPrefetchesTiles(false).setPrefetchZoomDelta(0).localIdeographFontFamily("sans-serif"))
        nativeView.onCreate(null)
        addView(nativeView, LayoutParams(-1, -1))
        notice = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(24), dp(18), dp(24), dp(18))
        }
        noticeText = TextView(context).apply { textSize = 20f; gravity = Gravity.CENTER; text = "도로 지도를 불러오는 중…" }
        notice.addView(noticeText)
        reload = TextView(context).apply {
            text = "지도 다시 불러오기"; textSize = 20f; gravity = Gravity.CENTER
            setPadding(dp(18), dp(14), dp(18), dp(14)); visibility = View.GONE
            isClickable = true; isFocusable = true
            setOnClickListener { if (interactive) loadStyle() }
        }
        notice.addView(reload, LinearLayout.LayoutParams(-2, dp(64)).apply { topMargin = dp(12) })
        addView(notice, LayoutParams(-2, -2, Gravity.CENTER).apply { leftMargin = dp(20); rightMargin = dp(130) })
        credit = TextView(context).apply {
            text = "OpenFreeMap · © OpenMapTiles · © OpenStreetMap contributors"
            textSize = 12f; gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8)); maxLines = 2
            contentDescription = "지도 출처 · OpenFreeMap, OpenMapTiles, OpenStreetMap"
            setOnClickListener {
                if (interactive) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://openfreemap.org/")))
            }
        }
        addView(credit, LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.RIGHT))
        updateColors()
        nativeView.addOnDidFailLoadingMapListener { if (!destroyed) showFailure() }
        nativeView.addOnDidFinishRenderingMapListener { _ ->
            val now = SystemClock.elapsedRealtime()
            if (!destroyed && style != null && width > 0 && height > 0 && now-lastRoadCheck>500) {
                lastRoadCheck = now
                val count = map?.queryRenderedFeatures(RectF(0f, 0f, width.toFloat(), height.toFloat()), *DriveMapStyle.roads)?.size ?: 0
                if (count > 0 && !roadsVisible) {
                    roadsVisible = true; failed = false; notice.visibility = View.GONE
                    publish("벡터 도로 지도 · ${if(camera.following) "현재 위치 추적" else "전체 / 둘러보기"}")
                    showOverlays(true)
                }
            }
        }
        nativeView.getMapAsync { ready ->
            if (!destroyed) {
                map = ready
                ready.setMinZoomPreference(3.0); ready.setMaxZoomPreference(19.0)
                ready.uiSettings.setAllGesturesEnabled(interactive)
                ready.addOnCameraMoveStartedListener { reason ->
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE && interactive) {
                        camera.gesture(); publish("벡터 도로 지도 · 둘러보기")
                    }
                }
                ready.addOnMapLongClickListener { latLng ->
                    if (interactive) onPick?.invoke(GeoPoint(latLng.latitude, latLng.longitude))
                    interactive
                }
                val start = current ?: destination?.point ?: GeoPoint(37.499, 127.029)
                ready.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(start.latLng()).zoom(15.5).build()))
                loadStyle()
            }
        }
    }
    private fun updateColors() {
        setBackgroundColor(if(night) Color.rgb(23,35,46) else Color.rgb(240,241,237))
        notice.background = GradientDrawable().apply { setColor(if(night) Color.rgb(27,39,51) else Color.WHITE); cornerRadius = dp(20).toFloat() }
        noticeText.setTextColor(if(night) Color.rgb(233,241,249) else Color.rgb(34,55,73))
        reload.setTextColor(if(night) Color.rgb(143,183,255) else Color.rgb(40,104,236))
        credit.setTextColor(if(night) Color.rgb(196,210,222) else Color.rgb(76,95,110))
        credit.setBackgroundColor(if(night) Color.argb(235,23,35,46) else Color.argb(235,255,255,255))
    }
    private fun publish(value: String) { lastState = value; tileState?.invoke(value) }
    private fun loadStyle() {
        val ready = map ?: return
        if (destroyed) return
        val generation = ++loadGeneration
        style = null; roadsVisible = false; failed = false
        notice.visibility = View.VISIBLE; noticeText.text = "도로 지도를 불러오는 중…"; reload.visibility = View.GONE
        publish("벡터 도로 지도 연결 중")
        ready.setStyle(Style.Builder().fromJson(DriveMapStyle.json(night))) { loaded ->
            if (!destroyed && generation == loadGeneration) {
                loaded.addImage("vehicle-arrow", vehicleBitmap())
                loaded.addImage("route-direction", directionBitmap())
                style = loaded; updateRouteSource(); updatePointSources(); applyCamera(false)
            }
        }
        handler.postDelayed({ if (!destroyed && generation == loadGeneration && !roadsVisible) showFailure() }, 18000)
    }
    private fun showFailure() {
        failed = true; roadsVisible = false; showOverlays(false)
        noticeText.text = "도로 지도를 확인하지 못했어요.\n인터넷 연결을 확인해 주세요."
        notice.visibility = View.VISIBLE; reload.visibility = if (interactive) View.VISIBLE else View.GONE
        publish("지도 연결 확인 필요")
    }
    private fun showOverlays(show: Boolean) {
        for (id in DriveMapStyle.overlays) style?.getLayer(id)?.setProperties(visibility(if(show) Property.VISIBLE else Property.NONE))
    }
    fun locate(point: GeoPoint?, bearing: Float? = null) {
        val next = point?.takeIf(::validMapPoint)
        val known=bearing != null && bearing.isFinite()
        val direction = if (known) ((bearing!! % 360f) + 360f) % 360f else heading
        if (next == current && direction == heading && known == headingKnown) return
        headingKnown=known
        current = next; heading = direction
        updatePointSources()
        if (camera.following && next != null) applyCamera(true)
    }
    fun setDestination(value: Destination?) {
        destination = value?.takeIf { validMapPoint(it.point) }
        updatePointSources()
    }
    fun setRoute(points: List<GeoPoint>) {
        route = points.filter(::validMapPoint)
        updateRouteSource()
        if (interactive) fit() else recenter()
    }
    fun recenter() {
        camera.recenter(); pendingCamera = true; applyCamera(true)
        if(roadsVisible) publish("벡터 도로 지도 · 현재 위치 추적")
    }
    fun zoomBy(amount: Int) {
        val ready = map ?: return
        if (!interactive) return
        camera.gesture()
        ready.animateCamera(CameraUpdateFactory.zoomTo((ready.cameraPosition.zoom + amount).coerceIn(3.0,19.0)), 300)
        publish("벡터 도로 지도 · 둘러보기")
    }
    fun fit() {
        camera.overview(); pendingCamera = true; applyCamera(true)
        if(roadsVisible) publish("벡터 도로 지도 · 전체 경로")
    }
    private fun applyCamera(animate: Boolean) {
        val ready = map ?: return
        if (style == null || width <= 0 || height <= 0 || destroyed) { pendingCamera = true; return }
        val update = if (camera.following && current != null) {
            CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(current!!.latLng()).zoom(camera.zoom())
                .bearing(heading.toDouble()).tilt(35.0).padding(0.0, height * 0.30, 0.0, 0.0).build())
        } else {
            val points = overviewPoints(route, current, destination?.point)
            if (points.size < 2) {
                CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target((points.firstOrNull() ?: GeoPoint(37.499,127.029)).latLng())
                    .zoom(15.5).bearing(0.0).tilt(0.0).padding(0.0,0.0,0.0,0.0).build())
            } else {
                ready.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().bearing(0.0).tilt(0.0).padding(0.0,0.0,0.0,0.0).build()))
                val bounds = LatLngBounds.Builder().includes(points.map { it.latLng() }).build()
                CameraUpdateFactory.newLatLngBounds(bounds, dp(44), minOf(dp(190),height/5), minOf(dp(144),width/4), minOf(dp(78),height/5))
            }
        }
        pendingCamera = false
        if (animate) ready.animateCamera(update, 450) else ready.moveCamera(update)
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w,h,oldw,oldh)
        if (w > 0 && h > 0) post { if (!destroyed) applyCamera(false) }
    }
    private fun updateRouteSource() {
        val features = if (route.distinct().size >= 2) listOf(Feature.fromGeometry(LineString.fromLngLats(route.map { it.geo() }))) else emptyList()
        style?.getSourceAs<GeoJsonSource>("route-data")?.setGeoJson(FeatureCollection.fromFeatures(features))
    }
    private fun updatePointSources() {
        fun pointSource(id: String, p: GeoPoint?, edit: (Feature) -> Unit = {}) {
            val features = p?.let { listOf(Feature.fromGeometry(it.geo()).also(edit)) } ?: emptyList()
            style?.getSourceAs<GeoJsonSource>(id)?.setGeoJson(FeatureCollection.fromFeatures(features))
        }
        pointSource("destination-data",destination?.point) { it.addStringProperty("name",destination!!.name) }
        pointSource("position-data",current) { it.addNumberProperty("bearing",heading);it.addBooleanProperty("headingKnown",headingKnown) }
        pointSource("selection-data",preview)
    }
    fun start() { if (!destroyed && !started) { nativeView.onStart(); started=true } }
    fun resume() { if (!destroyed && !resumed) { start(); nativeView.onResume(); resumed=true } }
    fun pause() { if (!destroyed && resumed) { nativeView.onPause(); resumed=false } }
    fun stop() { pause(); if (!destroyed && started) { nativeView.onStop(); started=false } }
    fun lowMemory() { if(!destroyed) nativeView.onLowMemory() }
    fun saveState(out: Bundle) { if(!destroyed) nativeView.onSaveInstanceState(out) }
    fun destroy() {
        if(destroyed)return
        stop(); destroyed=true; handler.removeCallbacksAndMessages(null); tileState=null; onPick=null; nativeView.onDestroy(); map=null; style=null
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private fun GeoPoint.latLng() = LatLng(lat,lon)
    private fun GeoPoint.geo() = Point.fromLngLat(lon,lat)
    private fun vehicleBitmap(): Bitmap = Bitmap.createBitmap(80,80,Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas=Canvas(bitmap); val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color=Color.WHITE; canvas.drawCircle(40f,40f,32f,paint)
        paint.color=Color.rgb(40,104,236)
        canvas.drawPath(Path().apply { moveTo(40f,13f);lineTo(62f,61f);lineTo(40f,51f);lineTo(18f,61f);close() },paint)
    }
    private fun directionBitmap(): Bitmap = Bitmap.createBitmap(28,32,Bitmap.Config.ARGB_8888).also { bitmap ->
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE;style=Paint.Style.STROKE;strokeWidth=4f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND }
        // Line-placement symbols use the image's right edge as forward, unlike the vehicle's north-up icon.
        Canvas(bitmap).drawPath(Path().apply { moveTo(9f,8f);lineTo(18f,16f);lineTo(9f,24f) },paint)
    }
}
