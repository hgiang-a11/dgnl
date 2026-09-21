package com.dgnl.taskflow.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import com.dgnl.taskflow.ui.theme.AccentColors
import com.dgnl.taskflow.ui.theme.Line
import com.dgnl.taskflow.ui.theme.StatusDoingColor
import com.dgnl.taskflow.ui.theme.Surface2
import com.dgnl.taskflow.ui.theme.Surface3
import com.dgnl.taskflow.ui.theme.TextHi
import com.dgnl.taskflow.ui.theme.TextLow
import com.dgnl.taskflow.ui.theme.TextMid

/** Nhan nho bo tron, dung cho ngay han / uu tien / so luong. */
@Composable
fun Pill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    leadingDot: Boolean = false
) {
    Row(
        modifier = modifier
            .background(
                color = if (filled) color.copy(alpha = 0.16f) else Color.Transparent,
                shape = RoundedCornerShape(7.dp)
            )
            .border(
                width = if (filled) 0.dp else 1.dp,
                color = if (filled) Color.Transparent else color.copy(alpha = 0.35f),
                shape = RoundedCornerShape(7.dp)
            )
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (leadingDot) {
            Box(
                Modifier
                    .size(6.dp)
                    .background(color, CircleShape)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** O thong ke nho tren man hinh chinh. */
@Composable
fun StatTile(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Surface2, RoundedCornerShape(14.dp))
            .border(1.dp, Line, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = color)
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = TextLow,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Hang chon mau nhan cho danh sach. */
@Composable
fun AccentPickerRow(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AccentColors.forEachIndexed { index, color ->
            val isSelected = index == selected
            Box(
                Modifier
                    .size(if (isSelected) 30.dp else 26.dp)
                    .background(color, CircleShape)
                    .border(
                        width = if (isSelected) 2.dp else 0.dp,
                        color = if (isSelected) TextHi else Color.Transparent,
                        shape = CircleShape
                    )
                    .clickable { onSelect(index) }
            )
        }
    }
}

@Composable
fun appTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextHi,
    unfocusedTextColor = TextHi,
    disabledTextColor = TextLow,
    focusedContainerColor = Surface3,
    unfocusedContainerColor = Surface2,
    disabledContainerColor = Surface2,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = Line,
    disabledBorderColor = Line,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = TextLow,
    focusedPlaceholderColor = TextLow,
    unfocusedPlaceholderColor = TextLow
)

/** Hop thoai nhap mot dong chu (tao / doi ten danh sach). */
@Composable
fun TextPromptDialog(
    title: String,
    label: String,
    initialValue: String = "",
    confirmLabel: String = "Lưu",
    accent: Int? = null,
    onConfirm: (String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var text by rememberSaveable { mutableStateOf(initialValue) }
    var accentIndex by rememberSaveable { mutableStateOf(accent ?: 0) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = Surface2,
        titleContentColor = TextHi,
        textContentColor = TextMid,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 80) text = it },
                    label = { Text(label) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = appTextFieldColors(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
                if (accent != null) {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "Màu nhãn",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextLow
                    )
                    Spacer(Modifier.height(10.dp))
                    AccentPickerRow(selected = accentIndex, onSelect = { accentIndex = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text.trim(), accentIndex) },
                enabled = text.isNotBlank()
            ) {
                Text(confirmLabel, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = TextMid)) {
                Text("Huỷ")
            }
        }
    )
}

/** Hop thoai xac nhan cho cac thao tac xoa. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "Xoá",
    destructive: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = Surface2,
        titleContentColor = TextHi,
        textContentColor = TextMid,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            ) {
                Text(confirmLabel, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = TextMid)) {
                Text("Huỷ")
            }
        }
    )
}

/** Man hinh trong. */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(58.dp)
                .background(Surface2, RoundedCornerShape(18.dp))
                .border(1.dp, Line, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    Modifier
                        .width(6.dp)
                        .height(22.dp)
                        .background(AccentColors[0], RoundedCornerShape(3.dp))
                )
                Box(
                    Modifier
                        .width(6.dp)
                        .height(14.dp)
                        .background(AccentColors[3], RoundedCornerShape(3.dp))
                )
                Box(
                    Modifier
                        .width(6.dp)
                        .height(18.dp)
                        .background(AccentColors[2], RoundedCornerShape(3.dp))
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = TextHi
        )
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = TextLow,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

/**
 * Thanh tien do ba phan cua mot danh sach, doc tu trai sang:
 * - **to dac**: viec da xong
 * - **vach cheo dang chay**: viec dang lam
 * - **de trong**: viec chua lam
 *
 * Do dai moi phan dung bang ti le so viec cua phan do.
 */
@Composable
fun TaskProgressBar(
    doneRatio: Float,
    doingRatio: Float,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val done = animateFloatAsState(
        targetValue = doneRatio.coerceIn(0f, 1f),
        animationSpec = tween(460),
        label = "doneRatio"
    )
    val doing = animateFloatAsState(
        targetValue = doingRatio.coerceIn(0f, 1f),
        animationSpec = tween(460),
        label = "doingRatio"
    )
    val marching = rememberInfiniteTransition(label = "stripes")
    val phase = marching.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "stripePhase"
    )

    // Cac gia tri dong duoc doc ben trong buoc ve, nen chi ve lai chu khong dung lai giao dien.
    Canvas(
        modifier
            .height(8.dp)
            .clip(RoundedCornerShape(50))
    ) {
        val barHeight = size.height
        val doneWidth = size.width * done.value
        val doingWidth = (size.width * doing.value).coerceAtMost(size.width - doneWidth)

        drawRect(color = Surface3)

        if (doneWidth > 0.5f) {
            drawRect(color = accent, size = Size(doneWidth, barHeight))
        }

        if (doingWidth > 0.5f) {
            val left = doneWidth
            val right = doneWidth + doingWidth
            clipRect(left = left, top = 0f, right = right, bottom = barHeight) {
                drawRect(
                    color = StatusDoingColor.copy(alpha = 0.22f),
                    topLeft = Offset(left, 0f),
                    size = Size(doingWidth, barHeight)
                )
                val period = barHeight * 1.7f
                var x = left - barHeight - phase.value * period
                while (x < right + barHeight) {
                    drawLine(
                        color = StatusDoingColor,
                        start = Offset(x, barHeight),
                        end = Offset(x + barHeight, 0f),
                        strokeWidth = barHeight * 0.4f,
                        cap = StrokeCap.Round
                    )
                    x += period
                }
            }
        }
    }
}
