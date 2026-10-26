// File build gốc của dự án — khai báo các "plugin" (bộ công cụ) dùng chung,
// nhưng chưa áp dụng vội (apply false). Module con (app) sẽ áp dụng khi cần.
plugins {
    id("com.android.application") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    // Chaquopy: thư viện nhúng Python vào app để chạy offline
    // 17.1.0: bản mới nhất, hỗ trợ điện thoại dùng bộ nhớ trang 16 KB
    id("com.chaquo.python") version "17.1.0" apply false
}
