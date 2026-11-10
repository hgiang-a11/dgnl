package app.taoqr

import android.content.Context
import android.util.Patterns
import org.json.JSONArray
import org.json.JSONObject

enum class QrMode(val key: String, val label: String) {
    LINK("link", "Liên kết"),
    TEXT("text", "Văn bản");

    companion object {
        fun fromKey(key: String?): QrMode = entries.firstOrNull { it.key == key } ?: LINK
    }
}

data class QrColor(val name: String, val argb: Int)

/** Only dark colours, so every code keeps strong contrast against the white background. */
val QrColors = listOf(
    QrColor("Đen", 0xFF111318.toInt()),
    QrColor("Xanh dương", 0xFF1F4FD8.toInt()),
    QrColor("Xanh lá", 0xFF0B6E4F.toInt()),
    QrColor("Tím", 0xFF5B2A9E.toInt()),
    QrColor("Đỏ", 0xFFB3261E.toInt()),
    QrColor("Nâu", 0xFF5D4037.toInt()),
)

data class HistoryItem(val mode: QrMode, val content: String, val time: Long)

object LinkUtils {
    private val schemeWithSlashes = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
    private val schemeWithoutSlashes =
        Regex("^(mailto|tel|sms|smsto|geo|market|wifi|upi):", RegexOption.IGNORE_CASE)
    private val httpUrl = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)

    /** Adds "https://" when the user typed something like "facebook.com/page". */
    fun normalize(raw: String): String {
        val s = raw.trim()
        if (s.isEmpty()) return s
        val hasScheme = schemeWithSlashes.containsMatchIn(s) || schemeWithoutSlashes.containsMatchIn(s)
        return if (hasScheme) s else "https://$s"
    }

    fun looksValid(normalized: String): Boolean {
        if (normalized.any { it.isWhitespace() }) return false
        val isWeb = normalized.startsWith("http://", ignoreCase = true) ||
            normalized.startsWith("https://", ignoreCase = true)
        return !isWeb || Patterns.WEB_URL.matcher(normalized).matches()
    }

    fun isSingleUrl(text: String): Boolean {
        val t = text.trim()
        return t.isNotEmpty() && t.none { it.isWhitespace() } && Patterns.WEB_URL.matcher(t).matches()
    }

    fun findHttpUrls(text: String): List<String> =
        httpUrl.findAll(text).map { it.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}', '\'', '"') }
            .distinct()
            .toList()
}

class HistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE)

    fun load(): List<HistoryItem> = runCatching {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        List(array.length()) { i ->
            val o = array.getJSONObject(i)
            HistoryItem(QrMode.fromKey(o.getString("m")), o.getString("c"), o.getLong("t"))
        }
    }.getOrDefault(emptyList())

    fun save(items: List<HistoryItem>) {
        val array = JSONArray()
        items.forEach { array.put(JSONObject().put("m", it.mode.key).put("c", it.content).put("t", it.time)) }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "items"
    }
}
