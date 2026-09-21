package com.dgnl.taskflow.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dgnl.taskflow.data.Board
import com.dgnl.taskflow.data.Priority
import com.dgnl.taskflow.data.Task
import com.dgnl.taskflow.data.TaskRepository
import com.dgnl.taskflow.data.TaskStatus
import com.dgnl.taskflow.data.isDueToday
import com.dgnl.taskflow.data.isOverdue
import com.dgnl.taskflow.ui.common.ConfirmDialog
import com.dgnl.taskflow.ui.common.TextPromptDialog
import com.dgnl.taskflow.ui.common.appTextFieldColors
import com.dgnl.taskflow.ui.common.todayEpochDay
import com.dgnl.taskflow.ui.theme.Danger
import com.dgnl.taskflow.ui.theme.Ink
import com.dgnl.taskflow.ui.theme.Line
import com.dgnl.taskflow.ui.theme.Surface1
import com.dgnl.taskflow.ui.theme.Surface2
import com.dgnl.taskflow.ui.theme.Surface3
import com.dgnl.taskflow.ui.theme.TextHi
import com.dgnl.taskflow.ui.theme.TextLow
import com.dgnl.taskflow.ui.theme.TextMid
import com.dgnl.taskflow.ui.theme.accentAt
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class BoardFilter(val label: String) {
    ALL("Tất cả"),
    TODAY("Hôm nay"),
    OVERDUE("Quá hạn"),
    IMPORTANT("Ưu tiên cao")
}

enum class BoardSort(val label: String) {
    MANUAL("Thứ tự thủ công"),
    DUE("Hạn gần nhất"),
    PRIORITY("Mức ưu tiên"),
    NEWEST("Mới tạo trước"),
    TITLE("Tên A → Z")
}

