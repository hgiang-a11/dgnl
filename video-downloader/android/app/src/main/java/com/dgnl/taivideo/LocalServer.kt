package com.dgnl.taivideo

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom

/**
 * Máy chủ nhỏ chạy trong ứng dụng, chỉ nghe ở 127.0.0.1 (không ra ngoài mạng).
 * Nó phục vụ giao diện web và các đường dẫn /api/... giống hệt bản máy tính (app.py),
 * nên giao diện dùng chung được cho cả hai bản.
 */
class LocalServer(private val ctx: Context) : NanoHTTPD("127.0.0.1", 0) {
    /** Mã bí mật gửi kèm qua cookie, để ứng dụng khác trên máy không gọi được API. */
    val token: String = ByteArray(24).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

    val baseUrl: String get() = "http://127.0.0.1:$listeningPort"

    override fun serve(session: IHTTPSession): Response = try {
        val uri = session.uri ?: "/"
        if (uri.startsWith("/api/")) {
            if (session.cookies.read(COOKIE) != token) {
                json(Response.Status.FORBIDDEN, detail("Không có quyền truy cập."))
            } else {
                api(session, uri)
            }
        } else {
            asset(uri)
        }
    } catch (e: Exception) {
        Log.e(TAG, "serve failed", e)
        json(Response.Status.INTERNAL_ERROR, detail("Máy chủ gặp lỗi, hãy thử lại."))
    }

    // ---------------------------------------------------------------- API

    private fun api(s: IHTTPSession, uri: String): Response {
        val parts = uri.split('/').filter { it.isNotEmpty() } // ["api", "jobs", id, sub]
        if (uri.trimEnd('/') == "/api/health") return json(Response.Status.OK, health())
        if (parts.size == 2 && parts[1] == "jobs" && s.method == Method.POST) return createJob(s)
        if (parts.size >= 3 && parts[1] == "jobs") {
            val job = JobManager.get(parts[2])
                ?: return json(Response.Status.NOT_FOUND, detail("Không tìm thấy lượt tải này (có thể đã hết hạn)."))
            return when (parts.getOrNull(3)) {
                null -> if (s.method == Method.DELETE) {
                    JobManager.cancel(job.id)
                    json(Response.Status.OK, JSONObject().put("ok", true))
                } else {
                    json(Response.Status.OK, job.toJson())
                }
                "media" -> fileResponse(s, job, inline = true)
                "download" -> fileResponse(s, job, inline = false)
                "thumbnail" -> thumbnail(job)
                else -> json(Response.Status.NOT_FOUND, detail("Không tìm thấy."))
            }
        }
        return json(Response.Status.NOT_FOUND, detail("Không tìm thấy."))
    }

    private fun health() = JSONObject().apply {
        put("ok", true)
        put("app", true)
        put("ready", Engine.ready)
        put("updating", Engine.updating)
        put("yt_dlp", Engine.version.ifEmpty { if (Engine.ready) "đi kèm" else "đang khởi động" })
        put("ffmpeg", true)
        put("cookies", false)
        put("version", BuildConfig.VERSION_NAME)
    }

    private fun readBody(s: IHTTPSession): String {
        val len = s.headers["content-length"]?.toIntOrNull() ?: 0
        if (len <= 0 || len > 64 * 1024) return ""
        val buf = ByteArray(len)
        var read = 0
        while (read < len) {
            val n = s.inputStream.read(buf, read, len - read)
            if (n < 0) break
            read += n
        }
        return String(buf, 0, read, Charsets.UTF_8)
    }

    private fun createJob(s: IHTTPSession): Response {
        val body = try { JSONObject(readBody(s)) } catch (e: Exception) { JSONObject() }
        val url = Texts.normalizeUrl(body.optString("url"))
            ?: return json(Response.Status.BAD_REQUEST, detail("Link không hợp lệ. Hãy dán link bắt đầu bằng http:// hoặc https://"))
        val quality = body.optString("quality").takeIf { it in QUALITIES } ?: "1080"
        return json(Response.Status.OK, JobManager.create(url, quality).toJson())
    }

    private fun fileResponse(s: IHTTPSession, job: Job, inline: Boolean): Response {
        val file = job.file
        val fi = job.fileInfo
        if (job.state != "done" || file == null || fi == null || !file.isFile) {
            return json(Response.Status.CONFLICT, detail("Video chưa sẵn sàng."))
        }
        val mime = if (inline) fi.optString("mime", "video/mp4") else "application/octet-stream"
        val disposition = Texts.contentDisposition(if (inline) "inline" else "attachment", fi.optString("filename", file.name))
        return rangeResponse(s, file, mime).apply {
            addHeader("Content-Disposition", disposition)
            addHeader("Cache-Control", "private, max-age=3600")
        }
    }

