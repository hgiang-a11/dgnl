package com.codelab.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.recyclerview.widget.GridLayoutManager
import com.codelab.app.MainActivity
import com.codelab.app.data.ProjectRepository
import com.codelab.app.databinding.FragmentHomeBinding
import com.codelab.app.model.Project
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Màn hình chính: hiển thị tất cả project dưới dạng lưới 2 ô mỗi hàng,
 * và nút dấu cộng (+) để tạo project mới.
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

        // Nút dấu cộng để tạo project mới
        binding.fabAdd.setOnClickListener {
            NewProjectDialog().show(parentFragmentManager, "new_project")
        }

        // Lắng nghe khi hộp thoại tạo project báo đã tạo xong
        setFragmentResultListener(NewProjectDialog.RESULT_KEY) { _, bundle ->
            val id = bundle.getString(NewProjectDialog.ARG_PROJECT_ID) ?: return@setFragmentResultListener
            refresh()
            (activity as? MainActivity)?.openEditor(id)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    /** Đọc lại danh sách project và cập nhật giao diện. */
    private fun refresh() {
        val projects = repository.getAll()
        adapter.submit(projects)
        binding.emptyView.visibility = if (projects.isEmpty()) View.VISIBLE else View.GONE
    }

    /** Hỏi xác nhận trước khi xóa project. */
    private fun confirmDelete(project: Project) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Xóa project?")
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
