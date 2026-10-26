package com.codelab.app.ui

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.codelab.app.databinding.ItemProjectBinding
import com.codelab.app.model.Project

/**
 * Bộ "adapter" nối dữ liệu danh sách project với lưới hiển thị trên màn hình chính.
 * Mỗi project hiển thị trong một ô (thẻ) có huy hiệu ngôn ngữ, tên, mô tả và thời gian sửa.
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
            projectBadge.styleAsBadge(lang)
            projectMode.styleAsModePill(lang)
            projectName.text = project.name
            projectLanguage.text = lang.displayName
            projectDescription.text = project.description.ifBlank { "Không có mô tả" }
            // Vd: "Sửa 5 phút trước" (tự theo ngôn ngữ của điện thoại)
            val now = System.currentTimeMillis()
            projectTime.text = if (now - project.updatedAt < DateUtils.MINUTE_IN_MILLIS) {
                "Sửa vừa xong"
            } else {
                "Sửa " + DateUtils.getRelativeTimeSpanString(
                    project.updatedAt, now, DateUtils.MINUTE_IN_MILLIS
                ).toString().lowercase()
            }
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
