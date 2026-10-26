package com.codelab.app.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.codelab.app.R
import com.codelab.app.data.ProjectRepository
import com.codelab.app.databinding.FragmentEditorBinding
import com.codelab.app.exec.ExecMode
import com.codelab.app.exec.ExecutorFactory
import com.codelab.app.exec.InputDetector
import com.codelab.app.exec.InputProvider
import com.codelab.app.model.Language
import com.codelab.app.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue

/**
 * Màn hình soạn thảo một project.
 *
 * Bố cục từ trên xuống:
 *  - Thanh trên cùng: nút quay lại + huy hiệu ngôn ngữ + tên project.
 *  - Khung CODE (phần trên, có thể gõ và sửa).
 *  - Thanh nút ở giữa: Chạy, Build+Chạy, Build (xem lỗi), Hoàn tác, Làm lại, Lưu.
 *  - Khung KẾT QUẢ (phần dưới) — kiêm luôn màn hình dòng lệnh: khi chương trình cần nhập
 *    (cin, input()...), ô nhập hiện ở cuối khung, gõ rồi bấm Enter để chương trình chạy tiếp.
 *
 * Bố cục luôn CỐ ĐỊNH: khi gõ code, bàn phím đè lên khung kết quả chứ không đẩy màn hình lên.
 * Khi gõ vào ô nhập của khung kết quả, khung kết quả trồi lên nằm ngay trên bàn phím.
 */
class EditorFragment : Fragment() {

    private var _binding: FragmentEditorBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: ProjectRepository
    private lateinit var project: Project
    private lateinit var language: Language

    private val normalColor by lazy { ContextCompat.getColor(requireContext(), R.color.editor_text) }
    private val errorColor by lazy { ContextCompat.getColor(requireContext(), R.color.danger) }

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Lần chạy hiện tại (tăng lên mỗi lần bấm Chạy, để bỏ qua kết quả của lần chạy cũ). */
    private var runToken = 0

    /** Ô nhập đang nối với chương trình đang chạy (null nếu chương trình không cần nhập). */
    private var terminal: TerminalInput? = null

    /** true khi chương trình đang dừng chờ người dùng gõ. */
    private var awaitingInput = false

    /** Chiều cao bàn phím hiện tại (0 nếu đang ẩn). */
    private var imeHeight = 0

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
        binding.btnClear.setOnClickListener { stopAndClear() }

        // Ô nhập ở khung kết quả: Enter / nút gửi để gửi dòng, "Kết thúc nhập" để báo hết dữ liệu
        binding.terminalInput.setOnEditorActionListener { _, actionId, event ->
            val isEnter = actionId == EditorInfo.IME_ACTION_SEND ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            if (isEnter) submitTerminalLine(endOfInput = false)
            isEnter
        }
        binding.btnSend.setOnClickListener { submitTerminalLine(endOfInput = false) }
        binding.btnEof.setOnClickListener { submitTerminalLine(endOfInput = true) }
        binding.terminalInput.setOnFocusChangeListener { _, _ -> updateKeyboardOverlap() }

