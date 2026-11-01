package com.floattile

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.Button
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.slider.Slider
import java.util.Date

/**
 * Màn hình cài đặt chính:
 *  - chọn ứng dụng đích
 *  - chỉnh kích thước, vị trí cửa sổ (có hình xem trước)
 *  - chọn cách ép cửa sổ tự do
 *  - nút kiểm tra tính năng cửa sổ tự do và hướng dẫn Shizuku
 */
class MainActivity : AppCompatActivity() {

    private lateinit var targetIcon: ImageView
    private lateinit var targetName: TextView
    private lateinit var targetPackage: TextView
    private lateinit var preview: BoundsPreviewView
    private lateinit var sliderWidth: Slider
    private lateinit var sliderHeight: Slider
    private lateinit var sliderPosX: Slider
    private lateinit var sliderPosY: Slider
    private lateinit var labelWidth: TextView
    private lateinit var labelHeight: TextView
    private lateinit var labelPosX: TextView
    private lateinit var labelPosY: TextView
    private lateinit var boundsText: TextView
    private lateinit var modeGroup: RadioGroup
    private lateinit var probeStatus: TextView
    private lateinit var shizukuStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        targetIcon = findViewById(R.id.target_icon)
        targetName = findViewById(R.id.target_name)
        targetPackage = findViewById(R.id.target_package)
        preview = findViewById(R.id.preview)
        sliderWidth = findViewById(R.id.slider_width)
        sliderHeight = findViewById(R.id.slider_height)
        sliderPosX = findViewById(R.id.slider_pos_x)
        sliderPosY = findViewById(R.id.slider_pos_y)
        labelWidth = findViewById(R.id.label_width)
        labelHeight = findViewById(R.id.label_height)
        labelPosX = findViewById(R.id.label_pos_x)
        labelPosY = findViewById(R.id.label_pos_y)
        boundsText = findViewById(R.id.bounds_text)
        modeGroup = findViewById(R.id.mode_group)
        probeStatus = findViewById(R.id.probe_status)
        shizukuStatus = findViewById(R.id.shizuku_status)

