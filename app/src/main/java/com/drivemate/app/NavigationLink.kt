package com.drivemate.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.navi.data.*
import ai.pleos.playground.navi.constants.NaviErrorCode

class NavigationLink(context: Context) {
    private val prefs = context.getSharedPreferences("driving", Context.MODE_PRIVATE)
    private val helper = NaviHelper(context.applicationContext)
    private val handler = Handler(Looper.getMainLooper())
    private var active = false
    private var closed = false
    private val listener = object : NaviHelperEventListener {
        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            val d = destinationInfo
            if (!d.latitude.isFinite() || !d.longitude.isFinite() || (d.latitude == 0.0 && d.longitude == 0.0)) return
            prefs.edit().putString("navDestination",d.name).putString("navLat",d.latitude.toString())
                .putString("navLon",d.longitude.toString()).putInt("navDistance",d.remainDistance)
                .putInt("navSeconds",d.remainTime).putLong("navAt",android.os.SystemClock.elapsedRealtime()).putString("navStatus","Pleos 목적지 연동됨").apply()
            Log.i("DriveMateNavi", "destination=${d.name}")
        }
        override fun onCurrentRoadSpeedLimit(speedLimit: Int) {
            prefs.edit().putInt("speedLimit",speedLimit).putLong("limitAt",android.os.SystemClock.elapsedRealtime()).apply()
        }
        override fun onDrivingInfo(drivingInfo: DrivingInfo) {
            prefs.edit().putInt("navDistance",drivingInfo.destination.distance)
                .putInt("navSeconds",drivingInfo.destination.duration).putLong("navAt",android.os.SystemClock.elapsedRealtime()).apply()
        }
        override fun onTBTInfo(tbtInfoList: List<TBTInfo>) {
            val next = tbtInfoList.firstOrNull() ?: return
            val then = tbtInfoList.getOrNull(1)
            prefs.edit().putString("navInstruction",next.description).putFloat("turnDistance",next.distance.toFloat()).putLong("tbtAt",android.os.SystemClock.elapsedRealtime())
                .apply { if (then != null) putString("thenInstruction",then.description).putFloat("thenDistance",then.distance.toFloat()) else remove("thenInstruction") }.apply()
        }
        override fun onRoutingCompleted() { prefs.edit().putString("navStatus","Pleos 경로 안내 연결됨").apply(); refresh() }
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) { refresh() }
        override fun onDestinationChanged() { refresh() }
        override fun onRouteCancelled() { prefs.edit().putString("navStatus","Pleos 안내 종료").remove("navInstruction").remove("speedLimit").apply() }
        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) { prefs.edit().putString("navStatus","목적지에 도착했어요").remove("navInstruction").apply() }
        override fun onError(errorCode: NaviErrorCode) { prefs.edit().putString("navStatus","Pleos 응답 확인 필요 · $errorCode").apply(); Log.w("DriveMateNavi","$errorCode") }
    }
    fun start() {
        runCatching { helper.initialize(); helper.addListener(listener); active = true }
            .onFailure { prefs.edit().putString("navStatus","Pleos 내비 연결 대기").apply(); Log.w("DriveMateNavi","initialize",it) }
        handler.postDelayed({ if (!closed) refresh() },2000)
    }
    private fun refresh() { if (active && !closed) runCatching { helper.getDestinationInfo(); helper.getTBTInfo() }.onFailure { Log.w("DriveMateNavi","query",it) } }
    fun navigate(destination: Destination): Boolean = runCatching {
        check(active)
        helper.requestRoute(RouteInfo(latitude=destination.point.lat,longitude=destination.point.lon,poiName=destination.name,address=destination.address,poiId="",poiSubId="0"))
        prefs.edit().putString("navStatus","Pleos에 목적지 전달 중").remove("navInstruction").remove("speedLimit").apply()
        true
    }.getOrElse { prefs.edit().putString("navStatus","Pleos 목적지 전달 실패").apply(); false }
    fun cancel() { if(active) runCatching { helper.cancelRoute() }; prefs.edit().remove("navDestination").remove("navLat").remove("navLon").remove("navInstruction").remove("speedLimit").apply() }
    fun close() { closed=true; handler.removeCallbacksAndMessages(null); if(active)runCatching { helper.removeListener(listener); helper.release() }; active=false }
}
