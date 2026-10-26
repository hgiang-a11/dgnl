package com.codelab.app.exec

import com.codelab.app.exec.InputDetector.OnlinePlan
import com.codelab.app.exec.WandboxApi.Echo
import com.codelab.app.model.Language
import org.json.JSONObject

/**
 * Điều khiển một lần chạy online (C, C++, Java) có nhập dữ liệu kiểu màn hình dòng lệnh.
 *
 * Máy chủ chạy chương trình một lần từ đầu đến cuối, không dừng giữa chừng chờ gõ được.
 * Vì vậy: chạy tới khi chương trình cần nhập thì dừng ngay tại đó, hỏi người dùng một dòng,
 * rồi chạy lại từ đầu kèm các dòng đã gõ — lặp lại tới khi chương trình chạy xong.
 * Kết quả cuối cùng giống hệt như gõ trực tiếp trên màn hình dòng lệnh.
 *
 * Phần gửi lên máy chủ được truyền vào qua [send] để có thể kiểm thử mà không cần mạng.
 *
 * @param send Gửi (code đã chuẩn bị, tùy chọn dịch, dữ liệu nhập) lên máy chủ, trả về kết quả JSON.
 */
class OnlineSession(
    private val language: Language,
    private val code: String,
    private val mode: ExecMode,
    private val input: InputProvider?,
    private val send: (preparedCode: String, compilerOptions: String, stdin: String) -> JSONObject
) {
    private val userLineCount = code.lines().size

    fun run(): ExecResult {
        if (mode == ExecMode.BUILD_ONLY || input == null || !InputDetector.needsInput(language, code)) {
            return WandboxApi.toResult(sendWith(Echo.NONE, stopAtEof = false, stdin = ""), mode)
        }
        return when (InputDetector.onlinePlan(language, code)) {
            OnlinePlan.CIN_ECHO -> interactiveWithEcho(input, Echo.CIN)
            OnlinePlan.STDIO_ECHO -> interactiveWithEcho(input, Echo.STDIO)
            OnlinePlan.JAVA_SCANNER -> interactiveJava(input)
            OnlinePlan.UPFRONT -> upfront(input)
        }
    }

    private fun sendWith(echo: Echo, stopAtEof: Boolean, stdin: String): JSONObject = send(
        WandboxApi.prepareCode(language, code, echo, stopAtEof),
        WandboxApi.compilerOptions(language, echo),
        stdin
    )

    /** C / C++: đoạn code thêm vào in lại giá trị đã nhập và dừng chương trình khi cần nhập thêm. */
    private fun interactiveWithEcho(input: InputProvider, echo: Echo): ExecResult {
        val typed = StringBuilder()
        while (true) {
            val res = sendWith(echo, stopAtEof = true, stdin = typed.toString())
            if (WandboxApi.failedInAddedCode(res, userLineCount)) {
                // Đoạn code thêm vào bị lỗi dịch (vd code người dùng thiếu dấu "}"):
                // dịch lại đúng code gốc; nếu code gốc lỗi thì báo lỗi đó, nếu không thì nhập trước.
                val plain = sendWith(Echo.NONE, stopAtEof = false, stdin = "")
                return if (WandboxApi.compileFailed(plain)) WandboxApi.toResult(plain, mode) else upfront(input)
            }
            val result = WandboxApi.toResult(res, mode)
            if (!result.needsInput) return result

            val line = input.requestLine(result.output)
            if (line == null) {
                if (input.isCancelled) return stopped()
                // Bấm "Kết thúc nhập": chạy lần cuối, chương trình gặp hết dữ liệu như bình thường
                return WandboxApi.toResult(sendWith(echo, stopAtEof = false, stdin = typed.toString()), mode)
            }
            typed.append(line).append('\n')
        }
    }

    /** Java + Scanner: hết dữ liệu thì Scanner báo NoSuchElementException -> biết là cần nhập. */
    private fun interactiveJava(input: InputProvider): ExecResult {
        val typed = StringBuilder()
        // Vị trí (trong kết quả gốc) nơi chương trình hỏi, kèm dòng người dùng đã gõ
        val typedLines = mutableListOf<Pair<Int, String>>()
        while (true) {
            val res = sendWith(Echo.NONE, stopAtEof = false, stdin = typed.toString())
            val result = WandboxApi.toResult(res, mode, detectJavaInputRequest = true, typedLines = typedLines)
            if (!result.needsInput) return result

            val askedAt = res.optString("program_output").length
            val line = input.requestLine(result.output)
            if (line == null) {
                if (input.isCancelled) return stopped()
                // Bấm "Kết thúc nhập": hiện kết quả như bình thường (Scanner báo hết dữ liệu)
                return WandboxApi.toResult(res, mode, typedLines = typedLines)
            }
            typedLines += askedAt to line
            typed.append(line).append('\n')
        }
    }

    /** Không theo dõi được lúc chương trình cần nhập: người dùng gõ trước toàn bộ dữ liệu. */
    private fun upfront(input: InputProvider): ExecResult {
        val hint = "ℹ Chương trình này cần nhập TẤT CẢ dữ liệu trước khi chạy.\n" +
            "Gõ từng dòng rồi bấm Enter; nhập xong bấm “Kết thúc nhập” để chạy.\n\n"
        val typed = StringBuilder()
        while (true) {
            val line = input.requestLine(hint + typed) ?: break
            typed.append(line).append('\n')
        }
        if (input.isCancelled) return stopped()
        val res = sendWith(Echo.NONE, stopAtEof = false, stdin = typed.toString())
        return WandboxApi.toResult(res, mode, shownInput = typed.toString())
    }

    private fun stopped() = ExecResult("(đã dừng)", isError = false)
}
