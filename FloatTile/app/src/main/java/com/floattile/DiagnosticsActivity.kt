package com.floattile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Màn hình "Kiểm tra cửa sổ tự do": chạy lần lượt từng bước và in kết quả ra màn hình.
 */
class DiagnosticsActivity : AppCompatActivity() {

    private lateinit var report: TextView
    private lateinit var progress: ProgressBar
    private lateinit var btnRun: Button
    private lateinit var btnEnable: Button
    private val log = StringBuilder()
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diagnostics)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        report = findViewById(R.id.report)
        progress = findViewById(R.id.progress)
        btnRun = findViewById(R.id.btn_run)
        btnEnable = findViewById(R.id.btn_enable_freeform)

        btnRun.setOnClickListener { runTests() }
        findViewById<Button>(R.id.btn_copy).setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("FloatTile", log.toString()))
            toast("Đã chép báo cáo")
        }
        findViewById<Button>(R.id.btn_dev_options).setOnClickListener { openDeveloperOptions() }
        findViewById<Button>(R.id.btn_shizuku_guide).setOnClickListener {
            startActivity(Intent(this, ShizukuGuideActivity::class.java))
        }
        btnEnable.setOnClickListener { enableFreeformWithShizuku() }

        runTests()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun line(text: String) {
        log.appendLine(text)
        report.text = log.toString()
    }

    private fun runTests() {
        if (running) return
        running = true
        btnRun.isEnabled = false
        progress.visibility = View.VISIBLE
        btnEnable.visibility = View.GONE
        log.clear()

        lifecycleScope.launch {
            try {
                doTests()
            } catch (e: Exception) {
                line("❌ Lỗi khi kiểm tra: $e")
            } finally {
                running = false
                btnRun.isEnabled = true
                progress.visibility = View.GONE
            }
        }
    }

    private suspend fun doTests() {
        val ctx = this
        line("📱 ${FreeformEnv.describeDevice()}")
        line("")

        // Bước 1: máy có khai báo tính năng cửa sổ tự do chính thức không.
        line("① Tính năng hệ thống")
        if (FreeformEnv.hasFreeformFeature(ctx)) {
            line("   ✅ Máy khai báo hỗ trợ cửa sổ tự do (FEATURE_FREEFORM_WINDOW_MANAGEMENT).")
        } else {
            line("   ℹ️ Máy không khai báo tính năng này. Vẫn có thể dùng nếu đã bật trong Tùy chọn nhà phát triển.")
        }

        // Bước 2: đọc các tùy chọn nhà phát triển liên quan.
        line("② Tùy chọn nhà phát triển")
        val freeform = FreeformEnv.readGlobal(ctx, FreeformEnv.KEY_FREEFORM)
        val resizable = FreeformEnv.readGlobal(ctx, FreeformEnv.KEY_FORCE_RESIZABLE)
        line("   • Bật cửa sổ dạng tự do: ${freeform.display()}")
        line("   • Buộc hoạt động đổi được kích thước: ${resizable.display()}")
        if (freeform.readable && !freeform.isOn) {
            line("   ⚠️ Tùy chọn cửa sổ tự do đang TẮT. Hãy bật rồi khởi động lại máy.")
        } else if (freeform.isOn) {
            line("   ℹ️ Nếu vừa bật, cần KHỞI ĐỘNG LẠI máy thì mới có hiệu lực.")
        }

        // Bước 3: hàm ẩn có gọi được không.
        line("③ Cách (a): gọi hàm ẩn setLaunchWindowingMode bằng reflection")
        val reflectionError = FreeformLauncher.checkReflection()
        if (reflectionError == null) {
            line("   ✅ Gọi được hàm ẩn.")
        } else {
            line("   ❌ $reflectionError")
        }

        // Bước 4: mở cửa sổ thử bằng cách (a).
        val bounds = WindowBounds.fromPrefs(ctx)
        line("   ⏳ Mở cửa sổ thử nghiệm bằng cách (a)…")
        val a = FreeformTester.probeReflection(ctx, bounds)
        line("   ${FreeformLauncher.stateText(a.state)}: ${a.note}")

        // Bước 5: Shizuku.
        line("④ Cách (b): am start --windowingMode 5 qua Shizuku")
        ShizukuHelper.awaitBinder()
        val status = ShizukuHelper.status(ctx)
        line("   ${ShizukuHelper.describe(status)}")
        val b = if (status == ShizukuHelper.Status.READY) {
            line("   ⏳ Mở cửa sổ thử nghiệm bằng Shizuku…")
            FreeformTester.probeShizuku(ctx)
        } else {
            FreeformTester.Outcome(ProbeState.UNTESTED, "Chưa thử vì Shizuku chưa sẵn sàng.")
        }
        line("   ${FreeformLauncher.stateText(b.state)}: ${b.note}")
        if (status == ShizukuHelper.Status.READY) {
            val pkgs = ShizukuHelper.exec("pm list packages | grep -i claude")
            line("   • Kết quả lệnh \"pm list packages | grep -i claude\":")
            line("     " + pkgs.output.trim().ifEmpty { "(không có gói nào chứa chữ claude)" }.replace("\n", "\n     "))
        }

        // Bước 6: ứng dụng đích.
        line("⑤ Ứng dụng đích")
        val target = TargetApp.resolve(ctx)
        if (target != null) {
            line("   ✅ ${target.label} — ${target.component.flattenToShortString()}")
        } else {
            line("   ❌ Không tìm thấy gói ${Prefs.targetPackage}. Hãy chọn lại ứng dụng đích.")
        }
        line("   Khung cửa sổ sẽ dùng: ${WindowBounds.describe(bounds)}")

        // Lưu kết quả để ô cài đặt nhanh dùng.
        Prefs.saveProbe(ctx, a.state, a.note, b.state, b.note)

        // Kết luận.
        line("")
        line("🧾 KẾT LUẬN")
        when {
            a.state == ProbeState.OK ->
                line("   Máy mở được cửa sổ tự do bằng cách (a). Ô cài đặt nhanh sẽ dùng cách này.")
            b.state == ProbeState.OK ->
                line("   Cách (a) không được, nhưng cách (b) Shizuku chạy được. Ô cài đặt nhanh sẽ dùng Shizuku (Shizuku cần đang chạy).")
            else -> {
                line("   Chưa có cách nào ép được cửa sổ tự do trên máy này.")
                line(FreeformLauncher.buildFailureReport(ctx).prependIndent("   "))
            }
        }
        if (status == ShizukuHelper.Status.READY && !(freeform.isOn && resizable.isOn)) {
            btnEnable.visibility = View.VISIBLE
        }
        line("")
        line("ℹ️ Lưu ý: ứng dụng đích (Claude) còn phải cho phép đổi kích thước. Nếu cửa sổ thử nổi được mà Claude vẫn toàn màn hình, hãy bật \"Buộc các hoạt động có thể thay đổi kích thước\".")
    }

    /** Dùng quyền ADB của Shizuku để bật 2 tùy chọn nhà phát triển (cần khởi động lại máy). */
    private fun enableFreeformWithShizuku() {
        lifecycleScope.launch {
            val r = ShizukuHelper.exec(
                "settings put global ${FreeformEnv.KEY_FREEFORM} 1 && " +
                    "settings put global ${FreeformEnv.KEY_FORCE_RESIZABLE} 1"
            )
            if (r.exitCode == 0) {
                toast("Đã bật. Hãy KHỞI ĐỘNG LẠI máy rồi kiểm tra lại.", long = true)
                btnEnable.visibility = View.GONE
            } else {
                // Ghi lý do vào báo cáo (Toast quá ngắn để đọc hết).
                line("")
                line("❌ Không bật được tùy chọn bằng Shizuku: ${r.output.trim().take(300)}")
                val hint = FreeformEnv.permissionHint(r.output).trim()
                if (hint.isNotEmpty()) line("   👉 $hint")
                toast("Không bật được, xem lý do ở cuối báo cáo.", long = true)
            }
        }
    }

    private fun openDeveloperOptions() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        } catch (e: Exception) {
            toast("Không mở được Tùy chọn nhà phát triển. Hãy vào Cài đặt → Cài đặt bổ sung → Tùy chọn nhà phát triển.", long = true)
        }
    }
}
