package com.codelab.app.exec

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

/**
 * Bộ chạy Python — chạy THẬT, OFFLINE ngay trên điện thoại nhờ Chaquopy.
 * Không cần Internet.
 */
class PythonExecutor(context: Context) : CodeExecutor {

    init {
        // Đảm bảo Python đã được khởi động (phòng trường hợp chưa chạy)
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context.applicationContext))
        }
    }

    override fun execute(code: String, mode: ExecMode): ExecResult {
        return try {
            val py = Python.getInstance()
            val module = py.getModule("code_runner")

            when (mode) {
                ExecMode.BUILD_ONLY -> {
                    val error = module.callAttr("check_syntax", code).toString()
                    if (error.isEmpty()) {
                        ExecResult("✓ Không phát hiện lỗi cú pháp.", isError = false)
                    } else {
                        ExecResult(error, isError = true)
                    }
                }
                else -> {
                    val output = module.callAttr("run_code", code).toString()
                    ExecResult(output, isError = false)
                }
            }
        } catch (e: Exception) {
            ExecResult("Lỗi khi chạy Python: ${e.message}", isError = true)
        }
    }
}
