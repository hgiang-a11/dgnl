package com.floattile

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Màn hình trung chuyển trong suốt, được ô cài đặt nhanh mở ra.
 * Nhiệm vụ: mở ứng dụng đích ở dạng cửa sổ nổi, rồi tự đóng.
 * Nếu thất bại: hiện hộp thoại giải thích lý do thay vì im lặng.
 */
class LaunchActivity : AppCompatActivity() {

    companion object {
        /** Số ô (1..10) đã được bấm; 0 = bản nháp của nút "Mở thử". */
        const val EXTRA_SLOT = "com.floattile.SLOT"
    }

    private var retrying = false
    private val slot: Int get() = intent.getIntExtra(EXTRA_SLOT, 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            // Bị hệ thống tạo lại (ví dụ sau khi xoay màn hình) → không chạy lại lần nữa.
            finishQuietly()
            return
        }
        lifecycleScope.launch { runLaunch() }
    }

    private suspend fun runLaunch() {
        // Bước 1: đọc cài đặt của ô vừa bấm, rồi tìm ứng dụng đích.
        val cfg = TileStore.get(slot)
        if (cfg == null) {
            showUnconfigured()
            return
        }
        val target = TargetApp.resolve(this, cfg)
        if (target == null) {
            showTargetMissing(cfg)
            return
        }
        // Bước 2: tính khung cửa sổ của ô (đã bù việc HyperOS tự thu nhỏ cửa sổ).
        val bounds = WindowBounds.forTile(this, cfg)

        // Bước 3: mở theo cách người dùng chọn.
        val outcome = try {
            FreeformLauncher.launch(this, target, bounds, Prefs.launchMode)
        } catch (e: Exception) {
            LaunchOutcome.Failure("Lỗi không mong muốn", e.toString())
        }

        // Bước 4: báo kết quả.
        when (outcome) {
            is LaunchOutcome.Success -> {
                outcome.note?.let { applicationContext.toast(it, long = true) }
                finishQuietly()
            }
            is LaunchOutcome.Failure -> showFailure(target, outcome)
        }
    }

    private fun showUnconfigured() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Ô số $slot chưa được cài đặt")
            .setMessage("Hãy chọn ứng dụng và đặt tên cho ô này trong FloatTile.")
            .setPositiveButton("Cài đặt ô") { _, _ -> openEditor() }
            .setNegativeButton("Đóng", null)
            .setOnDismissListener { finishQuietly() }
            .show()
    }

    private fun showTargetMissing(cfg: TileConfig) {
        val candidates = TargetApp.findClaudeCandidates(this)
        val extra = if (candidates.isNotEmpty() && cfg.packageName == TileStore.DEFAULT_PACKAGE) {
            "\n\nTìm thấy ứng dụng có thể là Claude:\n" +
                candidates.joinToString("\n") { "• ${it.second} (${it.first})" }
        } else ""
        MaterialAlertDialogBuilder(this)
            .setTitle("Không tìm thấy ứng dụng của ô “${cfg.name}”")
            .setMessage("Gói \"${cfg.packageName}\" chưa được cài hoặc không có màn hình chính.$extra\n\nHãy chọn lại ứng dụng cho ô này.")
            .setPositiveButton("Sửa ô") { _, _ -> if (slot in 1..TileStore.MAX_SLOTS) openEditor() else openSettings() }
            .setNegativeButton("Đóng", null)
            .setOnDismissListener { finishQuietly() }
            .show()
    }

    private fun openEditor() {
        startActivity(
            Intent(this, TileEditActivity::class.java)
                .putExtra(TileEditActivity.EXTRA_SLOT, slot)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun showFailure(target: TargetInfo, failure: LaunchOutcome.Failure) {
        MaterialAlertDialogBuilder(this)
            .setTitle(failure.title)
            .setMessage(failure.message)
            .setPositiveButton("Thử lại") { _, _ ->
                // Xóa kết quả kiểm tra cũ rồi chạy lại từ đầu (dùng sau khi bạn vừa đổi cài đặt máy).
                retrying = true
                Prefs.clearProbe()
                lifecycleScope.launch { runLaunch() }
            }
            .setNeutralButton("Cài đặt") { _, _ -> openSettings() }
            .setNegativeButton("Mở toàn màn hình") { _, _ ->
                runCatching { FreeformLauncher.launchNormally(this, target) }
            }
            .setOnDismissListener {
                if (retrying) retrying = false else finishQuietly()
            }
            .show()
    }

    private fun openSettings() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun finishQuietly() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
