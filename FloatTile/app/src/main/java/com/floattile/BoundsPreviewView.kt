package com.floattile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.min

/**
 * Hình xem trước: vẽ một "màn hình điện thoại" thu nhỏ và khung cửa sổ nổi bên trong,
 * để bạn thấy ngay kích thước/vị trí khi kéo các thanh trượt.
 */
class BoundsPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var screenW = 1080
    private var screenH = 2400
    private var windowRect = Rect(108, 480, 972, 1920)
    private var label = "Claude"

    private val density = resources.displayMetrics.density

    private val screenFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.preview_screen)
    }
    private val screenStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
        color = ContextCompat.getColor(context, R.color.preview_frame)
    }
    private val windowFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.preview_window)
    }
    private val windowStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
        color = ContextCompat.getColor(context, R.color.brand)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.preview_text)
        textSize = 13 * density
        textAlign = Paint.Align.CENTER
    }

    fun update(screen: WindowBounds.Screen, window: Rect, appLabel: String) {
        screenW = screen.width.coerceAtLeast(1)
        screenH = screen.height.coerceAtLeast(1)
        windowRect = Rect(window)
        label = appLabel
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = 8 * density
        val availW = width - 2 * pad
        val availH = height - 2 * pad
        if (availW <= 0 || availH <= 0) return

        // Thu nhỏ màn hình thật cho vừa khung xem trước, giữ đúng tỉ lệ.
        val scale = min(availW / screenW, availH / screenH)
        val fw = screenW * scale
        val fh = screenH * scale
        val left = (width - fw) / 2f
        val top = (height - fh) / 2f
        val frame = RectF(left, top, left + fw, top + fh)
        val corner = 10 * density
        canvas.drawRoundRect(frame, corner, corner, screenFill)
        canvas.drawRoundRect(frame, corner, corner, screenStroke)

        val wr = RectF(
            left + windowRect.left * scale,
            top + windowRect.top * scale,
            left + windowRect.right * scale,
            top + windowRect.bottom * scale
        )
        val wCorner = 6 * density
        canvas.drawRoundRect(wr, wCorner, wCorner, windowFill)
        canvas.drawRoundRect(wr, wCorner, wCorner, windowStroke)
        val textY = wr.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, wr.centerX(), textY, textPaint)
    }
}
