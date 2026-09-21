package com.dgnl.taskflow.data

import androidx.compose.runtime.Immutable
import java.time.LocalDate
import java.util.UUID

/** Ba trang thai cua mot cong viec. */
enum class TaskStatus(val id: String, val label: String) {
    TODO("todo", "Chưa làm"),
    DOING("doing", "Đang làm"),
    DONE("done", "Đã làm");

    companion object {
        fun from(id: String?): TaskStatus = entries.firstOrNull { it.id == id } ?: TODO
    }
}

/** Muc do uu tien. */
enum class Priority(val id: String, val label: String, val level: Int) {
    LOW("low", "Thấp", 0),
    NORMAL("normal", "Bình thường", 1),
    HIGH("high", "Cao", 2),
    URGENT("urgent", "Khẩn cấp", 3);

    companion object {
        fun from(id: String?): Priority = entries.firstOrNull { it.id == id } ?: NORMAL
    }
}

@Immutable
data class Task(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val note: String = "",
    val status: TaskStatus = TaskStatus.TODO,
    val priority: Priority = Priority.NORMAL,
    /** So ngay tinh tu 1970-01-01 (LocalDate.toEpochDay). */
    val startDate: Long = LocalDate.now().toEpochDay(),
    val dueDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val completedAt: Long? = null
)

@Immutable
data class Board(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val accent: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val tasks: List<Task> = emptyList()
)

@Immutable
data class AppState(
    val boards: List<Board> = emptyList(),
    val loaded: Boolean = false
)

fun Board.tasksOf(status: TaskStatus): List<Task> = tasks.filter { it.status == status }

fun Board.countOf(status: TaskStatus): Int = tasks.count { it.status == status }

val Board.doneRatio: Float
    get() = if (tasks.isEmpty()) 0f else countOf(TaskStatus.DONE) / tasks.size.toFloat()

/** Task da qua han va chua hoan thanh. */
fun Task.isOverdue(today: Long = LocalDate.now().toEpochDay()): Boolean =
    status != TaskStatus.DONE && dueDate != null && dueDate < today

/** Task den han dung hom nay. */
fun Task.isDueToday(today: Long = LocalDate.now().toEpochDay()): Boolean =
    status != TaskStatus.DONE && dueDate != null && dueDate == today

fun Board.overdueCount(today: Long = LocalDate.now().toEpochDay()): Int =
    tasks.count { it.isOverdue(today) }
