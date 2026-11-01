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

    /** Kết quả tính: [visual] = cửa sổ ước tính sẽ hiện ra, [system] = khung gửi cho hệ thống. */
    data class Plan(val visual: Rect, val system: Rect)

    /**
     * Tính khung gửi cho hệ thống khi HyperOS thu cửa sổ nổi còn [scalePct]% và neo góc trên-trái.
     *  Bước 1: kích thước gửi đi = kích thước muốn thấy ÷ hệ số thu, nhưng KHÔNG vượt màn hình
     *          (khung vượt màn hình làm nội dung ứng dụng bị đẩy ra ngoài cửa sổ).
     *  Bước 2: kích thước sẽ thấy = kích thước gửi đi × hệ số thu.
     *  Bước 3: đặt góc trên-trái sao cho tâm cửa sổ đúng chỗ mong muốn,
     *          rồi kéo vào trong nếu khung gửi đi bị tràn khỏi màn hình.
     */
    fun plan(screen: Screen, cfg: TileConfig, scalePct: Int): Plan {
        val desired = visualRect(screen, cfg)
        val s = if (scalePct in 1..99) scalePct / 100f else 1f
        val sysW = (desired.width() / s).roundToInt().coerceIn(1, screen.width)
        val sysH = (desired.height() / s).roundToInt().coerceIn(1, screen.height)
        val visW = (sysW * s).roundToInt()
        val visH = (sysH * s).roundToInt()
        val left = (desired.centerX() - visW / 2).coerceIn(0, screen.width - sysW)
        val top = (desired.centerY() - visH / 2).coerceIn(0, screen.height - sysH)
        return Plan(
            visual = Rect(left, top, left + visW, top + visH),
            system = Rect(left, top, left + sysW, top + sysH)
        )
    }

    /** Khung gửi cho hệ thống của một ô (đã bù tỉ lệ HyperOS, luôn nằm trong màn hình). */
    fun forTile(context: Context, cfg: TileConfig): Rect =
        plan(screen(context), cfg, Prefs.hyperScalePct).system

    fun describe(r: Rect): String =
        "${r.width()}×${r.height()} px, góc trên-trái (${r.left}, ${r.top})"
}
