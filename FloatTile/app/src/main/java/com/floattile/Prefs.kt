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
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.applicationContext.getSharedPreferences("floattile", Context.MODE_PRIVATE)
    }

    /**
     * HyperOS tự thu nhỏ mọi cửa sổ nổi (thường còn khoảng 70%) và giữ nguyên góc trên-trái.
     * FloatTile phóng to khung gửi cho hệ thống theo tỉ lệ nghịch để bù lại.
     * Máy Xiaomi mặc định 70%; máy khác mặc định 100% (= không bù).
     */
    var hyperScalePct: Int
        get() = sp.getInt("hyper_scale_pct", if (FreeformEnv.isXiaomi()) 70 else 100)
        set(v) = sp.edit().putInt("hyper_scale_pct", v).apply()

    /**
     * true = chỉ yêu cầu mở dạng cửa sổ nổi, KHÔNG tự đổi kích thước/vị trí sau khi mở.
     * Trên HyperOS, lệnh đổi kích thước từ bên ngoài có thể làm hệ thống "mất dấu" cửa sổ
     * (mất thanh kéo, lệch tỉ lệ), nên máy Xiaomi mặc định bật.
     */
    var hyperManaged: Boolean
        get() = sp.getBoolean("hyper_managed", FreeformEnv.isXiaomi())
        set(v) = sp.edit().putBoolean("hyper_managed", v).apply()

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
