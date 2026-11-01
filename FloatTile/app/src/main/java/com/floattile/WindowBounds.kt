package com.floattile

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlin.math.roundToInt

/**
 * Tính khung cửa sổ (Rect = hình chữ nhật gồm 4 cạnh trái/trên/phải/dưới, đơn vị pixel).
 */
object WindowBounds {

    data class Screen(val width: Int, val height: Int)

    /** Lấy kích thước toàn màn hình theo hướng xoay hiện tại. */
    fun screen(context: Context): Screen {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: maximumWindowMetrics = vùng lớn nhất một cửa sổ có thể chiếm.
            val b = wm.maximumWindowMetrics.bounds
            Screen(b.width(), b.height())
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            Screen(dm.widthPixels, dm.heightPixels)
        }
    }

    /**
     * Bước 1: chiều rộng/cao = % của màn hình.
     * Bước 2: đặt tâm cửa sổ tại vị trí % đã chọn (50% = giữa).
     * Bước 3: đẩy cửa sổ vào trong nếu bị tràn ra ngoài mép màn hình.
     */
    fun compute(screen: Screen, widthPct: Int, heightPct: Int, posXPct: Int, posYPct: Int): Rect {
        val w = (screen.width * widthPct / 100f).roundToInt().coerceIn(1, screen.width)
        val h = (screen.height * heightPct / 100f).roundToInt().coerceIn(1, screen.height)
        val centerX = screen.width * posXPct / 100f
        val centerY = screen.height * posYPct / 100f
        val left = (centerX - w / 2f).roundToInt().coerceIn(0, screen.width - w)
        val top = (centerY - h / 2f).roundToInt().coerceIn(0, screen.height - h)
        return Rect(left, top, left + w, top + h)
    }

    /** Khung người dùng muốn THẤY trên màn hình (theo cài đặt của ô). */
    fun visualRect(screen: Screen, cfg: TileConfig): Rect =
        compute(screen, cfg.widthPct, cfg.heightPct, cfg.posXPct, cfg.posYPct)

    /**
     * Khung gửi cho hệ thống.
     * HyperOS thu cửa sổ nổi còn [scalePct]% và giữ nguyên góc trên-trái, nên:
     *  - góc trên-trái giữ nguyên như khung mong muốn;
     *  - chiều rộng/cao chia cho hệ số thu nhỏ (ví dụ 576 / 0,7 ≈ 823).
     * Sau khi HyperOS thu lại, cửa sổ hiện ra đúng bằng khung mong muốn.
     * Khung gửi đi có thể vượt ra ngoài mép màn hình, đó là chủ ý.
     */
    fun systemRect(visual: Rect, scalePct: Int): Rect {
        if (scalePct >= 100 || scalePct <= 0) return Rect(visual)
        val s = scalePct / 100f
        return Rect(
            visual.left,
            visual.top,
            visual.left + (visual.width() / s).roundToInt(),
            visual.top + (visual.height() / s).roundToInt()
        )
    }

    /** Khung gửi cho hệ thống của một ô (đã bù tỉ lệ HyperOS). */
    fun forTile(context: Context, cfg: TileConfig): Rect =
        systemRect(visualRect(screen(context), cfg), Prefs.hyperScalePct)

    fun describe(r: Rect): String =
        "${r.width()}×${r.height()} px, góc trên-trái (${r.left}, ${r.top})"
}
