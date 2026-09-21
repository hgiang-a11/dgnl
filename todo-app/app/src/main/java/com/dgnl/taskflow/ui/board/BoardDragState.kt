package com.dgnl.taskflow.ui.board

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import com.dgnl.taskflow.data.Task
import com.dgnl.taskflow.data.TaskStatus

/**
 * Trang thai keo tha cua bang cong viec.
 *
 * Chi ba gia tri ([draggingTask], [targetStatus], [targetIndex]) la "state" cua Compose —
 * nhung thu con lai (vi tri cac the, vung nhin...) luu bang bien thuong de khong kich hoat
 * ve lai giao dien 60 lan moi giay khi ngon tay di chuyen.
 */
@Stable
class BoardDragState {

    /** Task dang duoc nhac len, null khi khong keo. */
    var draggingTask by mutableStateOf<Task?>(null)
        private set

    /** Vi tri ngon tay, toa do so voi goc man hinh. */
    var pointer by mutableStateOf(Offset.Zero)
        private set

    /** Cot dang duoc nham toi. */
    var targetStatus by mutableStateOf<TaskStatus?>(null)
        private set

    /** Vi tri se chen vao trong cot dich. */
    var targetIndex by mutableIntStateOf(-1)
        private set

    /** Khoang cach tu goc tren-trai cua the toi diem ngon tay cham. */
    var grab: Offset = Offset.Zero
        private set

    var cardSize: IntSize = IntSize.Zero
        private set

    /** Goc tren-trai cua khung chua lop phu, de quy doi toa do. */
    var overlayOrigin: Offset = Offset.Zero

    /** Vung nhin cua bang (dung cho tu dong cuon ngang). */
    var viewport: Rect = Rect.Zero

    /** True khi bang xep 3 hang ngang (the chay ngang), false khi xep 3 cot doc. */
    var horizontal: Boolean = false

    val columnRects = HashMap<TaskStatus, Rect>()
    val cardRects = HashMap<String, Rect>()

    /** Ham lay danh sach task dang hien thi cua mot cot. */
    var resolver: (TaskStatus) -> List<Task> = { emptyList() }

    val isDragging: Boolean get() = draggingTask != null

    fun begin(task: Task, pointerInRoot: Offset, grabOffset: Offset, size: IntSize) {
        draggingTask = task
        pointer = pointerInRoot
        grab = grabOffset
        cardSize = size
        recomputeTarget()
    }

    fun move(delta: Offset) {
        if (draggingTask == null) return
        pointer += delta
        recomputeTarget()
    }

    /** Goi lai sau moi lan tu dong cuon de vi tri tha luon dung. */
    fun refresh() {
        if (draggingTask != null) recomputeTarget()
    }

    /** Ket thuc keo. Tra ve (cot dich, vi tri chen) hoac null neu khong hop le. */
    fun finish(): Pair<TaskStatus, Int>? {
        val status = targetStatus
        val index = targetIndex
        cancel()
        return if (status != null && index >= 0) status to index else null
    }

    fun cancel() {
        draggingTask = null
        targetStatus = null
        targetIndex = -1
    }

    private fun recomputeTarget() {
        val task = draggingTask ?: return
        val status = nearestLane()
        if (status == null) {
            targetStatus = null
            targetIndex = -1
            return
        }
        val list = resolver(status).filter { it.id != task.id }
        var slot: Int? = null
        var lastKnown = -1
        for (i in list.indices) {
            val rect = cardRects[list[i].id] ?: continue
            val before = if (horizontal) pointer.x < rect.center.x else pointer.y < rect.center.y
            if (slot == null && before) slot = i
            lastKnown = i
        }
        val resolved = slot ?: if (lastKnown >= 0) lastKnown + 1 else list.size
        targetStatus = status
        targetIndex = resolved.coerceIn(0, list.size)
    }

    /** Cot (hoac hang) gan ngon tay nhat theo truc chinh cua bo cuc dang dung. */
    private fun nearestLane(): TaskStatus? {
        if (columnRects.isEmpty()) return null
        var best: TaskStatus? = null
        var bestDistance = Float.MAX_VALUE
        for ((status, rect) in columnRects) {
            val distance = if (horizontal) {
                when {
                    pointer.y < rect.top -> rect.top - pointer.y
                    pointer.y > rect.bottom -> pointer.y - rect.bottom
                    else -> 0f
                }
            } else {
                when {
                    pointer.x < rect.left -> rect.left - pointer.x
                    pointer.x > rect.right -> pointer.x - rect.right
                    else -> 0f
                }
            }
            if (distance < bestDistance) {
                bestDistance = distance
                best = status
            }
        }
        return best
    }
}
