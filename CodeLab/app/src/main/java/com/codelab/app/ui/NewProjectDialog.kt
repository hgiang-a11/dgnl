package com.codelab.app.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Space
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.codelab.app.R
import com.codelab.app.data.ProjectRepository
import com.codelab.app.databinding.DialogNewProjectBinding
import com.codelab.app.databinding.ItemLanguageTileBinding
import com.codelab.app.model.Language
import com.codelab.app.model.Project
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Hộp thoại nổi giữa màn hình để tạo project mới:
 * chọn NGÔN NGỮ (các ô có huy hiệu), nhập TÊN và MÔ TẢ.
 */
class NewProjectDialog : DialogFragment() {

    private var _binding: DialogNewProjectBinding? = null
    private val binding get() = _binding!!

    private var selectedLanguage: Language = Language.PYTHON
    private val tiles = mutableMapOf<Language, ItemLanguageTileBinding>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Lưu ý: không dùng layoutInflater của DialogFragment ở đây (sẽ gọi lặp vô hạn)
        val inflater = LayoutInflater.from(requireContext())
        _binding = DialogNewProjectBinding.inflate(inflater)

        savedInstanceState?.getString(STATE_LANGUAGE)?.let { selectedLanguage = Language.fromId(it) }
        buildLanguageTiles(inflater)
        selectLanguage(selectedLanguage)

        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnCreate.setOnClickListener { createProject() }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()
        // Khi bàn phím hiện, hộp thoại tự dịch lên để không bị che ô nhập
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        return dialog
    }

    /** Tạo các ô chọn ngôn ngữ, 3 ô mỗi hàng. */
    private fun buildLanguageTiles(inflater: LayoutInflater) {
        val margin = (4 * resources.displayMetrics.density).toInt()
        Language.entries.chunked(3).forEach { rowLanguages ->
            val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
            rowLanguages.forEach { lang ->
                val tile = ItemLanguageTileBinding.inflate(inflater, row, false)
                tile.tileBadge.styleAsBadge(lang)
                tile.tileName.text = lang.displayName
                tile.tileMode.text = if (lang.runsOffline) "Offline" else "Cần mạng"
                tile.tileMode.setTextColor(
                    ContextCompat.getColor(requireContext(), if (lang.runsOffline) R.color.success else R.color.warning)
                )
                tile.root.setOnClickListener { selectLanguage(lang) }
                row.addView(tile.root, cellParams(margin))
                tiles[lang] = tile
            }
            // Hàng cuối thiếu ô thì chèn chỗ trống để các ô vẫn đều nhau
            repeat(3 - rowLanguages.size) { row.addView(Space(requireContext()), cellParams(margin)) }
            binding.languageGrid.addView(row)
        }
    }

    private fun cellParams(margin: Int) =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(margin, margin, margin, margin)
        }

    /** Đánh dấu ô ngôn ngữ được chọn (viền xanh + nền xanh nhạt). */
    private fun selectLanguage(language: Language) {
        selectedLanguage = language
        val ctx = requireContext()
        val density = resources.displayMetrics.density
        tiles.forEach { (lang, tile) ->
            val selected = lang == language
            tile.root.strokeColor = ContextCompat.getColor(ctx, if (selected) R.color.primary else R.color.card_stroke)
            tile.root.strokeWidth = ((if (selected) 2 else 1) * density).toInt()
            tile.root.setCardBackgroundColor(ContextCompat.getColor(ctx, if (selected) R.color.primary_soft else R.color.bg))
        }
        binding.layoutName.placeholderText = "Dự án ${language.displayName}"
    }

    private fun createProject() {
        val name = binding.inputName.text?.toString()?.trim().orEmpty()
            .ifBlank { "Dự án ${selectedLanguage.displayName}" }
        val description = binding.inputDescription.text?.toString()?.trim().orEmpty()

        val project = Project(
            name = name,
            description = description,
            languageId = selectedLanguage.id,
            code = selectedLanguage.template
        )
        ProjectRepository.get(requireContext()).add(project)

        // Báo cho màn hình chính biết đã tạo xong (kèm mã project để mở ngay)
        setFragmentResult(RESULT_KEY, bundleOf(ARG_PROJECT_ID to project.id))
        dismiss()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_LANGUAGE, selectedLanguage.id)
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    companion object {
        const val TAG = "new_project"
        const val RESULT_KEY = "new_project_result"
        const val ARG_PROJECT_ID = "project_id"
        private const val STATE_LANGUAGE = "language"
    }
}
