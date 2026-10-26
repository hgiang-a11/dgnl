package com.codelab.app.exec

import com.codelab.app.model.Language
import org.json.JSONArray
import org.json.JSONObject

/**
 * Phần xử lý dữ liệu cho trình dịch online Wandbox (wandbox.org):
 * chọn trình dịch, chuẩn bị code và đọc kết quả trả về.
 *
 * Ở đây KHÔNG gọi mạng (việc đó do OnlineExecutor làm), để dễ kiểm thử.
 */
object WandboxApi {

    const val LIST_URL = "https://wandbox.org/api/list.json"
    const val COMPILE_URL = "https://wandbox.org/api/compile.json"

    /**
     * Chọn tên trình dịch cho một ngôn ngữ từ danh sách list.json của Wandbox.
     * Ưu tiên các tên trong preferredCompilers; nếu không có thì chọn bản mới nhất.
     * Trả về null nếu Wandbox không có trình dịch nào cho ngôn ngữ này.
     */
    fun pickCompiler(list: JSONArray, language: Language): String? {
        val names = (0 until list.length())
            .mapNotNull { list.optJSONObject(it) }
            // Lọc đúng nhãn ngôn ngữ (vd "C++"); các bản chỉ tiền xử lý có nhãn "CPP" sẽ bị loại
            .filter { it.optString("language") == language.onlineLanguage }
            .map { it.optString("name") }
            .filter { it.isNotEmpty() }

        language.preferredCompilers.firstOrNull { it in names }?.let { return it }
        return names.maxWithOrNull(::compareVersions)
    }

    /** So sánh 2 tên theo các con số bên trong, vd openjdk-jdk-22+36 mới hơn openjdk-jdk-21+35. */
    private fun compareVersions(a: String, b: String): Int {
        val va = Regex("\\d+").findAll(a).map { it.value.toLongOrNull() ?: 0L }.toList()
        val vb = Regex("\\d+").findAll(b).map { it.value.toLongOrNull() ?: 0L }.toList()
        for (i in 0 until maxOf(va.size, vb.size)) {
            val x = va.getOrElse(i) { -1L }
            val y = vb.getOrElse(i) { -1L }
            if (x != y) return x.compareTo(y)
        }
        return a.compareTo(b)
    }

    // "public class X" ở đầu dòng (lớp ngoài cùng), kể cả "public final class", "public enum"...
    private val publicTopLevelType = Regex(
        "(?m)^public[ \\t]+((?:(?:final|abstract|sealed|strictfp)[ \\t]+)*(?:class|interface|enum|record)\\b)"
    )

    /**
     * Chuẩn bị code trước khi gửi đi.
     * Java: Wandbox lưu code vào file prog.java, mà Java bắt buộc lớp "public" phải
     * nằm trong file trùng tên lớp. Vì vậy bỏ chữ "public" trước các lớp ngoài cùng
     * (Wandbox tự tìm lớp có hàm main để chạy). Số dòng giữ nguyên nên báo lỗi vẫn đúng dòng.
     */
    fun prepareCode(language: Language, code: String): String =
        if (language == Language.JAVA) code.replace(publicTopLevelType, "\$1") else code

    /** Đọc kết quả trả về từ compile.json và chuyển thành nội dung hiển thị cho người dùng. */
    fun toResult(res: JSONObject, mode: ExecMode): ExecResult {
        val status = res.optString("status")           // "0" = chương trình chạy xong, không lỗi
        val signal = res.optString("signal")           // vd "Segmentation fault" nếu chương trình bị dừng
        val compilerMsg = (res.optString("compiler_output") + res.optString("compiler_error")).trim()
        val programOut = res.optString("program_output")
        val programErr = res.optString("program_error")

        // Nếu dịch lỗi thì Wandbox không chạy chương trình -> không có các trường program_*
        val ran = res.has("program_output") || res.has("program_error") || res.has("program_message")
        val compileFailed = !ran && status != "0" && compilerMsg.contains("error", ignoreCase = true)

        if (mode == ExecMode.BUILD_ONLY) {
            return when {
                compileFailed -> ExecResult("Lỗi khi dịch:\n$compilerMsg", isError = true)
                compilerMsg.isNotEmpty() -> ExecResult("✓ Dịch thành công, có cảnh báo:\n$compilerMsg", isError = false)
                else -> ExecResult("✓ Dịch thành công, không có lỗi.", isError = false)
            }
        }

        if (compileFailed) {
            return ExecResult("Lỗi khi dịch:\n$compilerMsg", isError = true)
        }

        val sb = StringBuilder()
        if (mode == ExecMode.BUILD_AND_RUN) {
            if (compilerMsg.isEmpty()) {
                sb.append("✓ Dịch thành công.\n")
            } else {
                sb.append("✓ Dịch thành công, có cảnh báo:\n").append(compilerMsg).append('\n')
            }
            sb.append("──── Kết quả chạy ────\n")
        }

        val output = buildString {
            append(programOut)
            if (programErr.isNotEmpty()) {
                if (isNotEmpty() && !endsWith("\n")) append('\n')
                append(programErr)
            }
        }
        sb.append(output.ifEmpty { "(chương trình không in ra gì)" })

        val crashed = signal.isNotEmpty()
        val badExit = status.isNotEmpty() && status != "0"
        if (crashed) {
            sb.append("\n⚠ Chương trình bị dừng: ").append(signal)
        } else if (badExit) {
            sb.append("\n(chương trình kết thúc với mã lỗi ").append(status).append(')')
        }
        return ExecResult(sb.toString().trimEnd(), isError = crashed || badExit)
    }
}