        findViewById<Button>(R.id.btn_pick_app).setOnClickListener {
            startActivity(Intent(this, AppPickerActivity::class.java))
        }
        findViewById<Button>(R.id.btn_reset_bounds).setOnClickListener {
            Prefs.resetBounds()
            loadSliders()
            refreshBounds()
        }
        findViewById<Button>(R.id.btn_try_now).setOnClickListener {
            // Chạy đúng quy trình như khi bấm ô cài đặt nhanh.
            startActivity(Intent(this, LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        findViewById<Button>(R.id.btn_diagnose).setOnClickListener {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }
        findViewById<Button>(R.id.btn_shizuku).setOnClickListener {
            startActivity(Intent(this, ShizukuGuideActivity::class.java))
        }
        findViewById<Button>(R.id.btn_add_tile).setOnClickListener { requestAddTile() }
        findViewById<TextView>(R.id.version_text).text =
            getString(R.string.version_format, BuildConfig.VERSION_NAME)

        setupSliders()
        setupModeGroup()
        autoDetectClaude()
    }

    override fun onResume() {
        super.onResume()
        refreshTarget()
        loadSliders()
        refreshBounds()
        refreshStatus()
    }

    // ----- Ứng dụng đích -----

    /** Nếu chưa cài đúng gói mặc định, thử tự tìm ứng dụng có chữ "claude". */
    private fun autoDetectClaude() {
        if (TargetApp.resolve(this) != null) return
        if (Prefs.targetPackage != Prefs.DEFAULT_PACKAGE) return
        val candidates = TargetApp.findClaudeCandidates(this)
        if (candidates.size == 1) {
            Prefs.targetPackage = candidates[0].first
            Prefs.targetActivity = null
            toast("Đã tự chọn ${candidates[0].second} (${candidates[0].first})", long = true)
        }
    }

    private fun refreshTarget() {
        val target = TargetApp.resolve(this)
        if (target != null) {
            targetName.text = target.label
            targetPackage.text = target.component.flattenToShortString()
            targetIcon.setImageDrawable(target.icon)
        } else {
            targetName.text = getString(R.string.target_missing)
            targetPackage.text = getString(R.string.target_missing_detail, Prefs.targetPackage)
            targetIcon.setImageResource(R.drawable.ic_tile)
        }
    }

    // ----- Kích thước & vị trí -----

    private fun setupSliders() {
        sliderWidth.setLabelFormatter { "${it.toInt()}%" }
        sliderHeight.setLabelFormatter { "${it.toInt()}%" }
        sliderPosX.setLabelFormatter { "${it.toInt()}%" }
        sliderPosY.setLabelFormatter { "${it.toInt()}%" }
        sliderWidth.addOnChangeListener { _, value, fromUser ->
            if (fromUser) { Prefs.widthPct = value.toInt(); refreshBounds() }
        }
        sliderHeight.addOnChangeListener { _, value, fromUser ->
            if (fromUser) { Prefs.heightPct = value.toInt(); refreshBounds() }
        }
        sliderPosX.addOnChangeListener { _, value, fromUser ->
            if (fromUser) { Prefs.posXPct = value.toInt(); refreshBounds() }
        }
        sliderPosY.addOnChangeListener { _, value, fromUser ->
            if (fromUser) { Prefs.posYPct = value.toInt(); refreshBounds() }
        }
    }

    private fun loadSliders() {
        setSlider(sliderWidth, Prefs.widthPct)
        setSlider(sliderHeight, Prefs.heightPct)
        setSlider(sliderPosX, Prefs.posXPct)
        setSlider(sliderPosY, Prefs.posYPct)
    }

    /** Đặt giá trị thanh trượt, luôn giữ trong khoảng cho phép (tránh lỗi khi giá trị lưu bị sai). */
    private fun setSlider(slider: Slider, value: Int) {
        slider.value = value.toFloat().coerceIn(slider.valueFrom, slider.valueTo)
    }

    private fun refreshBounds() {
        labelWidth.text = getString(R.string.label_width, Prefs.widthPct)
        labelHeight.text = getString(R.string.label_height, Prefs.heightPct)
        labelPosX.text = getString(R.string.label_pos_x, Prefs.posXPct)
        labelPosY.text = getString(R.string.label_pos_y, Prefs.posYPct)
        val screen = WindowBounds.screen(this)
        val rect = WindowBounds.compute(screen, Prefs.widthPct, Prefs.heightPct, Prefs.posXPct, Prefs.posYPct)
        boundsText.text = getString(R.string.bounds_format, WindowBounds.describe(rect), screen.width, screen.height)
        preview.update(screen, rect, TargetApp.resolve(this)?.label ?: "?")
    }

    // ----- Cách mở -----

    private fun setupModeGroup() {
        val id = when (Prefs.launchMode) {
            LaunchMode.AUTO -> R.id.mode_auto
            LaunchMode.REFLECTION -> R.id.mode_reflection
            LaunchMode.SHIZUKU -> R.id.mode_shizuku
            LaunchMode.NORMAL -> R.id.mode_normal
        }
        modeGroup.check(id)
        modeGroup.setOnCheckedChangeListener { _, checkedId ->
            Prefs.launchMode = when (checkedId) {
                R.id.mode_reflection -> LaunchMode.REFLECTION
                R.id.mode_shizuku -> LaunchMode.SHIZUKU
                R.id.mode_normal -> LaunchMode.NORMAL
                else -> LaunchMode.AUTO
            }
        }
    }

    private fun refreshStatus() {
        probeStatus.text = if (Prefs.probeTime == 0L) {
            getString(R.string.probe_never)
        } else {
            val time = DateFormat.format("dd/MM HH:mm", Date(Prefs.probeTime))
            val stale = if (Prefs.isProbeCacheValid(this)) "" else "\n" + getString(R.string.probe_stale)
            getString(
                R.string.probe_summary, time,
                FreeformLauncher.stateText(Prefs.probeReflection),
                FreeformLauncher.stateText(Prefs.probeShizuku)
            ) + stale
        }
        shizukuStatus.text = ShizukuHelper.describe(ShizukuHelper.status(this))
    }

    // ----- Thêm ô vào bảng cài đặt nhanh -----

    private fun requestAddTile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            toast(getString(R.string.add_tile_manual), long = true)
            return
        }
        // Android 13+: hệ thống hiện hộp thoại "Thêm ô này?" (có thể không hỗ trợ trên một số giao diện hãng).
        val sbm = getSystemService(StatusBarManager::class.java)
        if (sbm == null) {
            toast(getString(R.string.add_tile_manual), long = true)
            return
        }
        try {
            sbm.requestAddTileService(
                ComponentName(this, FloatTileService::class.java),
                getString(R.string.tile_label),
                Icon.createWithResource(this, R.drawable.ic_tile),
                mainExecutor
            ) { result ->
                val msg = when (result) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "Đã thêm ô FloatTile."
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "Ô FloatTile đã có sẵn trong bảng cài đặt nhanh."
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> "Bạn đã chọn không thêm ô."
                    else -> getString(R.string.add_tile_manual)
                }
                toast(msg, long = true)
            }
        } catch (e: Exception) {
            toast(getString(R.string.add_tile_manual), long = true)
        }
    }
}
