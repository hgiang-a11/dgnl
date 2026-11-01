package com.floattile

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Một ứng dụng trong danh sách chọn. */
data class AppEntry(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: Drawable
) {
    val isClaudeLike: Boolean
        get() = packageName.contains("claude", true) || label.contains("claude", true)
}

/**
 * Màn hình chọn ứng dụng đích: liệt kê mọi ứng dụng có biểu tượng ngoài màn hình chính.
 * Ứng dụng có chữ "claude" được đưa lên đầu.
 */
class AppPickerActivity : AppCompatActivity() {

    private val adapter = AppAdapter { choose(it) }
    private var allApps: List<AppEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_picker)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val list = findViewById<RecyclerView>(R.id.app_list)
        val search = findViewById<EditText>(R.id.search)
        val progress = findViewById<ProgressBar>(R.id.progress)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        search.doAfterTextChanged { applyFilter(it?.toString().orEmpty()) }

        // Đọc danh sách ứng dụng ở luồng nền (background thread) để giao diện không bị đứng.
        lifecycleScope.launch {
            allApps = withContext(Dispatchers.IO) { loadApps() }
            progress.visibility = View.GONE
            applyFilter(search.text?.toString().orEmpty())
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    @Suppress("DEPRECATION")
    private fun loadApps(): List<AppEntry> {
        val pm = packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map {
                AppEntry(
                    label = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName,
                    activityName = it.activityInfo.name,
                    icon = it.loadIcon(pm)
                )
            }
            .sortedWith(compareBy<AppEntry>({ !it.isClaudeLike }, { it.label.lowercase() }))
    }

    private fun applyFilter(query: String) {
        val q = query.trim()
        val filtered = if (q.isEmpty()) allApps else allApps.filter {
            it.label.contains(q, true) || it.packageName.contains(q, true)
        }
        adapter.submit(filtered, Prefs.targetPackage, Prefs.targetActivity)
    }

    private fun choose(entry: AppEntry) {
        Prefs.targetPackage = entry.packageName
        Prefs.targetActivity = entry.activityName
        toast(getString(R.string.picked_app, entry.label))
        finish()
    }

    // ----- Bộ chuyển dữ liệu cho danh sách (Adapter = cầu nối giữa dữ liệu và từng dòng hiển thị) -----

    private class AppAdapter(val onClick: (AppEntry) -> Unit) : RecyclerView.Adapter<AppAdapter.Holder>() {
        private var items: List<AppEntry> = emptyList()
        private var selectedPackage: String? = null
        private var selectedActivity: String? = null

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.app_icon)
            val name: TextView = view.findViewById(R.id.app_name)
            val pkg: TextView = view.findViewById(R.id.app_package)
            val check: TextView = view.findViewById(R.id.app_check)
        }

        @SuppressLint("NotifyDataSetChanged")
        fun submit(list: List<AppEntry>, selPkg: String?, selActivity: String?) {
            items = list
            selectedPackage = selPkg
            selectedActivity = selActivity
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false))

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val e = items[position]
            holder.icon.setImageDrawable(e.icon)
            holder.name.text = e.label
            holder.pkg.text = e.packageName
            val selected = e.packageName == selectedPackage &&
                (selectedActivity == null || selectedActivity == e.activityName)
            holder.check.visibility = if (selected) View.VISIBLE else View.GONE
            holder.itemView.setOnClickListener { onClick(e) }
        }
    }
}