    /** Trả tệp có hỗ trợ "Range" để khung xem trước tua được. */
    private fun rangeResponse(s: IHTTPSession, file: File, mime: String): Response {
        val len = file.length()
        var start = 0L
        var end = len - 1
        var partial = false
        val range = s.headers["range"]
        if (range != null && range.startsWith("bytes=")) {
            val spec = range.removePrefix("bytes=").split(',')[0].trim()
            val dash = spec.indexOf('-')
            if (dash >= 0) {
                val a = spec.substring(0, dash).trim()
                val b = spec.substring(dash + 1).trim()
                if (a.isEmpty()) {
                    start = maxOf(0L, len - (b.toLongOrNull() ?: 0L))
                } else {
                    start = a.toLongOrNull() ?: 0L
                    if (b.isNotEmpty()) end = minOf(b.toLongOrNull() ?: end, len - 1)
                }
                partial = true
            }
        }
        if (start >= len || start > end) {
            return newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, "text/plain", "").apply {
                addHeader("Content-Range", "bytes */$len")
            }
        }
        val input: InputStream = FileInputStream(file).also { skipFully(it, start) }
        val status = if (partial) Response.Status.PARTIAL_CONTENT else Response.Status.OK
        return newFixedLengthResponse(status, mime, input, end - start + 1).apply {
            addHeader("Accept-Ranges", "bytes")
            if (partial) addHeader("Content-Range", "bytes $start-$end/$len")
        }
    }

    private fun skipFully(input: InputStream, n: Long) {
        var left = n
        while (left > 0) {
            val skipped = input.skip(left)
            if (skipped <= 0) {
                if (input.read() == -1) return
                left--
            } else {
                left -= skipped
            }
        }
    }

    /** Lấy ảnh bìa qua máy chủ vì nhiều trang (Instagram, Facebook) chặn nhúng ảnh trực tiếp. */
    private fun thumbnail(job: Job): Response {
        synchronized(job.thumbLock) {
            if (job.thumbFile == null) {
                val src = job.thumbUrl
                    ?: return json(Response.Status.NOT_FOUND, detail("Không có ảnh bìa."))
                try {
                    val conn = (URL(src).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15_000
                        readTimeout = 15_000
                        setRequestProperty("User-Agent", USER_AGENT)
                        setRequestProperty("Accept", "image/avif,image/webp,image/*,*/*;q=0.8")
                    }
                    val data = conn.inputStream.use { it.readBytes() }
                    if (data.size > 8 * 1024 * 1024) throw IllegalStateException("too large")
                    val type = conn.contentType?.substringBefore(';')?.trim()
                    job.dir.mkdirs()
                    val f = File(job.dir, "thumb")
                    f.writeBytes(data)
                    job.thumbFile = f
                    job.thumbType = type?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
                } catch (e: Exception) {
                    return json(Response.Status.NOT_FOUND, detail("Không tải được ảnh bìa."))
                }
            }
        }
        val f = job.thumbFile!!
        return newFixedLengthResponse(Response.Status.OK, job.thumbType, FileInputStream(f), f.length()).apply {
            addHeader("Cache-Control", "private, max-age=3600")
        }
    }

    // ---------------------------------------------------------------- Giao diện

    private fun asset(uri: String): Response {
        val path = uri.trimStart('/').ifEmpty { "index.html" }
        if (path.contains("..")) return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
        val bytes = try {
            ctx.assets.open(path).use { it.readBytes() }
        } catch (e: Exception) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
        }
        val ext = path.substringAfterLast('.', "").lowercase()
        val mime = MIME[ext] ?: "application/octet-stream"
        return newFixedLengthResponse(Response.Status.OK, mime, ByteArrayInputStream(bytes), bytes.size.toLong()).apply {
            addHeader("Cache-Control", "no-cache")
        }
    }

    private fun detail(msg: String) = JSONObject().put("detail", msg)

    private fun json(status: Response.Status, body: JSONObject): Response {
        val bytes = body.toString().toByteArray(Charsets.UTF_8)
        return newFixedLengthResponse(status, "application/json; charset=utf-8", ByteArrayInputStream(bytes), bytes.size.toLong())
            .apply { addHeader("Cache-Control", "no-store") }
    }

    companion object {
        private const val TAG = "TaiVideo"
        const val COOKIE = "tv_token"
        private val QUALITIES = setOf("best", "1080", "720", "480", "360", "audio")
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"
        private val MIME = mapOf(
            "html" to "text/html; charset=utf-8",
            "css" to "text/css; charset=utf-8",
            "js" to "application/javascript; charset=utf-8",
            "json" to "application/json; charset=utf-8",
            "svg" to "image/svg+xml",
            "png" to "image/png",
            "ico" to "image/x-icon",
            "webp" to "image/webp",
            "woff2" to "font/woff2",
        )
    }
}