        showPlaceholder()
        applyInsets()
    }

    // ===================== Bàn phím và vùng bị che =====================

    /**
     * App vẽ tràn cả màn hình, nên tự xử lý phần bị che:
     *  - Thanh trạng thái (trên cùng) và thanh điều hướng (dưới cùng): chừa khoảng trống.
     *  - Bàn phím: KHÔNG thu nhỏ màn hình (xem [updateKeyboardOverlap]).
     */
    private fun applyInsets() {
        val topBarTop = binding.topBar.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            binding.topBar.updatePadding(top = topBarTop + bars.top)
            // Khi bàn phím hiện thì nó đã che thanh điều hướng, không cần chừa thêm
            binding.outputPanel.updatePadding(bottom = if (imeHeight > 0) 0 else bars.bottom)
            updateKeyboardOverlap()
            insets
        }
        binding.root.requestInsetsWhenAttached()
    }

    /**
     * - Đang gõ CODE: bàn phím đè lên khung kết quả; ô code chừa chỗ phía dưới để dòng đang gõ
     *   vẫn nằm trên bàn phím. Thanh nút nếu chỉ bị che một nửa thì ẩn tạm.
     * - Đang gõ vào Ô NHẬP của khung kết quả: khung kết quả trồi lên nằm ngay trên bàn phím.
     */
    private fun updateKeyboardOverlap() {
        val editor = binding.editor
        editor.post {
            if (_binding == null) return@post
            val toolbar = binding.toolbar
            val panel = binding.outputPanel
            if (imeHeight <= 0) {
                editor.setKeyboardOverlap(0)
                toolbar.visibility = View.VISIBLE
                panel.translationY = 0f
                return@post
            }
            val keyboardTop = editor.rootView.height - imeHeight

            val typingInTerminal = binding.inputRow.visibility == View.VISIBLE && binding.terminalInput.hasFocus()
            if (typingInTerminal) {
                // Đáy khung kết quả (chưa tính phần đã dịch lên) đặt ngay trên bàn phím
                val panelBottom = bottomInWindow(panel) - panel.translationY.toInt()
                panel.translationY = -(panelBottom - keyboardTop).coerceAtLeast(0).toFloat()
                editor.setKeyboardOverlap(0)
            } else {
                panel.translationY = 0f
                editor.setKeyboardOverlap(bottomInWindow(editor) - keyboardTop)
            }

            val toolbarTop = bottomInWindow(toolbar) - toolbar.height
            val partlyCovered = toolbarTop < keyboardTop && bottomInWindow(toolbar) > keyboardTop
            toolbar.visibility = if (partlyCovered && !typingInTerminal) View.INVISIBLE else View.VISIBLE
        }
    }

    private fun bottomInWindow(view: View): Int {
        val location = IntArray(2)
        view.getLocationInWindow(location)
        return location[1] + view.height
    }

    private fun showKeyboard(view: View) {
        view.requestFocus()
        WindowCompat.getInsetsController(requireActivity().window, view).show(WindowInsetsCompat.Type.ime())
    }

    private fun hideKeyboard(view: View) {
        WindowCompat.getInsetsController(requireActivity().window, view).hide(WindowInsetsCompat.Type.ime())
    }

    // ===================== Chạy code =====================

    /** Chạy hoặc dịch code theo chế độ tương ứng với nút được bấm. */
    private fun run(mode: ExecMode) {
        val code = binding.editor.text?.toString().orEmpty()
        saveCode()

        // Dừng lần chạy trước (nếu đang chờ nhập)
        terminal?.cancel()
        val needsInput = mode != ExecMode.BUILD_ONLY && InputDetector.needsInput(language, code)
        val term = if (needsInput) TerminalInput() else null
        terminal = term
        val token = ++runToken
        hideInputRow()

        setRunning(true)
        binding.output.setTextColor(normalColor)
        binding.output.text = if (mode == ExecMode.BUILD_ONLY) "Đang kiểm tra lỗi..." else "Đang chạy..."
        setStatus(if (language.runsOffline) "Đang chạy trên máy…" else "Đang gửi lên máy chủ dịch…", R.color.text_secondary)

        // Lấy context trước khi sang luồng nền (tránh lỗi nếu màn hình bị đóng giữa chừng)
        val appContext = requireContext().applicationContext
        val startedAt = SystemClock.elapsedRealtime()
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                ExecutorFactory.create(appContext, language).execute(code, mode, term)
            }
            // Màn hình đã đóng, hoặc người dùng đã bấm chạy lần khác -> bỏ qua kết quả này
            if (_binding == null || token != runToken) return@launch
            terminal = null
            hideInputRow()

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

    /**
     * Chương trình đang dừng chờ nhập: hiện phần kết quả đã có (kết thúc bằng câu hỏi)
     * và ô nhập ở cuối khung kết quả, mở bàn phím.
     */
    private fun showWaitingForInput(term: TerminalInput, outputSoFar: String) {
        if (_binding == null || terminal !== term) return
        awaitingInput = true
        setRunning(false)
        setStatus("⌨ Đang chờ nhập dữ liệu…", R.color.warning)
        binding.output.setTextColor(normalColor)
        binding.output.text = outputSoFar
        binding.inputRow.visibility = View.VISIBLE
        showKeyboard(binding.terminalInput)
        scrollOutputToBottom()
        updateKeyboardOverlap()
    }

    /** Gửi dòng vừa gõ cho chương trình (hoặc báo hết dữ liệu nếu endOfInput). */
    private fun submitTerminalLine(endOfInput: Boolean) {
        val term = terminal ?: return
        if (!awaitingInput) return
        awaitingInput = false
        val text = binding.terminalInput.text?.toString().orEmpty()
        binding.terminalInput.setText("")
        if (!endOfInput) {
            // Hiện ngay giá trị vừa gõ sau câu hỏi, giống màn hình dòng lệnh
            binding.output.append(text + "\n")
            scrollOutputToBottom()
        }
        setRunning(true)
        setStatus(if (language.runsOffline) "Đang chạy trên máy…" else "Đang gửi lên máy chủ dịch…", R.color.text_secondary)
        term.submit(if (endOfInput) null else text)
    }

    private fun hideInputRow() {
        awaitingInput = false
        if (binding.terminalInput.hasFocus()) {
            hideKeyboard(binding.terminalInput)
            binding.terminalInput.clearFocus()
        }
        binding.terminalInput.setText("")
        binding.inputRow.visibility = View.GONE
        binding.outputPanel.translationY = 0f
        updateKeyboardOverlap()
    }

    private fun scrollOutputToBottom() {
        binding.outputScroll.post {
            if (_binding != null) binding.outputScroll.scrollTo(0, binding.output.height)
        }
    }

    /** Nút "Xóa": dừng lần chạy đang chờ nhập (nếu có) và xóa khung kết quả. */
    private fun stopAndClear() {
        terminal?.cancel()
        terminal = null
        runToken++
        hideInputRow()
        setRunning(false)
        showPlaceholder()
    }

    /**
     * Nối ô nhập ở khung kết quả với chương trình đang chạy (ở luồng nền).
     * Khi chương trình cần nhập, [requestLine] báo màn hình hiện ô nhập rồi CHỜ người dùng gõ.
     */
    inner class TerminalInput : InputProvider {
        private val queue = LinkedBlockingQueue<Entry>()

        @Volatile
        private var cancelled = false

        override val isCancelled: Boolean get() = cancelled

        override fun requestLine(outputSoFar: String): String? {
            if (cancelled) return null
            mainHandler.post { showWaitingForInput(this@TerminalInput, outputSoFar) }
            val entry = queue.take()
            return if (cancelled) null else entry.text
        }

        fun submit(text: String?) {
            queue.offer(Entry(text))
        }

        fun cancel() {
            cancelled = true
            queue.offer(Entry(null))
        }
    }

    private class Entry(val text: String?)

    // ===================== Linh tinh =====================

    private fun updateUndoRedoButtons() {
        binding.btnUndo.isEnabled = binding.editor.canUndo()
        binding.btnRedo.isEnabled = binding.editor.canRedo()
        binding.btnUndo.alpha = if (binding.editor.canUndo()) 1f else 0.4f
        binding.btnRedo.alpha = if (binding.editor.canRedo()) 1f else 0.4f
    }

    private fun showPlaceholder() {
        binding.output.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_hint))
        binding.output.text = "Bấm ▶ Chạy để chạy code. Kết quả sẽ hiện ở đây.\n" +
            "Nếu chương trình cần nhập dữ liệu (cin, input()...), ô nhập sẽ hiện ngay tại đây."
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
        // Rời màn hình: dừng chương trình đang chờ nhập
        terminal?.cancel()
        terminal = null
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
