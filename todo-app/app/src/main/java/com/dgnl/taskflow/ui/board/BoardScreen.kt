package com.dgnl.taskflow.ui.board

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
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

/** Hai kieu sap xep ba muc cong viec tren man hinh. */
enum class BoardLayout(val label: String) {
    /** Ba cot doc canh nhau, vuot ngang de doi cot. */
    COLUMNS("Cột dọc"),

    /** Ba hang ngang xep chong len nhau, tu chia chieu cao de khong phai luot doc. */
    ROWS("Hàng ngang")
}

/**
 * Chieu cao tuong doi cua moi hang o che do [BoardLayout.ROWS].
 * Hang trong chi can cho hien goi y nen lay it cho, hang nhieu viec duoc rong hon.
 */
private fun laneWeight(taskCount: Int): Float = when {
    taskCount == 0 -> 0.72f
    taskCount <= 2 -> 1f
    taskCount <= 5 -> 1.12f
    else -> 1.24f
}

/** Do truot can bu khi ngon tay cham sat mep mot vung. */
private fun edgeScroll(value: Float, min: Float, max: Float, edge: Float, step: Float): Float = when {
    value < min + edge -> -((min + edge - value) / edge).coerceIn(0f, 1f) * step
    value > max - edge -> ((value - (max - edge)) / edge).coerceIn(0f, 1f) * step
    else -> 0f
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
    var layout by rememberSaveable { mutableStateOf(BoardLayout.COLUMNS) }

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

    val rowsMode = layout == BoardLayout.ROWS

    SideEffect {
        drag.resolver = { status -> columns[status].orEmpty() }
        drag.horizontal = rowsMode
    }

    // Tu dong cuon khi keo the ra sat mep man hinh.
    LaunchedEffect(drag.isDragging, rowsMode) {
        if (!drag.isDragging) return@LaunchedEffect
        val edgeX = with(density) { 66.dp.toPx() }
        val edgeY = with(density) { 88.dp.toPx() }
        val step = with(density) { 15.dp.toPx() }
        while (true) {
            withFrameNanos { }
            val pointer = drag.pointer
            val status = drag.targetStatus
            val rect = status?.let { drag.columnRects[it] }
            val listState = status?.let { listStates[it] }
            if (rowsMode) {
                // Ba hang deu nam tron trong man hinh: chi can cuon ngang trong hang dang nham toi.
                if (rect != null && listState != null && rect.width > 1f) {
                    val dx = edgeScroll(pointer.x, rect.left, rect.right, edgeX, step)
                    if (dx != 0f) listState.scrollBy(dx)
                }
            } else {
                val viewport = drag.viewport
                if (viewport.width > 1f) {
                    val dx = edgeScroll(pointer.x, viewport.left, viewport.right, edgeX, step)
                    if (dx != 0f) horizontalScroll.scrollBy(dx)
                }
                if (rect != null && listState != null && rect.height > 1f) {
                    val dy = edgeScroll(pointer.y, rect.top, rect.bottom, edgeY, step)
                    if (dy != 0f) listState.scrollBy(dy)
                }
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

    fun cycleStatus(task: Task) {
        repository.setStatus(board.id, task.id, nextStatus(task.status))
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
                layout = layout,
                onToggleLayout = {
                    layout = if (layout == BoardLayout.ROWS) BoardLayout.COLUMNS else BoardLayout.ROWS
                },
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

                if (rowsMode) {
                    // Ba hang chia nhau dung chieu cao man hinh nen khong phai luot len xuong.
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp)
                            .padding(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TaskStatus.entries.forEach { status ->
                            val laneTasks = columns[status].orEmpty()
                            BoardLane(
                                status = status,
                                tasks = laneTasks,
                                accent = accent,
                                drag = drag,
                                listState = listStates.getValue(status),
                                showIndicator = manualOrder,
                                horizontal = true,
                                onAdd = { onCreateTask(status) },
                                onOpenTask = onOpenTask,
                                onMoveTask = { task, target ->
                                    repository.setStatus(board.id, task.id, target)
                                },
                                onCycleTask = { task -> cycleStatus(task) },
                                onDuplicateTask = { task -> repository.duplicateTask(board.id, task.id) },
                                onDeleteTask = { task -> deleteTaskWithUndo(task) },
                                onDropped = handleDrop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(laneWeight(laneTasks.size))
                            )
                        }
                    }
                } else {
                    Row(
                        Modifier
                            .fillMaxSize()
                            .horizontalScroll(horizontalScroll)
                            .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TaskStatus.entries.forEach { status ->
                            BoardLane(
                                status = status,
                                tasks = columns[status].orEmpty(),
                                accent = accent,
                                drag = drag,
                                listState = listStates.getValue(status),
                                showIndicator = manualOrder,
                                horizontal = false,
                                onAdd = { onCreateTask(status) },
                                onOpenTask = onOpenTask,
                                onMoveTask = { task, target ->
                                    repository.setStatus(board.id, task.id, target)
                                },
                                onCycleTask = { task -> cycleStatus(task) },
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
                }

                // Lop phu: the dang bay theo ngon tay.
                val flying = drag.draggingTask
                if (flying != null) {
                    val cardWidth = with(density) { drag.cardSize.width.toDp() }
                    val lift by animateFloatAsState(
                        targetValue = 1f,
                        animationSpec = spring(dampingRatio = 0.45f, stiffness = 480f),
                        label = "dragLift"
                    )
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
                                val pop = 1f + 0.055f * lift
                                scaleX = pop
                                scaleY = pop
                                rotationZ = 2.2f * lift
                                alpha = 0.97f
                                shadowElevation = 26f * lift
                                shape = RoundedCornerShape(14.dp)
                                clip = false
                            }
                    ) {
                        TaskCardContent(task = flying)
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
    layout: BoardLayout,
    onToggleLayout: () -> Unit,
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

        IconButton(onClick = onToggleLayout) {
            LayoutGlyph(
                rows = layout == BoardLayout.ROWS,
                tint = if (layout == BoardLayout.ROWS) accent else TextMid
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

/** Bieu tuong ba thanh, xoay 90 do khi doi qua lai giua kieu cot doc va kieu hang ngang. */
@Composable
private fun LayoutGlyph(rows: Boolean, tint: Color) {
    val turn by animateFloatAsState(
        targetValue = if (rows) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 320f),
        label = "layoutTurn"
    )
    val color by animateColorAsState(targetValue = tint, animationSpec = tween(240), label = "layoutTint")
    Canvas(
        Modifier
            .size(18.dp)
            .graphicsLayer { rotationZ = turn * 90f }
    ) {
        val gap = size.width * 0.16f
        val barWidth = (size.width - gap * 2f) / 3f
        repeat(3) { index ->
            drawRoundRect(
                color = color,
                topLeft = Offset(index * (barWidth + gap), 0f),
                size = Size(barWidth, size.height),
                cornerRadius = CornerRadius(barWidth * 0.4f)
            )
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
            FilterChip(
                label = option.label,
                selected = option == current,
                accent = accent,
                onClick = { onSelect(option) }
            )
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    val background by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.2f) else Surface1,
        animationSpec = tween(220),
        label = "chipBg"
    )
    val outline by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.6f) else Line,
        animationSpec = tween(220),
        label = "chipOutline"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) accent else TextMid,
        animationSpec = tween(220),
        label = "chipText"
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 650f),
        label = "chipScale"
    )

    Box(
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(background)
            .border(width = 1.dp, color = outline, shape = shape)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = textColor
        )
    }
}

/** Be rong co dinh cua the khi bang o che do hang ngang. */
private val RowCardWidth = 212.dp

@Composable
private fun BoardLane(
    status: TaskStatus,
    tasks: List<Task>,
    accent: Color,
    drag: BoardDragState,
    listState: LazyListState,
    showIndicator: Boolean,
    horizontal: Boolean,
    onAdd: () -> Unit,
    onOpenTask: (Task) -> Unit,
    onMoveTask: (Task, TaskStatus) -> Unit,
    onCycleTask: (Task) -> Unit,
    onDuplicateTask: (Task) -> Unit,
    onDeleteTask: (Task) -> Unit,
    onDropped: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    val draggingId = drag.draggingTask?.id
    val isTarget = draggingId != null && drag.targetStatus == status
    val visibleTasks = remember(tasks, draggingId) {
        if (draggingId == null) tasks else tasks.filterNot { it.id == draggingId }
    }

    val laneBackground by animateColorAsState(
        targetValue = if (isTarget) accent.copy(alpha = 0.07f) else Surface1,
        animationSpec = tween(220),
        label = "laneBg"
    )
    val laneOutline by animateColorAsState(
        targetValue = if (isTarget) accent.copy(alpha = 0.75f) else Line,
        animationSpec = tween(200),
        label = "laneOutline"
    )
    val laneOutlineWidth by animateDpAsState(
        targetValue = if (isTarget) 1.8.dp else 1.dp,
        animationSpec = tween(200),
        label = "laneOutlineWidth"
    )

    DisposableEffect(status, horizontal) {
        onDispose { drag.columnRects.remove(status) }
    }

    Column(
        modifier = modifier
            .onGloballyPositioned { drag.columnRects[status] = it.boundsInRoot() }
            .clip(shape)
            .background(laneBackground)
            .border(width = laneOutlineWidth, color = laneOutline, shape = shape)
    ) {
        LaneHeader(
            status = status,
            count = tasks.size,
            compact = horizontal,
            onAdd = onAdd
        )

        HorizontalDivider(color = Line)

        if (horizontal) {
            LazyRow(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                if (visibleTasks.isEmpty()) {
                    item(key = "__empty") {
                        EmptyLaneHint(isTarget = isTarget, accent = accent, horizontal = true)
                    }
                }

                itemsIndexed(visibleTasks, key = { _, task -> task.id }) { index, task ->
                    Row(Modifier.animateItem(), verticalAlignment = Alignment.Top) {
                        if (isTarget && showIndicator && drag.targetIndex == index) {
                            DropIndicator(accent = accent, horizontal = true)
                        }
                        DraggableTaskCard(
                            task = task,
                            drag = drag,
                            onOpen = { onOpenTask(task) },
                            onMove = { target -> onMoveTask(task, target) },
                            onCycleStatus = { onCycleTask(task) },
                            onDuplicate = { onDuplicateTask(task) },
                            onDelete = { onDeleteTask(task) },
                            onDropped = onDropped,
                            modifier = Modifier.width(RowCardWidth)
                        )
                    }
                }

                if (isTarget && showIndicator && drag.targetIndex >= visibleTasks.size && visibleTasks.isNotEmpty()) {
                    item(key = "__tail") { DropIndicator(accent = accent, horizontal = true) }
                }

                item(key = "__add") {
                    AddTaskTile(
                        onClick = onAdd,
                        modifier = Modifier.width(132.dp).fillMaxHeight()
                    )
                }
            }
        } else {
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
                        EmptyLaneHint(isTarget = isTarget, accent = accent, horizontal = false)
                    }
                }

                itemsIndexed(visibleTasks, key = { _, task -> task.id }) { index, task ->
                    Column(Modifier.fillMaxWidth().animateItem()) {
                        if (isTarget && showIndicator && drag.targetIndex == index) {
                            DropIndicator(accent = accent, horizontal = false)
                        }
                        DraggableTaskCard(
                            task = task,
                            drag = drag,
                            onOpen = { onOpenTask(task) },
                            onMove = { target -> onMoveTask(task, target) },
                            onCycleStatus = { onCycleTask(task) },
                            onDuplicate = { onDuplicateTask(task) },
                            onDelete = { onDeleteTask(task) },
                            onDropped = onDropped
                        )
                    }
                }

                if (isTarget && showIndicator && drag.targetIndex >= visibleTasks.size && visibleTasks.isNotEmpty()) {
                    item(key = "__tail") { DropIndicator(accent = accent, horizontal = false) }
                }

                item(key = "__add") {
                    AddTaskTile(
                        onClick = onAdd,
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LaneHeader(
    status: TaskStatus,
    count: Int,
    compact: Boolean,
    onAdd: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = 14.dp,
                end = 6.dp,
                top = if (compact) 5.dp else 12.dp,
                bottom = if (compact) 3.dp else 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(statusColor(status), CircleShape)
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
            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInVertically { height -> height } + fadeIn(tween(160)))
                            .togetherWith(slideOutVertically { height -> -height } + fadeOut(tween(160)))
                    } else {
                        (slideInVertically { height -> -height } + fadeIn(tween(160)))
                            .togetherWith(slideOutVertically { height -> height } + fadeOut(tween(160)))
                    }
                },
                label = "laneCount"
            ) { value ->
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMid
                )
            }
        }
        IconButton(onClick = onAdd, modifier = Modifier.size(if (compact) 30.dp else 34.dp)) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Thêm việc vào ${status.label}",
                tint = TextMid,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

/** O "Them viec" o cuoi moi lan — bam duoc that su. */
@Composable
private fun AddTaskTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 600f),
        label = "addTileScale"
    )

    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .border(1.dp, Line, shape)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .padding(vertical = 11.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Add, null, tint = TextLow, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Thêm việc",
                style = MaterialTheme.typography.labelMedium,
                color = TextLow,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DropIndicator(accent: Color, horizontal: Boolean) {
    if (horizontal) {
        Box(
            Modifier
                .padding(horizontal = 3.dp)
                .width(3.dp)
                .height(58.dp)
                .background(accent, RoundedCornerShape(2.dp))
        )
    } else {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .height(3.dp)
                .background(accent, RoundedCornerShape(2.dp))
        )
    }
}

@Composable
private fun EmptyLaneHint(isTarget: Boolean, accent: Color, horizontal: Boolean) {
    val shape = RoundedCornerShape(12.dp)
    val background by animateColorAsState(
        targetValue = if (isTarget) accent.copy(alpha = 0.1f) else Color.Transparent,
        animationSpec = tween(220),
        label = "emptyBg"
    )
    val outline by animateColorAsState(
        targetValue = if (isTarget) accent.copy(alpha = 0.6f) else Surface2,
        animationSpec = tween(220),
        label = "emptyOutline"
    )
    val textColor by animateColorAsState(
        targetValue = if (isTarget) accent else TextLow,
        animationSpec = tween(220),
        label = "emptyText"
    )

    Box(
        modifier = Modifier
            .then(
                if (horizontal) {
                    Modifier.width(RowCardWidth).fillMaxHeight()
                } else {
                    Modifier.fillMaxWidth().height(96.dp)
                }
            )
            .clip(shape)
            .background(background)
            .border(width = 1.dp, color = outline, shape = shape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isTarget) "Thả vào đây" else "Chưa có việc nào",
            style = MaterialTheme.typography.labelMedium,
            color = textColor
        )
    }
}
