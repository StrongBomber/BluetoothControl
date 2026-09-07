package io.github.strongbomber.blegamepad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

/**
 * DualSense tarzı dokunmatik joystick.
 *
 * - Merkezden sapma → 0..255 eksendeğerleri (127 = orta, %18 ölü bölge)
 * - 8 yönlü hat (0 = orta)
 * - Stick click: merkez yakınında basılı tutulursa `pressed=true` (L3/R3);
 *   eşiğin ötesine sürüklenirse click bırakılır ve analog sürüşe geçer
 */
class JoystickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /**
     * @param x 0..255 (127=orta)
     * @param y 0..255 (127=orta, yukarı=büyük)
     * @param hat 0..8 (0=orta, 1=üst, 2=sağ-üst, 3=sağ, 4=sağ-alt,
     *            5=alt, 6=sol-alt, 7=sol, 8=sol-üst)
     * @param pressed stick click (L3/R3)
     */
    var onChange: ((x: Int, y: Int, hat: Int, pressed: Boolean) -> Unit)? = null

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.parseColor("#2A2A36")
    }
    private val ringActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.parseColor("#00A3FF")
    }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knobPressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#34343F")
    }

    private var radius = 0f
    private var knobX = 0f
    private var knobY = 0f
    private var touching = false
    private var knobPressed = false

    private val clickThreshold get() = radius * 0.35f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        radius = (Math.min(w, h) / 2f) - 40f
        val knobR = (radius * 0.42f).coerceAtLeast(1f)
        knobPaint.shader = RadialGradient(
            0f, 0f, knobR,
            Color.parseColor("#40404E"), Color.parseColor("#232330"),
            Shader.TileMode.CLAMP
        )
        knobPressPaint.shader = RadialGradient(
            0f, 0f, knobR,
            Color.parseColor("#3E5468"), Color.parseColor("#16222E"),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (radius <= 0f) return
        val cx = width / 2f
        val cy = height / 2f
        canvas.drawCircle(cx, cy, radius, if (touching) ringActivePaint else ringPaint)
        canvas.drawCircle(cx, cy, 14f, centerPaint)
        val p = if (knobPressed) knobPressPaint else knobPaint
        canvas.drawCircle(cx + knobX, cy + knobY, radius * 0.42f, p)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (radius <= 0f) return false
        val cx = width / 2f
        val cy = height / 2f
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touching = true
                val (dx, dy, dist) = clampedOffset(event.x - cx, event.y - cy)
                knobX = dx
                knobY = dy
                if (dist < clickThreshold) {
                    // Stick click (L3/R3): knob merkezde kalır
                    knobPressed = true
                    knobX = 0f
                    knobY = 0f
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    emit(0.0, false)
                } else {
                    emit(dist / radius.toDouble(), false)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!touching) return false
                val (dx, dy, dist) = clampedOffset(event.x - cx, event.y - cy)
                if (knobPressed && dist > clickThreshold) {
                    knobPressed = false
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                knobX = dx
                knobY = dy
                emit(dist / radius.toDouble(), true)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (knobPressed) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                touching = false
                knobPressed = false
                knobX = 0f
                knobY = 0f
                emit(0.0, false)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /** Hedefi joy offset'ine (dx, dy) ve mutlak mesafeye (dist) çevirir. */
    private fun clampedOffset(px: Float, py: Float): Triple<Float, Float, Float> {
        var dx = px
        var dy = py
        var dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (dist > radius) {
            dx *= radius / dist
            dy *= radius / dist
            dist = radius
        }
        return Triple(dx, dy, dist)
    }

    /**
     * @param distRatio 0..1 (0: merkezde, 1: kenarda)
     * @param useHat hat değeri hesaplansın mı
     */
    private fun emit(distRatio: Double, useHat: Boolean) {
        val cb = onChange ?: return
        val dead = 0.18
        val normX = (knobX / radius).toDouble().coerceIn(-1.0, 1.0)
        val normY = (knobY / radius).toDouble().coerceIn(-1.0, 1.0)
        val lx = if (Math.abs(normX) < dead) 0.0
                 else (normX - Math.signum(normX) * dead) / (1.0 - dead)
        val ly = if (Math.abs(normY) < dead) 0.0
                 else (normY - Math.signum(normY) * dead) / (1.0 - dead)
        val x = (127 + lx * 127.0).toInt().coerceIn(0, 255)
        // HID Y yukarı artar; ekran dy aşağı artar → ters çevir
        val y = (127 - ly * 127.0).toInt().coerceIn(0, 255)
        val hat = if (useHat && distRatio > dead) hatFrom(lx.toFloat(), -ly.toFloat()) else 0
        cb(x, y, hat, knobPressed)
    }

    /**
     * 8 yönlü hat kodu (matematik koordinatlarında: ny yukarı doğru artar):
     * 1=üst 2=sağ-üst 3=sağ 4=sağ-alt 5=alt 6=sol-alt 7=sol 8=sol-üst.
     * Sektör sınırları köşegenlerde (±45°).
     */
    private fun hatFrom(nx: Float, ny: Float): Int {
        if (nx == 0f && ny == 0f) return 0
        val ang = Math.toDegrees(Math.atan2(ny.toDouble(), nx.toDouble()))
        val r = ((Math.round(ang / 45.0).toInt() % 8) + 8) % 8
        // r: 0=sağ 1=sağ-üst 2=üst 3=sol-üst 4=sol 5=sol-alt 6=alt 7=sağ-alt
        val m = (3 - r) % 8
        return if (m <= 0) m + 8 else m
    }
}