@Composable
fun BoardScreen(
    board: Board,
    repository: TaskRepository,
    onBack: () -> Unit,
    onCreateTask: (TaskStatus) -> Unit,
    onOpenTask: (Task) -> Unit,
    onBoardDeleted: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val snackbarHostState = remember { SnackbarHostState() }
    val drag = remember { BoardDragState() }
    val accent = accentAt(board.accent)

    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(BoardFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf(BoardSort.MANUAL) }

    var showRename by rememberSaveable { mutableStateOf(false) }
    var showDeleteBoard by rememberSaveable { mutableStateOf(false) }
    var showClearDone by rememberSaveable { mutableStateOf(false) }

    val horizontalScroll = rememberScrollState()
    val todoListState = rememberLazyListState()
    val doingListState = rememberLazyListState()
    val doneListState = rememberLazyListState()
    val listStates = remember(todoListState, doingListState, doneListState) {
        mapOf(
            TaskStatus.TODO to todoListState,
            TaskStatus.DOING to doingListState,
            TaskStatus.DONE to doneListState
        )
    }

    val manualOrder = sort == BoardSort.MANUAL && query.isBlank() && filter == BoardFilter.ALL

    val columns = remember(board.tasks, query, filter, sort) {
        buildColumns(board.tasks, query, filter, sort)
    }

    SideEffect {
        drag.resolver = { status -> columns[status].orEmpty() }
    }

    // Tu dong cuon khi keo the ra sat mep man hinh.
    LaunchedEffect(drag.isDragging) {
        if (!drag.isDragging) return@LaunchedEffect
        val edgeX = with(density) { 66.dp.toPx() }
        val edgeY = with(density) { 88.dp.toPx() }
        val step = with(density) { 15.dp.toPx() }
        while (true) {
            withFrameNanos { }
            val pointer = drag.pointer
            val viewport = drag.viewport
            if (viewport.width > 1f) {
                val dx = when {
                    pointer.x < viewport.left + edgeX ->
                        -((viewport.left + edgeX - pointer.x) / edgeX).coerceIn(0f, 1f) * step
                    pointer.x > viewport.right - edgeX ->
                        ((pointer.x - (viewport.right - edgeX)) / edgeX).coerceIn(0f, 1f) * step
                    else -> 0f
                }
                if (dx != 0f) horizontalScroll.scrollBy(dx)
            }
            val status = drag.targetStatus
            val rect = status?.let { drag.columnRects[it] }
            val listState = status?.let { listStates[it] }
            if (rect != null && listState != null && rect.height > 1f) {
                val dy = when {
                    pointer.y < rect.top + edgeY ->
                        -((rect.top + edgeY - pointer.y) / edgeY).coerceIn(0f, 1f) * step
                    pointer.y > rect.bottom - edgeY ->
                        ((pointer.y - (rect.bottom - edgeY)) / edgeY).coerceIn(0f, 1f) * step
                    else -> 0f
                }
                if (dy != 0f) listState.scrollBy(dy)
            }
            drag.refresh()
        }
    }

    fun deleteTaskWithUndo(task: Task) {
        val removed = repository.deleteTask(board.id, task.id) ?: return
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Đã xoá \"${task.title}\"",
                actionLabel = "Hoàn tác",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                repository.restoreTask(board.id, removed.first, removed.second)
            }
        }
    }

    val handleDrop: () -> Unit = {
        val moving = drag.draggingTask
        val result = drag.finish()
        if (moving != null && result != null) {
            val (status, index) = result
            if (manualOrder) {
                repository.moveTask(board.id, moving.id, status, index)
            } else if (moving.status != status) {
                repository.setStatus(board.id, moving.id, status)
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
                .navigationBarsPadding()
        ) {
            BoardHeader(
                board = board,
                accent = accent,
                searchOpen = searchOpen,
                sort = sort,
                onBack = onBack,
                onToggleSearch = {
                    searchOpen = !searchOpen
                    if (!searchOpen) query = ""
                },
                onRename = { showRename = true },
                onSort = { sort = it },
                onClearDone = { showClearDone = true },
                onPushOverdue = {
                    repository.pushOverdueToToday(board.id)
                    scope.launch {
                        snackbarHostState.showSnackbar("Đã dời các việc quá hạn sang hôm nay")
                    }
                },
                onDelete = { showDeleteBoard = true }
            )

            if (searchOpen) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Tìm theo tên hoặc mô tả...") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = appTextFieldColors(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    leadingIcon = {
                        Icon(Icons.Filled.Search, null, tint = TextLow, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Close, "Xoá từ khoá", tint = TextLow, modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                )
            }

            FilterRow(
                current = filter,
                accent = accent,
                onSelect = { filter = it },
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
            )

            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned {
                        val bounds = it.boundsInRoot()
                        drag.viewport = bounds
                        drag.overlayOrigin = bounds.topLeft
                    }
            ) {
                val wide = maxWidth >= 720.dp
                val columnWidth = if (wide) {
                    (maxWidth - 32.dp - 24.dp) / 3
                } else {
                    minOf(maxWidth * 0.84f, 360.dp)
                }

                Row(
                    Modifier
                        .fillMaxSize()
                        .horizontalScroll(horizontalScroll)
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TaskStatus.entries.forEach { status ->
                        BoardColumn(
                            status = status,
                            tasks = columns[status].orEmpty(),
                            accent = accent,
                            drag = drag,
                            listState = listStates.getValue(status),
                            showIndicator = manualOrder,
                            onAdd = { onCreateTask(status) },
                            onOpenTask = onOpenTask,
                            onMoveTask = { task, target ->
                                repository.setStatus(board.id, task.id, target)
                            },
                            onDuplicateTask = { task -> repository.duplicateTask(board.id, task.id) },
                            onDeleteTask = { task -> deleteTaskWithUndo(task) },
                            onDropped = handleDrop,
                            modifier = Modifier
                                .width(columnWidth)
                                .fillMaxHeight()
                                .padding(bottom = 12.dp)
                        )
                    }
                    Spacer(Modifier.width(2.dp))
                }

                // Lop phu: the dang bay theo ngon tay.
                val flying = drag.draggingTask
                if (flying != null) {
                    val cardWidth = with(density) { drag.cardSize.width.toDp() }
                    Box(
                        Modifier
                            .offset {
                                val pointer = drag.pointer
                                IntOffset(
                                    (pointer.x - drag.grab.x - drag.overlayOrigin.x).roundToInt(),
                                    (pointer.y - drag.grab.y - drag.overlayOrigin.y).roundToInt()
                                )
                            }
                            .width(if (cardWidth > 0.dp) cardWidth else columnWidth - 20.dp)
                            .graphicsLayer {
                                scaleX = 1.04f
                                scaleY = 1.04f
                                rotationZ = 1.6f
                                alpha = 0.97f
                                shadowElevation = 22f
                                shape = RoundedCornerShape(14.dp)
                                clip = false
                            }
                    ) {
                        TaskCardContent(task = flying)
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { onCreateTask(TaskStatus.TODO) },
            containerColor = accent,
            contentColor = Color(0xFF05070C),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 18.dp, bottom = 18.dp)
        ) {
            Icon(Icons.Filled.Add, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Thêm việc", fontWeight = FontWeight.SemiBold)
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 14.dp)
        )
    }

    if (showRename) {
        TextPromptDialog(
            title = "Danh sách",
            label = "Tên danh sách",
            initialValue = board.name,
            accent = board.accent,
            onConfirm = { name, accentIndex ->
                repository.renameBoard(board.id, name)
                repository.setBoardAccent(board.id, accentIndex)
                showRename = false
            },
            onDismiss = { showRename = false }
        )
    }

    if (showClearDone) {
        ConfirmDialog(
            title = "Xoá việc đã xong?",
            message = "Toàn bộ thẻ trong cột \"Đã làm\" sẽ bị xoá khỏi danh sách này.",
            confirmLabel = "Xoá hết",
            onConfirm = {
                showClearDone = false
                val removed = repository.clearDone(board.id)
                if (removed.isNotEmpty()) {
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = "Đã xoá ${removed.size} việc",
                            actionLabel = "Hoàn tác",
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            repository.restoreTasks(board.id, removed)
                        }
                    }
                }
            },
            onDismiss = { showClearDone = false }
        )
    }

    if (showDeleteBoard) {
        ConfirmDialog(
            title = "Xoá danh sách?",
            message = "\"${board.name}\" và ${board.tasks.size} việc bên trong sẽ bị xoá.",
            onConfirm = {
                showDeleteBoard = false
                onBoardDeleted()
            },
            onDismiss = { showDeleteBoard = false }
        )
    }
}

