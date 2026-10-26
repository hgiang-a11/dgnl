package com.codelab.app.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.codelab.app.data.ProjectRepository
import com.codelab.app.databinding.FragmentEditorBinding
import com.codelab.app.exec.ExecMode
import com.codelab.app.exec.ExecutorFactory
import com.codelab.app.model.Language
import com.codelab.app.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Màn hình soạn thảo một project.
 *
 * Bố cục từ trên xuống:
 *  - Thanh trên cùng: nút quay lại + tên project.
 *  - Khung CODE (phần trên, có thể gõ và sửa).
 *  - Thanh nút ở giữa: Chạy, Build+Chạy, Build (xem lỗi), Hoàn tác, Làm lại, Lưu.
 *  - Khung KẾT QUẢ (phần dưới, hiển thị kết quả hoặc lỗi).
 */
class EditorFragment : Fragment() {

    private var _binding: FragmentEditorBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: ProjectRepository
    private lateinit var project: Project
    private lateinit var language: Language

    private val normalColor = Color.parseColor("#D4D4D4")
    private val errorColor = Color.parseColor("#F48771")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        repository = ProjectRepository.get(requireContext())

        val projectId = arguments?.getString(ARG_PROJECT_ID)
        val loaded = projectId?.let { repository.get(it) }
        if (loaded == null) {
            // Project không còn tồn tại -> quay về màn hình chính
            parentFragmentManager.popBackStack()
            return
        }
        project = loaded
        language = project.language

        // Tiêu đề
        binding.textTitle.text = project.name
        binding.textSubtitle.text = if (language.runsOffline) {
            "${language.displayName} · chạy offline"
        } else {
            "${language.displayName} · cần mạng để chạy"
        }

        // Nạp code vào ô soạn thảo
        binding.editor.setLanguage(language)
        binding.editor.setCode(project.code)
        binding.editor.onHistoryChanged = { updateUndoRedoButtons() }
        updateUndoRedoButtons()

        // Gán hành động cho các nút
        binding.btnBack.setOnClickListener { saveCode(); parentFragmentManager.popBackStack() }
        binding.btnRun.setOnClickListener { run(ExecMode.RUN) }
        binding.btnBuildRun.setOnClickListener { run(ExecMode.BUILD_AND_RUN) }
        binding.btnBuild.setOnClickListener { run(ExecMode.BUILD_ONLY) }
        binding.btnUndo.setOnClickListener { binding.editor.undo() }
        binding.btnRedo.setOnClickListener { binding.editor.redo() }
        binding.btnSave.setOnClickListener {
            saveCode()
            Toast.makeText(requireContext(), "Đã lưu", Toast.LENGTH_SHORT).show()
        }

        binding.output.setTextColor(normalColor)
        binding.output.text = "Bấm ▶ để chạy code. Kết quả sẽ hiện ở đây."
    }

    private fun updateUndoRedoButtons() {
        binding.btnUndo.isEnabled = binding.editor.canUndo()
        binding.btnRedo.isEnabled = binding.editor.canRedo()
        binding.btnUndo.alpha = if (binding.editor.canUndo()) 1f else 0.4f
        binding.btnRedo.alpha = if (binding.editor.canRedo()) 1f else 0.4f
    }

    /** Lưu code hiện tại vào bộ nhớ máy. */
    private fun saveCode() {
        // Bỏ qua nếu màn hình đã đóng hoặc project chưa nạp được (vd: đã bị xóa)
        if (_binding == null || !::project.isInitialized) return
        repository.saveCode(project.id, binding.editor.text?.toString().orEmpty())
    }

    /** Chạy hoặc dịch code theo chế độ tương ứng với nút được bấm. */
    private fun run(mode: ExecMode) {
        val code = binding.editor.text?.toString().orEmpty()
        saveCode()

        setRunning(true)
        binding.output.setTextColor(normalColor)
        binding.output.text = when (mode) {
            ExecMode.BUILD_ONLY -> "Đang kiểm tra lỗi..."
            else -> "Đang chạy..."
        }

        // Lấy context trước khi sang luồng nền (tránh lỗi nếu màn hình bị đóng giữa chừng)
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                ExecutorFactory.create(appContext, language).execute(code, mode)
            }
            // Có thể màn hình đã bị đóng trong lúc chờ
            if (_binding == null) return@launch
            binding.output.text = result.output
            binding.output.setTextColor(if (result.isError) errorColor else normalColor)
            setRunning(false)
        }
    }

    /** Bật/tắt trạng thái đang chạy (hiện vòng xoay, khóa nút). */
    private fun setRunning(running: Boolean) {
        binding.progress.visibility = if (running) View.VISIBLE else View.GONE
        binding.btnRun.isEnabled = !running
        binding.btnBuildRun.isEnabled = !running
        binding.btnBuild.isEnabled = !running
    }

    override fun onPause() {
        super.onPause()
        // Tự động lưu để không mất code khi rời màn hình
        saveCode()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_PROJECT_ID = "project_id"

        fun newInstance(projectId: String): EditorFragment {
            return EditorFragment().apply {
                arguments = Bundle().apply { putString(ARG_PROJECT_ID, projectId) }
            }
        }
    }
}
