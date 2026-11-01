package com.floattile

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.graphics.Rect
import android.os.Build
import kotlinx.coroutines.delay

/** Kết quả một lần bấm ô. */
sealed class LaunchOutcome {
    /** Đã mở. [note] (nếu có) là lời nhắn cần hiện cho người dùng. */
    data class Success(val method: String, val note: String? = null) : LaunchOutcome()

    /** Không mở được theo yêu cầu. */
    data class Failure(val title: String, val message: String) : LaunchOutcome()
}

/** Lỗi: không ép được chế độ cửa sổ tự do bằng reflection. */
class FreeformUnavailable(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Kết quả mở bằng Shizuku. */
data class ShizukuLaunchResult(
    val started: Boolean,      // lệnh am start chạy thành công
    val freeform: Boolean?,    // true/false = đã xác minh; null = không xác minh được
    val resized: Boolean,      // đã đặt được kích thước/vị trí
    val detail: String
)

object FreeformLauncher {

    /** Android 8.x dùng "stack" thay cho "windowing mode"; mã stack của cửa sổ tự do là 2. */
    private const val FREEFORM_WORKSPACE_STACK_ID = 2

    // =================================================================================
    // CÁCH (a): ActivityOptions + reflection
    // =================================================================================

    /**
     * Tạo ActivityOptions (= gói tùy chọn đi kèm lệnh mở màn hình) gồm:
     *  1. setLaunchBounds(rect): API CÔNG KHAI — yêu cầu khung cửa sổ (chỉ có tác dụng khi ở chế độ tự do).
     *  2. setLaunchWindowingMode(5): API ẨN — yêu cầu chế độ cửa sổ tự do.
     *     "Reflection" = gọi một hàm theo tên lúc chạy, dùng được cả với hàm không công khai.
     *     Từ Android 9, Google hạn chế ứng dụng gọi API ẩn (chính sách "non-SDK interfaces"):
     *     nếu hàm nằm trong danh sách cấm, hệ thống giả vờ như hàm không tồn tại → ta báo lỗi rõ ràng.
     */
    fun makeFreeformOptions(bounds: Rect): ActivityOptions {
        val options = ActivityOptions.makeBasic()
        options.setLaunchBounds(bounds)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val setter = ActivityOptions::class.java
                    .getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                setter.invoke(options, FreeformEnv.WINDOWING_MODE_FREEFORM)
            } else {
                val setter = ActivityOptions::class.java
                    .getMethod("setLaunchStackId", Int::class.javaPrimitiveType)
                setter.invoke(options, FREEFORM_WORKSPACE_STACK_ID)
            }
        } catch (e: NoSuchMethodException) {
            throw FreeformUnavailable(
                "Android không cho gọi hàm ẩn setLaunchWindowingMode " +
                    "(bị chính sách hạn chế API ẩn chặn, hoặc hãng đã gỡ hàm này).", e
            )
        } catch (e: ReflectiveOperationException) {
            throw FreeformUnavailable("Gọi hàm ẩn setLaunchWindowingMode thất bại: ${e.cause ?: e}", e)
        } catch (e: SecurityException) {
            throw FreeformUnavailable("Hệ thống từ chối quyền gọi hàm ẩn: ${e.message}", e)
        }
        return options
    }

    /** Chỉ kiểm tra xem hàm ẩn có gọi được không. Trả về null nếu được, hoặc lý do nếu không. */
    fun checkReflection(): String? = try {
        makeFreeformOptions(Rect(0, 0, 100, 100))
        null
    } catch (e: FreeformUnavailable) {
        e.message
    }

    /** Mở ứng dụng đích bằng cách (a). Ném lỗi nếu không làm được. */
    fun launchWithReflection(activity: Activity, target: TargetInfo, bounds: Rect) {
        val options = makeFreeformOptions(bounds)
        activity.startActivity(target.launchIntent, options.toBundle())
    }

    // =================================================================================
    // CÁCH (b): lệnh "am start" chạy qua Shizuku
    // =================================================================================

    /**
     * Tạo lệnh mở ứng dụng ở chế độ tự do.
     *  --windowingMode 5 : yêu cầu chế độ cửa sổ tự do
     *  -f 0x18000000     : cờ NEW_TASK (0x10000000) | MULTIPLE_TASK (0x08000000)
     * LƯU Ý: lệnh "am start" của Android KHÔNG có tùy chọn --bounds, nên kích thước được đặt
     * ở bước sau bằng lệnh "am task resize".
     */
    fun amStartCommand(component: String, extra: String = ""): String =
        "am start --windowingMode ${FreeformEnv.WINDOWING_MODE_FREEFORM} -f 0x18000000 " +
            "-a android.intent.action.MAIN -c android.intent.category.LAUNCHER " +
            "-n '$component'$extra"

    suspend fun launchWithShizuku(context: Context, target: TargetInfo, bounds: Rect): ShizukuLaunchResult {
        // Bước 1: Shizuku phải đang chạy và đã cấp quyền.
        if (!ShizukuHelper.awaitBinder()) {
            return ShizukuLaunchResult(false, null, false, "Shizuku chưa chạy.")
        }
        val status = ShizukuHelper.status(context)
        if (status != ShizukuHelper.Status.READY) {
            return ShizukuLaunchResult(false, null, false, ShizukuHelper.describe(status))
        }

        // Bước 2: chạy lệnh am start.
        val start = ShizukuHelper.exec(amStartCommand(target.component.flattenToShortString()))
        if (!start.ok) {
            return ShizukuLaunchResult(
                false, null, false,
                "Lệnh am start báo lỗi:\n${start.output.trim()}${FreeformEnv.permissionHint(start.output)}"
            )
        }
        val broughtToFront = start.output.contains("brought to the front")

        // Bước 3: đọc danh sách task để xác minh ứng dụng có đang ở chế độ "freeform" không.
        //         Thử tối đa 8 lần, mỗi lần cách 0,25 giây (ứng dụng cần chút thời gian để hiện lên).
        var found: DumpsysParser.TaskInfo? = null
        for (attempt in 1..8) {
            delay(250)
            val dump = ShizukuHelper.exec(DumpsysParser.COMMAND)
            found = DumpsysParser.findTopTask(dump.output, target.packageName)
            if (found?.mode == "freeform") break
        }
        if (found == null) {
            return ShizukuLaunchResult(true, null, false, "Đã gửi lệnh mở nhưng không đọc được trạng thái cửa sổ.")
        }
        if (found.mode != "freeform") {
            val why = if (broughtToFront) {
                " Ứng dụng đang mở sẵn nên hệ thống chỉ đưa cửa sổ cũ lên trước (hãy đóng nó trong Đa nhiệm rồi thử lại)."
            } else ""
            return ShizukuLaunchResult(true, false, false, "Hệ thống mở ở chế độ \"${found.mode}\" thay vì cửa sổ tự do.$why")
        }

        // Bước 4: đặt kích thước & vị trí bằng "am task resize <mã task> <trái> <trên> <phải> <dưới>".
        val resize = ShizukuHelper.exec(
            "am task resize ${found.id} ${bounds.left} ${bounds.top} ${bounds.right} ${bounds.bottom}"
        )
        return if (resize.ok) {
            ShizukuLaunchResult(true, true, true, "Đã mở dạng cửa sổ tự do (task #${found.id}).")
        } else {
            ShizukuLaunchResult(
                true, true, false,
                "Đã mở dạng cửa sổ tự do nhưng không đặt được kích thước: ${resize.output.trim().take(200)}" +
                    FreeformEnv.permissionHint(resize.output)
            )
        }
    }

    /** Mở bình thường (không ép chế độ cửa sổ). */
    fun launchNormally(activity: Activity, target: TargetInfo) {
        activity.startActivity(target.launchIntent)
    }

    // =================================================================================
    // Điều phối: chọn cách mở theo cài đặt
    // =================================================================================

    suspend fun launch(activity: Activity, target: TargetInfo, bounds: Rect, mode: LaunchMode): LaunchOutcome =
        when (mode) {
            LaunchMode.NORMAL -> {
                launchNormally(activity, target)
                LaunchOutcome.Success("normal")
            }
            LaunchMode.REFLECTION -> viaReflection(activity, target, bounds)
            LaunchMode.SHIZUKU -> viaShizuku(activity, target, bounds)
            LaunchMode.AUTO -> auto(activity, target, bounds)
        }

    private fun viaReflection(activity: Activity, target: TargetInfo, bounds: Rect): LaunchOutcome = try {
        launchWithReflection(activity, target, bounds)
        val warn = if (Prefs.isProbeCacheValid(activity) && Prefs.probeReflection == ProbeState.FAIL) {
            "Lưu ý: lần kiểm tra trước cho thấy máy bỏ qua cách (a), cửa sổ có thể vẫn toàn màn hình."
        } else null
        LaunchOutcome.Success("reflection", warn)
    } catch (e: FreeformUnavailable) {
        LaunchOutcome.Failure(
            "Cách (a) bị hệ điều hành chặn",
            "${e.message}\n\nĐề xuất: cài Shizuku và chọn cách \"Tự động\" hoặc \"Chỉ Shizuku\"."
        )
    } catch (e: Exception) {
        LaunchOutcome.Failure("Không mở được ứng dụng", e.toString())
    }

    private suspend fun viaShizuku(activity: Activity, target: TargetInfo, bounds: Rect): LaunchOutcome {
        val r = launchWithShizuku(activity, target, bounds)
        // Ghi nhớ kết quả để lần sau chọn cách cho nhanh.
        if (r.freeform == true) Prefs.probeShizuku = ProbeState.OK
        if (r.freeform == false) Prefs.probeShizuku = ProbeState.FAIL
        return when {
            !r.started -> LaunchOutcome.Failure(
                "Cách (b) Shizuku không chạy được",
                "${r.detail}\n\nMở FloatTile → \"Hướng dẫn & cấp quyền Shizuku\" để xem cách bật."
            )
            r.freeform == false -> LaunchOutcome.Success("shizuku", "⚠️ ${r.detail}")
            r.freeform == null -> LaunchOutcome.Success("shizuku", r.detail)
            !r.resized -> LaunchOutcome.Success("shizuku", r.detail)
            else -> LaunchOutcome.Success("shizuku")
        }
    }

    /**
     * Chế độ tự động:
     *  1. Nếu chưa từng kiểm tra (hoặc môi trường đã đổi): chạy kiểm tra nhanh bằng cửa sổ thử nghiệm.
     *  2. Cách (a) đã được chứng minh chạy được → dùng (a).
     *  3. Không thì, nếu Shizuku sẵn sàng và chưa bị chứng minh là hỏng → dùng (b).
     *  4. Không cách nào được → báo lỗi rõ ràng kèm lý do.
     */
    private suspend fun auto(activity: Activity, target: TargetInfo, bounds: Rect): LaunchOutcome {
        ShizukuHelper.awaitBinder(1500)
        if (!Prefs.isProbeCacheValid(activity)) {
            FreeformTester.detectAndSave(activity, bounds)
        }
        if (Prefs.probeReflection == ProbeState.OK) {
            return viaReflection(activity, target, bounds)
        }
        val shizukuReady = ShizukuHelper.status(activity) == ShizukuHelper.Status.READY
        if (shizukuReady && Prefs.probeShizuku != ProbeState.FAIL) {
            return viaShizuku(activity, target, bounds)
        }
        return LaunchOutcome.Failure("Không ép được chế độ cửa sổ tự do", buildFailureReport(activity))
    }

    /** Báo cáo lý do thất bại + gợi ý, dùng chung cho hộp thoại lỗi. */
    fun buildFailureReport(context: Context): String {
        val sb = StringBuilder()
        sb.appendLine("• Cách (a) – API ẩn: ${stateText(Prefs.probeReflection)}")
        if (Prefs.probeReflectionNote.isNotBlank()) sb.appendLine("   ${Prefs.probeReflectionNote}")
        sb.appendLine("• Cách (b) – Shizuku: ${stateText(Prefs.probeShizuku)}")
        if (Prefs.probeShizukuNote.isNotBlank()) sb.appendLine("   ${Prefs.probeShizukuNote}")
        sb.appendLine("   ${ShizukuHelper.describe(ShizukuHelper.status(context))}")
        sb.appendLine()
        sb.appendLine("Gợi ý:")
        val tips = mutableListOf<String>()
        val freeform = FreeformEnv.readGlobal(context, FreeformEnv.KEY_FREEFORM)
        val resizable = FreeformEnv.readGlobal(context, FreeformEnv.KEY_FORCE_RESIZABLE)
        if (freeform.readable && !freeform.isOn) {
            tips += "Bật \"Bật cửa sổ dạng tự do\" trong Tùy chọn nhà phát triển, rồi KHỞI ĐỘNG LẠI máy."
        } else {
            tips += "Nếu vừa bật \"Bật cửa sổ dạng tự do\", hãy KHỞI ĐỘNG LẠI máy (tùy chọn chỉ có hiệu lực sau khi khởi động lại)."
        }
        if (!resizable.isOn) {
            tips += "Bật \"Buộc các hoạt động có thể thay đổi kích thước\" trong Tùy chọn nhà phát triển, rồi khởi động lại máy."
        }
        if (ShizukuHelper.status(context) != ShizukuHelper.Status.READY) {
            tips += "Cài Shizuku và cấp quyền cho FloatTile (xem hướng dẫn trong ứng dụng)."
        } else if (Prefs.probeShizuku == ProbeState.FAIL && FreeformEnv.isXiaomi()) {
            tips += FreeformEnv.XIAOMI_ADB_HINT
        }
        if (FreeformEnv.isXiaomi()) {
            tips += "Dùng cửa sổ nổi có sẵn của HyperOS: mở Đa nhiệm → nhấn giữ thẻ ứng dụng → biểu tượng cửa sổ nổi."
        }
        tips.forEachIndexed { i, tip -> sb.appendLine("${i + 1}. $tip") }
        return sb.toString().trim()
    }

    fun stateText(s: ProbeState): String = when (s) {
        ProbeState.OK -> "✅ chạy được"
        ProbeState.FAIL -> "❌ không được"
        ProbeState.UNTESTED -> "— chưa kiểm tra"
    }
}
