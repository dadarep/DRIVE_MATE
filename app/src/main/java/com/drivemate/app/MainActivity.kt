package com.drivemate.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.view.Gravity
import android.view.View
import android.widget.*
import com.drivemate.app.recording.DriveRecorder
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

class MainActivity : Activity() {
    private val handler=Handler(Looper.getMainLooper())
    private val storage by lazy { getSharedPreferences("driving",MODE_PRIVATE) }
    private val navy=Color.rgb(27,43,65)
    private val blue=Color.rgb(31,96,247)
    private val muted=Color.rgb(117,132,150)
    private lateinit var map:RouteMap
    private lateinit var speed:TextView
    private lateinit var state:TextView
    private lateinit var signals:TextView
    private lateinit var coachTitle:TextView
    private lateinit var coachDetail:TextView
    private lateinit var routeTitle:TextView
    private lateinit var routeDetail:TextView
    private lateinit var tripTitle:TextView
    private lateinit var tripDetail:TextView
    private lateinit var sourceLabel:TextView
    private lateinit var clock:TextView
    private lateinit var voice:TextView
    private lateinit var search:TextView
    private lateinit var navigation:NavigationLink
    private var selected:Destination?=null
    private var roadRoute:RoadRoute?=null
    private var routeMessage="목적지를 선택하면 도로 경로를 보여 드려요."
    private var routeVersion=0
    private var lastNavDestination=""
    private var pendingRoute=false
    private var dialog:AlertDialog?=null
    private var lastFit=false
    private val poll=object:Runnable { override fun run(){update();handler.postDelayed(this,300)} }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=Color.rgb(245,247,251);window.navigationBarColor=Color.WHITE
        buildScreen()
        navigation=NavigationLink(this);navigation.start()
        val permissions=arrayOf("android.car.permission.CAR_SPEED",android.Manifest.permission.ACCESS_COARSE_LOCATION,android.Manifest.permission.ACCESS_FINE_LOCATION)
        if(permissions.any{checkSelfPermission(it)!=PackageManager.PERMISSION_GRANTED}) requestPermissions(permissions,1) else startRecorder()
        if(storage.contains("destinationLat")) {
            selected=Destination(storage.getString("destinationName","")?:"목적지",GeoPoint(storage.getString("destinationLat","0")!!.toDouble(),storage.getString("destinationLon","0")!!.toDouble()))
            map.setDestination(selected);pendingRoute=true
        }
        handler.post(poll)
    }
    override fun onRequestPermissionsResult(code:Int,permissions:Array<String>,results:IntArray) {
        super.onRequestPermissionsResult(code,permissions,results)
        if(checkSelfPermission("android.car.permission.CAR_SPEED")==PackageManager.PERMISSION_GRANTED) startRecorder()
    }
    private fun startRecorder(){startForegroundService(Intent(this,DriveRecorder::class.java))}
    private fun text(value:String,size:Float=16f,color:Int=navy,bold:Boolean=false)=TextView(this).apply {
        text=value;textSize=size;setTextColor(color);if(bold)typeface=Typeface.DEFAULT_BOLD
        includeFontPadding=false
    }
    private fun rounded(color:Int=Color.WHITE,radius:Int=20)=GradientDrawable().apply {setColor(color);cornerRadius=dp(radius).toFloat()}
    private fun card()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(14),dp(18),dp(14));background=rounded();elevation=dp(3).toFloat()}
    private fun button(label:String,action:()->Unit)=text(label,14f,blue,true).apply{
        gravity=Gravity.CENTER;minHeight=dp(48);setPadding(dp(14),0,dp(14),0);background=rounded(Color.rgb(235,241,255),14)
        isClickable=true;isFocusable=true;setOnClickListener{action()}
    }
    private fun buildScreen() {
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(8),dp(12),dp(8));setBackgroundColor(Color.rgb(244,246,250))}
        val header=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(8),0,dp(8),dp(8))}
        header.addView(text("DRIVE MATE",19f,navy,true))
        header.addView(text("  /  함께 만드는 부드러운 주행",12f,muted),LinearLayout.LayoutParams(0,-2,1f))
        speed=text("— km/h",20f,navy,true).apply{setPadding(0,0,dp(20),0)};header.addView(speed)
        state=text("● 차량 연결 중",12f,blue,true);header.addView(state)
        clock=text("",13f,navy,true).apply{setPadding(dp(24),0,0,0)};header.addView(clock)
        root.addView(header,LinearLayout.LayoutParams(-1,dp(36)))
        val body=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        val right=FrameLayout(this).apply{background=rounded(Color.rgb(232,237,240));clipToOutline=true}
        map=RouteMap(this);right.addView(map,FrameLayout.LayoutParams(-1,-1))
        map.onPick={p->if(canEdit()) AlertDialog.Builder(this).setTitle("이 위치로 갈까요?").setMessage("선택한 위치에 목적지 핀을 표시하고 도로 경로를 탐색합니다.").setPositiveButton("목적지 설정"){_,_->select(Destination("지도에서 선택한 목적지",p))}.setNegativeButton("취소",null).show()}
        sourceLabel=text("지도 연결 중",10f,muted).apply{setPadding(dp(8),dp(3),dp(8),dp(3));background=rounded()}
        right.addView(sourceLabel,FrameLayout.LayoutParams(-2,-2,Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply{topMargin=dp(12)})
        map.tileState={sourceLabel.text=it}
        val guidance=card()
        routeTitle=text("어디로 떠나볼까요?",22f,navy,true);guidance.addView(routeTitle)
        routeDetail=text("목적지를 설정하고 코칭과 함께 출발해요.",12f,muted).apply{setPadding(0,dp(8),0,dp(10));maxLines=2};guidance.addView(routeDetail)
        search=button("⌕  목적지 검색 / 설정"){openDestinations()};guidance.addView(search)
        right.addView(guidance,FrameLayout.LayoutParams(dp(290),-2,Gravity.TOP or Gravity.LEFT).apply{leftMargin=dp(16);topMargin=dp(16)})
        val mascot=card().apply{gravity=Gravity.CENTER;setPadding(dp(6),dp(4),dp(6),dp(6))}
        mascot.addView(ImageView(this).apply{setImageBitmap(assets.open("garden.png").use{BitmapFactory.decodeStream(it)});scaleType=ImageView.ScaleType.FIT_CENTER;contentDescription="기존 드라이빙 정원 캐릭터"},LinearLayout.LayoutParams(dp(76),dp(66)))
        mascot.addView(text("나의 주행 메이트",10f,muted,true))
        right.addView(mascot,FrameLayout.LayoutParams(dp(104),dp(92),Gravity.RIGHT or Gravity.TOP).apply{rightMargin=dp(16);topMargin=dp(16)})
        val trip=card()
        trip.addView(text("DESTINATION",10f,blue,true))
        tripTitle=text("목적지 미설정",20f,navy,true).apply{setPadding(0,dp(7),0,dp(5));maxLines=1};trip.addView(tripTitle)
        tripDetail=text("현재 위치를 중심으로 표시 중",12f,muted).apply{maxLines=3};trip.addView(tripDetail)
        right.addView(trip,FrameLayout.LayoutParams(dp(250),dp(116),Gravity.BOTTOM or Gravity.LEFT).apply{leftMargin=dp(16);bottomMargin=dp(30)})
        val coaching=card()
        coaching.addView(text("● LIVE COACHING",10f,blue,true))
        coachTitle=text("여유 있게, 함께 출발해요",20f,navy,true).apply{setPadding(0,dp(8),0,dp(7));maxLines=2};coaching.addView(coachTitle)
        coachDetail=text("차량 신호를 확인하면 상황에 맞춰 알려드릴게요.",13f,muted).apply{maxLines=3};coaching.addView(coachDetail)
        right.addView(coaching,FrameLayout.LayoutParams(dp(350),dp(142),Gravity.BOTTOM or Gravity.RIGHT).apply{rightMargin=dp(16);bottomMargin=dp(30)})
        val tools=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        tools.addView(button("＋"){map.zoomBy(1)})
        tools.addView(button("−"){map.zoomBy(-1)})
        tools.addView(button("◎"){map.recenter()})
        right.addView(tools,FrameLayout.LayoutParams(dp(48),dp(144),Gravity.RIGHT or Gravity.CENTER_VERTICAL).apply{rightMargin=dp(16);bottomMargin=dp(100)})
        body.addView(right,LinearLayout.LayoutParams(-1,-1));root.addView(body,LinearLayout.LayoutParams(-1,0,1f))
        val footer=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(8),dp(5),dp(8),0)}
        signals=text("차속 · GPS · Pleos 차량 데이터",11f,muted);footer.addView(signals,LinearLayout.LayoutParams(0,-2,1f))
        voice=button("음성 켜짐"){val enabled=!storage.getBoolean("voiceEnabled",true);storage.edit().putBoolean("voiceEnabled",enabled).apply();startService(Intent(this,DriveRecorder::class.java).setAction(if(enabled) "VOICE_CHECK" else "VOICE_STOP"))};footer.addView(voice)
        footer.addView(button("주행 회고"){review()}.apply{setPadding(dp(18),0,dp(18),0)})
        footer.addView(button("경로 해제"){if(canEdit()){routeVersion++;pendingRoute=false;selected=null;roadRoute=null;map.setDestination(null);map.setRoute(emptyList());navigation.cancel();storage.edit().remove("destinationLat").remove("destinationLon").remove("destinationName").apply();routeMessage="목적지를 선택하면 도로 경로를 보여 드려요.";update()}})
        root.addView(footer,LinearLayout.LayoutParams(-1,dp(50)));setContentView(root)
    }
    private fun canEdit():Boolean {
        if(storage.getString("vehicleState","")!="parked") {Toast.makeText(this,"목적지 입력과 상세 확인은 주차 후 이용해 주세요.",Toast.LENGTH_SHORT).show();return false};return true
    }
    private fun openDestinations(){
        if(!canEdit())return
        val places=listOf(Destination("42dot",GeoPoint(37.48544722,127.03636666),"서울시 강남구 남부순환로 2621"),Destination("강남역",GeoPoint(37.49795,127.02762)),Destination("양재역",GeoPoint(37.48465,127.03415)))
        dialog=AlertDialog.Builder(this).setTitle("목적지 선택").setItems((places.map{it.name}+"장소 이름으로 검색"+"위도, 경도로 입력").toTypedArray()){_,which->
            if(!canEdit())return@setItems
            if(which<places.size)select(places[which]) else if(which==places.size)searchDialog() else coordinateDialog()
        }.setNegativeButton("닫기",null).show()
    }
    private fun searchDialog(){
        val input=EditText(this).apply{hint="장소 이름 또는 주소";setSingleLine()}
        dialog=AlertDialog.Builder(this).setTitle("목적지 검색").setView(input).setPositiveButton("검색",null).setNegativeButton("취소",null).create()
        dialog!!.setOnShowListener { dialog!!.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if(!canEdit())return@setOnClickListener
            val q=input.text.toString().trim();if(q.isBlank())return@setOnClickListener
            val d=dialog!!;d.getButton(-1).isEnabled=false;d.setTitle("검색 중…")
            MapData.search(q,map.current?:GeoPoint(37.499,127.029)){result->runOnUiThread{
                if(isDestroyed||isFinishing)return@runOnUiThread
                d.dismiss()
                if(storage.getString("vehicleState","")!="parked")return@runOnUiThread
                result.onSuccess{list->if(list.isEmpty())Toast.makeText(this,"검색 결과가 없어요. 좌표로도 입력할 수 있어요.",Toast.LENGTH_LONG).show() else {
                    dialog=AlertDialog.Builder(this).setTitle("검색 결과").setItems(list.map{"${it.name}\n${it.address}"}.toTypedArray()){_,i->if(canEdit())select(list[i])}.setNegativeButton("취소",null).show()
                }}.onFailure{Toast.makeText(this,"검색 연결을 확인해 주세요. 지도 길게 누르기나 좌표 입력도 가능해요.",Toast.LENGTH_LONG).show()}
            }}
        }};dialog!!.show()
    }
    private fun coordinateDialog(){
        val input=EditText(this).apply{hint="37.48544722, 127.03636666";setSingleLine()}
        val d=AlertDialog.Builder(this).setTitle("위도, 경도 입력").setView(input).setPositiveButton("설정",null).setNegativeButton("취소",null).create();dialog=d
        d.setOnShowListener{d.getButton(-1).setOnClickListener{
            if(!canEdit())return@setOnClickListener
            val values=input.text.toString().split(',').map{it.trim().toDoubleOrNull()}
            if(values.size!=2||values[0]==null||values[1]==null||values[0]!! !in -85.0..85.0||values[1]!! !in -180.0..180.0){input.error="올바른 위도, 경도를 입력해 주세요";return@setOnClickListener}
            select(Destination("입력한 목적지",GeoPoint(values[0]!!,values[1]!!)));d.dismiss()
        }};d.show()
    }
    private fun select(d:Destination){
        selected=d;roadRoute=null;map.setDestination(d);map.setRoute(emptyList());map.fit()
        storage.edit().putString("destinationName",d.name).putString("destinationLat",d.point.lat.toString()).putString("destinationLon",d.point.lon.toString()).apply()
        navigation.navigate(d);pendingRoute=true;requestRoadRoute();update()
    }
    private fun requestRoadRoute(){
        val destination=selected?:return
        val from=map.current?:run {routeMessage="현재 위치 수신 후 경로를 탐색해요.";return}
        pendingRoute=false;val version=++routeVersion;routeMessage="도로 경로 탐색 중…"
        MapData.io.execute{val result=runCatching{MapData.route(from,destination.point)};runOnUiThread{
            if(version!=routeVersion||isDestroyed)return@runOnUiThread
            result.onSuccess{roadRoute=it;map.setRoute(it.points);routeMessage="도로 경로 미리보기 · 실시간 교통 미반영"}
                .onFailure{roadRoute=null;map.setRoute(emptyList());map.fit();routeMessage="도로 경로 연결 실패 · 목적지 핀 표시 중"}
            update()
        }}
    }
    private fun update(){
        val now=SystemClock.elapsedRealtime()
        val signalFresh=now-storage.getLong("signalAt",0) in 0..2500
        val v=if(signalFresh&&storage.contains("liveSpeed"))storage.getFloat("liveSpeed",0f)else null
        speed.text=v?.let{"${(abs(it)*3.6).roundToInt()} km/h"}?:"— km/h"
        val steering=if(now-storage.getLong("extraAt",0)<2500&&storage.contains("steering"))storage.getFloat("steering",0f)else null
        val parked=storage.getString("vehicleState","")=="parked"
        state.text=if(!signalFresh)"● 차량 신호 확인 중"else if(parked)"● 주차 중 · 코칭 준비"else "● 실시간 주행 코칭"
        val brake=storage.getString("brakeLabel","연결 확인")
        val turn=storage.getString("turnLabel","연결 확인")
        signals.text="${storage.getString("gearLabel","—")}  ·  브레이크 $brake · 방향지시등 $turn · 조향각 ${steering?.let{"${it.roundToInt()}°"}?:"—"}"
        if(!parked) {dialog?.dismiss();dialog=null}
        if(storage.contains("liveLat")&& now-storage.getLong("locationAt",0) in 0..15000){
            map.locate(GeoPoint(storage.getString("liveLat","0")!!.toDouble(),storage.getString("liveLon","0")!!.toDouble()),storage.getFloat("bearing",0f))
            if(pendingRoute)requestRoadRoute()
        } else map.locate(null)
        val navName=storage.getString("navDestination","")?:""
        val navLat=storage.getString("navLat",null)?.toDoubleOrNull();val navLon=storage.getString("navLon",null)?.toDoubleOrNull()
        if(navName.isNotBlank()&&navLat!=null&&navLon!=null&&"$navName/$navLat/$navLon"!=lastNavDestination){
            lastNavDestination="$navName/$navLat/$navLon"
            if(selected==null||selected!!.name!=navName){selected=Destination(navName,GeoPoint(navLat,navLon));map.setDestination(selected);pendingRoute=true}
        }
        coachTitle.text=if(signalFresh)storage.getString("coachTitle","여유 있게, 함께 출발해요")else "차량 신호를 기다리고 있어요"
        coachDetail.text=if(signalFresh)storage.getString("coachDetail","주행 중 가감속과 차량 상태를 살피고 짧게 알려드릴게요.")else "연결이 확인되면 실시간 코칭을 시작해요."
        routeTitle.text=storage.getString("navInstruction",null)?.takeIf{it.isNotBlank()}?:selected?.name?:"어디로 떠나볼까요?"
        routeDetail.text=if(selected==null)"목적지를 설정하고 코칭과 함께 출발해요." else storage.getString("navStatus","")+"\n"+routeMessage
        tripTitle.text=selected?.name?:"목적지 미설정"
        val nativeRemaining=selected!=null && navName==selected?.name && now-storage.getLong("navAt",0) in 0..15000 && storage.getInt("navDistance",-1)>=0
        tripDetail.text=if(nativeRemaining){
            val seconds=storage.getInt("navSeconds",0)
            val eta=SimpleDateFormat("a h:mm",Locale.KOREAN).format(Date(System.currentTimeMillis()+seconds*1000L))
            "$eta 예상 도착\n${"%.1f".format(storage.getInt("navDistance",0)/1000.0)}km · ${ceil(seconds/60.0).toInt()}분 · Pleos 안내"
        }else roadRoute?.let{
            val eta=SimpleDateFormat("a h:mm",Locale.KOREAN).format(Date(System.currentTimeMillis()+(it.seconds*1000).toLong()))
            "$eta 예상 도착\n${"%.1f".format(it.meters/1000)}km · ${ceil(it.seconds/60).toInt()}분 · 경로 미리보기"
        }?:if(selected!=null)routeMessage else if(map.current!=null)"현재 위치를 중심으로 표시 중"else "현재 위치 수신 대기 · 기본 지역 표시"
        search.text=if(parked)"⌕  목적지 검색 / 설정"else "주행 중 · 목적지 입력 잠금"
        voice.text=if(storage.getBoolean("voiceEnabled",true)) "음성 · "+storage.getString("voiceStatus","준비 중") else "음성 꺼짐"
        clock.text=SimpleDateFormat("a h:mm",Locale.KOREAN).format(Date())
    }
    private fun review(){
        if(!canEdit())return
        val sessions=storage.getInt("sessions",0);val count=storage.getInt("lastCount",0)
        val goal=storage.getInt("goalSession",0)
        val builder=AlertDialog.Builder(this).setTitle("드라이빙 정원 · 주행 회고")
            .setMessage("누적 ${sessions}회 주행\n최근 주행 급감속 후보 ${count}건\n\n상황에 따른 필요한 감속도 포함될 수 있어요.\n다음 목표: 주변 흐름을 일찍 살피고 부드럽게 감속하기")
            .setNegativeButton("닫기",null)
        if(sessions>0&&goal==0)builder.setPositiveButton("목표 저장 · 씨앗 심기"){_,_->if(canEdit())storage.edit().putInt("goalSession",sessions).apply()}
        else if(goal>0&&sessions>goal)builder.setPositiveButton("목표 돌아보기 · 잎 남기기"){_,_->if(canEdit())storage.edit().remove("goalSession").putInt("leaves",storage.getInt("leaves",0)+1).apply()}
        dialog=builder.show()
    }
    private fun dp(value:Int)=(value*resources.displayMetrics.density).roundToInt()
    override fun onDestroy(){handler.removeCallbacksAndMessages(null);dialog?.dismiss();if(::navigation.isInitialized)navigation.close();super.onDestroy()}
}
