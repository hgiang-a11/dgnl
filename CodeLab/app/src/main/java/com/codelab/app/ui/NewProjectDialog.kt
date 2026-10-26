package com.codelab.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.codelab.app.data.ProjectRepository
import com.codelab.app.databinding.DialogNewProjectBinding
import com.codelab.app.model.Language
import com.codelab.app.model.Project
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip

/**
 * Hộp thoại (trượt lên từ dưới) để tạo project mới:
 * chọn NGÔN NGỮ, nhập TÊN và MÔ TẢ.
 */
class NewProjectDialog : BottomSheetDialogFragment() {

    private var _binding: DialogNewProjectBinding? = null
    private val binding get() = _binding!!

    private var selectedLanguage: Language = Language.PYTHON

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DialogNewProjectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Tạo một "chip" cho mỗi ngôn ngữ để người dùng chọn
        Language.entries.forEach { lang ->
            val chip = Chip(requireContext()).apply {
                id = View.generateViewId()   // cần id để ChipGroup theo dõi được lựa chọn
                text = if (lang.runsOffline) lang.displayName else "${lang.displayName} (cần mạng)"
                isCheckable = true
                tag = lang.id
                isChecked = (lang == Language.PYTHON)
            }
            binding.chipGroupLanguage.addView(chip)
        }

        binding.chipGroupLanguage.setOnCheckedStateChangeListener { group, checkedIds ->
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val chip = group.findViewById<Chip>(id)
            selectedLanguage = Language.fromId(chip.tag as String)
        }

        binding.btnCreate.setOnClickListener { createProject() }
    }

    private fun createProject() {
        val name = binding.inputName.text?.toString()?.trim().orEmpty()
            .ifBlank { "Project ${selectedLanguage.displayName}" }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val RESULT_KEY = "new_project_result"
        const val ARG_PROJECT_ID = "project_id"
    }
}
