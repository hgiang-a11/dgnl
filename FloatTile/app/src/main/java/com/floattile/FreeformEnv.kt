package com.floattile

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/** Giá trị một khóa trong Settings.Global (bảng cài đặt chung của hệ thống). */
data class GlobalSetting(val readable: Boolean, val value: String?) {
    val isOn: Boolean get() = value == "1"

    fun display(): String = when {
        !readable -> "không đọc được"
        value == null -> "chưa đặt"
        value == "1" -> "bật (1)"
        value == "0" -> "tắt (0)"
        else -> value.toString()
    }
}

/**
 * Các phép kiểm tra môi trường hệ thống liên quan tới cửa sổ tự do
 * (freeform = cửa sổ nổi, kéo thả, đổi kích thước được như trên máy tính).
 */
object FreeformEnv {
    /** Mã chế độ cửa sổ "tự do" bên trong Android (WINDOWING_MODE_FREEFORM = 5). */
    const val WINDOWING_MODE_FREEFORM = 5

    /** Khóa của tùy chọn nhà phát triển "Bật cửa sổ dạng tự do". */
    const val KEY_FREEFORM = "enable_freeform_support"

    /** Khóa của tùy chọn "Buộc các hoạt động có thể thay đổi kích thước". */
    const val KEY_FORCE_RESIZABLE = "force_resizable_activities"

    /** Máy có khai báo chính thức hỗ trợ quản lý cửa sổ tự do không. */
    fun hasFreeformFeature(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)

    /**
     * Đọc một khóa trong Settings.Global.
     * Từ Android 12, một số khóa ẩn bị cấm đọc → khi đó trả về readable = false thay vì làm sập ứng dụng.
     */
    fun readGlobal(context: Context, key: String): GlobalSetting = try {
        GlobalSetting(true, Settings.Global.getString(context.contentResolver, key))
    } catch (e: SecurityException) {
        GlobalSetting(false, null)
    }

    /** Có phải máy Xiaomi/Redmi/POCO (giao diện MIUI/HyperOS) không. */
    fun isXiaomi(): Boolean {
        val m = Build.MANUFACTURER.lowercase()
        val b = Build.BRAND.lowercase()
        return m.contains("xiaomi") || b.contains("xiaomi") || b.contains("redmi") || b.contains("poco")
    }

    /**
     * Trên Xiaomi/HyperOS, ADB bị hãng cắt bớt quyền cho tới khi bật
     * "Gỡ lỗi USB (Cài đặt bảo mật)". Shizuku chạy bằng ADB nên cũng bị ảnh hưởng.
     */
    const val XIAOMI_ADB_HINT =
        "Máy Xiaomi/HyperOS đang giới hạn quyền ADB. Hãy vào Tùy chọn nhà phát triển, bật " +
            "“Gỡ lỗi USB (Cài đặt bảo mật)”, rồi mở Shizuku bấm Dừng và Khởi động lại."

    /** Nếu kết quả lệnh cho thấy bị từ chối quyền trên máy Xiaomi → trả về lời khuyên, ngược lại trả về chuỗi rỗng. */
    fun permissionHint(output: String): String {
        val denied = output.contains("SecurityException") ||
            output.contains("Permission Denial", ignoreCase = true) ||
            output.contains("permission", ignoreCase = true)
        return if (isXiaomi() && denied) "\n\n$XIAOMI_ADB_HINT" else ""
    }

    fun describeDevice(): String =
        "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})" +
            "\n   Bản hệ điều hành: ${Build.VERSION.INCREMENTAL}"
}
