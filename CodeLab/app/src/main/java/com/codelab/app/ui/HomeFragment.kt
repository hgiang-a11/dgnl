package com.codelab.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.codelab.app.MainActivity
import com.codelab.app.data.ProjectRepository
import com.codelab.app.databinding.FragmentHomeBinding
import com.codelab.app.model.Project
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Màn hình chính: hiển thị tất cả project dưới dạng lưới 2 ô mỗi hàng,
 * và nút "Dự án mới" để tạo project mới.
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: ProjectRepository
    private lateinit var adapter: ProjectAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        repository = ProjectRepository.get(requireContext())

        adapter = ProjectAdapter(
            items = emptyList(),
            onClick = { project -> (activity as? MainActivity)?.openEditor(project.id) },
            onLongClick = { project -> confirmDelete(project) }
        )

        // Lưới 2 ô mỗi hàng
        binding.recyclerProjects.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.recyclerProjects.adapter = adapter

        // Nút "Dự án mới": thu gọn thành dấu + khi cuộn xuống, mở rộng lại khi cuộn lên
        binding.fabAdd.setOnClickListener {
            NewProjectDialog().show(parentFragmentManager, NewProjectDialog.TAG)
        }
        binding.recyclerProjects.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 8) binding.fabAdd.shrink() else if (dy < -8) binding.fabAdd.extend()
            }
        })

        applySystemBarInsets()

        // Lắng nghe khi hộp thoại tạo project báo đã tạo xong
        setFragmentResultListener(NewProjectDialog.RESULT_KEY) { _, bundle ->
            val id = bundle.getString(NewProjectDialog.ARG_PROJECT_ID) ?: return@setFragmentResultListener
            refresh()
            (activity as? MainActivity)?.openEditor(id)
        }
    }

    /**
     * App vẽ tràn cả màn hình (sau thanh trạng thái và thanh điều hướng),
     * nên cần chừa khoảng trống để nội dung không bị che.
     */
    private fun applySystemBarInsets() {
        val headerTop = binding.header.paddingTop
        val listBottom = binding.recyclerProjects.paddingBottom
        val fabMargin = (binding.fabAdd.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.header.updatePadding(top = headerTop + bars.top)
            binding.recyclerProjects.updatePadding(bottom = listBottom + bars.bottom)
            binding.fabAdd.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = fabMargin + bars.bottom
            }
            insets
        }
        binding.root.requestInsetsWhenAttached()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    /** Đọc lại danh sách project và cập nhật giao diện. */
    private fun refresh() {
        val projects = repository.getAll()
        adapter.submit(projects)
        binding.textCount.text = "${projects.size} dự án"
        binding.emptyView.visibility = if (projects.isEmpty()) View.VISIBLE else View.GONE
    }

    /** Hỏi xác nhận trước khi xóa project. */
    private fun confirmDelete(project: Project) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Xóa dự án?")
            .setMessage("Bạn có chắc muốn xóa \"${project.name}\"? Thao tác này không thể hoàn tác.")
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Xóa") { _, _ ->
                repository.delete(project.id)
                refresh()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
