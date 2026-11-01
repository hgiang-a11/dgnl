package com.floattile

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

/**
 * Màn hình hướng dẫn cài, khởi động Shizuku và cấp quyền cho FloatTile.
 */
class ShizukuGuideActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var outputText: TextView
    private lateinit var btnPermission: Button

    // Các "người nghe" sự kiện của Shizuku: khi Shizuku bật/tắt hoặc khi bạn trả lời hộp thoại cấp quyền.
    private val binderReceived = Shizuku.OnBinderReceivedListener { runOnUiThread { refresh() } }
    private val binderDead = Shizuku.OnBinderDeadListener { runOnUiThread { refresh() } }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, _ ->
        runOnUiThread { refresh() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shizuku)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        statusText = findViewById(R.id.shizuku_state)
        outputText = findViewById(R.id.shizuku_output)
        btnPermission = findViewById(R.id.btn_permission)

        // Máy Xiaomi: hiện khung hướng dẫn gỡ giới hạn quyền ADB.
        findViewById<View>(R.id.xiaomi_limit_card).visibility =
            if (FreeformEnv.isXiaomi()) View.VISIBLE else View.GONE

        btnPermission.setOnClickListener { askPermission() }
        findViewById<Button>(R.id.btn_open_shizuku).setOnClickListener { openShizuku() }
        findViewById<Button>(R.id.btn_download_shizuku).setOnClickListener { downloadShizuku() }
        findViewById<Button>(R.id.btn_dev_options).setOnClickListener { openDeveloperOptions() }
        findViewById<Button>(R.id.btn_find_claude).setOnClickListener {
            runShell("pm list packages | grep -i claude", "(không có gói nào chứa chữ claude)")
        }
        findViewById<Button>(R.id.btn_test_shell).setOnClickListener { runShell("id", "") }

        runCatching {
            Shizuku.addBinderReceivedListenerSticky(binderReceived)
            Shizuku.addBinderDeadListener(binderDead)
            Shizuku.addRequestPermissionResultListener(permissionResult)
        }
    }

    override fun onDestroy() {
        runCatching {
            Shizuku.removeBinderReceivedListener(binderReceived)
            Shizuku.removeBinderDeadListener(binderDead)
            Shizuku.removeRequestPermissionResultListener(permissionResult)
        }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun refresh() {
        val status = ShizukuHelper.status(this)
        statusText.text = ShizukuHelper.describe(status)
        btnPermission.isEnabled = status == ShizukuHelper.Status.NO_PERMISSION
        btnPermission.text = if (status == ShizukuHelper.Status.READY) {
            getString(R.string.shizuku_permission_granted)
        } else {
            getString(R.string.shizuku_request_permission)
        }
    }

    private fun askPermission() {
        when (ShizukuHelper.status(this)) {
            ShizukuHelper.Status.NOT_INSTALLED -> toast("Hãy cài Shizuku trước (nút Tải Shizuku).", long = true)
            ShizukuHelper.Status.NOT_RUNNING -> toast("Hãy mở Shizuku và bấm Khởi động trước.", long = true)
            ShizukuHelper.Status.TOO_OLD -> toast("Hãy cập nhật Shizuku lên bản mới.", long = true)
            ShizukuHelper.Status.READY -> toast("Đã có quyền rồi.")
            ShizukuHelper.Status.NO_PERMISSION -> {
                if (ShizukuHelper.permissionPermanentlyDenied()) {
                    toast(
                        "Bạn đã chọn \"không hỏi lại\". Hãy mở Shizuku → Ứng dụng được cấp quyền → bật FloatTile.",
                        long = true
                    )
                    return
                }
                lifecycleScope.launch {
                    val granted = ShizukuHelper.requestPermission()
                    toast(if (granted) "Đã cấp quyền Shizuku." else "Bạn chưa cấp quyền.")
                    refresh()
                }
            }
        }
    }

    private fun runShell(command: String, emptyText: String) {
        if (ShizukuHelper.status(this) != ShizukuHelper.Status.READY) {
            toast("Shizuku chưa sẵn sàng.")
            return
        }
        outputText.text = getString(R.string.running_command, command)
        lifecycleScope.launch {
            val r = ShizukuHelper.exec(command)
            val out = r.output.trim().ifEmpty { emptyText }
            val path = ShizukuHelper.lastExecPath?.let { "\n(đường chạy lệnh: $it)" } ?: ""
            outputText.text = "$ $command\n$out$path"
        }
    }

    private fun openShizuku() {
        val intent = packageManager.getLaunchIntentForPackage(ShizukuHelper.PACKAGE)
        if (intent == null) {
            toast("Chưa cài Shizuku.")
        } else {
            startActivity(intent)
        }
    }

    private fun downloadShizuku() {
        // Ưu tiên mở Google Play; nếu máy không có thì mở trang tải chính thức.
        val play = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${ShizukuHelper.PACKAGE}"))
        try {
            startActivity(play)
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
        }
    }

    private fun openDeveloperOptions() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        } catch (e: Exception) {
            toast("Hãy vào Cài đặt → Cài đặt bổ sung → Tùy chọn nhà phát triển.", long = true)
        }
    }
}
