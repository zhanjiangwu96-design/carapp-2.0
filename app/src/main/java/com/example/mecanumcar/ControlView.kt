package com.example.mecanumcar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/**
 * 左: 8 个方向键 (3x3 九宫格, 中间是"停")      右: A 左转 / B 右转 / C 加速 / D 减速
 * 支持多点触控: 同时按住两个方向键可合成斜向 (例如 ↑ + → = 右前), 相反方向互相抵消。
 * 对外仍然提供 joyX / joyY (-100, 0, 100) 和 pressed[A,B,C,D], 发送协议不变: DD 77 X Y A B C D
 */
class ControlView(ctx: Context) : View(ctx) {

    var joyX = 0
        private set
    var joyY = 0
        private set
    val pressed = BooleanArray(4)           // A B C D

    private val dirOn = BooleanArray(8)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // ---------- 方向键: 九宫格, 中间留空 ----------
    private val dirLabel = arrayOf("↖", "↑", "↗", "←", "→", "↙", "↓", "↘")
    private val dirDx = intArrayOf(-1, 0, 1, -1, 1, -1, 0, 1)      // X: 右为正
    private val dirDy = intArrayOf(1, 1, 1, 0, 0, -1, -1, -1)      // Y: 上为正
    private val gridCol = intArrayOf(-1, 0, 1, -1, 1, -1, 0, 1)
    private val gridRow = intArrayOf(-1, -1, -1, 0, 0, 1, 1, 1)    // 屏幕向下为正

    private fun cellSize() = height * 0.19f
    private fun dirR() = height * 0.08f
    private fun gridCx() = width * 0.24f
    private fun gridCy() = height * 0.64f
    private fun dirX(i: Int) = gridCx() + gridCol[i] * cellSize()
    private fun dirY(i: Int) = gridCy() + gridRow[i] * cellSize()

    // ---------- A B C D : 左转放在左边, 右转放在右边 ----------
    private val labels = arrayOf("A", "B", "C", "D")
    private val hints = arrayOf("左转", "右转", "加速", "减速")
    private val abx = floatArrayOf(0.77f, 0.90f, 0.80f, 0.91f)
    private val aby = floatArrayOf(0.82f, 0.72f, 0.56f, 0.38f)
    private val abr = floatArrayOf(0.095f, 0.095f, 0.085f, 0.085f)  // 相对高度

    private fun hitDir(i: Int, x: Float, y: Float) =
        hypot(x - dirX(i), y - dirY(i)) <= dirR() * 1.15f

    private fun hitBtn(b: Int, x: Float, y: Float) =
        hypot(x - abx[b] * width, y - aby[b] * height) <= abr[b] * height * 1.15f

    private fun resetAll() {
        joyX = 0
        joyY = 0
        for (i in 0..7) dirOn[i] = false
        for (i in 0..3) pressed[i] = false
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val act = e.actionMasked
        if (act == MotionEvent.ACTION_CANCEL || act == MotionEvent.ACTION_UP) {
            resetAll()
            invalidate()
            return true
        }
        val upIdx = if (act == MotionEvent.ACTION_POINTER_UP) e.actionIndex else -1
        val nd = BooleanArray(8)
        val nb = BooleanArray(4)

        for (i in 0 until e.pointerCount) {
            if (i == upIdx) continue
            val x = e.getX(i)
            val y = e.getY(i)
            for (d in 0..7) if (hitDir(d, x, y)) nd[d] = true
            for (b in 0..3) if (hitBtn(b, x, y)) nb[b] = true
        }

        var sx = 0
        var sy = 0
        for (d in 0..7) {
            dirOn[d] = nd[d]
            if (nd[d]) {
                sx += dirDx[d]
                sy += dirDy[d]
            }
        }
        joyX = sx.coerceIn(-1, 1) * 100
        joyY = sy.coerceIn(-1, 1) * 100
        for (b in 0..3) pressed[b] = nb[b]
        invalidate()
        return true
    }

    private fun drawCenteredText(c: Canvas, s: String, cx: Float, cy: Float) {
        c.drawText(s, cx, cy - (paint.ascent() + paint.descent()) / 2f, paint)
    }

    private fun drawButton(c: Canvas, cx: Float, cy: Float, r: Float, on: Boolean) {
        paint.style = Paint.Style.FILL
        paint.color = if (on) 0xFF4FC3F7.toInt() else 0x44FFFFFF
        c.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = 0x99FFFFFF.toInt()
        c.drawCircle(cx, cy, r, paint)
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(0xFF0B1B2E.toInt())
        paint.textAlign = Paint.Align.CENTER

        // 中间的"停"(仅显示, 不可按)
        val sr = dirR()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = 0x44FFFFFF
        c.drawCircle(gridCx(), gridCy(), sr, paint)
        paint.style = Paint.Style.FILL
        paint.color = 0x88FFFFFF.toInt()
        paint.textSize = sr * 0.7f
        drawCenteredText(c, "停", gridCx(), gridCy())

        // 8 个方向键
        for (i in 0..7) {
            val x = dirX(i)
            val y = dirY(i)
            drawButton(c, x, y, sr, dirOn[i])
            paint.style = Paint.Style.FILL
            paint.color = 0xFFFFFFFF.toInt()
            paint.textSize = sr * 1.05f
            drawCenteredText(c, dirLabel[i], x, y)
        }

        // A B C D
        for (b in 0..3) {
            val bx = abx[b] * width
            val by = aby[b] * height
            val rad = abr[b] * height
            drawButton(c, bx, by, rad, pressed[b])

            paint.style = Paint.Style.FILL
            paint.color = 0xFFFFFFFF.toInt()
            paint.textSize = rad * 0.8f
            drawCenteredText(c, labels[b], bx, by - rad * 0.12f)
            paint.textSize = rad * 0.30f
            paint.color = 0xCCFFFFFF.toInt()
            drawCenteredText(c, hints[b], bx, by + rad * 0.55f)
        }

        // 调试信息
        paint.color = 0xFFB0BEC5.toInt()
        paint.textSize = height * 0.045f
        drawCenteredText(c, "X=$joyX  Y=$joyY", width * 0.5f, height * 0.94f)
    }
}
