package com.floattile

import kotlin.system.exitProcess

/**
 * Dịch vụ chạy lệnh shell. LƯU Ý: lớp này KHÔNG chạy trong ứng dụng FloatTile,
 * mà chạy trong một tiến trình riêng do Shizuku tạo ra với quyền ADB (uid 2000 "shell")
 * hoặc quyền root, tùy cách bạn khởi động Shizuku.
 * Đây là cách chính thức Shizuku khuyến nghị (gọi là "UserService").
 */
class ShellService : IShellService.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(command: String?): String {
        if (command.isNullOrBlank()) return "-1\nLệnh trống"
        return try {
            // "sh -c <lệnh>": nhờ trình thông dịch shell chạy nguyên câu lệnh (có hỗ trợ dấu | để nối lệnh).
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true) // gộp luồng lỗi vào luồng kết quả cho dễ đọc
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val code = process.waitFor()
            "$code\n$output"
        } catch (t: Throwable) {
            "-1\n$t"
        }
    }
}
