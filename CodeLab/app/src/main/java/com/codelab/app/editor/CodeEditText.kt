package com.codelab.app.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import com.codelab.app.R
import com.codelab.app.model.Language

/**
 * Ô soạn thảo code, có 3 tính năng thêm so với ô nhập thường:
 *  1. Hiện SỐ DÒNG ở lề trái.
 *  2. TÔ MÀU cú pháp theo ngôn ngữ.
 *  3. HOÀN TÁC / LÀM LẠI (undo/redo) khi sửa.
 */
class CodeEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatEditText(context, attrs) {

    // --- Phần vẽ số dòng ---
    private val lineNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.editor_line_number)
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.RIGHT
    }
    private val gutterBgPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.editor_bg)
    }
    private val separatorPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.divider)
    }
    private var gutterWidth = 0
    private var gutterTextX = 0f
    private val gutterPadH = dp(10)

    // --- Phần chừa chỗ cho bàn phím ---
    private val basePaddingBottom = dp(8)

    // --- Phần tô màu ---
    private var highlighter: SyntaxHighlighter? = null

    // --- Phần hoàn tác/làm lại ---
    private data class Edit(val start: Int, val before: CharSequence, val after: CharSequence)
    private val undoStack = ArrayDeque<Edit>()
    private val redoStack = ArrayDeque<Edit>()
    private var suppress = false          // true khi app tự sửa (không ghi vào lịch sử)
    private var pendingBefore: CharSequence = ""
    private var pendingEdit: Edit? = null

    /** Được gọi khi lịch sử thay đổi, để màn hình bật/tắt nút Undo/Redo. */
    var onHistoryChanged: (() -> Unit)? = null

    init {
        setBackgroundColor(ContextCompat.getColor(context, R.color.editor_bg))
        setTextColor(ContextCompat.getColor(context, R.color.editor_text))
        typeface = Typeface.MONOSPACE
        textSize = 15f
        // Số dòng nhỏ hơn chữ code một chút (textSize ở đây đã đổi ra điểm ảnh)
        lineNumberPaint.textSize = textSize * 0.85f
        setLineSpacing(0f, 1.1f)
        gravity = android.view.Gravity.TOP or android.view.Gravity.START
        // Nhiều dòng, không gợi ý từ, không tự sửa chính tả (quan trọng khi gõ code)
        inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
            InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE
        isHorizontalScrollBarEnabled = false
        setPadding(dp(48), dp(8), dp(12), basePaddingBottom)

        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                if (!suppress) {
                    pendingBefore = s.subSequence(start, start + count)
                }
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (!suppress) {
                    val added = s.subSequence(start, start + count)
                    pendingEdit = Edit(start, pendingBefore, added)
                }
            }

            override fun afterTextChanged(e: Editable) {
                if (!suppress) {
                    pendingEdit?.let {
                        undoStack.addLast(it)
                        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
                        redoStack.clear()
                        pendingEdit = null
                        onHistoryChanged?.invoke()
                    }
                }
                highlighter?.highlight(e)
                updateGutter()
            }
        })
    }

    /** Đặt ngôn ngữ để tô màu đúng kiểu. */
    fun setLanguage(language: Language) {
        highlighter = SyntaxHighlighter(language)
        text?.let { highlighter?.highlight(it) }
    }

    /** Nạp code ban đầu (không ghi vào lịch sử hoàn tác). */
    fun setCode(code: String) {
        suppress = true
        setText(code)
        suppress = false
        undoStack.clear()
        redoStack.clear()
        pendingEdit = null
        text?.let { highlighter?.highlight(it) }
        updateGutter()
        setSelection(text?.length ?: 0)
        onHistoryChanged?.invoke()
    }

    /**
     * Khi bàn phím đè lên phần dưới của ô code, chừa thêm khoảng trống phía dưới đúng bằng
     * phần bị che, để dòng đang gõ luôn nằm trên bàn phím (ô code không đổi kích thước).
     */
    fun setKeyboardOverlap(overlapPx: Int) {
        val bottom = basePaddingBottom + overlapPx.coerceAtLeast(0)
        if (bottom != paddingBottom) {
            setPadding(paddingLeft, paddingTop, paddingRight, bottom)
        }
        if (overlapPx > 0 && hasFocus()) {
            post { bringPointIntoView(selectionStart.coerceAtLeast(0)) }
        }
    }

    fun canUndo() = undoStack.isNotEmpty()
    fun canRedo() = redoStack.isNotEmpty()

    fun undo() {
        if (undoStack.isEmpty()) return
        val edit = undoStack.removeLast()
        suppress = true
        val end = edit.start + edit.after.length
        editableText.replace(edit.start, end, edit.before)
        setSelection((edit.start + edit.before.length).coerceIn(0, length()))
        suppress = false
        redoStack.addLast(edit)
        text?.let { highlighter?.highlight(it) }
        updateGutter()
        onHistoryChanged?.invoke()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val edit = redoStack.removeLast()
        suppress = true
        val end = edit.start + edit.before.length
        editableText.replace(edit.start, end, edit.after)
        setSelection((edit.start + edit.after.length).coerceIn(0, length()))
        suppress = false
        undoStack.addLast(edit)
        text?.let { highlighter?.highlight(it) }
        updateGutter()
        onHistoryChanged?.invoke()
    }

    /** Tính lại độ rộng lề số dòng theo số dòng hiện có. */
    private fun updateGutter() {
        val lines = (text?.count { it == '\n' } ?: 0) + 1
        val digits = maxOf(2, lines.toString().length)
        val newWidth = (lineNumberPaint.measureText("0") * digits).toInt() + gutterPadH * 2
        if (newWidth != gutterWidth) {
            gutterWidth = newWidth
            gutterTextX = (gutterWidth - gutterPadH).toFloat()
            setPadding(gutterWidth + dp(6), paddingTop, paddingRight, paddingBottom)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val lay = layout
        if (lay != null && gutterWidth > 0) {
            val sx = scrollX.toFloat()
            val sy = scrollY.toFloat()
            // Nền lề số dòng + đường kẻ phân cách, luôn nằm cố định bên trái dù cuộn
            canvas.drawRect(sx, sy, sx + gutterWidth, sy + height, gutterBgPaint)
            canvas.drawRect(sx + gutterWidth - 1f, sy, sx + gutterWidth, sy + height, separatorPaint)
            val txt = text
            var logical = 0
            for (v in 0 until lay.lineCount) {
                val start = lay.getLineStart(v)
                val isLogicalStart = start == 0 ||
                    (txt != null && start in 1..txt.length && txt[start - 1] == '\n')
                if (isLogicalStart) {
                    logical++
                    val baseline = (lay.getLineBaseline(v) + paddingTop).toFloat()
                    canvas.drawText(logical.toString(), sx + gutterTextX, baseline, lineNumberPaint)
                }
            }
        }
        super.onDraw(canvas)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val MAX_HISTORY = 200
    }
}
