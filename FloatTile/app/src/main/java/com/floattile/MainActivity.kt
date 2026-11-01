package com.floattile

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import java.util.Date

/**
 * Màn hình chính:
 *  - lưới các ô đã tạo (bấm vào một ô để sửa), nút "Thêm ô"
 *  - chọn cách ép cửa sổ tự do (chung cho mọi ô)
 *  - nút kiểm tra tính năng cửa sổ tự do và hướng dẫn Shizuku
 */
class MainActivity : AppCompatActivity() {

    private lateinit var tileList: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var countText: TextView
    private lateinit var fab: ExtendedFloatingActionButton
    private lateinit var modeGroup: RadioGroup
    private lateinit var probeStatus: TextView
    private lateinit var shizukuStatus: TextView

    private val adapter = TileAdapter { openEditor(it.config.slot) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tileList = findViewById(R.id.tile_list)
        emptyText = findViewById(R.id.tiles_empty)
        countText = findViewById(R.id.tiles_count)
        fab = findViewById(R.id.fab_add)
        modeGroup = findViewById(R.id.mode_group)
        probeStatus = findViewById(R.id.probe_status)
        shizukuStatus = findViewById(R.id.shizuku_status)

        // Lưới 3 cột giống các ứng dụng tạo ô tắt.
        tileList.layoutManager = GridLayoutManager(this, 3)
        tileList.adapter = adapter
        tileList.isNestedScrollingEnabled = false

        fab.setOnClickListener {
            val free = TileStore.firstFreeSlot()
            if (free == null) toast(getString(R.string.no_free_slot), long = true) else openEditor(free)
        }
        findViewById<Button>(R.id.btn_diagnose).setOnClickListener {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }
        findViewById<Button>(R.id.btn_shizuku).setOnClickListener {
            startActivity(Intent(this, ShizukuGuideActivity::class.java))
        }
        findViewById<TextView>(R.id.version_text).text =
            getString(R.string.version_format, BuildConfig.VERSION_NAME)

        setupModeGroup()
        autoDetectClaude()
    }

    override fun onResume() {
        super.onResume()
        refreshTiles()
        refreshStatus()
    }

    private fun openEditor(slot: Int) {
        startActivity(Intent(this, TileEditActivity::class.java).putExtra(TileEditActivity.EXTRA_SLOT, slot))
    }

    // ----- Danh sách ô -----

    private fun refreshTiles() {
        val items = TileStore.all().map { cfg ->
            TileItem(cfg, TargetApp.resolve(this, cfg), TileStore.isAdded(cfg.slot))
        }
        adapter.submit(items)
        emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        countText.text = getString(R.string.tiles_count, items.size, TileStore.MAX_SLOTS)
        fab.isEnabled = items.size < TileStore.MAX_SLOTS
    }

    /** Ô số 1 mặc định là Claude; nếu máy dùng tên gói khác có chữ "claude" thì tự sửa lại. */
    private fun autoDetectClaude() {
        val first = TileStore.get(1) ?: return
        if (first.packageName != TileStore.DEFAULT_PACKAGE) return
        if (TargetApp.resolve(this, first) != null) return
        val candidates = TargetApp.findClaudeCandidates(this)
        if (candidates.size == 1) {
            TileStore.save(this, first.copy(packageName = candidates[0].first, activityName = null))
            toast("Đã tự chọn ${candidates[0].second} (${candidates[0].first}) cho ô số 1", long = true)
        }
    }

    // ----- Cách mở (chung cho mọi ô) -----

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

    // ----- Bộ chuyển dữ liệu cho lưới ô -----

    data class TileItem(val config: TileConfig, val target: TargetInfo?, val added: Boolean)

    private class TileAdapter(val onClick: (TileItem) -> Unit) : RecyclerView.Adapter<TileAdapter.Holder>() {
        private var items: List<TileItem> = emptyList()

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.tile_icon)
            val name: TextView = view.findViewById(R.id.tile_name)
            val info: TextView = view.findViewById(R.id.tile_info)
        }

        @SuppressLint("NotifyDataSetChanged")
        fun submit(list: List<TileItem>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_tile, parent, false))

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            val ctx = holder.itemView.context
            val icon = item.target?.icon
            if (icon != null) holder.icon.setImageDrawable(icon) else holder.icon.setImageResource(R.drawable.ic_tile)
            holder.name.text = item.config.name
            holder.info.text = when {
                item.target == null -> ctx.getString(R.string.tile_info_missing, item.config.slot)
                item.added -> ctx.getString(R.string.tile_info_added, item.config.slot)
                else -> ctx.getString(R.string.tile_info_not_added, item.config.slot)
            }
            holder.itemView.setOnClickListener { onClick(item) }
        }
    }
}
