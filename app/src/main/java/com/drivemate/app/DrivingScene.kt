package com.drivemate.app

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.abs

/** Stylized lane illustration; does not claim lane or obstacle detection. */
class DrivingScene(context:Context): View(context) {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    var speed:Float?=null
    var angle:Float?=null
    override fun onDraw(c:Canvas) {
        val w=width.toFloat();val h=height.toFloat()
        p.shader=LinearGradient(0f,0f,0f,h,intArrayOf(Color.rgb(247,249,252),Color.rgb(216,229,235)),null,Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,w,h,p);p.shader=null
        val horizon=h*0.22f
        p.color=Color.rgb(192,206,213)
        c.drawPath(Path().apply{moveTo(w*.43f,horizon);lineTo(w*.57f,horizon);lineTo(w*1.12f,h);lineTo(-w*.12f,h);close()},p)
        p.color=Color.argb(55,40,105,255)
        c.drawPath(Path().apply{moveTo(w*.48f,horizon);lineTo(w*.52f,horizon);lineTo(w*.67f,h);lineTo(w*.33f,h);close()},p)
        p.style=Paint.Style.STROKE;p.strokeWidth=3f;p.color=Color.WHITE
        for(i in listOf(-1,1)) { c.drawLine(w*(.5f+i*.045f),horizon,w*(.5f+i*.36f),h,p) }
        p.pathEffect=DashPathEffect(floatArrayOf(22f,18f),0f);p.strokeWidth=2f
        c.drawLine(w*.5f,horizon,w*.5f,h*.6f,p);p.pathEffect=null;p.style=Paint.Style.FILL
        // Only the driver's own vehicle is illustrated.
        c.save();c.translate(w*.5f,h*.67f);c.rotate((angle?:0f).coerceIn(-12f,12f)*.25f)
        val cw=w*.20f;val ch=h*.25f
        p.color=Color.argb(35,16,34,50);c.drawRoundRect(-cw*.6f,-ch*.37f,cw*.6f,ch*.7f,14f,14f,p)
        p.color=Color.rgb(29,51,76);c.drawRoundRect(-cw/2,-ch/2,cw/2,ch/2,13f,13f,p)
        p.color=Color.rgb(74,115,152);c.drawRoundRect(-cw*.39f,-ch*.34f,cw*.39f,ch*.14f,8f,8f,p)
        p.color=Color.rgb(166,198,219);c.drawRoundRect(-cw*.31f,-ch*.27f,cw*.31f,-ch*.08f,5f,5f,p)
        p.color=Color.rgb(246,94,104);c.drawRect(-cw*.44f,ch*.27f,-cw*.23f,ch*.34f,p);c.drawRect(cw*.23f,ch*.27f,cw*.44f,ch*.34f,p)
        c.restore()
        p.color=Color.rgb(107,124,143);p.textSize=10f;p.textAlign=Paint.Align.CENTER
        c.drawText("주행 시각화 · 차선/장애물 감지 아님",w/2,h-14,p)
    }
}
