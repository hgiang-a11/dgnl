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

    private var retrying = false

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
        // Bước 1: tìm ứng dụng đích.
        val target = TargetApp.resolve(this)
        if (target == null) {
            showTargetMissing()
            return
        }
        // Bước 2: tính khung cửa sổ theo cài đặt (80% × 60%, giữa màn hình nếu chưa đổi).
        val bounds = WindowBounds.fromPrefs(this)

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

    private fun showTargetMissing() {
        val candidates = TargetApp.findClaudeCandidates(this)
        val extra = if (candidates.isNotEmpty()) {
            "\n\nTìm thấy ứng dụng có thể là Claude:\n" +
                candidates.joinToString("\n") { "• ${it.second} (${it.first})" }
        } else ""
        MaterialAlertDialogBuilder(this)
            .setTitle("Không tìm thấy ứng dụng đích")
            .setMessage("Gói \"${Prefs.targetPackage}\" chưa được cài hoặc không có màn hình chính.$extra\n\nHãy chọn lại ứng dụng đích trong FloatTile.")
            .setPositiveButton("Mở cài đặt") { _, _ -> openSettings() }
            .setNegativeButton("Đóng", null)
            .setOnDismissListener { finishQuietly() }
            .show()
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
