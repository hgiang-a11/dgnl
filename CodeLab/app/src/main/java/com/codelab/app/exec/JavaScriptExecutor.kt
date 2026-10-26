package com.codelab.app.exec

import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject

/**
 * Bộ chạy JavaScript — chạy THẬT, OFFLINE ngay trên điện thoại nhờ thư viện Rhino.
 * Không cần Internet.
 *
 * Rhino chưa có sẵn console.log, nên ta tự định nghĩa console.log / print
 * để thu lại nội dung và hiển thị cho người dùng. Dữ liệu nhập được đọc bằng
 * prompt("câu hỏi") hoặc readline().
 */
class JavaScriptExecutor : CodeExecutor {

    /** Đối tượng Java dùng để JavaScript gọi về: thu nội dung in ra và đọc dữ liệu nhập. */
    class OutputCollector(stdin: String) {
        val builder = StringBuilder()
        private val inputLines = ArrayDeque(if (stdin.isEmpty()) emptyList() else stdin.removeSuffix("\n").split("\n"))

        fun print(text: String) {
            builder.append(text).append('\n')
        }

        fun write(text: String) {
            builder.append(text)
        }

        /** Lấy dòng dữ liệu nhập tiếp theo; trả về null khi đã hết. */
        fun readLine(): String? = inputLines.removeFirstOrNull()?.removeSuffix("\r")
    }

    override fun execute(code: String, mode: ExecMode, stdin: String): ExecResult {
        val rhino = Context.enter()
        // Trên Android bắt buộc đặt mức tối ưu = -1 (chạy kiểu thông dịch),
        // vì Android không cho sinh mã máy động như máy tính.
        rhino.optimizationLevel = -1
        // Bật cú pháp JavaScript hiện đại (ES6): let, const, hàm mũi tên...
        rhino.languageVersion = Context.VERSION_ES6

        val collector = OutputCollector(stdin)
        return try {
            val scope = rhino.initStandardObjects()

            // Đưa đối tượng thu output vào môi trường JavaScript
            ScriptableObject.putProperty(scope, "__out", Context.javaToJS(collector, scope))

            // Định nghĩa console.log, print và các lệnh nhập prompt / readline.
            // Giá trị nhập được in lại ngay sau câu hỏi, giống màn hình dòng lệnh.
            val preamble = """
                var __emit = function(args) {
                    var parts = [];
                    for (var i = 0; i < args.length; i++) { parts.push(String(args[i])); }
                    __out.print(parts.join(' '));
                };
                var console = {
                    log:  function() { __emit(arguments); },
                    info: function() { __emit(arguments); },
                    warn: function() { __emit(arguments); },
                    error:function() { __emit(arguments); }
                };
                function print() { __emit(arguments); }
                function readline() {
                    var line = __out.readLine();
                    if (line === null) return null;
                    line = String(line);
                    __out.write(line + '\n');
                    return line;
                }
                function prompt(message) {
                    if (message !== undefined && message !== null) __out.write(String(message));
                    var line = __out.readLine();
                    if (line === null) { __out.write('\n'); return null; }
                    line = String(line);
                    __out.write(line + '\n');
                    return line;
                }
            """.trimIndent()
            rhino.evaluateString(scope, preamble, "preamble", 1, null)

            if (mode == ExecMode.BUILD_ONLY) {
                // Chỉ kiểm tra lỗi cú pháp: dịch nhưng không chạy
                rhino.compileString(code, "main", 1, null)
                ExecResult("✓ Không phát hiện lỗi cú pháp.", isError = false)
            } else {
                rhino.evaluateString(scope, code, "main", 1, null)
                val out = collector.builder.toString()
                ExecResult(out.ifEmpty { "(chương trình không in ra gì)" }, isError = false)
            }
        } catch (e: Exception) {
            // Ghép nội dung đã in trước khi lỗi + thông báo lỗi
            val partial = collector.builder.toString()
            val message = if (partial.isEmpty()) "" else partial + "\n"
            ExecResult(message + "Lỗi: " + (e.message ?: e.toString()), isError = true)
        } finally {
            Context.exit()
        }
    }
}
