package com.dgnl.taskflow.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.dgnl.taskflow.data.Priority
import com.dgnl.taskflow.data.Task
import com.dgnl.taskflow.data.TaskStatus
import com.dgnl.taskflow.data.isDueToday
import com.dgnl.taskflow.data.isOverdue
import com.dgnl.taskflow.ui.common.Pill
import com.dgnl.taskflow.ui.common.describeDue
import com.dgnl.taskflow.ui.common.formatDayShort
import com.dgnl.taskflow.ui.common.todayEpochDay
import com.dgnl.taskflow.ui.theme.Danger
import com.dgnl.taskflow.ui.theme.Line
import com.dgnl.taskflow.ui.theme.StatusDoingColor
import com.dgnl.taskflow.ui.theme.StatusDoneColor
import com.dgnl.taskflow.ui.theme.StatusTodoColor
import com.dgnl.taskflow.ui.theme.Surface2
import com.dgnl.taskflow.ui.theme.TextHi
import com.dgnl.taskflow.ui.theme.TextLow
import com.dgnl.taskflow.ui.theme.TextMid
import com.dgnl.taskflow.ui.theme.Warn
import kotlin.math.roundToInt

fun priorityColor(priority: Priority): Color = when (priority) {
    Priority.LOW -> Color(0xFF5C6B7F)
    Priority.NORMAL -> Color(0xFF4C8DFF)
    Priority.HIGH -> Warn
    Priority.URGENT -> Danger
}

fun statusColor(status: TaskStatus): Color = when (status) {
    TaskStatus.TODO -> StatusTodoColor
    TaskStatus.DOING -> StatusDoingColor
    TaskStatus.DONE -> StatusDoneColor
}

private val CardShape = RoundedCornerShape(14.dp)

/** Phan hien thi cua mot the cong viec (dung chung cho the trong cot va the dang bay theo ngon tay). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskCardContent(
    task: Task,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    val today = todayEpochDay()
    val overdue = task.isOverdue(today)
    val dueToday = task.isDueToday(today)
    val done = task.status == TaskStatus.DONE
    val bar = priorityColor(task.priority)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Surface2, CardShape)
            .border(
                width = 1.dp,
                color = if (overdue) Danger.copy(alpha = 0.45f) else Line,
                shape = CardShape
            )
            .drawBehind {
                drawRoundRect(
                    color = bar,
                    topLeft = Offset(0f, size.height * 0.2f),
                    size = Size(3.dp.toPx(), size.height * 0.6f),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
            }
            .padding(start = 13.dp, end = 6.dp, top = 10.dp, bottom = 11.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = task.title.ifBlank { "(Chưa đặt tên)" },
                style = MaterialTheme.typography.titleSmall,
                color = if (done) TextMid else TextHi,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 2.dp, end = 4.dp)
            )
            if (trailing != null) trailing()
        }

        if (task.note.isNotBlank()) {
            Spacer(Modifier.height(5.dp))
            Text(
                text = task.note,
                style = MaterialTheme.typography.bodySmall,
                color = TextLow,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 6.dp)
            )
        }

        val showStart = task.startDate > today
        val showPriority = task.priority != Priority.NORMAL
        if (task.dueDate != null || showStart || showPriority) {
            Spacer(Modifier.height(9.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(end = 6.dp)
            ) {
                task.dueDate?.let { due ->
                    val color = when {
                        done -> TextLow
                        overdue -> Danger
                        dueToday -> Warn
                        else -> TextMid
                    }
                    Pill(
                        text = if (done) formatDayShort(due) else describeDue(due, today),
                        color = color,
                        filled = !done && (overdue || dueToday),
                        leadingDot = true
                    )
                }
                if (showStart) {
                    Pill(text = "Từ ${formatDayShort(task.startDate)}", color = TextLow)
                }
                if (showPriority) {
                    Pill(text = task.priority.label, color = bar, leadingDot = true)
                }
            }
        }
    }
}

/**
 * The cong viec dat trong cot: cham de mo, giu lau de keo sang cot khac.
 */
@Composable
fun DraggableTaskCard(
    task: Task,
    drag: BoardDragState,
    onOpen: () -> Unit,
    onMove: (TaskStatus) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onDropped: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }

    DisposableEffect(task.id) {
        onDispose { drag.cardRects.remove(task.id) }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { drag.cardRects[task.id] = it.boundsInRoot() }
            .clickable(onClick = onOpen)
            .pointerInput(task.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { touch ->
                        val rect = drag.cardRects[task.id]
                        if (rect != null) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            drag.begin(
                                task = task,
                                pointerInRoot = rect.topLeft + touch,
                                grabOffset = touch,
                                size = IntSize(
                                    rect.width.roundToInt(),
                                    rect.height.roundToInt()
                                )
                            )
                        }
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        drag.move(amount)
                    },
                    onDragEnd = { onDropped() },
                    onDragCancel = { drag.cancel() }
                )
            }
    ) {
        TaskCardContent(
            task = task,
            trailing = {
                Box {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Tuỳ chọn",
                            tint = TextLow,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Chỉnh sửa") },
                            leadingIcon = {
                                Icon(Icons.Filled.Edit, null, tint = TextMid, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                menuOpen = false
                                onOpen()
                            }
                        )
                        HorizontalDivider(color = Line)
                        TaskStatus.entries.filter { it != task.status }.forEach { target ->
                            DropdownMenuItem(
                                text = { Text("Chuyển sang \"${target.label}\"") },
                                leadingIcon = {
                                    Box(
                                        Modifier
                                            .size(10.dp)
                                            .background(statusColor(target), RoundedCornerShape(5.dp))
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    onMove(target)
                                }
                            )
                        }
                        HorizontalDivider(color = Line)
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
                        DropdownMenuItem(
                            text = { Text("Xoá", color = Danger) },
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
        )
    }
}
