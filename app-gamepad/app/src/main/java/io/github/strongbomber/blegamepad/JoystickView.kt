package io.github.strongbomber.blegamepad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Dokunmatik joystick: merkezden sapma → 0..255 eksendeğerleri (127 = orta)
 * ve 8 yönlü hat byte'ı üretir.
 */
class JoystickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** @param x 0..255 (127=orta) @param y 0..255 (127=orta, yukarı=büyük) @param hat 0..8 */
    var onChange: ((x: Int, y: Int, hat: Int) -> Unit)? = null

    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.parseColor("#2A3140")
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#3A4356")
    }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#00E676")
    }

    private var radius = 0f
    private var knobX = 0f
    private var knobY = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        radius = (Math.min(w, h) / 2f) - 44f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (radius <= 0f) return
        val cx = width / 2f
        val cy = height / 2f
        canvas.drawCircle(cx, cy, radius, outerPaint)
        canvas.drawCircle(cx, cy, 12f, centerPaint)
        canvas.drawCircle(cx + knobX, cy + knobY, 34f, knobPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (radius <= 0f) return false
        val cx = width / 2f
        val cy = height / 2f
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                var dx = event.x - cx
                var dy = event.y - cy
                val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                if (dist > radius) {
                    dx *= radius / dist
                    dy *= radius / dist
                }
                knobX = dx
                knobY = dy
                val normX = (dx / radius * 127f).toInt().coerceIn(-127, 127)
                val normY = (dy / radius * 127f).toInt().coerceIn(-127, 127)
                // HID Y yukarı artar; dokunma dy aşağı artar → ters çevir
                val x = (127 + normX).coerceIn(0, 255)
                val y = (127 - normY).coerceIn(0, 255)
                val hat = if (dist < radius * 0.2f) 0 else hatFrom(normX, -normY)
                onChange?.invoke(x, y, hat)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                knobX = 0f
                knobY = 0f
                onChange?.invoke(127, 127, 0)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * 8 yönlü hat kodu (matematik koordinatlarında: ny yukarı doğru artar):
     * 1=yukarı 2=sağ-yukarı 3=sağ 4=sağ-aşağı 5=aşağı 6=sol-aşağı 7=sol 8=sol-yukarı.
     * Sektör sınırları köşegenlerde (±45°) — eksen yönleri tam 8 şer sektör tutar.
     */
    private fun hatFrom(nx: Int, ny: Int): Int {
        if (nx == 0 && ny == 0) return 0
        val ang = Math.toDegrees(Math.atan2(ny.toDouble(), nx.toDouble()))
        val r = ((Math.round(ang / 45.0).toInt() % 8) + 8) % 8
        // r: 0=sağ 1=sağ-yukarı 2=yukarı 3=sol-yukarı 4=sol 5=sol-aşağı 6=aşağı 7=sağ-aşağı
        val m = (3 - r) % 8
        return if (m <= 0) m + 8 else m
    }
}
