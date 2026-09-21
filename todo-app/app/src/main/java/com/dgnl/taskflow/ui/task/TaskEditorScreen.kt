package com.dgnl.taskflow.ui.task

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dgnl.taskflow.data.Priority
import com.dgnl.taskflow.data.Task
import com.dgnl.taskflow.data.TaskStatus
import com.dgnl.taskflow.ui.board.priorityColor
import com.dgnl.taskflow.ui.board.statusColor
import com.dgnl.taskflow.ui.common.ConfirmDialog
import com.dgnl.taskflow.ui.common.appTextFieldColors
import com.dgnl.taskflow.ui.common.epochDayToMillisUtc
import com.dgnl.taskflow.ui.common.formatDateTime
import com.dgnl.taskflow.ui.common.formatDay
import com.dgnl.taskflow.ui.common.millisUtcToEpochDay
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

private const val NO_DATE = Long.MIN_VALUE

/**
 * Man hinh nhap/sua mot cong viec.
 *
 * Neu khong chon ngay bat dau thi mac dinh la ngay tao task (hom nay).
 */
@Composable
fun TaskEditorScreen(
    boardName: String,
    accent: Color,
    existing: Task?,
    defaultStatus: TaskStatus,
    onSave: (Task) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    val editing = existing != null

    var title by rememberSaveable(existing?.id) { mutableStateOf(existing?.title ?: "") }
    var note by rememberSaveable(existing?.id) { mutableStateOf(existing?.note ?: "") }
    var status by rememberSaveable(existing?.id) { mutableStateOf(existing?.status ?: defaultStatus) }
    var priority by rememberSaveable(existing?.id) { mutableStateOf(existing?.priority ?: Priority.NORMAL) }
    var startDate by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.startDate ?: todayEpochDay())
    }
    var dueDate by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.dueDate ?: NO_DATE)
    }

    var pickingStart by rememberSaveable { mutableStateOf(false) }
    var pickingDue by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val canSave = title.isNotBlank()

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .statusBarsPadding()
            .imePadding()
    ) {
        // Thanh tieu de
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, "Đóng", tint = TextMid)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (editing) "Chỉnh sửa việc" else "Việc mới",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextHi
                )
                Text(
                    text = boardName,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextLow,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (editing) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, "Xoá việc", tint = Danger)
                }
            }
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(6.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { if (it.length <= 140) title = it },
                label = { Text("Tên việc") },
                placeholder = { Text("Ví dụ: Ôn tập chương 3") },
                singleLine = false,
                maxLines = 3,
                shape = RoundedCornerShape(14.dp),
                colors = appTextFieldColors(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= 2000) note = it },
                label = { Text("Mô tả") },
                placeholder = { Text("Ghi chú chi tiết, các bước cần làm...") },
                shape = RoundedCornerShape(14.dp),
                colors = appTextFieldColors(),
                minLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(22.dp))
            SectionTitle("Trạng thái")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskStatus.entries.forEach { option ->
                    val selected = option == status
                    val tint = statusColor(option)
                    Box(
                        Modifier
                            .weight(1f)
                            .background(
                                color = if (selected) tint.copy(alpha = 0.16f) else Surface1,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (selected) tint.copy(alpha = 0.7f) else Line,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { status = option }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .background(tint, CircleShape)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) tint else TextMid,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            SectionTitle("Mức ưu tiên")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Priority.entries.forEach { option ->
                    val selected = option == priority
                    val tint = priorityColor(option)
                    Box(
                        Modifier
                            .weight(1f)
                            .background(
                                color = if (selected) tint.copy(alpha = 0.16f) else Surface1,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (selected) tint.copy(alpha = 0.7f) else Line,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { priority = option }
                            .padding(vertical = 10.dp, horizontal = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) tint else TextMid,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            SectionTitle("Thời gian")
            Spacer(Modifier.height(10.dp))

            DateRow(
                label = "Ngày bắt đầu",
                value = formatDay(startDate),
                hint = if (existing == null) "Mặc định là ngày tạo việc" else null,
                accent = accent,
                onClick = { pickingStart = true }
            )
            Spacer(Modifier.height(10.dp))
            DateRow(
                label = "Ngày đến hạn",
                value = if (dueDate == NO_DATE) "Chưa đặt" else formatDay(dueDate),
                hint = null,
                accent = accent,
                muted = dueDate == NO_DATE,
                onClear = if (dueDate != NO_DATE) {
                    { dueDate = NO_DATE }
                } else {
                    null
                },
                onClick = { pickingDue = true }
            )

            if (dueDate != NO_DATE && dueDate < startDate) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Ngày đến hạn đang sớm hơn ngày bắt đầu.",
                    style = MaterialTheme.typography.labelMedium,
                    color = Danger
                )
            }

            if (existing != null) {
                Spacer(Modifier.height(22.dp))
                SectionTitle("Thông tin")
                Spacer(Modifier.height(8.dp))
                InfoLine("Tạo lúc", formatDateTime(existing.createdAt))
                InfoLine("Sửa lần cuối", formatDateTime(existing.updatedAt))
                existing.completedAt?.let { InfoLine("Hoàn thành", formatDateTime(it)) }
            }

            Spacer(Modifier.height(28.dp))
        }

        // Thanh luu
        Box(
            Modifier
                .fillMaxWidth()
                .background(Surface1)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onClose,
                    colors = ButtonDefaults.textButtonColors(contentColor = TextMid)
                ) {
                    Text("Huỷ")
                }
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = {
                        val cleanTitle = title.trim()
                        val due = if (dueDate == NO_DATE) null else dueDate
                        val result = existing?.copy(
                            title = cleanTitle,
                            note = note.trim(),
                            status = status,
                            priority = priority,
                            startDate = startDate,
                            dueDate = due
                        ) ?: Task(
                            title = cleanTitle,
                            note = note.trim(),
                            status = status,
                            priority = priority,
                            startDate = startDate,
                            dueDate = due
                        )
                        onSave(result)
                    },
                    enabled = canSave,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = Color(0xFF05070C),
                        disabledContainerColor = Surface3,
                        disabledContentColor = TextLow
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Filled.Check, null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (editing) "Lưu thay đổi" else "Tạo việc",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    if (pickingStart) {
        DayPickerDialog(
            initial = startDate,
            title = "Chọn ngày bắt đầu",
            onPick = {
                startDate = it
                pickingStart = false
            },
            onClear = null,
            onDismiss = { pickingStart = false }
        )
    }

    if (pickingDue) {
        DayPickerDialog(
            initial = if (dueDate == NO_DATE) null else dueDate,
            title = "Chọn ngày đến hạn",
            onPick = {
                dueDate = it
                pickingDue = false
            },
            onClear = {
                dueDate = NO_DATE
                pickingDue = false
            },
            onDismiss = { pickingDue = false }
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Xoá việc này?",
            message = "\"${existing?.title.orEmpty()}\" sẽ bị xoá khỏi danh sách.",
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = TextLow
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextLow,
            modifier = Modifier.width(110.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = TextMid
        )
    }
}

@Composable
private fun DateRow(
    label: String,
    value: String,
    hint: String?,
    accent: Color,
    onClick: () -> Unit,
    muted: Boolean = false,
    onClear: (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Surface1, RoundedCornerShape(14.dp))
            .border(1.dp, Line, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.DateRange,
            contentDescription = null,
            tint = if (muted) TextLow else accent,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextLow)
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = if (muted) TextLow else TextHi
            )
            if (hint != null) {
                Spacer(Modifier.height(2.dp))
                Text(hint, style = MaterialTheme.typography.labelSmall, color = TextLow)
            }
        }
        if (onClear != null) {
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Close, "Bỏ ngày đến hạn", tint = TextLow, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPickerDialog(
    initial: Long?,
    title: String,
    onPick: (Long) -> Unit,
    onClear: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.let { epochDayToMillisUtc(it) }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        colors = DatePickerDefaults.colors(containerColor = Surface2),
        confirmButton = {
            TextButton(
                onClick = {
                    val picked = state.selectedDateMillis
                    if (picked != null) onPick(millisUtcToEpochDay(picked)) else onDismiss()
                }
            ) {
                Text("Chọn", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            Row {
                if (onClear != null) {
                    TextButton(
                        onClick = onClear,
                        colors = ButtonDefaults.textButtonColors(contentColor = Danger)
                    ) {
                        Text("Bỏ hạn")
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = TextMid)
                ) {
                    Text("Huỷ")
                }
            }
        }
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = TextMid,
            modifier = Modifier.padding(start = 24.dp, top = 16.dp)
        )
        DatePicker(
            state = state,
            title = null,
            showModeToggle = true,
            colors = DatePickerDefaults.colors(containerColor = Surface2)
        )
    }
}
