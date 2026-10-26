package com.codelab.app.ui

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.codelab.app.R
import com.codelab.app.data.ProjectRepository
import com.codelab.app.databinding.FragmentEditorBinding
import com.codelab.app.exec.ExecMode
import com.codelab.app.exec.ExecutorFactory
import com.codelab.app.model.Language
import com.codelab.app.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Màn hình soạn thảo một project.
 *
 * Bố cục từ trên xuống:
 *  - Thanh trên cùng: nút quay lại + huy hiệu ngôn ngữ + tên project.
 *  - Khung CODE (phần trên, có thể gõ và sửa).
 *  - Thanh nút ở giữa: Chạy, Build+Chạy, Build (xem lỗi), Hoàn tác, Làm lại, Lưu.
 *  - Khung KẾT QUẢ (phần dưới, hiển thị kết quả hoặc lỗi).
 *
 * Bố cục luôn CỐ ĐỊNH: khi mở bàn phím, bàn phím đè lên khung kết quả chứ không đẩy màn hình lên.
 */
class EditorFragment : Fragment() {

    private var _binding: FragmentEditorBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: ProjectRepository
    private lateinit var project: Project
    private lateinit var language: Language

    private val normalColor by lazy { ContextCompat.getColor(requireContext(), R.color.editor_text) }
    private val errorColor by lazy { ContextCompat.getColor(requireContext(), R.color.danger) }

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
        binding.langBadge.styleAsBadge(language)
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
        binding.btnClear.setOnClickListener { showPlaceholder() }

        showPlaceholder()
        applyInsets()
    }

    /**
     * App vẽ tràn cả màn hình, nên tự xử lý phần bị che:
     *  - Thanh trạng thái (trên cùng) và thanh điều hướng (dưới cùng): chừa khoảng trống.
     *  - Bàn phím: KHÔNG thu nhỏ màn hình. Bàn phím đè lên khung kết quả; chỉ riêng ô code
     *    được chừa thêm khoảng trống phía dưới nếu bàn phím che mất một phần, để dòng đang gõ
     *    vẫn nhìn thấy được.
     */
    private fun applyInsets() {
        val topBarTop = binding.topBar.paddingTop
        val outputBottom = binding.outputScroll.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            binding.topBar.updatePadding(top = topBarTop + bars.top)
            binding.outputScroll.updatePadding(bottom = outputBottom + bars.bottom)
            updateKeyboardOverlap(ime.bottom)
            insets
        }
        binding.root.requestInsetsWhenAttached()
    }

    /**
     * Tính phần ô code bị bàn phím che và báo cho ô code chừa chỗ.
     * Thanh nút nếu chỉ bị che một nửa thì ẩn tạm (tránh lộ nửa biểu tượng), đóng bàn phím lại hiện.
     */
    private fun updateKeyboardOverlap(imeHeight: Int) {
        val editor = binding.editor
        editor.post {
            if (_binding == null) return@post
            val toolbar = binding.toolbar
            if (imeHeight <= 0) {
                editor.setKeyboardOverlap(0)
                toolbar.visibility = View.VISIBLE
                return@post
            }
            val keyboardTop = editor.rootView.height - imeHeight
            editor.setKeyboardOverlap(bottomInWindow(editor) - keyboardTop)

            val toolbarTop = bottomInWindow(toolbar) - toolbar.height
            val partlyCovered = toolbarTop < keyboardTop && bottomInWindow(toolbar) > keyboardTop
            toolbar.visibility = if (partlyCovered) View.INVISIBLE else View.VISIBLE
        }
    }

    private fun bottomInWindow(view: View): Int {
        val location = IntArray(2)
        view.getLocationInWindow(location)
        return location[1] + view.height
    }

    private fun updateUndoRedoButtons() {
        binding.btnUndo.isEnabled = binding.editor.canUndo()
        binding.btnRedo.isEnabled = binding.editor.canRedo()
        binding.btnUndo.alpha = if (binding.editor.canUndo()) 1f else 0.4f
        binding.btnRedo.alpha = if (binding.editor.canRedo()) 1f else 0.4f
    }

    private fun showPlaceholder() {
        binding.output.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_hint))
        binding.output.text = "Bấm ▶ Chạy để chạy code. Kết quả sẽ hiện ở đây."
        setStatus("", R.color.text_secondary)
    }

    private fun setStatus(text: String, colorRes: Int) {
        binding.textStatus.text = text
        binding.textStatus.setTextColor(ContextCompat.getColor(requireContext(), colorRes))
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
        binding.output.text = if (mode == ExecMode.BUILD_ONLY) "Đang kiểm tra lỗi..." else "Đang chạy..."
        setStatus(if (language.runsOffline) "Đang chạy trên máy…" else "Đang gửi lên máy chủ dịch…", R.color.text_secondary)

        // Lấy context trước khi sang luồng nền (tránh lỗi nếu màn hình bị đóng giữa chừng)
        val appContext = requireContext().applicationContext
        val startedAt = SystemClock.elapsedRealtime()
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                ExecutorFactory.create(appContext, language).execute(code, mode)
            }
            // Có thể màn hình đã bị đóng trong lúc chờ
            if (_binding == null) return@launch
            val seconds = (SystemClock.elapsedRealtime() - startedAt) / 1000.0
            val time = String.format(Locale("vi", "VN"), "%.2f giây", seconds)

            binding.output.text = result.output
            binding.output.setTextColor(if (result.isError) errorColor else normalColor)
            binding.outputScroll.scrollTo(0, 0)
            when {
                result.requiresInternet -> setStatus("● Không có mạng", R.color.warning)
                result.isError -> setStatus("✕ Có lỗi · $time", R.color.danger)
                else -> setStatus("✓ Hoàn thành · $time", R.color.success)
            }
            setRunning(false)
        }
    }

    /** Bật/tắt trạng thái đang chạy (hiện vòng xoay, khóa nút). */
    private fun setRunning(running: Boolean) {
        binding.progress.visibility = if (running) View.VISIBLE else View.GONE
        listOf(binding.btnRun, binding.btnBuildRun, binding.btnBuild).forEach {
            it.isEnabled = !running
            it.alpha = if (running) 0.5f else 1f
        }
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
