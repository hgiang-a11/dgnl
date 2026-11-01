package com.floattile

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager

/** Cài đặt của một ô: tên, ứng dụng sẽ mở, kích thước và vị trí (đơn vị % màn hình). */
data class TileConfig(
    val slot: Int,
    val name: String,
    val packageName: String,
    val activityName: String?,
    val widthPct: Int = TileStore.DEFAULT_WIDTH,
    val heightPct: Int = TileStore.DEFAULT_HEIGHT,
    val posXPct: Int = TileStore.DEFAULT_POS,
    val posYPct: Int = TileStore.DEFAULT_POS
)

/**
 * Kho lưu các ô.
 *
 * Android bắt mỗi ô trên bảng cài đặt nhanh phải là một dịch vụ (TileService) khai báo sẵn
 * trong AndroidManifest, không tạo thêm lúc chạy được. Vì vậy FloatTile khai báo sẵn 10 "chỗ"
 * (FloatTileService, FloatTileService2 … FloatTileService10).
 * Chỗ nào chưa dùng thì bị tắt (disable) để không hiện trong danh sách thêm ô của hệ thống.
 */
object TileStore {
    const val MAX_SLOTS = 10

    /** Chỗ số 0: bản nháp dùng cho nút "Mở thử", không gắn với ô nào. */
    const val PREVIEW_SLOT = 0

    const val DEFAULT_PACKAGE = "com.anthropic.claude"
    const val DEFAULT_NAME = "Claude nổi"
    const val DEFAULT_WIDTH = 80
    const val DEFAULT_HEIGHT = 60
    const val DEFAULT_POS = 50   // 50% = chính giữa

    private lateinit var sp: SharedPreferences

    /** Lớp dịch vụ tương ứng với từng chỗ 1..10 (thứ tự phải khớp với AndroidManifest). */
    private val serviceClasses: List<Class<*>> = listOf(
        FloatTileService::class.java,
        FloatTileService2::class.java,
        FloatTileService3::class.java,
        FloatTileService4::class.java,
        FloatTileService5::class.java,
        FloatTileService6::class.java,
        FloatTileService7::class.java,
        FloatTileService8::class.java,
        FloatTileService9::class.java,
        FloatTileService10::class.java
    )

    fun init(context: Context) {
        sp = context.applicationContext.getSharedPreferences("floattile", Context.MODE_PRIVATE)
        migrateFromSingleTile(context)
        syncComponents(context)
    }

    private fun key(slot: Int, field: String) = "slot${slot}_$field"

    fun defaultConfig(slot: Int) = TileConfig(slot, DEFAULT_NAME, DEFAULT_PACKAGE, null)

    fun get(slot: Int): TileConfig? {
        if (!sp.getBoolean(key(slot, "used"), false)) return null
        val pkg = sp.getString(key(slot, "pkg"), null) ?: return null
        return TileConfig(
            slot = slot,
            name = sp.getString(key(slot, "name"), null) ?: "Ô $slot",
            packageName = pkg,
            activityName = sp.getString(key(slot, "act"), null),
            widthPct = sp.getInt(key(slot, "w"), DEFAULT_WIDTH),
            heightPct = sp.getInt(key(slot, "h"), DEFAULT_HEIGHT),
            posXPct = sp.getInt(key(slot, "x"), DEFAULT_POS),
            posYPct = sp.getInt(key(slot, "y"), DEFAULT_POS)
        )
    }

    /** Các ô đang dùng, theo thứ tự số ô. */
    fun all(): List<TileConfig> = (1..MAX_SLOTS).mapNotNull { get(it) }

    fun firstFreeSlot(): Int? = (1..MAX_SLOTS).firstOrNull { get(it) == null }

    fun save(context: Context, config: TileConfig) {
        val s = config.slot
        sp.edit()
            .putBoolean(key(s, "used"), true)
            .putString(key(s, "name"), config.name)
            .putString(key(s, "pkg"), config.packageName)
            .putString(key(s, "act"), config.activityName)
            .putInt(key(s, "w"), config.widthPct)
            .putInt(key(s, "h"), config.heightPct)
            .putInt(key(s, "x"), config.posXPct)
            .putInt(key(s, "y"), config.posYPct)
            .apply()
        // Bật "chỗ" tương ứng để ô hiện trong danh sách thêm ô của hệ thống.
        if (s in 1..MAX_SLOTS) setComponentEnabled(context, s, true)
    }

    fun delete(context: Context, slot: Int) {
        val e = sp.edit()
        for (f in listOf("used", "name", "pkg", "act", "w", "h", "x", "y", "added")) e.remove(key(slot, f))
        e.apply()
        // Tắt "chỗ": hệ thống tự gỡ ô khỏi bảng cài đặt nhanh.
        if (slot in 1..MAX_SLOTS) setComponentEnabled(context, slot, false)
    }

    // ----- Ghi nhớ ô đã được thêm vào bảng cài đặt nhanh chưa (do dịch vụ ô báo về) -----
    fun isAdded(slot: Int): Boolean = sp.getBoolean(key(slot, "added"), false)

    fun setAdded(slot: Int, added: Boolean) {
        if (isAdded(slot) != added) sp.edit().putBoolean(key(slot, "added"), added).apply()
    }

    fun component(context: Context, slot: Int): ComponentName =
        ComponentName(context, serviceClasses[slot - 1])

    /** Bật các chỗ đang dùng, tắt các chỗ trống. */
    fun syncComponents(context: Context) {
        for (slot in 1..MAX_SLOTS) setComponentEnabled(context, slot, get(slot) != null)
    }

    private fun setComponentEnabled(context: Context, slot: Int, enabled: Boolean) {
        val pm = context.packageManager
        val cn = component(context, slot)
        val wanted = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        try {
            if (pm.getComponentEnabledSetting(cn) != wanted) {
                // DONT_KILL_APP: không tắt ứng dụng khi đổi trạng thái.
                pm.setComponentEnabledSetting(cn, wanted, PackageManager.DONT_KILL_APP)
            }
        } catch (e: Exception) {
            // Bỏ qua: nếu lỗi thì ô chỉ hiện thừa trong danh sách, không ảnh hưởng chức năng.
        }
    }

    /**
     * Chuyển từ bản cũ (chỉ có một ô "Claude nổi") sang bản nhiều ô:
     * ô cũ trở thành ô số 1, giữ nguyên ứng dụng và kích thước đã chọn.
     * Cài mới thì ô số 1 mặc định mở Claude.
     */
    private fun migrateFromSingleTile(context: Context) {
        if (sp.getBoolean("slots_migrated", false)) return
        if (get(1) == null) {
            save(
                context,
                TileConfig(
                    slot = 1,
                    name = DEFAULT_NAME,
                    packageName = sp.getString("target_package", null) ?: DEFAULT_PACKAGE,
                    activityName = sp.getString("target_activity", null),
                    widthPct = sp.getInt("width_pct", DEFAULT_WIDTH),
                    heightPct = sp.getInt("height_pct", DEFAULT_HEIGHT),
                    posXPct = sp.getInt("pos_x_pct", DEFAULT_POS),
                    posYPct = sp.getInt("pos_y_pct", DEFAULT_POS)
                )
            )
        }
        sp.edit().putBoolean("slots_migrated", true).apply()
    }
}
