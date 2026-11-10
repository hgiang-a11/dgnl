package app.taoqr

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QrViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {

    private val store = HistoryStore(app)

    var mode by mutableStateOf(QrMode.fromKey(saved.get<String>(KEY_MODE)))
        private set
    var linkInput by mutableStateOf(saved.get<String>(KEY_LINK) ?: "")
        private set
    var textInput by mutableStateOf(saved.get<String>(KEY_TEXT) ?: "")
        private set
    var colorIndex by mutableIntStateOf(saved.get<Int>(KEY_COLOR) ?: 0)
        private set

    /** The rendered code, and the exact content and mode it was rendered from. */
    var qrBitmap by mutableStateOf<Bitmap?>(null)
        private set
    var qrContent by mutableStateOf("")
        private set
    private var qrMode = QrMode.LINK

    var tooLong by mutableStateOf(false)
        private set

    val history = mutableStateListOf<HistoryItem>()

    private var renderJob: Job? = null

    init {
        history.addAll(store.load())
        regenerate(debounceMs = 0)
    }

    val currentInput: String
        get() = if (mode == QrMode.LINK) linkInput else textInput

    private val contentToEncode: String
        get() = when (mode) {
            QrMode.LINK -> LinkUtils.normalize(linkInput)
            QrMode.TEXT -> textInput
        }

    fun changeMode(newMode: QrMode) {
        if (newMode == mode) return
        mode = newMode
        saved[KEY_MODE] = newMode.key
        regenerate(debounceMs = 0)
    }

    fun changeInput(value: String) {
        when (mode) {
            QrMode.LINK -> {
                linkInput = value
                saved[KEY_LINK] = value
            }
            QrMode.TEXT -> {
                textInput = value
                saved[KEY_TEXT] = value
            }
        }
        regenerate()
    }

    /** Pasting into the link box keeps only the link when the copied text also has other words. */
    fun paste(value: String) {
        val text = value.trim()
        if (mode == QrMode.LINK && !LinkUtils.isSingleUrl(text)) {
            val urls = LinkUtils.findHttpUrls(text)
            changeInput(if (urls.size == 1) urls.first() else text)
        } else {
            changeInput(if (mode == QrMode.LINK) text else value)
        }
    }

    fun changeColor(index: Int) {
        if (index == colorIndex) return
        colorIndex = index
        saved[KEY_COLOR] = index
        regenerate(debounceMs = 0)
    }

    /** Text shared from another app: open it as a link when it is (or contains exactly) one link. */
    fun loadSharedText(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        val urls = LinkUtils.findHttpUrls(text)
        when {
            LinkUtils.isSingleUrl(text) -> {
                setInputs(link = text, text = null)
                applyMode(QrMode.LINK)
            }
            urls.size == 1 -> {
                setInputs(link = urls.first(), text = text)
                applyMode(QrMode.LINK)
            }
            else -> {
                setInputs(link = null, text = text)
                applyMode(QrMode.TEXT)
            }
        }
        regenerate(debounceMs = 0)
    }

    fun recordHistory() {
        val content = qrContent
        if (content.isBlank()) return
        history.removeAll { it.content == content && it.mode == qrMode }
        history.add(0, HistoryItem(qrMode, content, System.currentTimeMillis()))
        while (history.size > MAX_HISTORY) history.removeAt(history.lastIndex)
        store.save(history.toList())
    }

    fun openFromHistory(item: HistoryItem) {
        if (item.mode == QrMode.LINK) setInputs(link = item.content, text = null)
        else setInputs(link = null, text = item.content)
        applyMode(item.mode)
        regenerate(debounceMs = 0)
    }

    fun deleteHistory(item: HistoryItem) {
        history.remove(item)
        store.save(history.toList())
    }

    fun clearHistory() {
        history.clear()
        store.save(emptyList())
    }

    private fun applyMode(newMode: QrMode) {
        mode = newMode
        saved[KEY_MODE] = newMode.key
    }

    private fun setInputs(link: String?, text: String?) {
        link?.let { linkInput = it; saved[KEY_LINK] = it }
        text?.let { textInput = it; saved[KEY_TEXT] = it }
    }

    private fun regenerate(debounceMs: Long = 150) {
        renderJob?.cancel()
        val content = contentToEncode
        val encodeMode = mode
        val color = QrColors.getOrElse(colorIndex) { QrColors.first() }.argb

        if (content.isBlank()) {
            showResult(null, "", encodeMode, tooLong = false)
            return
        }
        if (QrGenerator.byteLength(content) > QrGenerator.MAX_BYTES) {
            showResult(null, "", encodeMode, tooLong = true)
            return
        }
        renderJob = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            val bitmap = withContext(Dispatchers.Default) { QrGenerator.render(content, color) }
            showResult(bitmap, if (bitmap != null) content else "", encodeMode, tooLong = bitmap == null)
        }
    }

    private fun showResult(bitmap: Bitmap?, content: String, encodeMode: QrMode, tooLong: Boolean) {
        qrBitmap = bitmap
        qrContent = content
        qrMode = encodeMode
        this.tooLong = tooLong
    }

    private companion object {
        const val MAX_HISTORY = 50
        const val KEY_MODE = "mode"
        const val KEY_LINK = "link"
        const val KEY_TEXT = "text"
        const val KEY_COLOR = "color"
    }
}
