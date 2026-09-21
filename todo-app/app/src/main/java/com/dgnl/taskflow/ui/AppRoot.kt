package com.dgnl.taskflow.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dgnl.taskflow.data.TaskRepository
import com.dgnl.taskflow.data.TaskStatus
import com.dgnl.taskflow.ui.board.BoardScreen
import com.dgnl.taskflow.ui.home.HomeScreen
import com.dgnl.taskflow.ui.task.TaskEditorScreen
import com.dgnl.taskflow.ui.theme.Ink
import com.dgnl.taskflow.ui.theme.accentAt

/** Cac man hinh cua app. */
sealed interface Screen {
    data object Home : Screen
    data class BoardView(val boardId: String) : Screen
    data class Editor(
        val boardId: String,
        val taskId: String?,
        val defaultStatus: TaskStatus
    ) : Screen
}

private fun depthOf(screen: Screen): Int = when (screen) {
    Screen.Home -> 0
    is Screen.BoardView -> 1
    is Screen.Editor -> 2
}

@Composable
fun TaskFlowRoot(repository: TaskRepository) {
    val state by repository.state.collectAsStateWithLifecycle()
    var stack by remember { mutableStateOf<List<Screen>>(listOf(Screen.Home)) }
    val current = stack.last()

    BackHandler(enabled = stack.size > 1) {
        stack = stack.dropLast(1)
    }

    // Neu danh sach dang mo bi xoa thi tro ve trang chu.
    LaunchedEffect(state.boards, current) {
        val boardId = when (current) {
            Screen.Home -> null
            is Screen.BoardView -> current.boardId
            is Screen.Editor -> current.boardId
        }
        if (boardId != null && state.boards.none { it.id == boardId }) {
            stack = listOf(Screen.Home)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink)
    ) {
        if (!state.loaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color = accentAt(0),
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(34.dp)
                )
            }
        } else {
            AnimatedContent(
                targetState = current,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val forward = depthOf(targetState) >= depthOf(initialState)
                    if (forward) {
                        (slideInHorizontally(tween(260)) { full -> full / 3 } + fadeIn(tween(200)))
                            .togetherWith(fadeOut(tween(150)))
                    } else {
                        fadeIn(tween(200)).togetherWith(
                            slideOutHorizontally(tween(260)) { full -> full / 3 } + fadeOut(tween(190))
                        )
                    }
                },
                label = "screen"
            ) { screen ->
                when (screen) {
                    Screen.Home -> HomeScreen(
                        boards = state.boards,
                        repository = repository,
                        onOpenBoard = { boardId -> stack = stack + Screen.BoardView(boardId) }
                    )

                    is Screen.BoardView -> {
                        val board = state.boards.firstOrNull { it.id == screen.boardId }
                        if (board != null) {
                            BoardScreen(
                                board = board,
                                repository = repository,
                                onBack = { stack = stack.dropLast(1) },
                                onCreateTask = { status ->
                                    stack = stack + Screen.Editor(board.id, null, status)
                                },
                                onOpenTask = { task ->
                                    stack = stack + Screen.Editor(board.id, task.id, task.status)
                                },
                                onBoardDeleted = {
                                    repository.deleteBoard(board.id)
                                    stack = listOf(Screen.Home)
                                }
                            )
                        }
                    }

                    is Screen.Editor -> {
                        val board = state.boards.firstOrNull { it.id == screen.boardId }
                        if (board != null) {
                            val existing = screen.taskId?.let { id ->
                                board.tasks.firstOrNull { it.id == id }
                            }
                            TaskEditorScreen(
                                boardName = board.name,
                                accent = accentAt(board.accent),
                                existing = existing,
                                defaultStatus = screen.defaultStatus,
                                onSave = { task ->
                                    if (existing == null) {
                                        repository.addTask(board.id, task)
                                    } else {
                                        repository.updateTask(board.id, task)
                                    }
                                    stack = stack.dropLast(1)
                                },
                                onDelete = {
                                    if (existing != null) {
                                        repository.deleteTask(board.id, existing.id)
                                    }
                                    stack = stack.dropLast(1)
                                },
                                onClose = { stack = stack.dropLast(1) }
                            )
                        }
                    }
                }
            }
        }
    }
}
