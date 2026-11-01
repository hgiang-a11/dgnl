package com.floattile

import android.app.StatusBarManager
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText

/**
 * Màn hình tạo/sửa một ô: tên, ứng dụng, kích thước và vị trí cửa sổ.
 * Mọi thay đổi chỉ là "bản nháp" cho tới khi bấm Lưu.
 */
class TileEditActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SLOT = "com.floattile.EDIT_SLOT"
    }

    private var slot = 1
    private var isNew = false

    // ----- Bản nháp (chưa lưu) -----
    private var draftPackage: String? = null
    private var draftActivity: String? = null
    private var draftW = TileStore.DEFAULT_WIDTH
    private var draftH = TileStore.DEFAULT_HEIGHT
    private var draftX = TileStore.DEFAULT_POS
    private var draftY = TileStore.DEFAULT_POS
    private var dirty = false
    private var loading = false

    private lateinit var nameInput: TextInputEditText
    private lateinit var appIcon: ImageView
    private lateinit var appName: TextView
    private lateinit var appPackage: TextView
    private lateinit var preview: BoundsPreviewView
    private lateinit var sliderWidth: Slider
    private lateinit var sliderHeight: Slider
    private lateinit var sliderPosX: Slider
    private lateinit var sliderPosY: Slider
    private lateinit var sliderScale: Slider
    private lateinit var labelWidth: TextView
    private lateinit var labelHeight: TextView
    private lateinit var labelPosX: TextView
    private lateinit var labelPosY: TextView
    private lateinit var labelScale: TextView
    private lateinit var boundsText: TextView

    /** Nhận kết quả từ màn hình chọn ứng dụng. */
    private val pickApp = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode != RESULT_OK || data == null) {
            // Ô mới mà chưa chọn ứng dụng nào → thoát luôn, không tạo ô rỗng.
            if (isNew && draftPackage == null) finish()
            return@registerForActivityResult
        }
        val pkg = data.getStringExtra(AppPickerActivity.EXTRA_PACKAGE) ?: return@registerForActivityResult
        draftPackage = pkg
        draftActivity = data.getStringExtra(AppPickerActivity.EXTRA_ACTIVITY)
        // Chưa đặt tên thì lấy luôn tên ứng dụng làm tên ô.
        if (nameInput.text.isNullOrBlank()) {
            nameInput.setText(data.getStringExtra(AppPickerActivity.EXTRA_LABEL))
        }
        dirty = true
        refreshApp()
        refreshBounds()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tile_edit)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Bước 1: xác định đang sửa ô số mấy (hoặc lấy chỗ trống đầu tiên nếu tạo mới).
        val requested = intent.getIntExtra(EXTRA_SLOT, -1)
        val chosen = if (requested in 1..TileStore.MAX_SLOTS) requested else TileStore.firstFreeSlot()
        if (chosen == null) {
            toast(getString(R.string.no_free_slot), long = true)
            finish()
            return
        }
        slot = chosen
        val cfg = TileStore.get(slot)
        isNew = cfg == null
        title = getString(if (isNew) R.string.title_new_tile else R.string.title_edit_tile, slot)

        bindViews()

        // Bước 2: nạp dữ liệu đã lưu vào bản nháp.
        loading = true
        if (cfg != null) {
            nameInput.setText(cfg.name)
            draftPackage = cfg.packageName
            draftActivity = cfg.activityName
            draftW = cfg.widthPct
            draftH = cfg.heightPct
            draftX = cfg.posXPct
            draftY = cfg.posYPct
        }
        loadSliders()
        loading = false

        findViewById<TextView>(R.id.slot_hint).text = getString(R.string.slot_hint, slot)
        findViewById<Button>(R.id.btn_delete).visibility = if (isNew) View.GONE else View.VISIBLE

        setupListeners()
        refreshApp()
        refreshBounds()

        // Hỏi trước khi thoát nếu còn thay đổi chưa lưu.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = confirmLeave()
        })

        // Ô mới: mở luôn danh sách chọn ứng dụng.
        if (isNew && savedInstanceState == null) openPicker()
    }

    override fun onSupportNavigateUp(): Boolean {
        confirmLeave()
        return true
    }

    private fun bindViews() {
        nameInput = findViewById(R.id.name_input)
        appIcon = findViewById(R.id.app_icon)
        appName = findViewById(R.id.app_name)
        appPackage = findViewById(R.id.app_package)
        preview = findViewById(R.id.preview)
        sliderWidth = findViewById(R.id.slider_width)
        sliderHeight = findViewById(R.id.slider_height)
        sliderPosX = findViewById(R.id.slider_pos_x)
        sliderPosY = findViewById(R.id.slider_pos_y)
        sliderScale = findViewById(R.id.slider_scale)
        labelWidth = findViewById(R.id.label_width)
        labelHeight = findViewById(R.id.label_height)
        labelPosX = findViewById(R.id.label_pos_x)
        labelPosY = findViewById(R.id.label_pos_y)
        labelScale = findViewById(R.id.label_scale)
        boundsText = findViewById(R.id.bounds_text)
    }

    private fun setupListeners() {
        nameInput.doAfterTextChanged {
            if (!loading) {
                dirty = true
                refreshBounds() // cập nhật tên hiện trong hình xem trước
            }
        }

        for (s in listOf(sliderWidth, sliderHeight, sliderPosX, sliderPosY, sliderScale)) {
            s.setLabelFormatter { "${it.toInt()}%" }
        }
        sliderWidth.addOnChangeListener { _, v, fromUser -> if (fromUser) { draftW = v.toInt(); changed() } }
        sliderHeight.addOnChangeListener { _, v, fromUser -> if (fromUser) { draftH = v.toInt(); changed() } }
        sliderPosX.addOnChangeListener { _, v, fromUser -> if (fromUser) { draftX = v.toInt(); changed() } }
        sliderPosY.addOnChangeListener { _, v, fromUser -> if (fromUser) { draftY = v.toInt(); changed() } }
        // Hệ số bù HyperOS là cài đặt chung cho mọi ô nên lưu ngay.
        sliderScale.addOnChangeListener { _, v, fromUser ->
            if (fromUser) { Prefs.hyperScalePct = v.toInt(); refreshBounds() }
        }

        findViewById<Button>(R.id.btn_pick_app).setOnClickListener { openPicker() }
        findViewById<Button>(R.id.btn_reset_bounds).setOnClickListener {
            draftW = TileStore.DEFAULT_WIDTH
            draftH = TileStore.DEFAULT_HEIGHT
            draftX = TileStore.DEFAULT_POS
            draftY = TileStore.DEFAULT_POS
            loadSliders()
            changed()
        }
        findViewById<Button>(R.id.btn_try_now).setOnClickListener { tryNow() }
        findViewById<Button>(R.id.btn_save).setOnClickListener { if (save()) finish() }
        findViewById<Button>(R.id.btn_add_tile).setOnClickListener {
            if (save()) requestAddTile()
        }
        findViewById<Button>(R.id.btn_delete).setOnClickListener { confirmDelete() }
    }

    private fun changed() {
        dirty = true
        refreshBounds()
    }

    private fun openPicker() {
        pickApp.launch(
            Intent(this, AppPickerActivity::class.java)
                .putExtra(AppPickerActivity.EXTRA_CURRENT_PACKAGE, draftPackage)
                .putExtra(AppPickerActivity.EXTRA_CURRENT_ACTIVITY, draftActivity)
        )
    }

    // ----- Hiển thị -----

    private fun refreshApp() {
        val pkg = draftPackage
        val target = pkg?.let { TargetApp.resolve(this, it, draftActivity) }
        when {
            pkg == null -> {
                appName.text = getString(R.string.app_not_chosen)
                appPackage.text = ""
                appIcon.setImageResource(R.drawable.ic_tile)
            }
            target == null -> {
                appName.text = getString(R.string.target_missing)
                appPackage.text = pkg
                appIcon.setImageResource(R.drawable.ic_tile)
            }
            else -> {
                appName.text = target.label
                appPackage.text = target.component.flattenToShortString()
                appIcon.setImageDrawable(target.icon)
            }
        }
    }

    private fun loadSliders() {
        setSlider(sliderWidth, draftW)
        setSlider(sliderHeight, draftH)
        setSlider(sliderPosX, draftX)
        setSlider(sliderPosY, draftY)
        setSlider(sliderScale, Prefs.hyperScalePct)
    }

    /** Đặt giá trị thanh trượt, luôn giữ trong khoảng cho phép. */
    private fun setSlider(slider: Slider, value: Int) {
        slider.value = value.toFloat().coerceIn(slider.valueFrom, slider.valueTo)
    }

    private fun refreshBounds() {
        labelWidth.text = getString(R.string.label_width, draftW)
        labelHeight.text = getString(R.string.label_height, draftH)
        labelPosX.text = getString(R.string.label_pos_x, draftX)
        labelPosY.text = getString(R.string.label_pos_y, draftY)
        val scale = Prefs.hyperScalePct
        labelScale.text = getString(R.string.label_scale, scale)

        val screen = WindowBounds.screen(this)
        val visual = WindowBounds.compute(screen, draftW, draftH, draftX, draftY)
        val system = WindowBounds.systemRect(visual, scale)
        boundsText.text = getString(
            R.string.bounds_format,
            WindowBounds.describe(visual), WindowBounds.describe(system), screen.width, screen.height
        )
        val label = nameInput.text?.toString()?.trim().orEmpty().ifEmpty { appName.text.toString() }
        preview.update(screen, visual, label)
    }

    // ----- Lưu, mở thử, xóa -----

    private fun draftConfig(forSlot: Int): TileConfig? {
        val pkg = draftPackage ?: return null
        val appLabel = TargetApp.resolve(this, pkg, draftActivity)?.label ?: pkg
        val name = nameInput.text?.toString()?.trim().orEmpty().ifEmpty { appLabel }
        return TileConfig(forSlot, name.take(40), pkg, draftActivity, draftW, draftH, draftX, draftY)
    }

    /** Lưu bản nháp. Trả về false nếu chưa đủ thông tin. */
    private fun save(): Boolean {
        val cfg = draftConfig(slot)
        if (cfg == null) {
            toast(getString(R.string.need_app))
            return false
        }
        TileStore.save(this, cfg)
        if (nameInput.text.isNullOrBlank()) nameInput.setText(cfg.name)
        dirty = false
        if (isNew) {
            isNew = false
            title = getString(R.string.title_edit_tile, slot)
            findViewById<Button>(R.id.btn_delete).visibility = View.VISIBLE
        }
        toast(getString(R.string.saved_tile, cfg.name, slot))
        return true
    }

    /** Mở thử bằng bản nháp (lưu tạm vào chỗ số 0, không ảnh hưởng ô thật). */
    private fun tryNow() {
        val cfg = draftConfig(TileStore.PREVIEW_SLOT)
        if (cfg == null) {
            toast(getString(R.string.need_app))
            return
        }
        TileStore.save(this, cfg)
        startActivity(
            Intent(this, LaunchActivity::class.java)
                .putExtra(LaunchActivity.EXTRA_SLOT, TileStore.PREVIEW_SLOT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_title, slot))
            .setMessage(R.string.delete_message)
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                TileStore.delete(this, slot)
                toast(getString(R.string.deleted_tile, slot))
                finish()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun confirmLeave() {
        if (!dirty) {
            finish()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.unsaved_title)
            .setMessage(R.string.unsaved_message)
            .setPositiveButton(R.string.btn_save) { _, _ -> if (save()) finish() }
            .setNegativeButton(R.string.btn_discard) { _, _ -> finish() }
            .setNeutralButton(R.string.btn_cancel, null)
            .show()
    }

    // ----- Thêm vào bảng cài đặt nhanh -----

    private fun requestAddTile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            showManualAdd()
            return
        }
        // Android 13+: hệ thống hiện hộp thoại "Thêm ô này?" (một số giao diện hãng không hỗ trợ).
        val sbm = getSystemService(StatusBarManager::class.java)
        if (sbm == null) {
            showManualAdd()
            return
        }
        val name = TileStore.get(slot)?.name ?: getString(R.string.tile_unconfigured, slot)
        try {
            sbm.requestAddTileService(
                TileStore.component(this, slot),
                name,
                Icon.createWithResource(this, R.drawable.ic_tile),
                mainExecutor
            ) { result ->
                when (result) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> toast("Đã thêm ô “$name”.")
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> toast("Ô “$name” đã có sẵn trong bảng cài đặt nhanh.")
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> toast("Bạn đã chọn không thêm ô.")
                    else -> showManualAdd()
                }
            }
        } catch (e: Exception) {
            showManualAdd()
        }
    }

    private fun showManualAdd() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.manual_add_title)
            .setMessage(getString(R.string.manual_add_message, slot))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
