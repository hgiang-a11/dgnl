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

    /** Dấu hiệu đoạn code thêm vào in ra khi chương trình cần nhập thêm dữ liệu. */
    const val NEED_INPUT_MARKER = "\u0001\u0002CODELAB_NEED_INPUT"

    /** Có thêm đoạn code in lại dữ liệu nhập không, và loại nào. */
    enum class Echo { NONE, CIN, STDIO }

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
     * Đoạn code thêm vào CUỐI chương trình C++ đọc bằng cin. Nó thay bộ đệm của cin bằng một
     * bộ đệm vừa đọc vừa in lại từng ký tự, nên giá trị đã nhập hiện ngay sau câu hỏi.
     * Khi hết dữ liệu nhập mà CODELAB_STOP_AT_EOF_ = 1: in dấu hiệu "cần nhập" rồi dừng chương
     * trình, để app hỏi người dùng và chạy lại. Không dùng từ khóa "int" để không vướng
     * "#define int long long".
     */
    private const val CIN_SNIPPET = """
// ---- CodeLab: hiện dữ liệu đã nhập (cin) ngay trong kết quả, giống màn hình dòng lệnh ----
#include <cstdio>
#include <cstdlib>
#include <iostream>
#include <streambuf>
#define CODELAB_STOP_AT_EOF_ @STOP@
namespace codelab_echo_ {
class EchoBuf : public std::streambuf {
public:
    EchoBuf(std::streambuf* in, std::streambuf* out) : in_(in), out_(out), ch_(0) {}
protected:
    std::streambuf::int_type underflow() override {
        std::streambuf::int_type c = in_->sbumpc();
        if (traits_type::eq_int_type(c, traits_type::eof())) {
            if (CODELAB_STOP_AT_EOF_) {
                out_->sputn("\x01\x02" "CODELAB_NEED_INPUT", 20);
                out_->pubsync();
                std::fflush(stdout);
                std::_Exit(0);
            }
            return c;
        }
        ch_ = traits_type::to_char_type(c);
        out_->sputc(ch_);
        setg(&ch_, &ch_, &ch_ + 1);
        return c;
    }
private:
    std::streambuf* in_;
    std::streambuf* out_;
    char ch_;
};
struct Installer {
    Installer() {
        static EchoBuf buf(std::cin.rdbuf(), std::cout.rdbuf());
        std::cin.rdbuf(&buf);
    }
};
static Installer installer_;
}
"""

    /**
     * Đoạn code thêm vào CUỐI chương trình C (hoặc C++ chỉ dùng scanf): thay "stdin" bằng một
     * luồng đọc từng ký tự và in lại ký tự đó (cho scanf, getchar, fgets...). Hết dữ liệu thì
     * xử lý giống đoạn của cin. Với C cần tùy chọn dịch -D_GNU_SOURCE.
     */
    private const val STDIO_SNIPPET = """
/* ---- CodeLab: hiện dữ liệu đã nhập (scanf, getchar...) ngay trong kết quả ---- */
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#define CODELAB_STOP_AT_EOF_ @STOP@
static ssize_t codelab_echo_read_(void *cookie, char *buf, size_t size) {
    ssize_t n;
    (void)cookie;
    if (size == 0) return 0;
    n = read(0, buf, 1);
    if (n > 0) {
        fwrite(buf, 1, (size_t)n, stdout);
    } else if (CODELAB_STOP_AT_EOF_) {
        fputs("\x01\x02" "CODELAB_NEED_INPUT", stdout);
        fflush(stdout);
        _exit(0);
    }
    return n;
}
__attribute__((constructor)) static void codelab_echo_install_(void) {
    cookie_io_functions_t io = { codelab_echo_read_, NULL, NULL, NULL };
    FILE *f = fopencookie(NULL, "r", io);
    if (f != NULL) { setvbuf(f, NULL, _IONBF, 0); stdin = f; }
}
"""

    /**
     * Chuẩn bị code trước khi gửi đi.
     * - Java: Wandbox lưu code vào file prog.java, mà Java bắt buộc lớp "public" phải
     *   nằm trong file trùng tên lớp. Vì vậy bỏ chữ "public" trước các lớp ngoài cùng
     *   (Wandbox tự tìm lớp có hàm main để chạy).
     * - echo khác NONE: thêm đoạn code ở CUỐI file để in lại dữ liệu đã nhập; stopAtEof = true
     *   thì chương trình dừng lại khi cần nhập thêm (để app hỏi người dùng).
     * Code của người dùng không bị thêm/bớt dòng nào nên báo lỗi vẫn đúng dòng.
     */
    fun prepareCode(
        language: Language,
        code: String,
        echo: Echo = Echo.NONE,
        stopAtEof: Boolean = false
    ): String {
        val base = if (language == Language.JAVA) code.replace(publicTopLevelType, "\$1") else code
        val template = when (echo) {
            Echo.NONE -> return base
            Echo.CIN -> CIN_SNIPPET
            Echo.STDIO -> STDIO_SNIPPET
        }
        val snippet = template.trim('\n').replace("@STOP@", if (stopAtEof) "1" else "0")
        return base + (if (base.endsWith("\n")) "" else "\n") + "\n" + snippet + "\n"
    }

    /** Tùy chọn thêm cho trình dịch (đoạn code dùng stdio của C cần -D_GNU_SOURCE). */
    fun compilerOptions(language: Language, echo: Echo): String =
        if (echo == Echo.STDIO && language == Language.C) "-D_GNU_SOURCE" else ""

    /**
     * Lỗi dịch có nằm trong phần code app thêm vào không (số dòng lớn hơn số dòng của người dùng).
     * Nếu có, app sẽ dịch lại mà không thêm đoạn code đó.
     */
    fun failedInAddedCode(res: JSONObject, userLineCount: Int): Boolean {
        val errors = res.optString("compiler_error") + "\n" + res.optString("compiler_message")
        return Regex("""prog\.\w+:(\d+):\d+:\s*(?:fatal )?error""").findAll(errors)
            .any { (it.groupValues[1].toIntOrNull() ?: 0) > userLineCount }
    }

    // Dòng báo lỗi của trình dịch (gcc, clang, javac, trình liên kết): "...: error: ..."
    private val compileErrorLine = Regex("""(?m)(^|[\s:])(fatal )?error:""")

    /** Dịch có lỗi không: trình dịch báo "error:" (chỉ có "warning:" thì vẫn là dịch thành công). */
    fun compileFailed(res: JSONObject): Boolean {
        val compilerMsg = res.optString("compiler_output") + res.optString("compiler_error")
        return res.optString("status") != "0" && compileErrorLine.containsMatchIn(compilerMsg)
    }

    /**
     * Đọc kết quả trả về từ compile.json và chuyển thành nội dung hiển thị cho người dùng.
     *
     * @param shownInput Dữ liệu nhập cần hiện ở khung riêng (khi không in lại được ngay sau
     *                   câu hỏi); null nếu không cần.
     * @param detectJavaInputRequest true: lỗi NoSuchElementException của Scanner nghĩa là
     *                   chương trình đang cần nhập thêm (không phải lỗi thật).
     * @param typedLines Các dòng người dùng đã gõ, kèm vị trí trong kết quả nơi chương trình
     *                   hỏi (dùng cho Java để chèn giá trị đã nhập ngay sau câu hỏi).
     */
    fun toResult(
        res: JSONObject,
        mode: ExecMode,
        shownInput: String? = null,
        detectJavaInputRequest: Boolean = false,
        typedLines: List<Pair<Int, String>> = emptyList()
    ): ExecResult {
        val status = res.optString("status")           // "0" = chương trình chạy xong, không lỗi
        val signal = res.optString("signal")           // vd "Segmentation fault" nếu chương trình bị dừng
        val compilerMsg = (res.optString("compiler_output") + res.optString("compiler_error")).trim()
        val rawOut = res.optString("program_output")
        val programErr = res.optString("program_error")

        val failed = compileFailed(res)

        if (mode == ExecMode.BUILD_ONLY) {
            return when {
                failed -> ExecResult("Lỗi khi dịch:\n$compilerMsg", isError = true)
                compilerMsg.isNotEmpty() -> ExecResult("✓ Dịch thành công, có cảnh báo:\n$compilerMsg", isError = false)
                else -> ExecResult("✓ Dịch thành công, không có lỗi.", isError = false)
            }
        }

        if (failed) {
            return ExecResult("Lỗi khi dịch:\n$compilerMsg", isError = true)
        }

        // Chương trình dừng lại vì cần nhập thêm dữ liệu?
        val markerAt = rawOut.indexOf(NEED_INPUT_MARKER)
        if (markerAt >= 0) {
            return ExecResult(rawOut.substring(0, markerAt), isError = false, needsInput = true)
        }
        if (detectJavaInputRequest && status != "0" &&
            programErr.contains("java.util.NoSuchElementException")
        ) {
            return ExecResult(insertTypedLines(rawOut, typedLines), isError = false, needsInput = true)
        }
        val programOut = insertTypedLines(rawOut, typedLines)

        val sb = StringBuilder()
        if (mode == ExecMode.BUILD_AND_RUN) {
            if (compilerMsg.isEmpty()) {
                sb.append("✓ Dịch thành công.\n")
            } else {
                sb.append("✓ Dịch thành công, có cảnh báo:\n").append(compilerMsg).append('\n')
            }
        }
        val hasInputBlock = !shownInput.isNullOrBlank()
        if (hasInputBlock) {
            sb.append("📥 Dữ liệu nhập:\n").append(shownInput!!.trimEnd()).append('\n')
        }
        if (mode == ExecMode.BUILD_AND_RUN || hasInputBlock) {
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

    /**
     * Chèn các dòng người dùng đã gõ vào đúng chỗ chương trình hỏi (vị trí tính trong kết quả
     * gốc chưa chèn), để kết quả trông giống màn hình dòng lệnh. Chỉ chèn khi vị trí hợp lệ.
     */
    fun insertTypedLines(output: String, typedLines: List<Pair<Int, String>>): String {
        val sb = StringBuilder(output)
        typedLines.sortedByDescending { it.first }.forEach { (pos, line) ->
            if (pos in 0..output.length) sb.insert(pos, line + "\n")
        }
        return sb.toString()
    }
}
