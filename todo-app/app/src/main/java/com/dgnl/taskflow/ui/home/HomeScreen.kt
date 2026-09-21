package com.dgnl.taskflow.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dgnl.taskflow.data.Board
import com.dgnl.taskflow.data.TaskRepository
import com.dgnl.taskflow.data.TaskStatus
import com.dgnl.taskflow.data.countOf
import com.dgnl.taskflow.data.doneRatio
import com.dgnl.taskflow.data.isOverdue
import com.dgnl.taskflow.ui.common.ConfirmDialog
import com.dgnl.taskflow.ui.common.EmptyState
import com.dgnl.taskflow.ui.common.SearchHint
import com.dgnl.taskflow.ui.common.SearchOverlay
import com.dgnl.taskflow.ui.common.Pill
import com.dgnl.taskflow.ui.common.StatTile
import com.dgnl.taskflow.ui.common.TaskProgressBar
import com.dgnl.taskflow.ui.common.TextPromptDialog
import com.dgnl.taskflow.ui.common.formatToday
import com.dgnl.taskflow.ui.common.todayEpochDay
import com.dgnl.taskflow.ui.theme.Danger
import com.dgnl.taskflow.ui.theme.Ink
import com.dgnl.taskflow.ui.theme.Line
import com.dgnl.taskflow.ui.theme.StatusDoingColor
import com.dgnl.taskflow.ui.theme.StatusDoneColor
import com.dgnl.taskflow.ui.theme.StatusTodoColor
import com.dgnl.taskflow.ui.theme.Surface1
import com.dgnl.taskflow.ui.theme.TextHi
import com.dgnl.taskflow.ui.theme.TextLow
import com.dgnl.taskflow.ui.theme.TextMid
import com.dgnl.taskflow.ui.theme.accentAt
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    boards: List<Board>,
    repository: TaskRepository,
    onOpenBoard: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var renamingId by remember { mutableStateOf<String?>(null) }
    var deletingId by remember { mutableStateOf<String?>(null) }

    val today = todayEpochDay()
    val totalTasks = boards.sumOf { it.tasks.size }
    val doingTasks = boards.sumOf { board -> board.countOf(TaskStatus.DOING) }
    val overdueTasks = boards.sumOf { board -> board.tasks.count { it.isOverdue(today) } }

    val visibleBoards = remember(boards, query) {
        val keyword = query.trim().lowercase()
        if (keyword.isEmpty()) boards else boards.filter { it.name.lowercase().contains(keyword) }
    }

    fun deleteBoardWithUndo(boardId: String) {
        val removed = repository.deleteBoard(boardId) ?: return
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Đã xoá \"${removed.first.name}\"",
                actionLabel = "Hoàn tác",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                repository.restoreBoard(removed.first, removed.second)
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 10.dp, top = 14.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Công việc",
                        style = MaterialTheme.typography.displaySmall,
                        color = TextHi
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = formatToday(),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextLow
                    )
                }
                IconButton(
                    onClick = {
                        searchOpen = !searchOpen
                        if (!searchOpen) query = ""
                    }
                ) {
                    Icon(
                        imageVector = if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
                        contentDescription = "Tìm danh sách",
                        tint = if (searchOpen) accentAt(0) else TextMid
                    )
                }
                IconButton(onClick = { showCreate = true }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Tạo danh sách mới",
                        tint = accentAt(0)
                    )
                }
            }

            if (boards.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatTile(
                        value = totalTasks.toString(),
                        label = "Tổng việc",
                        color = TextHi,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        value = doingTasks.toString(),
                        label = "Đang làm",
                        color = StatusDoingColor,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        value = overdueTasks.toString(),
                        label = "Quá hạn",
                        color = if (overdueTasks > 0) Danger else TextMid,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (visibleBoards.isEmpty()) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (boards.isEmpty()) {
                        EmptyState(
                            title = "Chưa có danh sách nào",
                            message = "Tạo danh sách đầu tiên để bắt đầu sắp xếp công việc theo ba cột: chưa làm, đang làm, đã làm.",
                            action = {
                                Button(
                                    onClick = { showCreate = true },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = accentAt(0),
                                        contentColor = Color(0xFF05070C)
                                    )
                                ) {
                                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Tạo danh sách", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        )
                    } else {
                        EmptyState(
                            title = "Không tìm thấy",
                            message = "Không có danh sách nào khớp với \"${query.trim()}\"."
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(visibleBoards, key = { it.id }) { board ->
                        val index = boards.indexOfFirst { it.id == board.id }
                        BoardCard(
                            board = board,
                            today = today,
                            canMoveUp = index > 0,
                            canMoveDown = index >= 0 && index < boards.lastIndex,
                            onOpen = { onOpenBoard(board.id) },
                            onRename = { renamingId = board.id },
                            onDuplicate = { repository.duplicateBoard(board.id) },
                            onMoveUp = { if (index > 0) repository.moveBoard(index, index - 1) },
                            onMoveDown = {
                                if (index >= 0 && index < boards.lastIndex) {
                                    repository.moveBoard(index, index + 1)
                                }
                            },
                            onDelete = { deletingId = board.id }
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 14.dp)
        )

        // Man hinh tim kiem: o trang chu chi tim ten cac danh sach.
        if (searchOpen) {
            SearchOverlay(
                placeholder = "Tìm danh sách...",
                query = query,
                accent = accentAt(0),
                onQueryChange = { query = it },
                onClose = {
                    searchOpen = false
                    query = ""
                }
            ) {
                when {
                    query.isBlank() -> SearchHint("Gõ tên danh sách bạn muốn tìm.")
                    visibleBoards.isEmpty() -> SearchHint("Không có danh sách nào tên giống \"${query.trim()}\".")
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        items(visibleBoards, key = { it.id }) { board ->
                            BoardSearchRow(
                                board = board,
                                today = today,
                                onOpen = {
                                    searchOpen = false
                                    query = ""
                                    onOpenBoard(board.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        TextPromptDialog(
            title = "Danh sách mới",
            label = "Tên danh sách",
            confirmLabel = "Tạo",
            accent = boards.size % 7,
            onConfirm = { name, accentIndex ->
                showCreate = false
                val id = repository.createBoard(name, accentIndex)
                onOpenBoard(id)
            },
            onDismiss = { showCreate = false }
        )
    }

    val renaming = renamingId?.let { id -> boards.firstOrNull { it.id == id } }
    if (renaming != null) {
        TextPromptDialog(
            title = "Đổi tên danh sách",
            label = "Tên danh sách",
            initialValue = renaming.name,
            accent = renaming.accent,
            onConfirm = { name, accentIndex ->
                repository.renameBoard(renaming.id, name)
                repository.setBoardAccent(renaming.id, accentIndex)
                renamingId = null
            },
            onDismiss = { renamingId = null }
        )
    }

    val deleting = deletingId?.let { id -> boards.firstOrNull { it.id == id } }
    if (deleting != null) {
        ConfirmDialog(
            title = "Xoá danh sách?",
            message = "\"${deleting.name}\" và ${deleting.tasks.size} việc bên trong sẽ bị xoá.",
            onConfirm = {
                val id = deleting.id
                deletingId = null
                deleteBoardWithUndo(id)
            },
            onDismiss = { deletingId = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoardCard(
    board: Board,
    today: Long,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val accent = accentAt(board.accent)
    val todo = board.countOf(TaskStatus.TODO)
    val doing = board.countOf(TaskStatus.DOING)
    val done = board.countOf(TaskStatus.DONE)
    val overdue = board.tasks.count { it.isOverdue(today) }
    val ratio = board.doneRatio

    Column(
        Modifier
            .fillMaxWidth()
            .background(Surface1, RoundedCornerShape(18.dp))
            .border(1.dp, Line, RoundedCornerShape(18.dp))
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, end = 8.dp, top = 15.dp, bottom = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(accent, CircleShape)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = board.name,
                style = MaterialTheme.typography.titleMedium,
                color = TextHi,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Filled.MoreVert,
                        "Tuỳ chọn",
                        tint = TextLow,
                        modifier = Modifier.size(19.dp)
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Đổi tên / đổi màu") },
                        leadingIcon = {
                            Icon(Icons.Filled.Edit, null, tint = TextMid, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            menuOpen = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Nhân bản") },
                        leadingIcon = {
                            Icon(Icons.Filled.Add, null, tint = TextMid, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            menuOpen = false
                            onDuplicate()
                        }
                    )
                    if (canMoveUp) {
                        DropdownMenuItem(
                            text = { Text("Đưa lên trên") },
                            leadingIcon = {
                                Icon(Icons.Filled.KeyboardArrowUp, null, tint = TextMid, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                menuOpen = false
                                onMoveUp()
                            }
                        )
                    }
                    if (canMoveDown) {
                        DropdownMenuItem(
                            text = { Text("Đưa xuống dưới") },
                            leadingIcon = {
                                Icon(Icons.Filled.KeyboardArrowDown, null, tint = TextMid, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                menuOpen = false
                                onMoveDown()
                            }
                        )
                    }
                    HorizontalDivider(color = Line)
                    DropdownMenuItem(
                        text = { Text("Xoá danh sách", color = Danger) },
                        leadingIcon = {
                            Icon(Icons.Filled.Delete, null, tint = Danger, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            TaskProgressBar(
                doneRatio = ratio,
                doingRatio = if (board.tasks.isEmpty()) 0f else doing / board.tasks.size.toFloat(),
                accent = accent,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "${(ratio * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = TextMid,
                modifier = Modifier.padding(end = 8.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(end = 8.dp)
        ) {
            Pill(text = "$todo chưa làm", color = StatusTodoColor, leadingDot = true)
            Pill(text = "$doing đang làm", color = StatusDoingColor, leadingDot = true)
            Pill(text = "$done đã làm", color = StatusDoneColor, leadingDot = true)
        }

        if (overdue > 0) {
            Spacer(Modifier.height(10.dp))
            Pill(text = "$overdue việc quá hạn", color = Danger, filled = true, leadingDot = true)
        }
    }
}

/** Mot dong ket qua khi tim danh sach. */
@Composable
private fun BoardSearchRow(
    board: Board,
    today: Long,
    onOpen: () -> Unit
) {
    val done = board.countOf(TaskStatus.DONE)
    val overdue = board.tasks.count { it.isOverdue(today) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(10.dp)
                .background(accentAt(board.accent), CircleShape)
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = board.name,
                style = MaterialTheme.typography.titleSmall,
                color = TextHi,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildString {
                    append("${board.tasks.size} việc · $done đã xong")
                    if (overdue > 0) append(" · $overdue quá hạn")
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (overdue > 0) Danger else TextLow,
                maxLines = 1
            )
        }
    }
}
