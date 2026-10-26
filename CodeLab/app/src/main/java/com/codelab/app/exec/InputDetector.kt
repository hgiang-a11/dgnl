package com.codelab.app.exec

import com.codelab.app.model.Language

/**
 * Nhận biết code có dùng lệnh NHẬP dữ liệu không (cin, scanf, input(), Scanner...),
 * để hỏi người dùng gõ trước dữ liệu nhập trước khi chạy.
 * Phần chú thích (comment) được bỏ qua để không báo nhầm.
 */
object InputDetector {

    private val patterns = mapOf(
        Language.PYTHON to Regex("""\binput\s*\(|sys\.stdin"""),
        Language.JAVASCRIPT to Regex("""\bprompt\s*\(|\breadline\s*\("""),
        Language.CPP to Regex("""\bcin\b|\bscanf\s*\(|\bgetline\s*\(|\bgetchar\s*\(|\bfgets\s*\(|\bgets\s*\("""),
        Language.C to Regex("""\bscanf\s*\(|\bgetchar\s*\(|\bfgets\s*\(|\bgets\s*\(|\bf?getc\s*\("""),
        Language.JAVA to Regex("""\bScanner\b|System\.in\b|BufferedReader""")
    )

    /** Code có đọc dữ liệu nhập hay không. */
    fun needsInput(language: Language, code: String): Boolean =
        patterns[language]?.containsMatchIn(stripComments(language, code)) == true

    /** Tên lệnh nhập tiêu biểu của từng ngôn ngữ, để hiện trong lời nhắc. */
    fun inputFunctionName(language: Language): String = when (language) {
        Language.PYTHON -> "input()"
        Language.JAVASCRIPT -> "prompt() / readline()"
        Language.CPP -> "cin"
        Language.C -> "scanf"
        Language.JAVA -> "Scanner"
    }

    /**
     * Với ngôn ngữ dịch online: có thể hiện lại giá trị đã nhập ngay sau câu hỏi
     * (giống màn hình dòng lệnh) hay không.
     *  - C: được (dữ liệu đọc qua scanf, getchar, fgets...).
     *  - C++: được nếu chỉ đọc bằng cin/getline (không trộn scanf, không tắt đồng bộ
     *    bằng sync_with_stdio). Trường hợp còn lại sẽ hiện dữ liệu nhập ở một khung riêng.
     *  - Java: không (dữ liệu nhập hiện ở khung riêng).
     */
    fun canEchoOnline(language: Language, code: String): Boolean {
        val c = stripComments(language, code)
        return when (language) {
            Language.C -> true
            Language.CPP ->
                Regex("""\bcin\b|\bgetline\s*\(""").containsMatchIn(c) &&
                    !Regex("""sync_with_stdio|\bscanf\s*\(|\bgetchar\s*\(|\bfgets\s*\(|\bgets\s*\(""").containsMatchIn(c)
            else -> false
        }
    }

    private fun stripComments(language: Language, code: String): String =
        if (language == Language.PYTHON) {
            code.replace(Regex("#[^\\n]*"), "")
        } else {
            code.replace(Regex("//[^\\n]*|/\\*[\\s\\S]*?\\*/"), "")
        }
}
