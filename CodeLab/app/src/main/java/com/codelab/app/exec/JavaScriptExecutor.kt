package com.codelab.app.exec

import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject

/**
 * Bộ chạy JavaScript — chạy THẬT, OFFLINE ngay trên điện thoại nhờ thư viện Rhino.
 * Không cần Internet.
 *
 * Rhino chưa có sẵn console.log, nên ta tự định nghĩa console.log / print
 * để thu lại nội dung và hiển thị cho người dùng.
 */
class JavaScriptExecutor : CodeExecutor {

    /** Đối tượng Java dùng để JavaScript gọi về, thu lại nội dung in ra. */
    class OutputCollector {
        val builder = StringBuilder()
        fun print(text: String) {
            builder.append(text).append('\n')
        }
    }

    override fun execute(code: String, mode: ExecMode): ExecResult {
        val rhino = Context.enter()
        // Trên Android bắt buộc đặt mức tối ưu = -1 (chạy kiểu thông dịch),
        // vì Android không cho sinh mã máy động như máy tính.
        rhino.optimizationLevel = -1
        // Bật cú pháp JavaScript hiện đại (ES6): let, const, hàm mũi tên...
        rhino.languageVersion = Context.VERSION_ES6

        val collector = OutputCollector()
        return try {
            val scope = rhino.initStandardObjects()

            // Đưa đối tượng thu output vào môi trường JavaScript
            ScriptableObject.putProperty(scope, "__out", Context.javaToJS(collector, scope))

            // Định nghĩa console.log, console.error và print
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
