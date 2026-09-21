package com.dgnl.taskflow.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * Kho du lieu duy nhat cua app.
 *
 * Toan bo du lieu nam trong bo nho (RAM) duoi dang [StateFlow] nen giao dien doc rat nhanh,
 * khong bao gio phai cho o cung. Moi thay doi se duoc ghi xuong mot file JSON nho o bo nho
 * rieng cua app, chay tren luong nen (IO) nen khong lam giat giao dien.
 */
class TaskRepository(context: Context, private val scope: CoroutineScope) {

    private val appContext = context.applicationContext
    private val file: File by lazy { File(appContext.filesDir, FILE_NAME) }
    private val tempFile: File by lazy { File(appContext.filesDir, "$FILE_NAME.tmp") }

    private val writeLock = Mutex()

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            val boards = runCatching { readFromDisk() }.getOrDefault(emptyList())
            _state.update { it.copy(boards = boards, loaded = true) }
        }
    }

    // ------------------------------------------------------------------
    // Danh sach (board)
    // ------------------------------------------------------------------

    fun createBoard(name: String, accent: Int): String {
        val board = Board(name = name.trim().ifEmpty { "Danh sách mới" }, accent = accent)
        mutate { it + board }
        return board.id
    }

    /** Ghi nho kieu hien thi (cot doc / hang ngang) rieng cho tung danh sach. */
    fun setBoardLayout(boardId: String, layout: BoardLayout) {
        mutate { boards -> boards.map { if (it.id == boardId) it.copy(layout = layout) else it } }
    }

    fun renameBoard(boardId: String, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        mutate { boards -> boards.map { if (it.id == boardId) it.copy(name = clean) else it } }
    }

    fun setBoardAccent(boardId: String, accent: Int) {
        mutate { boards -> boards.map { if (it.id == boardId) it.copy(accent = accent) else it } }
    }

    /** Xoa mot danh sach, tra ve (danh sach da xoa, vi tri cu) de co the hoan tac. */
    fun deleteBoard(boardId: String): Pair<Board, Int>? {
        val boards = _state.value.boards
        val index = boards.indexOfFirst { it.id == boardId }
        if (index < 0) return null
        val removed = boards[index]
        mutate { current -> current.filterNot { it.id == boardId } }
        return removed to index
    }

    fun restoreBoard(board: Board, index: Int) {
        mutate { boards ->
            val safeIndex = index.coerceIn(0, boards.size)
            boards.toMutableList().apply { add(safeIndex, board) }
        }
    }

    fun duplicateBoard(boardId: String): String? {
        val source = _state.value.boards.firstOrNull { it.id == boardId } ?: return null
        val now = System.currentTimeMillis()
        val copy = source.copy(
            id = java.util.UUID.randomUUID().toString(),
            name = "${source.name} (bản sao)",
            createdAt = now,
            tasks = source.tasks.map {
                it.copy(id = java.util.UUID.randomUUID().toString(), createdAt = now, updatedAt = now)
            }
        )
        val index = _state.value.boards.indexOfFirst { it.id == boardId }
        mutate { boards ->
            boards.toMutableList().apply { add((index + 1).coerceIn(0, size), copy) }
        }
        return copy.id
    }

    fun moveBoard(from: Int, to: Int) {
        mutate { boards ->
            if (from !in boards.indices) return@mutate boards
            val list = boards.toMutableList()
            val item = list.removeAt(from)
            list.add(to.coerceIn(0, list.size), item)
            list
        }
    }

    // ------------------------------------------------------------------
    // Cong viec (task)
    // ------------------------------------------------------------------

    fun addTask(boardId: String, task: Task, toTop: Boolean = false) {
        mutateBoard(boardId) { board ->
            val tasks = if (toTop) {
                val sameColumn = board.tasks.indexOfFirst { it.status == task.status }
                board.tasks.toMutableList().apply {
                    add(if (sameColumn >= 0) sameColumn else size, task)
                }
            } else {
                board.tasks + task
            }
            board.copy(tasks = tasks)
        }
    }

    fun updateTask(boardId: String, task: Task) {
        val stamped = task.copy(
            updatedAt = System.currentTimeMillis(),
            completedAt = when {
                task.status == TaskStatus.DONE && task.completedAt == null -> System.currentTimeMillis()
                task.status != TaskStatus.DONE -> null
                else -> task.completedAt
            }
        )
        mutateBoard(boardId) { board ->
            board.copy(tasks = board.tasks.map { if (it.id == stamped.id) stamped else it })
        }
    }

    /** Xoa mot task, tra ve (task, vi tri cu) de hoan tac. */
    fun deleteTask(boardId: String, taskId: String): Pair<Task, Int>? {
        val board = _state.value.boards.firstOrNull { it.id == boardId } ?: return null
        val index = board.tasks.indexOfFirst { it.id == taskId }
        if (index < 0) return null
        val removed = board.tasks[index]
        mutateBoard(boardId) { b -> b.copy(tasks = b.tasks.filterNot { it.id == taskId }) }
        return removed to index
    }

    fun restoreTask(boardId: String, task: Task, index: Int) {
        mutateBoard(boardId) { board ->
            val list = board.tasks.toMutableList()
            list.add(index.coerceIn(0, list.size), task)
            board.copy(tasks = list)
        }
    }

    fun duplicateTask(boardId: String, taskId: String) {
        val board = _state.value.boards.firstOrNull { it.id == boardId } ?: return
        val index = board.tasks.indexOfFirst { it.id == taskId }
        if (index < 0) return
        val now = System.currentTimeMillis()
        val copy = board.tasks[index].copy(
            id = java.util.UUID.randomUUID().toString(),
            title = board.tasks[index].title,
            createdAt = now,
            updatedAt = now
        )
        mutateBoard(boardId) { b ->
            b.copy(tasks = b.tasks.toMutableList().apply { add(index + 1, copy) })
        }
    }

    /** Doi trang thai nhanh (khong keo tha) — task se nhay xuong cuoi cot dich. */
    fun setStatus(boardId: String, taskId: String, status: TaskStatus) {
        val board = _state.value.boards.firstOrNull { it.id == boardId } ?: return
        val target = board.tasksOf(status).size
        moveTask(boardId, taskId, status, target)
    }

    /**
     * Di chuyen task sang [toStatus] tai vi tri [toIndex] (tinh trong cot dich,
     * sau khi da bo chinh task nay ra).
     */
    fun moveTask(boardId: String, taskId: String, toStatus: TaskStatus, toIndex: Int) {
        mutateBoard(boardId) { board ->
            val moving = board.tasks.firstOrNull { it.id == taskId } ?: return@mutateBoard board
            val now = System.currentTimeMillis()
            val updated = moving.copy(
                status = toStatus,
                updatedAt = now,
                completedAt = when {
                    toStatus == TaskStatus.DONE -> moving.completedAt ?: now
                    else -> null
                }
            )

            // Gom lai theo tung cot, giu nguyen thu tu hien tai.
            val grouped = LinkedHashMap<TaskStatus, MutableList<Task>>()
            TaskStatus.entries.forEach { grouped[it] = mutableListOf() }
            board.tasks.forEach { task ->
                if (task.id != taskId) grouped.getValue(task.status).add(task)
            }
            val destination = grouped.getValue(toStatus)
            destination.add(toIndex.coerceIn(0, destination.size), updated)

            val rebuilt = ArrayList<Task>(board.tasks.size)
            TaskStatus.entries.forEach { rebuilt.addAll(grouped.getValue(it)) }
            board.copy(tasks = rebuilt)
        }
    }

    /** Xoa toan bo task da hoan thanh, tra ve danh sach da xoa de hoan tac. */
    fun clearDone(boardId: String): List<Task> {
        val board = _state.value.boards.firstOrNull { it.id == boardId } ?: return emptyList()
        val removed = board.tasksOf(TaskStatus.DONE)
        if (removed.isEmpty()) return emptyList()
        mutateBoard(boardId) { b -> b.copy(tasks = b.tasks.filterNot { it.status == TaskStatus.DONE }) }
        return removed
    }

    fun restoreTasks(boardId: String, tasks: List<Task>) {
        if (tasks.isEmpty()) return
        mutateBoard(boardId) { board -> board.copy(tasks = board.tasks + tasks) }
    }

    /** Doi ngay den han cho tat ca task qua han sang hom nay. */
    fun pushOverdueToToday(boardId: String) {
        val today = LocalDate.now().toEpochDay()
        mutateBoard(boardId) { board ->
            board.copy(tasks = board.tasks.map {
                if (it.isOverdue(today)) it.copy(dueDate = today, updatedAt = System.currentTimeMillis()) else it
            })
        }
    }

    // ------------------------------------------------------------------
    // Ghi / doc file
    // ------------------------------------------------------------------

    /** Ghi ngay lap tuc (goi khi app chuyen sang chay nen). */
    fun flush() {
        scope.launch(Dispatchers.IO) {
            withContext(NonCancellable) {
                writeLock.withLock { runCatching { writeToDisk(_state.value.boards) } }
            }
        }
    }

    private inline fun mutate(crossinline block: (List<Board>) -> List<Board>) {
        _state.update { it.copy(boards = block(it.boards)) }
        persist()
    }

    private inline fun mutateBoard(boardId: String, crossinline block: (Board) -> Board) {
        mutate { boards -> boards.map { if (it.id == boardId) block(it) else it } }
    }

    private fun persist() {
        val snapshot = _state.value.boards
        scope.launch(Dispatchers.IO) {
            writeLock.withLock { runCatching { writeToDisk(snapshot) } }
        }
    }

    private fun writeToDisk(boards: List<Board>) {
        val root = JSONObject()
        root.put("version", SCHEMA_VERSION)
        val boardArray = JSONArray()
        boards.forEach { board ->
            val bo = JSONObject()
            bo.put("id", board.id)
            bo.put("name", board.name)
            bo.put("accent", board.accent)
            bo.put("layout", board.layout.id)
            bo.put("createdAt", board.createdAt)
            val taskArray = JSONArray()
            board.tasks.forEach { task ->
                val to = JSONObject()
                to.put("id", task.id)
                to.put("title", task.title)
                to.put("note", task.note)
                to.put("status", task.status.id)
                to.put("priority", task.priority.id)
                to.put("startDate", task.startDate)
                task.dueDate?.let { to.put("dueDate", it) }
                to.put("createdAt", task.createdAt)
                to.put("updatedAt", task.updatedAt)
                task.completedAt?.let { to.put("completedAt", it) }
                taskArray.put(to)
            }
            bo.put("tasks", taskArray)
            boardArray.put(bo)
        }
        root.put("boards", boardArray)

        val payload = root.toString()
        tempFile.writeText(payload)
        if (!tempFile.renameTo(file)) {
            file.writeText(payload)
            tempFile.delete()
        }
    }

    private fun readFromDisk(): List<Board> {
        if (!file.exists()) return emptyList()
        val text = file.readText()
        if (text.isBlank()) return emptyList()
        val root = JSONObject(text)
        val boardArray = root.optJSONArray("boards") ?: return emptyList()
        val boards = ArrayList<Board>(boardArray.length())
        for (i in 0 until boardArray.length()) {
            val bo = boardArray.optJSONObject(i) ?: continue
            val taskArray = bo.optJSONArray("tasks") ?: JSONArray()
            val tasks = ArrayList<Task>(taskArray.length())
            for (j in 0 until taskArray.length()) {
                val to = taskArray.optJSONObject(j) ?: continue
                val title = to.optString("title")
                val created = to.optLong("createdAt", System.currentTimeMillis())
                tasks.add(
                    Task(
                        id = to.optString("id", java.util.UUID.randomUUID().toString()),
                        title = title,
                        note = to.optString("note", ""),
                        status = TaskStatus.from(to.optString("status")),
                        priority = Priority.from(to.optString("priority")),
                        startDate = to.optLong("startDate", LocalDate.now().toEpochDay()),
                        dueDate = if (to.has("dueDate") && !to.isNull("dueDate")) to.optLong("dueDate") else null,
                        createdAt = created,
                        updatedAt = to.optLong("updatedAt", created),
                        completedAt = if (to.has("completedAt") && !to.isNull("completedAt")) {
                            to.optLong("completedAt")
                        } else {
                            null
                        }
                    )
                )
            }
            boards.add(
                Board(
                    id = bo.optString("id", java.util.UUID.randomUUID().toString()),
                    name = bo.optString("name", "Danh sách"),
                    accent = bo.optInt("accent", 0),
                    layout = BoardLayout.from(bo.optString("layout")),
                    createdAt = bo.optLong("createdAt", System.currentTimeMillis()),
                    tasks = tasks
                )
            )
        }
        return boards
    }

    private companion object {
        const val FILE_NAME = "taskflow.json"
        const val SCHEMA_VERSION = 1
    }
}
