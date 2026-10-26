package com.codelab.app.exec

/**
 * Nguồn dữ liệu nhập cho chương trình khi chạy (cin, scanf, input()...).
 *
 * Khi chương trình cần nhập, bộ chạy gọi [requestLine]: màn hình hiện phần kết quả đã có
 * (kết thúc bằng câu hỏi) và chờ người dùng gõ một dòng rồi bấm Enter.
 * Hàm này CHẶN (chờ) cho đến khi người dùng gõ xong, nên chỉ gọi ở luồng nền.
 */
interface InputProvider {
    /**
     * Chờ người dùng nhập một dòng.
     * @param outputSoFar Kết quả chương trình đã in ra tới lúc cần nhập (để hiện cho người dùng).
     * @return Dòng vừa nhập (không có ký tự xuống dòng), hoặc null khi người dùng bấm
     *         "Kết thúc nhập" (EOF) hoặc đã hủy lần chạy.
     */
    fun requestLine(outputSoFar: String): String?

    /** true nếu người dùng đã hủy lần chạy này (vd bấm Chạy lại, rời màn hình). */
    val isCancelled: Boolean get() = false

    companion object {
        /** Nguồn nhập từ một đoạn văn bản có sẵn (mỗi dòng một lần nhập), hết thì trả về null. */
        fun fromText(text: String): InputProvider = object : InputProvider {
            private val lines = ArrayDeque(if (text.isEmpty()) emptyList() else text.removeSuffix("\n").split("\n"))
            override fun requestLine(outputSoFar: String): String? = lines.removeFirstOrNull()
        }
    }
}
