package com.codelab.app.model

import java.util.UUID

/**
 * Một "project" (dự án code) do người dùng tạo.
 *
 * @param id        Mã định danh duy nhất của project.
 * @param name      Tên project do người dùng đặt.
 * @param description Mô tả ngắn về project.
 * @param languageId Mã ngôn ngữ (python, cpp, java...).
 * @param code      Nội dung code đang được lưu.
 * @param createdAt Thời điểm tạo (mili-giây).
 * @param updatedAt Thời điểm sửa gần nhất (mili-giây).
 * @param lastInput Dữ liệu nhập (cho cin, input()...) lần chạy gần nhất, để gợi ý lại lần sau.
 *                  Có thể null với dự án tạo từ bản app cũ.
 */
data class Project(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var description: String,
    var languageId: String,
    var code: String,
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var lastInput: String? = null
) {
    /** Lấy đối tượng ngôn ngữ tương ứng. */
    val language: Language
        get() = Language.fromId(languageId)
}
