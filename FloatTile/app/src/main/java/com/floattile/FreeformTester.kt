package com.floattile

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Kiểm tra thực tế: mở cửa sổ thử nghiệm (ProbeActivity) bằng từng cách,
 * rồi để chính cửa sổ đó tự đo xem có đang ở chế độ tự do không.
 * Cách này đáng tin hơn đoán qua tùy chọn hệ thống, vì mỗi hãng (Xiaomi, Samsung...) xử lý khác nhau.
 */
object FreeformTester {

    data class Outcome(val state: ProbeState, val note: String)

    /** Thử cách (a): reflection. */
    suspend fun probeReflection(activity: Activity, bounds: Rect, timeoutMs: Long = 4000): Outcome {
        val (token, deferred) = ProbeBus.register()
        val intent = Intent(activity, ProbeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            .putExtra(ProbeActivity.EXTRA_TOKEN, token)
        try {
            val options = FreeformLauncher.makeFreeformOptions(bounds)
            activity.startActivity(intent, options.toBundle())
        } catch (e: FreeformUnavailable) {
            ProbeBus.cancel(token)
            return Outcome(ProbeState.FAIL, e.message ?: "Không gọi được hàm ẩn")
        } catch (e: Exception) {
            ProbeBus.cancel(token)
            return Outcome(ProbeState.FAIL, "Hệ thống từ chối mở cửa sổ: $e")
        }
        val result = withTimeoutOrNull(timeoutMs) { deferred.await() }
        if (result == null) {
            ProbeBus.cancel(token)
            return Outcome(ProbeState.FAIL, "Cửa sổ thử nghiệm không phản hồi (có thể bị hệ thống chặn mở).")
        }
        return if (result.isFreeform) {
            Outcome(ProbeState.OK, "Cửa sổ thử mở dạng tự do (${result.describe()}).")
        } else {
            Outcome(
                ProbeState.FAIL,
                "Hàm ẩn gọi được nhưng hệ thống bỏ qua yêu cầu và mở toàn màn hình (${result.describe()})."
            )
        }
    }

    /** Thử cách (b): am start --windowingMode 5 qua Shizuku. */
    suspend fun probeShizuku(context: Context, timeoutMs: Long = 6000): Outcome {
        if (!ShizukuHelper.awaitBinder()) {
            return Outcome(ProbeState.UNTESTED, "Shizuku chưa chạy nên chưa thử được.")
        }
        val status = ShizukuHelper.status(context)
        if (status != ShizukuHelper.Status.READY) {
            return Outcome(ProbeState.UNTESTED, ShizukuHelper.describe(status))
        }
        val (token, deferred) = ProbeBus.register()
        val component = ComponentName(context, ProbeActivity::class.java).flattenToShortString()
        val cmd = FreeformLauncher.amStartCommand(component, " --es ${ProbeActivity.EXTRA_TOKEN} $token")
        val res = ShizukuHelper.exec(cmd)
        if (!res.ok) {
            ProbeBus.cancel(token)
            return Outcome(
                ProbeState.FAIL,
                "Lệnh am start báo lỗi: ${res.output.trim().take(300)}${FreeformEnv.permissionHint(res.output)}"
            )
        }
        val result = withTimeoutOrNull(timeoutMs) { deferred.await() }
        if (result == null) {
            ProbeBus.cancel(token)
            return Outcome(ProbeState.FAIL, "Cửa sổ thử nghiệm không phản hồi sau lệnh am start.")
        }
        return if (result.isFreeform) {
            Outcome(ProbeState.OK, "Cửa sổ thử mở dạng tự do (${result.describe()}).")
        } else {
            Outcome(ProbeState.FAIL, "Hệ thống bỏ qua --windowingMode 5 và mở toàn màn hình (${result.describe()}).")
        }
    }

    /**
     * Kiểm tra nhanh dùng khi bấm ô lần đầu: thử (a) trước; chỉ khi (a) hỏng mới thử (b).
     * Kết quả được lưu lại để lần bấm sau không phải thử nữa.
     */
    suspend fun detectAndSave(activity: Activity, bounds: Rect) {
        val a = probeReflection(activity, bounds)
        val b = if (a.state == ProbeState.OK) {
            Outcome(ProbeState.UNTESTED, "Không cần thử vì cách (a) đã chạy được.")
        } else {
            probeShizuku(activity)
        }
        Prefs.saveProbe(activity, a.state, a.note, b.state, b.note)
    }
}
