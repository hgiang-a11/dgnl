package com.dgnl.taskflow.ui.board

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import com.dgnl.taskflow.ui.theme.Ink
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

/** Trang thai ke tiep khi cham vao vong tron tren the: Chua lam -> Dang lam -> Da lam -> Chua lam. */
fun nextStatus(status: TaskStatus): TaskStatus = when (status) {
    TaskStatus.TODO -> TaskStatus.DOING
    TaskStatus.DOING -> TaskStatus.DONE
    TaskStatus.DONE -> TaskStatus.TODO
}

/**
 * Vong tron trang thai. Cham mot cai la doi ngay sang muc ke tiep, khong can mo man hinh sua.
 * [onCycle] null thi chi ve ra de xem (dung cho the dang bay theo ngon tay).
 */
@Composable
private fun StatusToggle(status: TaskStatus, onCycle: (() -> Unit)?) {
    val tint by animateColorAsState(
        targetValue = statusColor(status),
        animationSpec = tween(260),
        label = "statusTint"
    )
    val fill by animateFloatAsState(
        targetValue = when (status) {
            TaskStatus.TODO -> 0f
            TaskStatus.DOING -> 0.58f
            TaskStatus.DONE -> 1f
        },
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 420f),
        label = "statusFill"
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.82f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 700f),
        label = "statusScale"
    )

    Box(
        modifier = Modifier
            .size(30.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .then(
                if (onCycle != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        onClickLabel = "Đổi trạng thái",
                        onClick = onCycle
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(17.dp)) {
            val radius = size.minDimension / 2f
            val ring = 1.7.dp.toPx()
            drawCircle(
                color = tint.copy(alpha = 0.9f),
                radius = radius - ring / 2f,
                style = Stroke(width = ring)
            )
            if (fill > 0.01f) {
                drawCircle(color = tint, radius = (radius - ring * 1.6f) * fill)
            }
        }
        AnimatedVisibility(
            visible = status == TaskStatus.DONE,
            enter = scaleIn(spring(dampingRatio = 0.45f, stiffness = 600f)) + fadeIn(tween(150)),
            exit = scaleOut(tween(120)) + fadeOut(tween(120))
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = Ink,
                modifier = Modifier.size(11.dp)
            )
        }
    }
}

/** Phan hien thi cua mot the cong viec (dung chung cho the trong cot va the dang bay theo ngon tay). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskCardContent(
    task: Task,
    modifier: Modifier = Modifier,
    onCycleStatus: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val today = todayEpochDay()
    val overdue = task.isOverdue(today)
    val dueToday = task.isDueToday(today)
    val done = task.status == TaskStatus.DONE
    val bar = priorityColor(task.priority)
    val borderColor by animateColorAsState(
        targetValue = if (overdue) Danger.copy(alpha = 0.45f) else Line,
        animationSpec = tween(240),
        label = "cardBorder"
    )
    val titleColor by animateColorAsState(
        targetValue = if (done) TextMid else TextHi,
        animationSpec = tween(240),
        label = "cardTitle"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Surface2, CardShape)
            .border(width = 1.dp, color = borderColor, shape = CardShape)
            .drawBehind {
                drawRoundRect(
                    color = bar,
                    topLeft = Offset(0f, size.height * 0.2f),
                    size = Size(3.dp.toPx(), size.height * 0.6f),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
            }
            .padding(start = 7.dp, end = 6.dp, top = 8.dp, bottom = 11.dp)
    ) {
        StatusToggle(status = task.status, onCycle = onCycleStatus)

        Column(Modifier.weight(1f).padding(start = 3.dp, top = 5.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = task.title.ifBlank { "(Chưa đặt tên)" },
                    style = MaterialTheme.typography.titleSmall,
                    color = titleColor,
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
    onCycleStatus: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onDropped: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.968f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 540f),
        label = "cardScale"
    )

    DisposableEffect(task.id) {
        onDispose { drag.cardRects.remove(task.id) }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onGloballyPositioned { drag.cardRects[task.id] = it.boundsInRoot() }
            .clip(CardShape)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onOpen
            )
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
            onCycleStatus = onCycleStatus,
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
