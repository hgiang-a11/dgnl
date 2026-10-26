package com.codelab.app.exec

import android.content.Context
import com.codelab.app.model.Language

/**
 * Chọn đúng bộ chạy cho từng ngôn ngữ:
 * - Python  -> chạy offline (Chaquopy)
 * - JavaScript -> chạy offline (Rhino)
 * - C++, C, Java -> chạy online (Wandbox)
 */
object ExecutorFactory {
    fun create(context: Context, language: Language): CodeExecutor {
        return when (language) {
            Language.PYTHON -> PythonExecutor(context)
            Language.JAVASCRIPT -> JavaScriptExecutor()
            else -> OnlineExecutor(context, language)
        }
    }
}
