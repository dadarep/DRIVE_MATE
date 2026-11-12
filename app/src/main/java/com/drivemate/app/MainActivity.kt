package com.drivemate.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.*
import android.text.TextUtils
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
    private val night by lazy { (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES }
    private val ink by lazy { if(night)Color.rgb(236,240,246) else Color.rgb(27,43,65) }
    private val muted by lazy { if(night)Color.rgb(162,174,190) else Color.rgb(84,98,116) }
    private val page by lazy { if(night)Color.rgb(12,16,22) else Color.rgb(240,243,247) }
    private val surface by lazy { if(night)Color.rgb(27,33,43) else Color.WHITE }
    private val soft by lazy { if(night)Color.rgb(41,49,62) else Color.rgb(235,241,255) }
    private val accentText by lazy { if(night)Color.rgb(132,170,255) else blue }
    private val blue=Color.rgb(31,96,247)
    private val amber=Color.rgb(222,134,20)
    private val banner=Color.rgb(20,32,52)
    private lateinit var map:RouteMap
    private lateinit var speed:TextView
    private lateinit var state:TextView
    private lateinit var signals:TextView
    private lateinit var stateDot:GradientDrawable
    private lateinit var coachAccent:GradientDrawable
    private lateinit var coachLabel:TextView
    private lateinit var mascot:ImageView
    private lateinit var coachCard:LinearLayout
    private lateinit var mapTools:LinearLayout
    private lateinit var coachTitle:TextView
    private lateinit var coachDetail:TextView
    private lateinit var routeBanner:View
    private lateinit var maneuverIcon:TextView
    private lateinit var maneuverDistance:TextView
    private lateinit var routeTitle:TextView
    private lateinit var routeDetail:TextView
    private lateinit var tripLabel:TextView
    private lateinit var chips:LinearLayout
    private lateinit var thenRow:TextView
    private lateinit var overview:View
    private val earcons by lazy { Earcons(this) }
    private var chipKey=""
    private lateinit var tripTitle:TextView
    private lateinit var tripDetail:TextView
    private lateinit var tripCard:View
    private lateinit var mapOnly:TextView
    private lateinit var sourceLabel:TextView
    private lateinit var clock:TextView
    private lateinit var voice:TextView
    private lateinit var search:TextView
    private lateinit var clear:TextView
    private lateinit var retry:TextView
    private var routeFailed=false
    private lateinit var reviewButton:TextView
    private lateinit var settingsButton:TextView
    private lateinit var navigation:NavigationLink
    private var selected:Destination?=null
    private var roadRoute:RoadRoute?=null
    private var routeMessage="목적지를 선택하면 도로 경로를 보여 드려요."
    private var routeVersion=0
    private var lastNavDestination=""
    private var pendingRoute=false
    private var dialog:AlertDialog?=null
    // Notifications only show the recording/paused state; Android 13+ hides even the service notice without this grant.
    private val permissions=arrayOf("android.car.permission.CAR_SPEED",android.Manifest.permission.ACCESS_COARSE_LOCATION,android.Manifest.permission.ACCESS_FINE_LOCATION)+
        (if(Build.VERSION.SDK_INT>=33)arrayOf(android.Manifest.permission.POST_NOTIFICATIONS) else emptyArray())
    private val poll=object:Runnable { override fun run(){update();handler.postDelayed(this,300)} }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=page;window.navigationBarColor=page
        buildScreen()
        navigation=NavigationLink(this);navigation.start()
        if(permissions.any{checkSelfPermission(it)!=PackageManager.PERMISSION_GRANTED}) requestPermissions(permissions,1)
        if(storage.contains("destinationLat")) {
            selected=Destination(storage.getString("destinationName","")?:"목적지",GeoPoint(storage.getString("destinationLat","0")!!.toDouble(),storage.getString("destinationLon","0")!!.toDouble()))
            map.setDestination(selected);pendingRoute=true
        }
        handler.post(poll)
    }
    private var grantKey=""
    private var wasLocked=true
    private var seenSessions=-1
    // Pleos keeps this activity resumed while system settings is open, so grants are noticed by polling, not onResume.
    private fun syncGrants(){
        val key=permissions.joinToString{"${checkSelfPermission(it)==PackageManager.PERMISSION_GRANTED}"}
        if(key==grantKey)return
        grantKey=key;if(!speedDenied())startRecorder()
    }
    override fun onRequestPermissionsResult(code:Int,permissions:Array<String>,results:IntArray) {
        super.onRequestPermissionsResult(code,permissions,results)
        if(!speedDenied()) startRecorder()
        else Toast.makeText(this,"차속 권한이 없으면 주행을 기록할 수 없어요. 계속 거부되면 시스템 설정에서 허용해 주세요.",Toast.LENGTH_LONG).show()
    }
    // Without speed there is no parked state, so settings stay locked; the card is the only way back to the request.
    private fun speedDenied()=checkSelfPermission("android.car.permission.CAR_SPEED")!=PackageManager.PERMISSION_GRANTED
    private fun startRecorder(){startForegroundService(Intent(this,DriveRecorder::class.java))}
    private fun text(value:String,size:Float=24f,color:Int=ink,bold:Boolean=false)=TextView(this).apply {
        text=value;textSize=size;setTextColor(color);if(bold)typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        includeFontPadding=false
    }
    private fun rounded(color:Int=surface,radius:Int=20)=GradientDrawable().apply {setColor(color);cornerRadius=dp(radius).toFloat()}
    private fun card()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(24),dp(22),dp(24),dp(24));background=rounded(radius=24);elevation=dp(if(night)0 else 2).toFloat()}
    private fun button(label:String,primary:Boolean=false,action:()->Unit)=text(label,22f,if(primary)Color.WHITE else accentText,true).apply{
        gravity=Gravity.CENTER;minHeight=dp(76);setPadding(dp(20),0,dp(20),0)
        background=rounded(if(primary)blue else soft,20)
        isClickable=true;isFocusable=true;setOnClickListener{action()}
    }
    private fun mapButton(label:String,description:String,action:()->Unit)=text(label,30f,ink,true).apply{
        gravity=Gravity.CENTER;background=rounded(radius=20);elevation=dp(4).toFloat();contentDescription=description
        isClickable=true;isFocusable=true;setOnClickListener{action()}
    }
    private fun buildScreen() {
        val root=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(dp(20),dp(16),dp(20),dp(20));setBackgroundColor(page)}
        val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}

        val header=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(4),0,dp(4),dp(14))}
        header.addView(text("DRIVE MATE",24f,ink,true),LinearLayout.LayoutParams(0,-2,1f))
        clock=text("",24f,ink,true);header.addView(clock)
        panel.addView(header)

        val status=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(24),dp(16),dp(24),dp(16));background=rounded(radius=24);elevation=dp(if(night)0 else 2).toFloat()}
        val stateBox=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
        stateDot=GradientDrawable().apply{shape=GradientDrawable.OVAL;setColor(muted)}
        stateBox.addView(View(this).apply{background=stateDot},LinearLayout.LayoutParams(dp(16),dp(16)).apply{rightMargin=dp(12)})
        state=text("차량 연결 중",24f,ink,true).apply{maxLines=2};stateBox.addView(state,LinearLayout.LayoutParams(0,-2,1f))
        status.addView(stateBox,LinearLayout.LayoutParams(0,-2,1f))
        speed=text("—",56f,ink,true);status.addView(speed)
        status.addView(text(" km/h",24f,muted))
        panel.addView(status,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(16)})

        coachCard=card().apply{setPadding(dp(30),dp(22),dp(24),dp(26));minimumHeight=dp(340);setOnClickListener{if(speedDenied())requestPermissions(permissions,1) else review()}}
        coachAccent=rounded(muted,24)
        coachCard.background=LayerDrawable(arrayOf(coachAccent,rounded(radius=22))).apply{setLayerInset(1,dp(8),0,0,0)}
        val coachHead=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
        coachLabel=text("드라이빙 정원",22f,muted,true);coachHead.addView(coachLabel,LinearLayout.LayoutParams(0,-2,1f))
        coachHead.addView(ImageView(this).also{mascot=it}.apply{setImageBitmap(assets.open("garden.png").use{BitmapFactory.decodeStream(it)});scaleType=ImageView.ScaleType.FIT_CENTER;contentDescription="드라이빙 정원 캐릭터"},LinearLayout.LayoutParams(dp(68),dp(60)))
        coachCard.addView(coachHead)
        coachTitle=text("여유 있게, 함께 출발해요",32f,ink,true).apply{setPadding(0,dp(8),0,dp(12));maxLines=2;setLineSpacing(dp(6).toFloat(),1f)};coachCard.addView(coachTitle)
        coachDetail=text("차량 신호를 확인하면 상황에 맞춰 알려드릴게요.",24f,muted).apply{maxLines=3;setLineSpacing(dp(6).toFloat(),1f)};coachCard.addView(coachDetail)
        chips=LinearLayout(this).apply{setPadding(0,dp(16),0,0)};coachCard.addView(chips)
        panel.addView(coachCard,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(16)})

        val trip=card();tripCard=trip
        tripLabel=text("목적지",22f,accentText,true).apply{maxLines=1;ellipsize=TextUtils.TruncateAt.END};trip.addView(tripLabel)
        tripTitle=text("목적지 미설정",32f,ink,true).apply{setPadding(0,dp(10),0,dp(10));maxLines=1;ellipsize=TextUtils.TruncateAt.END};trip.addView(tripTitle)
        tripDetail=text("현재 위치를 중심으로 표시 중",24f,ink).apply{maxLines=2;setLineSpacing(dp(6).toFloat(),1f)};trip.addView(tripDetail)
        routeDetail=text("",20f,muted).apply{setPadding(0,dp(10),0,0);maxLines=1;ellipsize=TextUtils.TruncateAt.END};trip.addView(routeDetail)
        val actionsRow=LinearLayout(this).apply{setPadding(0,dp(20),0,0)}
        search=button("목적지 검색",true){openDestinations()};actionsRow.addView(search,LinearLayout.LayoutParams(0,dp(76),1f))
        retry=button("다시 시도",true){if(canEdit()){routeFailed=false;pendingRoute=true;requestRoadRoute();update()}}.apply{contentDescription="도로 경로 다시 탐색";visibility=View.GONE}
        actionsRow.addView(retry,LinearLayout.LayoutParams(0,dp(76),1f))
        mapOnly=button("지도만 보기"){if(canEdit()){routeFailed=false;routeMessage="목적지 핀만 표시 중 · 도로 경로 없음";map.fit();update()}}.apply{contentDescription="경로 없이 목적지 핀만 보기";visibility=View.GONE}
        actionsRow.addView(mapOnly,LinearLayout.LayoutParams(0,dp(76),1f).apply{leftMargin=dp(16)})
        clear=button("해제"){clearRoute()}.apply{contentDescription="목적지 해제"};actionsRow.addView(clear,LinearLayout.LayoutParams(dp(110),dp(76)).apply{leftMargin=dp(16)})
        trip.addView(actionsRow)
        panel.addView(trip,LinearLayout.LayoutParams(-1,-2))

        panel.addView(View(this),LinearLayout.LayoutParams(-1,0,1f))
        signals=text("차속 · GPS · Pleos 차량 데이터",18f,muted).apply{setPadding(dp(6),0,dp(6),dp(12));maxLines=2};panel.addView(signals)
        val actions=LinearLayout(this)
        voice=button("음성 꺼짐"){val enabled=!storage.getBoolean("voiceEnabled",false);storage.edit().putBoolean("voiceEnabled",enabled).apply();startService(Intent(this,DriveRecorder::class.java).setAction(if(enabled) "VOICE_CHECK" else "VOICE_STOP"));update()}
        actions.addView(voice,LinearLayout.LayoutParams(0,dp(76),1f))
        reviewButton=button("주행 회고"){review()};actions.addView(reviewButton,LinearLayout.LayoutParams(0,dp(76),1f).apply{leftMargin=dp(16)})
        settingsButton=button("⚙"){settings()}.apply{textSize=28f;setPadding(0,0,0,0);contentDescription="설정"};actions.addView(settingsButton,LinearLayout.LayoutParams(dp(76),dp(76)).apply{leftMargin=dp(16)})
        panel.addView(actions)
        root.addView(panel,LinearLayout.LayoutParams(dp(500),-1))

        val right=FrameLayout(this).apply{background=rounded(if(night)Color.rgb(30,36,44) else Color.rgb(232,237,240),24);clipToOutline=true}
        map=RouteMap(this).apply{night=this@MainActivity.night};right.addView(map,FrameLayout.LayoutParams(-1,-1))
        map.onPick={p->if(canEdit()){
            map.preview=p
            val away=map.current?.let{"현재 위치에서 ${distanceLabel(meters(it,p))} 떨어진 곳이에요.\n"}?:""
            sheet("파란 핀 위치로 갈까요?","${away}목적지로 정하면 도로 경로를 탐색해요.",primary="목적지 설정",cancel="취소",gravity=Gravity.BOTTOM,onPrimary={s->s.dialog.dismiss();if(canEdit())select(Destination("지도에서 선택한 목적지",p))})
                .dialog.setOnDismissListener{map.preview=null}
        }}
        val tbt=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(18),dp(32),dp(18));background=rounded(banner,24);elevation=dp(6).toFloat();visibility=View.GONE}
        val tbtMain=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
        maneuverIcon=text("↑",64f,Color.WHITE,true).apply{gravity=Gravity.CENTER;background=rounded(blue,18)}
        tbtMain.addView(maneuverIcon,LinearLayout.LayoutParams(dp(96),dp(96)).apply{rightMargin=dp(22)})
        val tbtText=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        maneuverDistance=text("",40f,Color.WHITE,true);tbtText.addView(maneuverDistance)
        routeTitle=text("",26f,Color.rgb(205,216,232)).apply{setPadding(0,dp(8),0,0);maxLines=2;ellipsize=TextUtils.TruncateAt.END};tbtText.addView(routeTitle)
        tbtMain.addView(tbtText);tbt.addView(tbtMain)
        thenRow=text("",22f,Color.rgb(170,184,204)).apply{setPadding(dp(4),dp(14),0,0);maxLines=1;ellipsize=TextUtils.TruncateAt.END;visibility=View.GONE}
        tbt.addView(thenRow)
        routeBanner=tbt
        right.addView(tbt,FrameLayout.LayoutParams(-2,-2,Gravity.TOP or Gravity.LEFT).apply{leftMargin=dp(24);topMargin=dp(24);rightMargin=dp(130)})
        sourceLabel=text("지도 연결 중",18f,muted).apply{setPadding(dp(14),dp(8),dp(14),dp(8));background=rounded(if(night)Color.argb(230,27,33,43) else Color.argb(230,255,255,255),14)}
        right.addView(sourceLabel,FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM or Gravity.LEFT).apply{leftMargin=dp(20);bottomMargin=dp(20)})
        map.tileState={sourceLabel.text=it}
        val tools=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};mapTools=tools
        tools.addView(mapButton("＋","지도 확대"){map.zoomBy(1)},LinearLayout.LayoutParams(dp(76),dp(76)))
        tools.addView(mapButton("−","지도 축소"){map.zoomBy(-1)},LinearLayout.LayoutParams(dp(76),dp(76)).apply{topMargin=dp(24)})
        tools.addView(mapButton("◎","현재 위치로 이동"){map.recenter()},LinearLayout.LayoutParams(dp(76),dp(76)).apply{topMargin=dp(40)})
        overview=mapButton("전체","경로 전체 보기"){map.fit()}.apply{textSize=20f;visibility=View.GONE}
        tools.addView(overview,LinearLayout.LayoutParams(dp(76),dp(76)).apply{topMargin=dp(24)})
        right.addView(tools,FrameLayout.LayoutParams(-2,-2,Gravity.RIGHT or Gravity.CENTER_VERTICAL).apply{rightMargin=dp(24)})
        root.addView(right,LinearLayout.LayoutParams(0,-1,1f).apply{leftMargin=dp(20)})
        setContentView(root)
    }
    private var reclaimUntil=0L
    // Pleos brings its navigation app to the front when a route is cancelled; clearing or deleting should keep the driver here.
    private fun cancelNavigation(){reclaimUntil=SystemClock.elapsedRealtime()+3000;navigation.cancel()}
    override fun onTopResumedActivityChanged(top:Boolean){
        super.onTopResumedActivityChanged(top)
        if(!top&&SystemClock.elapsedRealtime()<reclaimUntil){reclaimUntil=0;startActivity(Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))}
    }
    private fun clearRoute(){
        if(!canEdit())return
        routeVersion++;pendingRoute=false;routeFailed=false;selected=null;roadRoute=null;map.setDestination(null);map.setRoute(emptyList());cancelNavigation()
        storage.edit().remove("destinationLat").remove("destinationLon").remove("destinationName").apply()
        map.recenter();routeMessage="목적지를 선택하면 도로 경로를 보여 드려요.";update()
    }
    private fun tripSummary(count:Int):String{
        val gap=TripQuality.gapLabel(storage.getLong("lastGapMs",0))
        val base=when{storage.getBoolean("lastInsufficient",false)->"기록이 부족해 급감속 후보를 확인할 수 없어요.";count==0->"급감속 후보 없이 기록됐어요.";else->"급감속 후보 ${count}건이 있었어요."}
        return base+(gap?.let{" 신호 공백 ${it}는 제외했어요."}?:" 주행 회고에서 돌아봐요.")
    }
    private fun confirmedParked()=storage.getString("vehicleState","")=="parked"&&SystemClock.elapsedRealtime()-storage.getLong("signalAt",0) in 0..2500
    private fun canEdit():Boolean {
        if(!confirmedParked()) {Toast.makeText(this,"목적지 입력과 상세 확인은 주차 후 이용해 주세요.",Toast.LENGTH_SHORT).show();return false};return true
    }
    private class Sheet(val dialog:AlertDialog,val title:TextView,val primary:TextView?)
    private fun sheet(title:String,message:String?=null,primary:String?=null,cancel:String="닫기",gravity:Int=Gravity.CENTER,scroll:Boolean=false,onPrimary:((Sheet)->Unit)?=null,body:(LinearLayout.()->Unit)?=null):Sheet{
        val content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(36),dp(32),dp(36),dp(28));background=rounded(radius=28)}
        val heading=text(title,32f,ink,true).apply{setPadding(0,0,0,dp(16))};content.addView(heading)
        message?.let{content.addView(text(it,24f,muted).apply{setLineSpacing(dp(6).toFloat(),1f);setPadding(0,0,0,dp(14))})}
        if(scroll&&body!=null){
            val inner=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};inner.body()
            val maxHeight=(resources.displayMetrics.heightPixels*0.62).roundToInt()
            content.addView(object:ScrollView(this){override fun onMeasure(w:Int,h:Int)=super.onMeasure(w,View.MeasureSpec.makeMeasureSpec(maxHeight,View.MeasureSpec.AT_MOST))}.apply{addView(inner)})
        } else body?.invoke(content)
        val d=AlertDialog.Builder(this).setView(content).create()
        val actions=LinearLayout(this).apply{setPadding(0,dp(20),0,0)}
        actions.addView(button(cancel){d.dismiss()},LinearLayout.LayoutParams(0,dp(76),1f))
        val primaryButton=primary?.let{label->button(label,true){}.also{actions.addView(it,LinearLayout.LayoutParams(0,dp(76),1f).apply{leftMargin=dp(24)})}}
        content.addView(actions)
        val result=Sheet(d,heading,primaryButton)
        primaryButton?.setOnClickListener{onPrimary?.invoke(result)}
        d.show();d.window?.apply{setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT));setLayout(dp(720),-2);setGravity(gravity);if(gravity==Gravity.BOTTOM){attributes=attributes.apply{y=dp(24)};setDimAmount(0.2f)}}
        dialog=d;return result
    }
    private fun LinearLayout.choice(title:String,subtitle:String?=null,action:()->Unit){
        val row=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_VERTICAL;minimumHeight=dp(88);setPadding(dp(24),dp(14),dp(24),dp(14))
            background=rounded(soft,20);isClickable=true;isFocusable=true;setOnClickListener{action()}}
        row.addView(text(title,26f,ink,true))
        subtitle?.takeIf{it.isNotBlank()}?.let{row.addView(text(it,20f,muted).apply{setPadding(0,dp(8),0,0);maxLines=1;ellipsize=TextUtils.TruncateAt.END})}
        addView(row,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(12)})
    }
    private fun section(label:String,top:Int=0)=text(label,20f,accentText,true).apply{setPadding(dp(4),dp(top),0,dp(12))}
    private fun input(hint:String)=EditText(this).apply{
        this.hint=hint;setSingleLine();textSize=26f;setTextColor(ink);setHintTextColor(muted);minHeight=dp(80);setPadding(dp(24),0,dp(24),0)
        background=rounded(soft,20)
    }
    private fun openDestinations(){
        if(!canEdit())return
        val places=listOf(Destination("42dot",GeoPoint(37.48544722,127.03636666),"서울시 강남구 남부순환로 2621"),Destination("강남역",GeoPoint(37.49795,127.02762),"서울시 강남구 강남대로"),Destination("양재역",GeoPoint(37.48465,127.03415),"서울시 서초구 남부순환로"))
        sheet("어디로 갈까요?"){
            addView(section("자주 가는 곳"))
            places.forEach{p->choice(p.name,p.address){if(canEdit()){dialog?.dismiss();select(p)}}}
            addView(section("직접 찾기",12))
            choice("⌕  장소 이름으로 검색","주소나 장소 이름으로 찾아요"){dialog?.dismiss();searchDialog()}
            choice("⌖  위도, 경도로 입력","좌표를 직접 입력해요"){dialog?.dismiss();coordinateDialog()}
        }
    }
    private fun searchDialog(){
        if(!canEdit())return
        val field=input("장소 이름 또는 주소")
        val error=errorText()
        sheet("목적지 검색",primary="검색",cancel="취소",onPrimary={s->
            val q=field.text.toString().trim()
            if(q.isBlank())showError(error,"검색어를 입력해 주세요.")
            else if(canEdit()){
                error.visibility=View.GONE
                s.primary?.isEnabled=false;s.primary?.text="검색 중…";s.primary?.alpha=0.6f
                MapData.search(q,map.current?:GeoPoint(37.499,127.029)){result->runOnUiThread{
                    if(isDestroyed||isFinishing||!s.dialog.isShowing)return@runOnUiThread
                    s.primary?.isEnabled=true;s.primary?.text="다시 검색";s.primary?.alpha=1f
                    result.onSuccess{list->if(list.isEmpty())showError(error,"검색 결과가 없어요. 다른 이름이나 좌표로 찾아보세요.") else {
                        s.dialog.dismiss()
                        sheet("검색 결과 ${list.size}건",cancel="취소"){
                            val rows=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.VERTICAL}
                            list.forEach{p->rows.choice(p.name,listOfNotNull(map.current?.let{distanceLabel(meters(it,p.point))},p.address.takeIf{it.isNotBlank()}).joinToString(" · ")){if(canEdit()){dialog?.dismiss();select(p)}}}
                            addView(ScrollView(this@MainActivity).apply{addView(rows)},LinearLayout.LayoutParams(-1,dp(min(list.size,4)*112)))
                        }
                    }}.onFailure{showError(error,"검색 서버에 연결하지 못했어요. 지도를 길게 누르거나 좌표로 입력할 수 있어요.")}
                }}
            }
        }){addView(field,LinearLayout.LayoutParams(-1,-2));addView(error)
            addView(text("검색어와 대략적인 위치(약 1km 단위)가 외부 지도 서비스(Photon)로 전송돼요.",20f,muted).apply{setPadding(dp(8),dp(14),dp(8),0);setLineSpacing(dp(4).toFloat(),1f)})}
    }
    private fun coordinateDialog(){
        if(!canEdit())return
        val field=input("37.48544722, 127.03636666")
        val error=errorText()
        sheet("위도, 경도 입력","쉼표로 구분해 입력해 주세요.",primary="목적지 설정",cancel="취소",onPrimary={s->
            if(canEdit()){
                val values=field.text.toString().split(',').map{it.trim().toDoubleOrNull()}
                if(values.size!=2||values[0]==null||values[1]==null||values[0]!! !in -85.0..85.0||values[1]!! !in -180.0..180.0)showError(error,"위도는 -85~85, 경도는 -180~180 사이 숫자로 입력해 주세요.")
                else {select(Destination("입력한 목적지",GeoPoint(values[0]!!,values[1]!!)));s.dialog.dismiss()}
            }
        }){addView(field,LinearLayout.LayoutParams(-1,-2));addView(error)}
    }
    private fun errorText()=text("",20f,if(night)Color.rgb(255,128,128) else Color.rgb(200,40,40)).apply{setPadding(dp(8),dp(14),dp(8),0);visibility=View.GONE;setLineSpacing(dp(4).toFloat(),1f)}
    private fun showError(view:TextView,message:String){view.text="⚠ $message";view.visibility=View.VISIBLE}
    private fun select(d:Destination){
        selected=d;roadRoute=null;map.setDestination(d);map.setRoute(emptyList());map.fit()
        storage.edit().putString("destinationName",d.name).putString("destinationLat",d.point.lat.toString()).putString("destinationLon",d.point.lon.toString()).apply()
        navigation.navigate(d);pendingRoute=true;requestRoadRoute();update()
        earcons.play(Earcons.Cue.DESTINATION)
    }
    private fun requestRoadRoute(){
        val destination=selected?:return
        val from=map.current?:run {routeMessage="현재 위치 수신 후 경로를 탐색해요.";return}
        pendingRoute=false;routeFailed=false;val version=++routeVersion;routeMessage="도로 경로 탐색 중…"
        MapData.io.execute{val result=runCatching{MapData.route(from,destination.point)};runOnUiThread{
            if(version!=routeVersion||isDestroyed)return@runOnUiThread
            result.onSuccess{roadRoute=it;map.setRoute(it.points);routeMessage="도로 경로 미리보기 · 실시간 교통 미반영"}
                .onFailure{roadRoute=null;routeFailed=true;map.setRoute(emptyList());map.fit();routeMessage="도로 경로 연결 실패 · 다시 시도하거나 핀으로 볼 수 있어요"}
            update()
        }}
    }
    private fun maneuverGlyph(instruction:String)=when{
        "유턴" in instruction->"↶"
        "좌회전" in instruction||"왼쪽" in instruction->"↰"
        "우회전" in instruction||"오른쪽" in instruction->"↱"
        "도착" in instruction||"목적지" in instruction->"⚑"
        else->"↑"
    }
    private fun meters(a:GeoPoint,b:GeoPoint):Float{
        val dLat=Math.toRadians(b.lat-a.lat);val dLon=Math.toRadians(b.lon-a.lon)
        val h=sin(dLat/2).pow(2)+cos(Math.toRadians(a.lat))*cos(Math.toRadians(b.lat))*sin(dLon/2).pow(2)
        return (12742000*asin(sqrt(h))).toFloat()
    }
    private fun distanceLabel(meters:Float)=when{
        meters>=1000->"%.1f km".format(meters/1000)
        meters>=100->"${(meters/50).roundToInt()*50} m"
        else->"${(meters/10).roundToInt()*10} m"
    }
    private fun update(){
        syncGrants()
        val now=SystemClock.elapsedRealtime()
        val signalFresh=now-storage.getLong("signalAt",0) in 0..2500
        val v=if(signalFresh&&storage.contains("liveSpeed"))storage.getFloat("liveSpeed",0f)else null
        val kph=v?.let{(abs(it)*3.6).roundToInt()}
        speed.text=kph?.toString()?:"—"
        val parked=signalFresh&&storage.getString("vehicleState","")=="parked"
        val driving=signalFresh&&!parked
        val locked=!parked
        state.text=if(!signalFresh)"차량 신호 확인 중"else if(parked)"주차 중"else if(storage.getString("vehicleState","")=="settling")"주차 확인 중" else "주행 중"
        stateDot.setColor(if(parked)amber else muted)
        signals.text="기어 ${storage.getString("gearLabel","—")} · 차속 신호 수신 중"
        signals.visibility=if(locked)View.GONE else View.VISIBLE
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
        val tripDone=parked&&storage.getInt("sessions",0)>0&&!storage.getBoolean("reviewed",true)
        val lastCount=storage.getInt("lastCount",0)
        val activeGoal=garden().goal
        val goalPending=activeGoal!=null
        val goalDue=garden().due(storage.getInt("sessions",0))
        // While not confirmed parked the card carries no evaluation text, colors, or touch targets.
        val paused=storage.getBoolean("recordingPaused",false)
        val noSpeed=speedDenied()
        coachTitle.text=when{noSpeed->"차속 권한이 필요해요";!signalFresh->"차량 신호를 기다리고 있어요";tripDone->"주행을 마쳤어요, 수고했어요";paused->"기록이 중지돼 있어요";parked&&activeGoal!=null->activeGoal.text;parked->"여유 있게, 함께 출발해요";else->"주행을 기록하고 있어요"}
        coachDetail.text=when{noSpeed->"차속을 받아야 주행을 기록할 수 있어요. 이 카드를 눌러 권한을 요청해요.";!signalFresh->"연결이 확인되면 주행 기록을 이어가요.";tripDone->tripSummary(lastCount);paused&&parked->"속도·위치를 기록하지 않아요. 설정에서 다시 켤 수 있어요.";paused->"속도·위치를 기록하지 않아요. 주차 후 설정에서 다시 켤 수 있어요.";parked&&goalPending->if(goalDue)"주행 회고에서 어땠는지 답하면 첫 잎이 돋아요." else "이번 주행의 연습 목표예요. 주차 후 어땠는지 돌아봐요.";parked->"출발하면 속도 변화를 기록하고, 주차 후 함께 돌아봐요.";else->"목적지 변경과 회고는 주차 후 열려요."}
        val accent=if(parked)blue else muted
        coachAccent.setColor(accent);coachLabel.setTextColor(if(accent==blue)accentText else accent)
        coachLabel.text=when{noSpeed->"권한 필요";tripDone&&paused->"주행 완료 · 기록 중지";tripDone->"주행 완료";paused->"기록 중지";parked&&goalPending->if(goalDue)"🌱 돌아볼 목표" else "🌱 오늘의 연습 목표";driving->"기록 중";else->"드라이빙 정원"}
        coachCard.isClickable=noSpeed||tripDone||(parked&&goalPending)
        val instruction=storage.getString("navInstruction",null)?.takeIf{it.isNotBlank()&&selected!=null&&now-storage.getLong("tbtAt",0) in 0..30000}
        routeBanner.visibility=if(instruction!=null&&!locked)View.VISIBLE else View.GONE
        if(instruction!=null){
            routeTitle.text=instruction;maneuverIcon.text=maneuverGlyph(instruction)
            val turnDistance=storage.getFloat("turnDistance",-1f)
            maneuverDistance.text=if(turnDistance>=0)distanceLabel(turnDistance)else "다음 안내"
            val then=storage.getString("thenInstruction",null)?.takeIf{it.isNotBlank()}
            thenRow.text=then?.let{"그다음  ${maneuverGlyph(it)}  ${storage.getFloat("thenDistance",-1f).takeIf{d->d>=0}?.let{d->distanceLabel(d)+" · "}?:""}$it"}?:""
            thenRow.visibility=if(then!=null)View.VISIBLE else View.GONE
        }
        overview.visibility=if(selected!=null)View.VISIBLE else View.GONE
        // Map tools are hidden while driving, so a view panned while parked must not strand the car off screen.
        if(locked&&!wasLocked){if(map.route.isEmpty())map.recenter() else map.fit()}
        wasLocked=locked
        val sessions=storage.getInt("sessions",0)
        // The preview was measured from the old start; after a trip it is re-measured from where the car parked.
        if(sessions!=seenSessions){if(seenSessions>=0&&selected!=null&&roadRoute!=null){roadRoute=null;pendingRoute=true};seenSessions=sessions}
        mapTools.visibility=if(locked)View.GONE else View.VISIBLE
        map.interactive=!locked
        voice.visibility=if(locked)View.GONE else View.VISIBLE
        updateChips(signalFresh,driving)
        val navStatus=storage.getString("navStatus","")?.takeIf{it.isNotBlank()&&selected!=null}
        routeDetail.text=navStatus?:"";routeDetail.visibility=if(navStatus!=null&&!locked)View.VISIBLE else View.GONE
        val nativeRemaining=selected!=null && navName==selected?.name && now-storage.getLong("navAt",0) in 0..15000 && storage.getInt("navDistance",-1)>=0
        val (remainMeters,remainSeconds,source)=when{
            nativeRemaining->Triple(storage.getInt("navDistance",0).toDouble(),storage.getInt("navSeconds",0).toDouble(),"남음")
            roadRoute!=null->Triple(roadRoute!!.meters,roadRoute!!.seconds,"남음 · 미리보기")
            else->Triple(-1.0,-1.0,"")
        }
        if(selected!=null&&remainSeconds>=0){
            val eta=SimpleDateFormat("a h:mm",Locale.KOREAN).format(Date(System.currentTimeMillis()+(remainSeconds*1000).toLong()))
            tripLabel.text="목적지 · ${selected!!.name}"
            tripTitle.text="🏁 $eta 도착"
            tripDetail.text="${"%.1f".format(remainMeters/1000)}km · ${ceil(remainSeconds/60).toInt()}분 $source"
        }else{
            tripLabel.text="목적지"
            tripTitle.text=selected?.name?:if(driving)"자유 주행 중" else "어디로 떠나볼까요?"
            tripDetail.text=if(selected!=null)routeMessage else if(driving)"주차 후 함께 돌아봐요." else "목적지를 정하고 함께 출발해요.\n지도를 길게 눌러 고를 수도 있어요."
        }
        mascot.visibility=if(locked)View.INVISIBLE else View.VISIBLE
        tripCard.visibility=if(locked)View.GONE else View.VISIBLE
        reviewButton.visibility=if(locked)View.GONE else View.VISIBLE
        settingsButton.visibility=if(locked)View.GONE else View.VISIBLE
        search.text=if(selected==null)"목적지 검색" else "목적지 변경"
        clear.visibility=if(selected!=null)View.VISIBLE else View.GONE
        val failed=routeFailed&&selected!=null
        search.visibility=if(failed)View.GONE else View.VISIBLE
        retry.visibility=if(failed)View.VISIBLE else View.GONE
        mapOnly.visibility=if(failed)View.VISIBLE else View.GONE
        voice.text=if(!storage.getBoolean("voiceEnabled",false))"🔇 음성 꺼짐" else when(storage.getString("voiceStatus","")){"재생 중"->"🔊 안내 중";"음성 연결 확인"->"⚠ 음성 확인";else->"🔊 음성 켜짐"}
        clock.text=SimpleDateFormat("a h:mm",Locale.KOREAN).format(Date())
    }
    private fun chip(label:String,tone:Int)=text(label,20f,when(tone){1->if(night)Color.rgb(120,220,170) else Color.rgb(14,110,70);2->if(night)Color.rgb(255,150,150) else Color.rgb(180,40,40);else->accentText},true).apply{
        setPadding(dp(16),dp(10),dp(16),dp(10))
        background=rounded(when(tone){1->if(night)Color.rgb(25,60,45) else Color.rgb(225,245,236);2->if(night)Color.rgb(70,30,32) else Color.rgb(253,232,232);else->soft},20)
    }
    private fun updateChips(signalFresh:Boolean,driving:Boolean){
        val items=when{
            !signalFresh||driving->emptyList()
            else->listOf("누적 ${storage.getInt("sessions",0)}회 주행" to 0,"🍃 잎 ${garden().leaves}장" to 1)
        }
        val key=items.joinToString()
        if(key==chipKey)return
        chipKey=key;chips.removeAllViews()
        items.forEachIndexed{i,(label,tone)->chips.addView(chip(label,tone),LinearLayout.LayoutParams(-2,-2).apply{if(i>0)leftMargin=dp(10)})}
        chips.visibility=if(items.isEmpty())View.GONE else View.VISIBLE
    }
    private var gardenSource:String?=null
    private var gardenCache=Garden()
    /** Reads the garden, migrating the pre-ID fixed goal and leaf count once. A broken value is kept aside, never overwritten silently. */
    private fun garden():Garden{
        val raw=storage.getString("garden",null)
        if(raw!=null&&raw==gardenSource)return gardenCache
        val parsed=raw?.let{runCatching{Garden.fromJson(it)}.getOrNull()}
        val g=when{
            parsed!=null->parsed
            raw!=null->Garden().also{storage.edit().putString("gardenBroken",raw).putString("garden",it.toJson()).commit()}
            else->{
                val legacyGoal=storage.getInt("goalSession",0)
                val migrated=Garden(legacyLeaves=storage.getInt("leaves",0)).let{if(legacyGoal>0)it.withGoal(UUID.randomUUID().toString(),Garden.PRESET_GOALS[0],"legacy-$legacyGoal",legacyGoal) else it}
                storage.edit().putString("garden",migrated.toJson()).remove("goalSession").remove("goalCompletedSession").remove("lastResponse").remove("leaves").commit()
                migrated
            }
        }
        gardenSource=storage.getString("garden",null);gardenCache=g;return g
    }
    private fun saveGarden(g:Garden,extra:(SharedPreferences.Editor.()->Unit)?=null):Boolean{
        val ok=storage.edit().putString("garden",g.toJson()).apply{extra?.invoke(this)}.commit()
        if(!ok)Toast.makeText(this,"저장하지 못했어요. 다시 눌러 주세요.",Toast.LENGTH_SHORT).show()
        return ok
    }
    private fun tripId(sessions:Int)=storage.getString("lastSessionId",null)?:"legacy-$sessions"
    private val contextOptions=listOf("FLOW" to "신호·정체","CUT_IN" to "끼어들기","HAZARD" to "보행자·장애물","LATE" to "늦게 봤어요")
    private fun clock(offsetMs:Long)="${offsetMs/60000}분 ${offsetMs/1000%60}초"
    private fun review(){
        if(!canEdit())return
        storage.edit().putBoolean("reviewed",true).apply()
        val sessions=storage.getInt("sessions",0)
        if(sessions==0){sheet("드라이빙 정원 · 주행 회고","첫 주행을 마치면 회고와 다음 목표를 볼 수 있어요.");return}
        val g=garden();val trip=tripId(sessions)
        val justRewarded=g.goal==null&&g.reflections.lastOrNull()?.afterSessionId==trip
        val title=when{g.due(sessions)->"지난 목표는 어땠나요?";justRewarded->"🍃 첫 잎이 돋았어요";g.goal!=null->"🌱 씨앗을 심었어요";else->"드라이빙 정원 · 주행 회고"}
        val message=when{g.due(sessions)->"어떤 답이든 회고를 마치면 첫 잎이 돋아요.";justRewarded->"지난 목표를 돌아본 기록이 정원에 남았어요. 어려웠던 날도 솔직하게 돌아보면 잎이 자라요.";else->null}
        sheet(title,message,scroll=true){
            stats(sessions,g)
            moments()
            when{
                g.due(sessions)->responses(g,sessions,trip)
                g.goal!=null->{
                    goalBlock("저장한 목표",g.goal.text,"다음 주행을 마치면 이 목표를 돌아볼 수 있어요.")
                    addView(button("목표 바꾸기"){if(canEdit()){dialog?.dismiss();goalPicker(sessions,trip)}},LinearLayout.LayoutParams(-1,dp(76)).apply{topMargin=dp(16)})
                }
                else->addView(button("🌱 다음 목표 고르기",true){if(canEdit()){dialog?.dismiss();goalPicker(sessions,trip)}},LinearLayout.LayoutParams(-1,dp(76)).apply{topMargin=dp(24)})
            }
            val row=LinearLayout(this@MainActivity).apply{setPadding(0,dp(16),0,0)}
            row.addView(button("정원 기록"){if(canEdit()){dialog?.dismiss();history()}},LinearLayout.LayoutParams(0,dp(76),1f))
            row.addView(button("설정·기록 삭제"){if(canEdit()){dialog?.dismiss();settings()}},LinearLayout.LayoutParams(0,dp(76),1f).apply{leftMargin=dp(16)})
            addView(row)
        }
    }
    private fun LinearLayout.note(value:String)=addView(text(value,20f,muted).apply{setPadding(dp(4),dp(14),0,0);setLineSpacing(dp(4).toFloat(),1f)})
    private fun LinearLayout.stats(sessions:Int,g:Garden){
        val hasTrip=storage.contains("lastCount")
        val insufficient=storage.getBoolean("lastInsufficient",false)
        val stats=LinearLayout(this@MainActivity)
        fun stat(value:String,label:String)=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(22),dp(18),dp(22),dp(18));background=rounded(soft,20)
            addView(text(value,if(value.length>4)30f else 40f,ink,true));addView(text(label,20f,muted).apply{setPadding(0,dp(8),0,0)})}
        stats.addView(stat("${sessions}회","누적 주행"),LinearLayout.LayoutParams(0,-2,1f))
        stats.addView(stat(if(!hasTrip||insufficient)"확인 불가" else "${storage.getInt("lastCount",0)}건","최근 급감속 후보"),LinearLayout.LayoutParams(0,-2,1f).apply{leftMargin=dp(16)})
        stats.addView(stat("${g.leaves}장","모은 잎"),LinearLayout.LayoutParams(0,-2,1f).apply{leftMargin=dp(16)})
        addView(stats)
        if(!hasTrip)note("최근 주행 기록이 삭제됐어요.")
        if(insufficient)note("속도 기록이 부족해 0건으로 단정하지 않아요.")
        if(storage.getBoolean("lastInterrupted",false))note("기록을 중지해 이 주행은 중지한 시점까지만 저장됐어요.")
        TripQuality.gapLabel(storage.getLong("lastGapMs",0))?.let{note("⚠ 차량 신호 공백 ${it} · 이 구간은 회고에서 제외했어요.")}
    }
    /** Up to three candidate moments; the driver's own context is saved on tap so leaving mid-review keeps it. */
    private fun LinearLayout.moments(){
        if(storage.getBoolean("lastInsufficient",false))return
        val moments=Garden.Moment.listFromJson(storage.getString("lastMoments",null))
        if(moments.isEmpty())return
        addView(section("돌아볼 순간 · 그때 상황을 골라 주세요",24))
        val contexts=runCatching{org.json.JSONObject(storage.getString("lastContexts","{}")!!)}.getOrDefault(org.json.JSONObject())
        moments.forEachIndexed{i,m->
            val box=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(16),dp(20),dp(16));background=rounded(soft,20)}
            box.addView(text("출발 후 ${clock(m.offsetMs)} · 시속 ${m.fromKph} → ${m.toKph}km/h",22f,ink,true))
            val row=LinearLayout(this@MainActivity).apply{setPadding(0,dp(12),0,0)}
            val chips=contextOptions.map{(code,label)->text(label,18f,ink).apply{gravity=Gravity.CENTER;setPadding(dp(14),dp(10),dp(14),dp(10));isClickable=true;contentDescription="$label 선택"}}
            fun paint(){chips.forEachIndexed{j,c->val on=contexts.optString("$i")==contextOptions[j].first;c.background=rounded(if(on)blue else surface,16);c.setTextColor(if(on)Color.WHITE else ink)}}
            chips.forEachIndexed{j,c->
                c.setOnClickListener{if(canEdit()){contexts.put("$i",contextOptions[j].first);storage.edit().putString("lastContexts",contexts.toString()).apply();paint()}}
                row.addView(c,LinearLayout.LayoutParams(-2,-2).apply{if(j>0)leftMargin=dp(8)})
            }
            paint();box.addView(row)
            addView(box,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(12)})
        }
        note("급감속 후보는 상황을 알 수 없어 과실로 보지 않아요.")
    }
    private fun LinearLayout.goalBlock(label:String,goal:String,note:String){
        addView(section(label,24))
        addView(text(goal,26f,ink,true).apply{setPadding(dp(4),0,0,dp(10));setLineSpacing(dp(4).toFloat(),1f)})
        addView(text(note,20f,muted).apply{setPadding(dp(4),0,0,0);setLineSpacing(dp(4).toFloat(),1f)})
    }
    private fun LinearLayout.responses(g:Garden,sessions:Int,trip:String){
        val goal=g.goal!!
        goalBlock("지난 목표",goal.text,"필요한 감속을 했다고 해서 잎이 줄지 않아요.")
        val buttons=mutableListOf<TextView>()
        val row=LinearLayout(this@MainActivity).apply{setPadding(0,dp(24),0,0)}
        listOf("DIFFICULT" to "어려웠어요","GOOD" to "잘됐어요").forEachIndexed{i,(code,label)->
            val b=button(label,i==1){
                buttons.forEach{it.isEnabled=false}
                if(!canEdit()){buttons.forEach{it.isEnabled=true};return@button}
                val contexts=runCatching{org.json.JSONObject(storage.getString("lastContexts","{}")!!)}.getOrDefault(org.json.JSONObject())
                val next=garden().reflect(goal.id,code,trip,storage.getInt("sessions",0),contexts.keys().asSequence().map{contexts.getString(it)}.toList(),System.currentTimeMillis())
                if(next==null||saveGarden(next)){dialog?.dismiss();review()} else buttons.forEach{it.isEnabled=true}
            }
            buttons+=b
            row.addView(b,LinearLayout.LayoutParams(0,dp(76),1f).apply{if(i>0)leftMargin=dp(24)})
        }
        addView(row)
    }
    private fun goalPicker(sessions:Int,trip:String){
        if(!canEdit())return
        val current=garden().goal?.text
        var chosen=storage.getString("goalDraft",null)?:current?:Garden.PRESET_GOALS[0]
        val rows=mutableListOf<Pair<String,TextView>>()
        val field=input("직접 쓰기 (최대 ${Garden.MAX_GOAL_LENGTH}자)").apply{filters=arrayOf(android.text.InputFilter.LengthFilter(Garden.MAX_GOAL_LENGTH))}
        fun paint(){rows.forEach{(goal,v)->val on=goal==chosen;v.background=rounded(if(on)blue else soft,20);v.setTextColor(if(on)Color.WHITE else ink)}}
        val error=errorText()
        sheet(if(current==null)"다음 목표 고르기" else "목표 바꾸기","다음 주행에서 연습할 행동 하나를 골라요.",primary="🌱 목표 저장하고 씨앗 심기",cancel="취소",scroll=true,onPrimary={s->
            val text=chosen.trim()
            if(text.isBlank())showError(error,"목표를 고르거나 직접 써 주세요.")
            else if(canEdit()){
                s.primary?.isEnabled=false
                val g=garden()
                if(g.due(storage.getInt("sessions",0))){s.dialog.dismiss();review()}
                else if(saveGarden(g.withGoal(UUID.randomUUID().toString(),text,trip,storage.getInt("sessions",0))){remove("goalDraft")}){s.dialog.dismiss();review()}
                else s.primary?.isEnabled=true
            }
        }){
            Garden.PRESET_GOALS.forEach{goal->
                val v=text(goal,24f,ink,true).apply{setPadding(dp(24),dp(20),dp(24),dp(20));isClickable=true;setOnClickListener{field.setText("");chosen=goal;storage.edit().remove("goalDraft").apply();paint()}}
                rows+=goal to v;addView(v,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(10)})
            }
            if(chosen !in Garden.PRESET_GOALS)field.setText(chosen)
            field.addTextChangedListener(object:android.text.TextWatcher{
                override fun beforeTextChanged(s:CharSequence?,a:Int,b:Int,c:Int)=Unit
                override fun onTextChanged(s:CharSequence?,a:Int,b:Int,c:Int)=Unit
                override fun afterTextChanged(s:android.text.Editable?){
                    val typed=s?.toString().orEmpty()
                    if(typed.isNotBlank()){chosen=typed;storage.edit().putString("goalDraft",typed).apply();paint()}
                    else if(chosen !in Garden.PRESET_GOALS){chosen="";storage.edit().remove("goalDraft").apply();paint()}
                }
            })
            addView(field,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(6)});addView(error)
            paint()
        }
    }
    private fun history(){
        val g=garden()
        val responseLabel=mapOf("GOOD" to "잘됐어요","DIFFICULT" to "어려웠어요")
        val contextLabel=contextOptions.toMap()
        sheet("정원 기록","목표별로 돌아본 기록이에요. 잎은 운전 실력을 인증하지 않아요.",scroll=true){
            g.goal?.let{goalBlock("🌱 진행 중인 목표",it.text,"다음 주행 후 돌아볼 수 있어요.")}
            if(g.reflections.isEmpty()&&g.legacyLeaves==0)note("아직 돌아본 목표가 없어요.")
            if(g.reflections.isNotEmpty())addView(section("🍃 완료한 목표",24))
            g.reflections.asReversed().forEach{r->
                val box=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(16),dp(20),dp(16));background=rounded(soft,20)}
                box.addView(text(r.goalText,24f,ink,true))
                val contexts=r.contexts.mapNotNull{contextLabel[it]}.distinct()
                box.addView(text("${SimpleDateFormat("M월 d일",Locale.KOREAN).format(Date(r.completedAt))} · ${responseLabel[r.response]?:r.response} · 잎 1장"+(if(contexts.isEmpty())"" else " · ${contexts.joinToString()}"),20f,muted).apply{setPadding(0,dp(8),0,0)})
                addView(box,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(10)})
            }
            if(g.legacyLeaves>0)note("이전 버전에서 모은 잎 ${g.legacyLeaves}장 · 목표 이력은 남아 있지 않아요.")
        }
    }
    private fun settings(){
        if(!canEdit())return
        val paused=storage.getBoolean("recordingPaused",false)
        fun granted(p:String)=checkSelfPermission(p)==PackageManager.PERMISSION_GRANTED
        sheet("설정",scroll=true){
            addView(section("주행 기록"))
            note(if(paused)"기록이 중지돼 있어요. 속도·위치를 저장하지 않고, 앱을 다시 열어도 중지 상태가 유지돼요." else "주행하면 속도 변화와 경로를 이 기기에 기록해요.")
            addView(button(if(paused)"기록 다시 켜기" else "기록 중지",paused){
                if(!canEdit())return@button
                if(!storage.edit().putBoolean("recordingPaused",!paused).commit()){Toast.makeText(this@MainActivity,"저장하지 못했어요. 다시 눌러 주세요.",Toast.LENGTH_SHORT).show();return@button}
                startService(Intent(this@MainActivity,DriveRecorder::class.java).setAction(if(paused)"RESUME" else "PAUSE"))
                dialog?.dismiss();update();settings()
            },LinearLayout.LayoutParams(-1,dp(76)).apply{topMargin=dp(16)})
            addView(section("권한",28))
            note("차속 · ${if(granted("android.car.permission.CAR_SPEED"))"허용됨" else "허용 안 됨"} · 주행 기록에 꼭 필요해요")
            note("위치 · ${if(granted(android.Manifest.permission.ACCESS_FINE_LOCATION))"허용됨" else "허용 안 됨"} · 지도와 경로를 기기 안에서 그릴 때만 써요(선택)")
            if(Build.VERSION.SDK_INT>=33)note("알림 · ${if(granted(android.Manifest.permission.POST_NOTIFICATIONS))"허용됨" else "허용 안 됨"} · 기록 중인지, 중지됐는지 알려요(선택)")
            if(permissions.any{!granted(it)})addView(button("권한 다시 요청"){dialog?.dismiss();requestPermissions(permissions,1)},LinearLayout.LayoutParams(-1,dp(76)).apply{topMargin=dp(16)})
            addView(section("데이터",28))
            choice("기록 삭제","최근 주행 기록 또는 전체 데이터를 지워요"){if(canEdit()){dialog?.dismiss();deleteMenu()}}
            addView(section("수집 목적·지도 출처",16))
            note("주행 경로·급감속 후보·목표·회고는 이 기기에만 저장하고 앱 서버로 보내지 않아요.")
            note("지도 타일 © OpenStreetMap contributors · 장소 검색 Photon(검색어와 약 1km 단위 위치) · 경로 미리보기 OSRM(출발·도착 좌표)")
            note("버전 ${packageManager.getPackageInfo(packageName,0).versionName}")
        }
    }
    private fun deleteMenu(){
        sheet("기록 삭제","삭제한 기록과 연결된 잎은 다시 나타나지 않아요."){
            if(storage.contains("lastCount"))choice("최근 주행 기록 삭제","방금 주행의 경로·후보·맥락과 그 뒤에 남긴 회고를 지워요"){if(canEdit()){dialog?.dismiss();confirmDelete(false)}}
            choice("전체 데이터 삭제","주행 기록·목표·회고·정원·목적지를 모두 지워요"){if(canEdit()){dialog?.dismiss();confirmDelete(true)}}
        }
    }
    private fun confirmDelete(all:Boolean){
        sheet(if(all)"전체 데이터를 삭제할까요?" else "최근 주행 기록을 삭제할까요?","삭제하면 되돌릴 수 없어요.",primary="삭제",cancel="취소",onPrimary={s->
            if(!canEdit())return@sheet
            s.primary?.isEnabled=false
            if(all)cancelNavigation()
            val ok=if(all)DataReset.wipeAll(storage) else {
                val sessions=storage.getInt("sessions",0)
                saveGarden(garden().withoutSession(tripId(sessions),max(0,sessions-1))){putInt("sessions",max(0,sessions-1));putBoolean("reviewed",true);lastTripKeys.forEach{remove(it)}}
            }
            if(!ok){s.primary?.isEnabled=true;return@sheet}
            if(all){routeVersion++;pendingRoute=false;routeFailed=false;selected=null;roadRoute=null;map.setDestination(null);map.setRoute(emptyList());lastNavDestination="";routeMessage="목적지를 선택하면 도로 경로를 보여 드려요."}
            s.dialog.dismiss();Toast.makeText(this,if(all)"전체 데이터를 삭제했어요." else "최근 주행 기록을 삭제했어요.",Toast.LENGTH_SHORT).show();update()
        })
    }
    private val lastTripKeys=DataReset.LAST_TRIP
    private fun dp(value:Int)=(value*resources.displayMetrics.density).roundToInt()
    override fun onDestroy(){handler.removeCallbacksAndMessages(null);dialog?.dismiss();if(::navigation.isInitialized)navigation.close();super.onDestroy()}
}
