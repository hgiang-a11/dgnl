package com.codelab.app.exec

import com.codelab.app.model.Language

/**
 * Nhận biết code có dùng lệnh NHẬP dữ liệu không (cin, scanf, input(), Scanner...),
 * và chọn cách cho người dùng nhập khi chạy online.
 * Phần chú thích (comment) được bỏ qua để không báo nhầm.
 */
object InputDetector {

    /**
     * Cách cho người dùng nhập dữ liệu khi chạy trên máy chủ (C, C++, Java):
     *  - CIN_ECHO: C++ đọc bằng cin/getline -> thêm đoạn code dừng chương trình đúng lúc cần nhập.
     *  - STDIO_ECHO: C (hoặc C++ chỉ dùng scanf...) -> tương tự, cho scanf, getchar, fgets...
     *  - JAVA_SCANNER: Java dùng Scanner -> biết lúc cần nhập nhờ lỗi NoSuchElementException.
     *  - UPFRONT: không biết được lúc nào cần nhập -> người dùng gõ trước toàn bộ dữ liệu.
     */
    enum class OnlinePlan { CIN_ECHO, STDIO_ECHO, JAVA_SCANNER, UPFRONT }

    private val patterns = mapOf(
        Language.PYTHON to Regex("""\binput\s*\(|sys\.stdin"""),
        Language.JAVASCRIPT to Regex("""\bprompt\s*\(|\breadline\s*\("""),
        Language.CPP to Regex("""\bcin\b|\bscanf\s*\(|\bgetline\s*\(|\bgetchar\s*\(|\bfgets\s*\(|\bgets\s*\("""),
        Language.C to Regex("""\bscanf\s*\(|\bgetchar\s*\(|\bfgets\s*\(|\bgets\s*\(|\bf?getc\s*\("""),
        Language.JAVA to Regex("""\bScanner\b|System\.in\b|BufferedReader""")
    )

    private val cppCin = Regex("""\bcin\b|\bgetline\s*\(""")
    private val cppStdio = Regex("""\bscanf\s*\(|\bgetchar\s*\(|\bfgets\s*\(|\bgets\s*\(""")

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

    /** Chọn cách cho người dùng nhập dữ liệu khi chạy online (xem [OnlinePlan]). */
    fun onlinePlan(language: Language, code: String): OnlinePlan {
        val c = stripComments(language, code)
        return when (language) {
            Language.C -> OnlinePlan.STDIO_ECHO
            Language.CPP -> {
                val usesCin = cppCin.containsMatchIn(c)
                val usesStdio = cppStdio.containsMatchIn(c)
                when {
                    // Tắt đồng bộ cin với scanf: cin dùng bộ đệm riêng, không theo dõi được
                    c.contains("sync_with_stdio") -> OnlinePlan.UPFRONT
                    usesCin && !usesStdio -> OnlinePlan.CIN_ECHO
                    usesStdio && !usesCin -> OnlinePlan.STDIO_ECHO
                    else -> OnlinePlan.UPFRONT
                }
            }
            Language.JAVA ->
                if (c.contains("Scanner") && !c.contains("BufferedReader")) OnlinePlan.JAVA_SCANNER
                else OnlinePlan.UPFRONT
            else -> OnlinePlan.UPFRONT
        }
    }

    private fun stripComments(language: Language, code: String): String =
        if (language == Language.PYTHON) {
            code.replace(Regex("#[^\\n]*"), "")
        } else {
            code.replace(Regex("//[^\\n]*|/\\*[\\s\\S]*?\\*/"), "")
        }
}