/** Loc + sap xep, tra ve ba cot da san sang hien thi. */
private fun buildColumns(
    tasks: List<Task>,
    query: String,
    filter: BoardFilter,
    sort: BoardSort
): Map<TaskStatus, List<Task>> {
    val today = todayEpochDay()
    val keyword = query.trim().lowercase()

    val filtered = tasks.filter { task ->
        val matchesKeyword = keyword.isEmpty() ||
            task.title.lowercase().contains(keyword) ||
            task.note.lowercase().contains(keyword)
        val matchesFilter = when (filter) {
            BoardFilter.ALL -> true
            BoardFilter.TODAY -> task.isDueToday(today) || task.startDate == today
            BoardFilter.OVERDUE -> task.isOverdue(today)
            BoardFilter.IMPORTANT -> task.priority.level >= Priority.HIGH.level
        }
        matchesKeyword && matchesFilter
    }

    val comparator: Comparator<Task>? = when (sort) {
        BoardSort.MANUAL -> null
        BoardSort.DUE -> compareBy<Task> { it.dueDate ?: Long.MAX_VALUE }
            .thenByDescending { it.priority.level }
        BoardSort.PRIORITY -> compareByDescending<Task> { it.priority.level }
            .thenBy { it.dueDate ?: Long.MAX_VALUE }
        BoardSort.NEWEST -> compareByDescending<Task> { it.createdAt }
        BoardSort.TITLE -> compareBy<Task> { it.title.lowercase() }
    }

    val result = LinkedHashMap<TaskStatus, List<Task>>(3)
    TaskStatus.entries.forEach { status ->
        val column = filtered.filter { it.status == status }
        result[status] = if (comparator == null) column else column.sortedWith(comparator)
    }
    return result
}

