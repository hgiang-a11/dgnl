package com.codelab.app.exec

/**
 * Chế độ chạy tương ứng với các nút trên thanh công cụ.
 * - RUN: chạy code.
 * - BUILD_AND_RUN: dịch rồi chạy (hiện cả lỗi dịch lẫn kết quả).
 * - BUILD_ONLY: chỉ dịch để xem lỗi, KHÔNG chạy.
 */
enum class ExecMode {
    RUN,
    BUILD_AND_RUN,
    BUILD_ONLY
}

/**
 * Kết quả sau khi chạy/dịch code.
 *
 * @param output  Nội dung hiển thị ở khung kết quả bên dưới.
 * @param isError true nếu có lỗi (để tô màu đỏ cho dễ nhận biết).
 * @param requiresInternet true nếu cần mạng nhưng hiện không có kết nối.
 */
data class ExecResult(
    val output: String,
    val isError: Boolean,
    val requiresInternet: Boolean = false
)

/**
 * Giao diện chung cho tất cả bộ chạy code.
 * Lưu ý: hàm này có thể mất thời gian nên luôn gọi ở luồng nền.
 */
interface CodeExecutor {
    fun execute(code: String, mode: ExecMode): ExecResult
}
