package com.codelab.app

import android.app.Application
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

/**
 * Lớp khởi động của app (chạy một lần khi mở app).
 * Tại đây ta khởi động bộ xử lý Python nhúng (Chaquopy) để sẵn sàng chạy offline.
 */
class CodeLabApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Khởi động Python nếu chưa chạy
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
    }
}
