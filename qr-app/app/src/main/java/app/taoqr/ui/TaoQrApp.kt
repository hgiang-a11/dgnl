package app.taoqr.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.taoqr.QrViewModel

private enum class Screen { Main, History }

@Composable
fun TaoQrApp(vm: QrViewModel) {
    var screen by rememberSaveable { mutableStateOf(Screen.Main) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                val forward = targetState == Screen.History
                (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
            },
            label = "screen",
        ) { current ->
            when (current) {
                Screen.Main -> MainScreen(
                    vm = vm,
                    onOpenHistory = { screen = Screen.History },
                    onFullscreen = {
                        vm.recordHistory()
                        fullscreen = true
                    },
                )
                Screen.History -> HistoryScreen(
                    items = vm.history,
                    onBack = { screen = Screen.Main },
                    onOpen = {
                        vm.openFromHistory(it)
                        screen = Screen.Main
                    },
                    onDelete = vm::deleteHistory,
                    onClearAll = vm::clearHistory,
                )
            }
        }

        val bitmap = vm.qrBitmap
        AnimatedVisibility(visible = fullscreen && bitmap != null, enter = fadeIn(), exit = fadeOut()) {
            if (bitmap != null) {
                FullscreenQr(bitmap = bitmap, content = vm.qrContent, onClose = { fullscreen = false })
            }
        }
    }
}