@Composable
private fun BoardHeader(
    board: Board,
    accent: Color,
    searchOpen: Boolean,
    sort: BoardSort,
    onBack: () -> Unit,
    onToggleSearch: () -> Unit,
    onRename: () -> Unit,
    onSort: (BoardSort) -> Unit,
    onClearDone: () -> Unit,
    onPushOverdue: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    val today = todayEpochDay()
    val overdue = board.tasks.count { it.isOverdue(today) }
    val done = board.tasks.count { it.status == TaskStatus.DONE }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 8.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại", tint = TextMid)
        }
        Column(
            Modifier
                .weight(1f)
                .padding(end = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(9.dp)
                        .background(accent, CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = board.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextHi,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = buildString {
                    append("${board.tasks.size} việc")
                    append(" · ")
                    append("$done đã xong")
                    if (overdue > 0) append(" · $overdue quá hạn")
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (overdue > 0) Danger else TextLow,
                maxLines = 1,
                modifier = Modifier.padding(start = 17.dp)
            )
        }

        IconButton(onClick = onToggleSearch) {
            Icon(
                imageVector = if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
                contentDescription = "Tìm kiếm",
                tint = if (searchOpen) accent else TextMid
            )
        }

        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, "Tuỳ chọn danh sách", tint = TextMid)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Đổi tên / đổi màu") },
                    leadingIcon = { Icon(Icons.Filled.Edit, null, tint = TextMid, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        menuOpen = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Sắp xếp: ${sort.label}") },
                    leadingIcon = { Icon(Icons.Filled.KeyboardArrowDown, null, tint = TextMid, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        menuOpen = false
                        sortOpen = true
                    }
                )
                DropdownMenuItem(
                    text = { Text("Dời việc quá hạn sang hôm nay") },
                    leadingIcon = { Icon(Icons.Filled.DateRange, null, tint = TextMid, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        menuOpen = false
                        onPushOverdue()
                    }
                )
                HorizontalDivider(color = Line)
                DropdownMenuItem(
                    text = { Text("Xoá việc đã xong") },
                    leadingIcon = { Icon(Icons.Filled.Delete, null, tint = TextMid, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        menuOpen = false
                        onClearDone()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Xoá danh sách", color = Danger) },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Delete,
                            null,
                            tint = Danger,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    }
                )
            }
            DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                BoardSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                option.label,
                                color = if (option == sort) accent else TextHi
                            )
                        },
                        onClick = {
                            sortOpen = false
                            onSort(option)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterRow(
    current: BoardFilter,
    accent: Color,
    onSelect: (BoardFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BoardFilter.entries.forEach { option ->
            val selected = option == current
            Box(
                Modifier
                    .background(
                        color = if (selected) accent.copy(alpha = 0.18f) else Surface1,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = if (selected) accent.copy(alpha = 0.55f) else Line,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) accent else TextMid
                )
            }
        }
    }
}

@Composable
private fun BoardColumn(
    status: TaskStatus,
    tasks: List<Task>,
    accent: Color,
    drag: BoardDragState,
    listState: LazyListState,
    showIndicator: Boolean,
    onAdd: () -> Unit,
    onOpenTask: (Task) -> Unit,
    onMoveTask: (Task, TaskStatus) -> Unit,
    onDuplicateTask: (Task) -> Unit,
    onDeleteTask: (Task) -> Unit,
    onDropped: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusTint = statusColor(status)
    val draggingId = drag.draggingTask?.id
    val isTarget = draggingId != null && drag.targetStatus == status
    val visibleTasks = remember(tasks, draggingId) {
        if (draggingId == null) tasks else tasks.filterNot { it.id == draggingId }
    }

    DisposableEffect(status) {
        onDispose { drag.columnRects.remove(status) }
    }

    Column(
        modifier = modifier
            .onGloballyPositioned { drag.columnRects[status] = it.boundsInRoot() }
            .background(Surface1, RoundedCornerShape(18.dp))
            .border(
                width = if (isTarget) 1.5.dp else 1.dp,
                color = if (isTarget) accent.copy(alpha = 0.7f) else Line,
                shape = RoundedCornerShape(18.dp)
            )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(statusTint, CircleShape)
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = status.label,
                style = MaterialTheme.typography.titleSmall,
                color = TextHi,
                modifier = Modifier.weight(1f)
            )
            Box(
                Modifier
                    .background(Surface3, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = tasks.size.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMid
                )
            }
            IconButton(onClick = onAdd, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Filled.Add, "Thêm việc vào ${status.label}", tint = TextMid, modifier = Modifier.size(19.dp))
            }
        }

        HorizontalDivider(color = Line)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (visibleTasks.isEmpty()) {
                item(key = "__empty") {
                    EmptyColumnHint(isTarget = isTarget, accent = accent)
                }
            }

            itemsIndexed(visibleTasks, key = { _, task -> task.id }) { index, task ->
                Column(Modifier.fillMaxWidth()) {
                    if (isTarget && showIndicator && drag.targetIndex == index) {
                        DropIndicator(accent)
                    }
                    DraggableTaskCard(
                        task = task,
                        drag = drag,
                        onOpen = { onOpenTask(task) },
                        onMove = { target -> onMoveTask(task, target) },
                        onDuplicate = { onDuplicateTask(task) },
                        onDelete = { onDeleteTask(task) },
                        onDropped = onDropped
                    )
                }
            }

            if (isTarget && showIndicator && drag.targetIndex >= visibleTasks.size && visibleTasks.isNotEmpty()) {
                item(key = "__tail") { DropIndicator(accent) }
            }

            item(key = "__add") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                        .border(1.dp, Line, RoundedCornerShape(12.dp))
                        .background(Color.Transparent, RoundedCornerShape(12.dp))
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Add, null, tint = TextLow, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Thêm việc",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextLow
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DropIndicator(accent: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(3.dp)
            .background(accent, RoundedCornerShape(2.dp))
    )
}

@Composable
private fun EmptyColumnHint(isTarget: Boolean, accent: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(96.dp)
            .background(
                color = if (isTarget) accent.copy(alpha = 0.08f) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = 1.dp,
                color = if (isTarget) accent.copy(alpha = 0.6f) else Surface2,
                shape = RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isTarget) "Thả vào đây" else "Chưa có việc nào",
            style = MaterialTheme.typography.labelMedium,
            color = if (isTarget) accent else TextLow
        )
    }
}
