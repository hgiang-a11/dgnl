package com.floattile

/**
 * Đọc kết quả lệnh "dumpsys activity activities" (lệnh in ra danh sách các task đang mở)
 * để tìm task của một ứng dụng và chế độ cửa sổ (mode) của nó.
 *
 * Ví dụ một dòng trên Android 12+:
 *   * Task{9bf2e4c #123 type=standard A=10234:com.anthropic.claude U=0 visible=true mode=freeform ...}
 * Trên Android 9–10, chế độ nằm ở dòng "Stack #5: type=standard mode=freeform" phía trên.
 */
object DumpsysParser {

    data class TaskInfo(val id: Int, val mode: String?)

    private val idRegex = Regex("#(\\d+)")
    private val modeRegex = Regex("(?<![A-Za-z])mode=([A-Za-z-]+)")
    private val containerRegex = Regex("^(Stack|RootTask) #\\d+")

    /** Lệnh shell lấy các dòng cần thiết (lọc bớt bằng grep cho gọn). */
    const val COMMAND = "dumpsys activity activities | grep -E 'Task\\{|TaskRecord\\{|Stack #|RootTask #'"

    /** Trả về task nằm trên cùng (mới nhất) của gói [pkg], hoặc null nếu không thấy. */
    fun findTopTask(dump: String, pkg: String): TaskInfo? {
        val pkgRegex = Regex("(A=|:|\\s)" + Regex.escape(pkg) + "(?=[\\s}/]|$)")
        var containerMode: String? = null
        for (raw in dump.lineSequence()) {
            val line = raw.trim().removePrefix("* ")
            if (containerRegex.containsMatchIn(line)) {
                containerMode = modeRegex.find(line)?.groupValues?.get(1)
                continue
            }
            val isTask = line.startsWith("Task{") || line.startsWith("TaskRecord{")
            if (!isTask || !pkgRegex.containsMatchIn(line)) continue
            val id = idRegex.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            val mode = modeRegex.find(line)?.groupValues?.get(1) ?: containerMode
            return TaskInfo(id, mode)
        }
        return null
    }
}
