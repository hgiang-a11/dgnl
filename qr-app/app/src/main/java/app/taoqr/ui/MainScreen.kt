@file:OptIn(ExperimentalMaterial3Api::class)

package app.taoqr.ui

import android.Manifest
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.taoqr.ImageActions
import app.taoqr.LinkUtils
import app.taoqr.QrColors
import app.taoqr.QrGenerator
import app.taoqr.QrMode
import app.taoqr.QrViewModel
import kotlinx.coroutines.launch

@Composable
fun MainScreen(vm: QrViewModel, onOpenHistory: () -> Unit, onFullscreen: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val focus = LocalFocusManager.current

    fun notify(message: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    val save: () -> Unit = {
        vm.qrBitmap?.let { bitmap ->
            vm.recordHistory()
            scope.launch {
                val ok = ImageActions.saveToGallery(context, bitmap)
                notify(if (ok) "Đã lưu ảnh mã QR vào Thư viện ảnh" else "Không lưu được ảnh, vui lòng thử lại")
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) save() else notify("Cần cho phép truy cập bộ nhớ để lưu ảnh")
    }
    val onSave: () -> Unit = {
        focus.clearFocus()
        if (ImageActions.needsStoragePermission(context)) {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            save()
        }
    }
    val onShare: () -> Unit = {
        focus.clearFocus()
        vm.qrBitmap?.let { bitmap ->
            vm.recordHistory()
            scope.launch {
                if (!ImageActions.share(context, bitmap)) notify("Không chia sẻ được, vui lòng thử lại")
            }
        }
    }
    val onZoom: () -> Unit = {
        focus.clearFocus()
        onFullscreen()
    }
    val onPaste: () -> Unit = {
        val text = ImageActions.readClipboard(context)
        if (text.isNullOrBlank()) notify("Bộ nhớ tạm đang trống") else vm.paste(text)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        val actions = @Composable {
            ActionRow(enabled = vm.qrBitmap != null, onSave = onSave, onShare = onShare, onZoom = onZoom)
        }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                // On phones the main actions stay pinned to the bottom so they are always in reach.
                if (!wide) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shadowElevation = 8.dp) {
                        Box(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                            actions()
                        }
                    }
                }
            },
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Outlined.QrCode2,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text("Tạo mã QR")
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenHistory) {
                            Icon(Icons.Outlined.History, contentDescription = "Lịch sử")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            },
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePadding(),
            ) {
                val preview = @Composable {
                    PreviewCard(
                        bitmap = vm.qrBitmap,
                        content = vm.qrContent,
                        tooLong = vm.tooLong,
                        colorIndex = vm.colorIndex,
                        onColorChange = vm::changeColor,
                        onZoom = onZoom,
                    )
                }
                if (wide) {
                    // Tablets and landscape: editor on the left, the code on the right.
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        Column(
                            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
                                .padding(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            Intro()
                            Editor(vm = vm, onPaste = onPaste, onDone = { focus.clearFocus() })
                            actions()
                        }
                        Column(
                            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
                                .padding(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            preview()
                        }
                    }
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Intro()
                        Editor(vm = vm, onPaste = onPaste, onDone = { focus.clearFocus() })
                        preview()
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Intro() {
    Text(
        "Dán đường liên kết hoặc nhập văn bản, mã QR sẽ được tạo ngay lập tức.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Editor(vm: QrViewModel, onPaste: () -> Unit, onDone: () -> Unit) {
    val isLink = vm.mode == QrMode.LINK
    val value = vm.currentInput

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            QrMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = vm.mode == mode,
                    onClick = { vm.changeMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, QrMode.entries.size),
                    icon = {
                        Icon(
                            if (mode == QrMode.LINK) Icons.Outlined.Link else Icons.Outlined.TextFields,
                            contentDescription = null,
                            modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                        )
                    },
                ) { Text(mode.label) }
            }
        }

        val normalized = if (isLink) LinkUtils.normalize(value) else ""
        val linkInvalid = isLink && value.isNotBlank() && !LinkUtils.looksValid(normalized)
        val isError = vm.tooLong || linkInvalid
        val supporting: (@Composable () -> Unit)? = when {
            vm.tooLong -> {
                { Text("Nội dung quá dài để tạo mã QR, hãy rút gọn bớt") }
            }
            linkInvalid -> {
                { Text("Liên kết có vẻ chưa đúng, hãy kiểm tra lại") }
            }
            isLink && value.isNotBlank() && normalized != value.trim() -> {
                { Text("Mã sẽ mở: $normalized", maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            else -> null
        }

        OutlinedTextField(
            value = value,
            onValueChange = vm::changeInput,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (isLink) "Đường liên kết" else "Nội dung văn bản") },
            placeholder = {
                Text(
                    if (isLink) "vd: facebook.com/trangcuaban"
                    else "Lời nhắn, số tài khoản, địa chỉ, ghi chú…",
                )
            },
            leadingIcon = if (isLink) {
                { Icon(Icons.Outlined.Link, contentDescription = null) }
            } else null,
            singleLine = isLink,
            minLines = if (isLink) 1 else 4,
            maxLines = if (isLink) 1 else 10,
            isError = isError,
            supportingText = supporting,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                errorContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isLink) KeyboardType.Uri else KeyboardType.Text,
                imeAction = if (isLink) ImeAction.Done else ImeAction.Default,
                autoCorrectEnabled = !isLink,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onPaste) {
                Icon(Icons.Outlined.ContentPaste, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Dán")
            }
            TextButton(onClick = { vm.changeInput("") }, enabled = value.isNotEmpty()) {
                Icon(Icons.Outlined.Close, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Xóa")
            }
            Spacer(Modifier.weight(1f))
            if (!isLink && value.isNotEmpty()) {
                val percent = QrGenerator.byteLength(value) * 100 / QrGenerator.MAX_BYTES
                Text(
                    "${value.length} ký tự · đã dùng ${percent.coerceAtMost(999)}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (vm.tooLong) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PreviewCard(
    bitmap: Bitmap?,
    content: String,
    tooLong: Boolean,
    colorIndex: Int,
    onColorChange: (Int) -> Unit,
    onZoom: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(
                targetState = bitmap,
                contentKey = { it != null },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "qr",
                modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth().aspectRatio(1f),
            ) { current ->
                if (current != null) {
                    val image = remember(current) { current.asImageBitmap() }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                            .clickable(onClickLabel = "Phóng to", onClick = onZoom),
                    ) {
                        Image(
                            bitmap = image,
                            contentDescription = "Mã QR",
                            modifier = Modifier.fillMaxSize(),
                            filterQuality = FilterQuality.None,
                        )
                    }
                } else {
                    Placeholder(tooLong)
                }
            }

            if (bitmap != null) {
                Spacer(Modifier.height(14.dp))
                Text(
                    content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Chạm vào mã để phóng to",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            HorizontalDivider(
                Modifier.padding(vertical = 18.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            )
            ColorPicker(selected = colorIndex, onSelect = onColorChange)
        }
    }
}

@Composable
private fun Placeholder(tooLong: Boolean) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawRoundRect(
                    color = outline,
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f)),
                    ),
                )
            }
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (tooLong) Icons.Outlined.ErrorOutline else Icons.Outlined.QrCode2,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = if (tooLong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (tooLong) "Nội dung quá dài" else "Mã QR sẽ hiện ở đây",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                if (tooLong) "Hãy rút gọn nội dung để tạo được mã"
                else "Nhập liên kết hoặc văn bản để bắt đầu",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun ColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Màu của mã",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            QrColors.forEachIndexed { index, qrColor ->
                val isSelected = index == selected
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(index) })
                        .semantics { contentDescription = qrColor.name }
                        .border(
                            width = 2.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = CircleShape,
                        )
                        .padding(if (isSelected) 5.dp else 2.dp)
                        .clip(CircleShape)
                        .background(Color(qrColor.argb)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionRow(enabled: Boolean, onSave: () -> Unit, onShare: () -> Unit, onZoom: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = onSave, enabled = enabled, shape = shape, modifier = Modifier.weight(1f).height(52.dp)) {
            Icon(Icons.Outlined.Download, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Lưu ảnh", maxLines = 1)
        }
        FilledTonalButton(onClick = onShare, enabled = enabled, shape = shape, modifier = Modifier.weight(1f).height(52.dp)) {
            Icon(Icons.Outlined.Share, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Chia sẻ", maxLines = 1)
        }
        OutlinedIconButton(onClick = onZoom, enabled = enabled, shape = shape, modifier = Modifier.size(52.dp)) {
            Icon(Icons.Outlined.Fullscreen, contentDescription = "Phóng to")
        }
    }
}
