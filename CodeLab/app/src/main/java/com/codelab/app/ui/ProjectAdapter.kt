package com.codelab.app.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.codelab.app.databinding.ItemProjectBinding
import com.codelab.app.model.Project

/**
 * Bộ "adapter" nối dữ liệu danh sách project với lưới hiển thị trên màn hình chính.
 * Mỗi project hiển thị trong một ô vuông (card).
 */
class ProjectAdapter(
    private var items: List<Project>,
    private val onClick: (Project) -> Unit,
    private val onLongClick: (Project) -> Unit
) : RecyclerView.Adapter<ProjectAdapter.VH>() {

    inner class VH(val binding: ItemProjectBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemProjectBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val project = items[position]
        val lang = project.language
        with(holder.binding) {
            projectName.text = project.name
            projectLanguage.text = lang.displayName
            projectDescription.text =
                project.description.ifBlank { "(không có mô tả)" }
            // Nhãn nhỏ cho biết chạy offline hay cần mạng
            projectMode.text = if (lang.runsOffline) "● Offline" else "● Cần mạng"
            projectMode.setTextColor(
                if (lang.runsOffline) Color.parseColor("#4CAF50")
                else Color.parseColor("#FF9800")
            )
            root.setOnClickListener { onClick(project) }
            root.setOnLongClickListener { onLongClick(project); true }
        }
    }

    /** Cập nhật danh sách và vẽ lại. */
    fun submit(newItems: List<Project>) {
        items = newItems
        notifyDataSetChanged()
    }
}
