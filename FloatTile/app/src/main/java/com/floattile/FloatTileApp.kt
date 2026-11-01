package com.floattile

import android.app.Application
import android.content.Context
import android.widget.Toast

/**
 * Lớp Application: chạy đầu tiên mỗi khi tiến trình (process) của ứng dụng khởi động,
 * trước cả màn hình hay ô cài đặt nhanh. Ở đây chỉ chuẩn bị nơi lưu cài đặt.
 */
class FloatTileApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        TileStore.init(this) // nạp danh sách ô, chuyển dữ liệu từ bản một ô (nếu có)
    }
}

/** Hiện một dòng thông báo ngắn (Toast) ở cuối màn hình. */
fun Context.toast(message: CharSequence, long: Boolean = false) {
    Toast.makeText(this, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}
