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
     * Đoạn code thêm vào CUỐI chương trình C++ để giá trị đọc bằng cin được in lại ngay sau
     * câu hỏi, giống màn hình dòng lệnh. Nó thay bộ đệm của cin bằng một bộ đệm vừa đọc vừa
     * in lại từng ký tự. Không dùng từ khóa "int" để không vướng "#define int long long".
     */
    private val cppEchoSnippet = """
        // ---- CodeLab: hiện dữ liệu đã nhập (cin) ngay trong kết quả, giống màn hình dòng lệnh ----
        #include <iostream>
        #include <streambuf>
        namespace codelab_echo_ {
        class EchoBuf : public std::streambuf {
        public:
            EchoBuf(std::streambuf* in, std::streambuf* out) : in_(in), out_(out), ch_(0) {}
        protected:
            std::streambuf::int_type underflow() override {
                std::streambuf::int_type c = in_->sbumpc();
                if (traits_type::eq_int_type(c, traits_type::eof())) return c;
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
    """.trimIndent()

    /**
     * Đoạn code thêm vào CUỐI chương trình C: thay "stdin" bằng một luồng đọc từng ký tự
     * và in lại ký tự đó (cho scanf, getchar, fgets...). Cần tùy chọn dịch -D_GNU_SOURCE.
     */
    private val cEchoSnippet = """
        /* ---- CodeLab: hiện dữ liệu đã nhập (scanf, getchar...) ngay trong kết quả ---- */
        #include <stdio.h>
        #include <unistd.h>
        static ssize_t codelab_echo_read_(void *cookie, char *buf, size_t size) {
            ssize_t n;
            (void)cookie;
            if (size == 0) return 0;
            n = read(0, buf, 1);
            if (n > 0) fwrite(buf, 1, (size_t)n, stdout);
            return n;
        }
        __attribute__((constructor)) static void codelab_echo_install_(void) {
            cookie_io_functions_t io = { codelab_echo_read_, NULL, NULL, NULL };
            FILE *f = fopencookie(NULL, "r", io);
            if (f != NULL) { setvbuf(f, NULL, _IONBF, 0); stdin = f; }
        }
    """.trimIndent()

    /**
     * Chuẩn bị code trước khi gửi đi.
     * - Java: Wandbox lưu code vào file prog.java, mà Java bắt buộc lớp "public" phải
     *   nằm trong file trùng tên lớp. Vì vậy bỏ chữ "public" trước các lớp ngoài cùng
     *   (Wandbox tự tìm lớp có hàm main để chạy).
     * - echoInput = true (C/C++): thêm đoạn code ở CUỐI file để in lại dữ liệu đã nhập.
     * Code của người dùng không bị thêm/bớt dòng nào nên báo lỗi vẫn đúng dòng.
     */
    fun prepareCode(language: Language, code: String, echoInput: Boolean = false): String {
        val base = if (language == Language.JAVA) code.replace(publicTopLevelType, "\$1") else code
        if (!echoInput) return base
        val snippet = when (language) {
            Language.CPP -> cppEchoSnippet
            Language.C -> cEchoSnippet
            else -> return base
        }
        return base + (if (base.endsWith("\n")) "" else "\n") + "\n" + snippet + "\n"
    }

    /** Tùy chọn thêm cho trình dịch (đoạn code in lại dữ liệu nhập của C cần -D_GNU_SOURCE). */
    fun compilerOptions(language: Language, echoInput: Boolean): String =
        if (echoInput && language == Language.C) "-D_GNU_SOURCE" else ""

    /**
     * Lỗi dịch có nằm trong phần code app thêm vào không (số dòng lớn hơn số dòng của người dùng).
     * Nếu có, app sẽ dịch lại mà không thêm đoạn code đó.
     */
    fun failedInAddedCode(res: JSONObject, userLineCount: Int): Boolean {
        val errors = res.optString("compiler_error") + "\n" + res.optString("compiler_message")
        return Regex("""prog\.\w+:(\d+):\d+:\s*(?:fatal )?error""").findAll(errors)
            .any { (it.groupValues[1].toIntOrNull() ?: 0) > userLineCount }
    }

    /**
     * Đọc kết quả trả về từ compile.json và chuyển thành nội dung hiển thị cho người dùng.
     * @param shownInput Dữ liệu nhập cần hiện ở khung riêng (khi không in lại được ngay sau
     *                   câu hỏi, vd Java); null nếu không cần.
     */
    fun toResult(res: JSONObject, mode: ExecMode, shownInput: String? = null): ExecResult {
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
}
