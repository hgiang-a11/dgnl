package com.floattile

import android.content.Context
import android.content.SharedPreferences
import android.os.Build

/** Các cách mở cửa sổ mà người dùng có thể chọn trong cài đặt. */
enum class LaunchMode {
    AUTO,        // Tự động: dùng cách đã được kiểm tra là chạy được trên máy này
    REFLECTION,  // Chỉ dùng cách (a): gọi hàm ẩn qua reflection
    SHIZUKU,     // Chỉ dùng cách (b): lệnh "am start" qua Shizuku
    NORMAL       // Không ép: mở bình thường (để so sánh)
}

/** Kết quả kiểm tra một cách mở cửa sổ tự do. */
enum class ProbeState { UNTESTED, OK, FAIL }

/**
 * Nơi lưu cài đặt của người dùng, dùng SharedPreferences
 * (= một file nhỏ dạng "khóa → giá trị" nằm trong bộ nhớ riêng của ứng dụng).
 */
object Prefs {
    const val DEFAULT_PACKAGE = "com.anthropic.claude"

    const val DEFAULT_WIDTH = 80
    const val DEFAULT_HEIGHT = 60
    const val DEFAULT_POS = 50   // 50% = chính giữa

    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.applicationContext.getSharedPreferences("floattile", Context.MODE_PRIVATE)
    }

    // ----- Ứng dụng đích -----
    var targetPackage: String
        get() = sp.getString("target_package", DEFAULT_PACKAGE) ?: DEFAULT_PACKAGE
        set(v) = sp.edit().putString("target_package", v).apply()

    /** Tên màn hình (activity) cụ thể của ứng dụng đích; null = để hệ thống tự chọn màn hình chính. */
    var targetActivity: String?
        get() = sp.getString("target_activity", null)
        set(v) = sp.edit().putString("target_activity", v).apply()

    // ----- Kích thước & vị trí (đơn vị: % màn hình) -----
    var widthPct: Int
        get() = sp.getInt("width_pct", DEFAULT_WIDTH)
        set(v) = sp.edit().putInt("width_pct", v).apply()

    var heightPct: Int
        get() = sp.getInt("height_pct", DEFAULT_HEIGHT)
        set(v) = sp.edit().putInt("height_pct", v).apply()

    /** Vị trí tâm cửa sổ theo chiều ngang: 0 = sát trái, 50 = giữa, 100 = sát phải. */
    var posXPct: Int
        get() = sp.getInt("pos_x_pct", DEFAULT_POS)
        set(v) = sp.edit().putInt("pos_x_pct", v).apply()

    /** Vị trí tâm cửa sổ theo chiều dọc: 0 = sát trên, 50 = giữa, 100 = sát dưới. */
    var posYPct: Int
        get() = sp.getInt("pos_y_pct", DEFAULT_POS)
        set(v) = sp.edit().putInt("pos_y_pct", v).apply()

    fun resetBounds() {
        widthPct = DEFAULT_WIDTH
        heightPct = DEFAULT_HEIGHT
        posXPct = DEFAULT_POS
        posYPct = DEFAULT_POS
    }

    // ----- Cách mở -----
    var launchMode: LaunchMode
        get() = runCatching { LaunchMode.valueOf(sp.getString("launch_mode", null) ?: "AUTO") }
            .getOrDefault(LaunchMode.AUTO)
        set(v) = sp.edit().putString("launch_mode", v.name).apply()

    // ----- Kết quả kiểm tra lần trước (để lần bấm sau không phải kiểm tra lại) -----
    var probeReflection: ProbeState
        get() = readState("probe_reflection")
        set(v) = sp.edit().putString("probe_reflection", v.name).apply()

    var probeShizuku: ProbeState
        get() = readState("probe_shizuku")
        set(v) = sp.edit().putString("probe_shizuku", v.name).apply()

    var probeReflectionNote: String
        get() = sp.getString("probe_reflection_note", "") ?: ""
        set(v) = sp.edit().putString("probe_reflection_note", v).apply()

    var probeShizukuNote: String
        get() = sp.getString("probe_shizuku_note", "") ?: ""
        set(v) = sp.edit().putString("probe_shizuku_note", v).apply()

    var probeTime: Long
        get() = sp.getLong("probe_time", 0L)
        set(v) = sp.edit().putLong("probe_time", v).apply()

    private var probeFingerprint: String?
        get() = sp.getString("probe_fingerprint", null)
        set(v) = sp.edit().putString("probe_fingerprint", v).apply()

    private fun readState(key: String): ProbeState =
        runCatching { ProbeState.valueOf(sp.getString(key, null) ?: "UNTESTED") }
            .getOrDefault(ProbeState.UNTESTED)

    /**
     * "Dấu vân tay" của môi trường: phiên bản hệ điều hành + giá trị các tùy chọn cửa sổ tự do.
     * Nếu một trong các giá trị này đổi (ví dụ bạn vừa bật/tắt tùy chọn), kết quả cũ bị coi là hết hạn.
     */
    fun environmentFingerprint(context: Context): String {
        val freeform = FreeformEnv.readGlobal(context, FreeformEnv.KEY_FREEFORM).display()
        val resizable = FreeformEnv.readGlobal(context, FreeformEnv.KEY_FORCE_RESIZABLE).display()
        return "${Build.FINGERPRINT}|$freeform|$resizable|${BuildConfig.VERSION_CODE}"
    }

    fun isProbeCacheValid(context: Context): Boolean =
        probeTime > 0 && probeFingerprint == environmentFingerprint(context)

    fun saveProbe(
        context: Context,
        reflection: ProbeState, reflectionNote: String,
        shizuku: ProbeState, shizukuNote: String
    ) {
        probeReflection = reflection
        probeReflectionNote = reflectionNote
        probeShizuku = shizuku
        probeShizukuNote = shizukuNote
        probeTime = System.currentTimeMillis()
        probeFingerprint = environmentFingerprint(context)
    }

    fun clearProbe() {
        sp.edit()
            .remove("probe_reflection").remove("probe_shizuku")
            .remove("probe_reflection_note").remove("probe_shizuku_note")
            .remove("probe_time").remove("probe_fingerprint")
            .apply()
    }
}
