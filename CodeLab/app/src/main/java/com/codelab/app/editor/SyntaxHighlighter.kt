package com.codelab.app.editor

import android.text.Editable
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import com.codelab.app.model.Language
import java.util.regex.Pattern

/**
 * Tô màu cú pháp cho code: từ khóa, chuỗi, số, chú thích... mỗi loại một màu.
 *
 * Cách làm: ghép nhiều mẫu tìm kiếm thành một mẫu lớn, quét code từ trái sang phải.
 * Vùng nào khớp trước thì tô theo loại đó (ví dụ một từ khóa nằm trong chuỗi thì
 * được coi là chuỗi, không tô màu từ khóa).
 */
class SyntaxHighlighter(language: Language) {

    // Nhãn đánh dấu các màu do bộ này tạo ra, để xóa đúng màu cũ trước khi tô lại
    private class HSpan(color: Int) : ForegroundColorSpan(color)

    private val pattern: Pattern = buildPattern(language)

    fun highlight(editable: Editable) {
        // Xóa toàn bộ màu cũ do chính bộ này tạo ra
        val old = editable.getSpans(0, editable.length, HSpan::class.java)
        for (span in old) editable.removeSpan(span)

        val matcher = pattern.matcher(editable)
        while (matcher.find()) {
            val color = when {
                matcher.group("cmt") != null -> COLOR_COMMENT
                matcher.group("str") != null -> COLOR_STRING
                matcher.group("num") != null -> COLOR_NUMBER
                matcher.group("kw") != null -> COLOR_KEYWORD
                else -> continue
            }
            editable.setSpan(
                HSpan(color),
                matcher.start(),
                matcher.end(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private fun buildPattern(language: Language): Pattern {
        // Kiểu chú thích khác nhau: Python dùng #, còn lại dùng // và /* */
        val comment = if (language == Language.PYTHON) {
            "(?<cmt>#[^\\n]*)"
        } else {
            "(?<cmt>//[^\\n]*|/\\*[\\s\\S]*?\\*/)"
        }
        val string = "(?<str>\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*')"
        val number = "(?<num>\\b\\d+\\.?\\d*\\b)"
        val keyword = "(?<kw>\\b(?:" + keywordsFor(language).joinToString("|") + ")\\b)"

        // Thứ tự quan trọng: chú thích và chuỗi xét trước từ khóa
        val full = "$comment|$string|$number|$keyword"
        return Pattern.compile(full)
    }

    private fun keywordsFor(language: Language): List<String> = when (language) {
        Language.PYTHON -> listOf(
            "False", "None", "True", "and", "as", "assert", "async", "await",
            "break", "class", "continue", "def", "del", "elif", "else", "except",
            "finally", "for", "from", "global", "if", "import", "in", "is",
            "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "try",
            "while", "with", "yield", "print", "range", "len", "input"
        )
        Language.JAVASCRIPT -> listOf(
            "var", "let", "const", "function", "return", "if", "else", "for",
            "while", "do", "switch", "case", "break", "continue", "new", "this",
            "typeof", "instanceof", "null", "undefined", "true", "false", "class",
            "extends", "super", "try", "catch", "finally", "throw", "in", "of",
            "delete", "void", "yield", "async", "await", "console"
        )
        Language.CPP, Language.C -> listOf(
            "int", "float", "double", "char", "bool", "void", "long", "short",
            "unsigned", "signed", "const", "static", "struct", "class", "public",
            "private", "protected", "return", "if", "else", "for", "while", "do",
            "switch", "case", "break", "continue", "new", "delete", "true", "false",
            "nullptr", "NULL", "using", "namespace", "include", "sizeof", "auto",
            "template", "typename", "try", "catch", "throw", "enum", "typedef"
        )
        Language.JAVA -> listOf(
            "public", "private", "protected", "static", "final", "class", "interface",
            "extends", "implements", "void", "int", "long", "short", "byte", "float",
            "double", "char", "boolean", "String", "return", "if", "else", "for",
            "while", "do", "switch", "case", "break", "continue", "new", "this",
            "super", "null", "true", "false", "try", "catch", "finally", "throw",
            "throws", "import", "package", "abstract", "enum", "instanceof"
        )
    }

    companion object {
        // Bộ màu theo kiểu VS Code (nền tối)
        private const val COLOR_KEYWORD = 0xFF569CD6.toInt() // xanh dương
        private const val COLOR_STRING = 0xFFCE9178.toInt()  // cam
        private const val COLOR_NUMBER = 0xFFB5CEA8.toInt()  // xanh lá nhạt
        private const val COLOR_COMMENT = 0xFF6A9955.toInt() // xanh rêu
    }
}
