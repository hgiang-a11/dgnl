package com.dgnl.taivideo

import android.net.Uri
import java.net.URLEncoder
import java.text.Normalizer

/** Lỗi đã có sẵn câu giải thích dễ hiểu cho người dùng. */
class FriendlyException(val friendly: String, detail: String) : Exception(detail)

object Texts {
    private val PLATFORM_NAMES = mapOf(
        "youtube" to "YouTube", "youtubetab" to "YouTube", "facebook" to "Facebook", "instagram" to "Instagram",
        "tiktok" to "TikTok", "twitter" to "X (Twitter)", "vimeo" to "Vimeo", "reddit" to "Reddit",
        "dailymotion" to "Dailymotion", "soundcloud" to "SoundCloud", "twitch" to "Twitch",
        "bilibili" to "Bilibili", "pinterest" to "Pinterest", "threads" to "Threads", "linkedin" to "LinkedIn",
        "zingmp3" to "Zing MP3", "nhaccuatui" to "NhacCuaTui",
    )

    private val ANSI = Regex("\u001B\\[[0-9;]*m")

    fun cleanError(msg: String?): String {
        var m = ANSI.replace(msg ?: "", "").trim()
        m = m.replace(Regex("^ERROR:\\s*"), "")
        m = m.replace(Regex("^\\[[^\\]]+\\]\\s*"), "")
        m = m.replace(Regex("^[\\w-]{6,}:\\s*"), "")
        return m.lineSequence().firstOrNull().orEmpty().take(300)
    }

    /** Đổi lỗi kỹ thuật của yt-dlp thành câu dễ hiểu (giống bản máy tính). */
    fun friendlyError(raw: String): String {
        val m = raw.lowercase()
        val rules = listOf(
            listOf("unsupported url") to "Link này chưa được hỗ trợ hoặc trang không chứa video.",
            listOf("not a bot", "confirm you") to "YouTube đang yêu cầu xác minh bạn không phải máy. Hãy thử lại sau ít phút.",
            listOf("private", "login", "log in", "sign in", "cookies", "registered users", "authentication") to
                "Video riêng tư hoặc cần đăng nhập mới xem được.",
            listOf("age-restricted", "age restricted", "confirm your age", "inappropriate") to
                "Video giới hạn độ tuổi, cần đăng nhập mới tải được.",
            listOf("unavailable", "removed", "deleted", "does not exist", "no longer available", "404") to
                "Video không tồn tại, đã bị xóa hoặc bị chặn ở khu vực của bạn.",
            listOf("no video", "no media", "there's no video", "no formats") to "Không tìm thấy video nào trong link này.",
            listOf("requested format") to "Không có chất lượng phù hợp. Hãy chọn chất lượng khác rồi thử lại.",
            listOf("geo", "country", "region") to "Video bị chặn ở khu vực của bạn.",
            listOf("timed out", "getaddrinfo", "unable to download webpage", "connection", "network", "resolve",
                "temporary failure", "unreachable") to "Không kết nối được tới trang video. Hãy kiểm tra mạng rồi thử lại.",
            listOf("no space") to "Điện thoại đã hết dung lượng trống.",
        )
        for ((keys, text) in rules) if (keys.any { m.contains(it) }) return text
        return "Không tải được video. Hãy kiểm tra lại link hoặc thử chất lượng khác."
    }

    fun safeFilename(name: String?, ext: String): String {
        var n = Normalizer.normalize(name ?: "video", Normalizer.Form.NFC)
        n = n.replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1f]+"), " ")
        n = n.replace(Regex("\\s+"), " ").trim(' ', '.')
        if (n.isEmpty()) n = "video"
        return n.take(120).trimEnd() + "." + ext
    }

    fun contentDisposition(kind: String, filename: String): String {
        val plain = filename.replace("đ", "d").replace("Đ", "D")
        var ascii = Normalizer.normalize(plain, Normalizer.Form.NFKD).replace(Regex("[^\\x00-\\x7F]"), "")
        ascii = ascii.replace(Regex("[^A-Za-z0-9 ._()-]+"), "").trim().ifEmpty { "video" }
        val quoted = URLEncoder.encode(filename, "UTF-8").replace("+", "%20")
        return "$kind; filename=\"$ascii\"; filename*=UTF-8''$quoted"
    }

    /** Lấy link từ đoạn chữ người dùng dán (có thể kèm tiêu đề), trả về null nếu không hợp lệ. */
    fun normalizeUrl(input: String?): String? {
        var url = (input ?: "").trim()
        val match = Regex("https?://\\S+", RegexOption.IGNORE_CASE).find(url)
        url = when {
            match != null -> match.value
            !Regex("^[a-z][a-z0-9+.-]*://", RegexOption.IGNORE_CASE).containsMatchIn(url) -> "https://$url"
            else -> url
        }
        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase()
        val host = uri.host ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (!host.contains('.')) return null
        return url
    }

    fun extractUrl(text: String?): String? {
        val t = text ?: return null
        return Regex("https?://\\S+", RegexOption.IGNORE_CASE).find(t)?.value
    }

    fun platformName(extractorKey: String?, webpageUrl: String?): String {
        val key = (extractorKey ?: "").lowercase().substringBefore(':')
        PLATFORM_NAMES[key]?.let { return it }
        PLATFORM_NAMES.entries.firstOrNull { key.startsWith(it.key) }?.let { return it.value }
        val host = try { webpageUrl?.let { java.net.URI(it).host } } catch (e: Exception) { null }.orEmpty()
        return host.removePrefix("www.").ifEmpty { "Trang web" }
    }

    /** "20240131" -> "31/01/2024" */
    fun formatDate(yyyymmdd: String?): String? {
        val d = yyyymmdd ?: return null
        if (d.length != 8 || !d.all { it.isDigit() }) return null
        return "${d.substring(6, 8)}/${d.substring(4, 6)}/${d.substring(0, 4)}"
    }
}
