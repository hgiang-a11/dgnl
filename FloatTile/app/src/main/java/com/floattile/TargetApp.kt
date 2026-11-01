package com.floattile

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

/** Thông tin ứng dụng đích đã sẵn sàng để mở. */
data class TargetInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val component: ComponentName,
    val launchIntent: Intent
)

object TargetApp {

    /**
     * Tìm ứng dụng đích theo cài đặt và tạo Intent (= "lời nhắn" yêu cầu hệ thống mở một màn hình).
     * Trả về null nếu ứng dụng chưa cài hoặc không có màn hình chính.
     */
    @Suppress("DEPRECATION")
    fun resolve(context: Context): TargetInfo? {
        val pm = context.packageManager
        val pkg = Prefs.targetPackage
        val act = Prefs.targetActivity

        // Bước 1: nếu người dùng đã chọn một màn hình cụ thể, thử dùng nó trước.
        var intent: Intent? = null
        if (!act.isNullOrBlank()) {
            val explicit = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(pkg, act)
            if (pm.resolveActivity(explicit, 0) != null) intent = explicit
        }
        // Bước 2: nếu không, hỏi hệ thống "màn hình khởi động" của ứng dụng đó.
        if (intent == null) intent = pm.getLaunchIntentForPackage(pkg) ?: return null

        val component = intent.component ?: intent.resolveActivity(pm) ?: return null
        intent.component = component

        // Bước 3: gắn cờ theo yêu cầu:
        //  - FLAG_ACTIVITY_NEW_TASK: mở trong một "task" (ngăn xếp màn hình) mới.
        //  - FLAG_ACTIVITY_MULTIPLE_TASK: cho phép tạo task mới kể cả khi ứng dụng đang mở sẵn ở task khác.
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK

        val (label, icon) = try {
            val ai = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(ai).toString() to pm.getApplicationIcon(ai)
        } catch (e: PackageManager.NameNotFoundException) {
            pkg to null
        }
        return TargetInfo(pkg, label, icon, component, intent)
    }

    /** Ứng dụng có biểu tượng ngoài màn hình chính mà tên gói hoặc tên hiển thị chứa chữ "claude". */
    @Suppress("DEPRECATION")
    fun findClaudeCandidates(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { (pkg, label) ->
                pkg != context.packageName &&
                    (pkg.contains("claude", ignoreCase = true) || label.contains("claude", ignoreCase = true))
            }
            .distinctBy { it.first }
    }
}
