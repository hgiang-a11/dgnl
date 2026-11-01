package com.floattile

import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CompletableDeferred
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Kết quả đo của cửa sổ thử nghiệm. */
data class ProbeResult(
    val windowingMode: String?,   // ví dụ "freeform", "fullscreen"; null nếu không đọc được
    val inMultiWindow: Boolean,   // hệ thống báo đang ở chế độ đa cửa sổ
    val width: Int, val height: Int,
    val maxWidth: Int, val maxHeight: Int
) {
    /** Kết luận: có thật sự đang là cửa sổ tự do không. */
    val isFreeform: Boolean
        get() = windowingMode == "freeform" ||
            (windowingMode == null && inMultiWindow && (width < maxWidth || height < maxHeight))

    fun describe(): String =
        "chế độ=${windowingMode ?: "?"}, đa cửa sổ=${if (inMultiWindow) "có" else "không"}, " +
            "kích thước ${width}×${height} / màn hình ${maxWidth}×${maxHeight}"
}

/**
 * "Hộp thư" trong bộ nhớ: bên kiểm tra đăng ký một mã (token), cửa sổ thử nghiệm
 * đo xong thì gửi kết quả về đúng mã đó.
 */
object ProbeBus {
    private val waiting = ConcurrentHashMap<String, CompletableDeferred<ProbeResult>>()

    fun register(): Pair<String, CompletableDeferred<ProbeResult>> {
        val token = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<ProbeResult>()
        waiting[token] = deferred
        return token to deferred
    }

    fun report(token: String?, result: ProbeResult) {
        if (token != null) waiting.remove(token)?.complete(result)
    }

    fun cancel(token: String) {
        waiting.remove(token)
    }
}

/**
 * Cửa sổ thử nghiệm: được mở bằng đúng cách sẽ dùng để mở ứng dụng đích,
 * sau đó tự đo xem mình có đang nằm trong cửa sổ tự do không, báo kết quả rồi tự đóng.
 */
class ProbeActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TOKEN = "com.floattile.PROBE_TOKEN"
    }

    private var measured = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_probe)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Đợi cửa sổ thật sự hiện lên và có tiêu điểm (focus) rồi mới đo.
        if (hasFocus && !measured) {
            measured = true
            window.decorView.postDelayed({ measureAndFinish() }, 250)
        }
    }

    private fun measureAndFinish() {
        val result = measure()
        ProbeBus.report(intent.getStringExtra(EXTRA_TOKEN), result)
        findViewById<TextView>(R.id.probe_text).text =
            if (result.isFreeform) getString(R.string.probe_ok) else getString(R.string.probe_fail)
        window.decorView.postDelayed({
            finishAndRemoveTask()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }, 500)
    }

    private fun measure(): ProbeResult {
        // Cách đọc chế độ cửa sổ bằng API công khai: chuỗi mô tả cấu hình (Configuration.toString())
        // có chứa đoạn "mWindowingMode=freeform" / "mWindowingMode=fullscreen".
        val cfg = resources.configuration.toString()
        val mode = Regex("mWindowingMode=([A-Za-z-]+)").find(cfg)?.groupValues?.get(1)

        val w: Int
        val h: Int
        val mw: Int
        val mh: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val cur = windowManager.currentWindowMetrics.bounds
            val max = windowManager.maximumWindowMetrics.bounds
            w = cur.width(); h = cur.height(); mw = max.width(); mh = max.height()
        } else {
            val screen = WindowBounds.screen(this)
            w = window.decorView.width; h = window.decorView.height
            mw = screen.width; mh = screen.height
        }
        return ProbeResult(mode, isInMultiWindowMode, w, h, mw, mh)
    }
}
