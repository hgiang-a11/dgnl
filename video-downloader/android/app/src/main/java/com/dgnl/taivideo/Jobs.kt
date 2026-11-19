package com.dgnl.taivideo

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import android.webkit.MimeTypeMap
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.withLock
import kotlin.math.max
import kotlin.math.min

/** Một lượt tải: trạng thái, tiến độ và tệp kết quả (cùng định dạng JSON với bản máy tính). */
class Job(val id: String, val url: String, val quality: String, val dir: File) {
    @Volatile var state = "queued" // queued | analyzing | downloading | processing | done | error | cancelled
    @Volatile var message = "Đang chờ đến lượt…"
    @Volatile var error: String? = null
    @Volatile var errorDetail: String? = null
    @Volatile var info: JSONObject? = null
    @Volatile var progress = 0.0
    @Volatile var speed: Double? = null
    @Volatile var eta: Double? = null
    @Volatile var downloaded = 0L
    @Volatile var total: Long? = null
    @Volatile var file: File? = null
    @Volatile var fileInfo: JSONObject? = null
    @Volatile var thumbUrl: String? = null
    @Volatile var thumbFile: File? = null
    @Volatile var thumbType = "image/jpeg"
    @Volatile var cancelled = false
    @Volatile var isLive = false
    @Volatile var outputPath: String? = null
    @Volatile var finishedAt = 0L
    val created = System.currentTimeMillis()
    val lock = Any()
    /** Khóa riêng cho ảnh bìa: tải ảnh chậm không được làm nghẽn việc đọc tiến độ. */
    val thumbLock = Any()
    val errorLines = ArrayList<String>()

    private val parts = LinkedHashMap<String, LongArray>() // mã định dạng -> [đã tải, tổng (-1 = chưa biết)]
    private val expected = LinkedHashMap<String, Long?>()

    val active get() = state in ACTIVE_STATES
    val title: String get() = info?.optString("title")?.takeIf { it.isNotEmpty() } ?: "Video"

    /** Đọc từng dòng yt-dlp in ra (các dòng có tiền tố TV... do mình đặt mẫu in). */
    fun handleLine(raw: String) {
        val line = raw.trim()
        try {
            when {
                line.startsWith("TVPROG ") -> onProgress(line.substring(7))
                line.startsWith("TVINFO ") -> onInfo(line.substring(7))
                line.startsWith("TVFMT ") -> onFormats(line.substring(6))
                line.startsWith("TVPP ") -> onPostprocess(line.substring(5))
                line.startsWith("TVFILE ") -> outputPath = line.substring(7).trim()
                line.startsWith("ERROR:") -> synchronized(errorLines) { errorLines += line }
            }
        } catch (e: Exception) {
            Log.w("TaiVideo", "bad line: $line", e)
        }
    }

    private fun onInfo(json: String) {
        val o = JSONObject(json)
        isLive = o.optBoolean("is_live", false)
        thumbUrl = o.optString("thumbnail").takeIf { it.startsWith("http") }
        val webpage = o.optString("webpage_url").ifEmpty { url }
        val uploader = listOf("uploader", "channel", "creator", "uploader_id")
            .map { o.optString(it) }.firstOrNull { it.isNotEmpty() }
        info = JSONObject().apply {
            put("title", o.optString("title").ifEmpty { o.optString("fulltitle").ifEmpty { "Video" } })
            putOpt("uploader", uploader)
            if (o.has("duration") && !o.isNull("duration")) put("duration", o.optDouble("duration"))
            put("platform", Texts.platformName(o.optString("extractor_key"), webpage))
            put("webpage_url", webpage)
            if (o.has("view_count") && !o.isNull("view_count")) put("view_count", o.optLong("view_count"))
            putOpt("upload_date", Texts.formatDate(o.optString("upload_date")))
            put("has_thumbnail", thumbUrl != null)
            if (o.has("width") && !o.isNull("width")) put("width", o.optInt("width"))
            if (o.has("height") && !o.isNull("height")) put("height", o.optInt("height"))
        }
        synchronized(lock) {
            if (expected.isEmpty()) {
                val size = sizeOf(o)
                expected[o.optString("format_id").ifEmpty { "0" }] = size
            }
        }
        if (state == "analyzing" || state == "queued") {
            state = "downloading"
            message = "Đang tạo video…"
        }
    }

    private fun onFormats(json: String) {
        if (!json.startsWith("[")) return
        val arr = JSONArray(json)
        if (arr.length() == 0) return
        synchronized(lock) {
            expected.clear()
            for (i in 0 until arr.length()) {
                val f = arr.optJSONObject(i) ?: continue
                expected[f.optString("format_id").ifEmpty { "$i" }] = sizeOf(f)
            }
        }
    }

    private fun sizeOf(o: JSONObject): Long? {
        for (k in listOf("filesize", "filesize_approx")) {
            if (o.has(k) && !o.isNull(k)) {
                val v = o.optDouble(k)
                if (!v.isNaN() && v > 0) return v.toLong()
            }
        }
        return null
    }

    private fun onProgress(s: String) {
        val f = s.split('|')
        fun num(i: Int) = f.getOrNull(i)?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() }
        val done = num(0)?.toLong() ?: 0L
        val tot = (num(1) ?: num(2))?.toLong()
        val fid = f.getOrNull(5)?.trim()?.takeIf { it.isNotEmpty() && it != "NA" } ?: "0"
        val status = f.getOrNull(6)?.trim()
        synchronized(lock) {
            if (status == "finished") {
                val d = num(1)?.toLong() ?: done
                parts[fid] = longArrayOf(d, d)
                speed = null
                eta = null
            } else {
                parts[fid] = longArrayOf(done, tot ?: -1L)
                speed = num(3)
                eta = num(4)
            }
            recompute()
        }
        if (state == "analyzing") {
            state = "downloading"
            message = "Đang tạo video…"
        }
    }

    private fun recompute() {
        val keys = LinkedHashSet<String>(expected.keys.ifEmpty { parts.keys })
        keys.addAll(parts.keys)
        val done = parts.values.sumOf { it[0] }
        val totals = keys.map { k -> parts[k]?.get(1)?.takeIf { it > 0 } ?: expected[k] }
        val pct: Double
        if (totals.isNotEmpty() && totals.all { it != null && it > 0 }) {
            val t = totals.sumOf { it!! }
            total = t
            pct = if (t > 0) done.toDouble() / t else 0.0
        } else {
            val fracs = parts.values.map { if (it[1] > 0) it[0].toDouble() / it[1] else 0.0 }
            pct = fracs.sum() / max(keys.size, 1)
            total = totals.filterNotNull().sum().takeIf { it > 0 }
        }
        downloaded = done
        progress = max(progress, min(pct * 100, 99.0))
    }

    private fun onPostprocess(s: String) {
        val f = s.split('|')
        val pp = f.getOrNull(0)?.trim().orEmpty()
        if (f.getOrNull(1)?.trim() != "started" || pp == "MoveFiles" || pp == "Metadata") return
        state = "processing"
        speed = null
        eta = null
        message = when (pp) {
            "Merger" -> "Đang ghép hình ảnh và âm thanh…"
            "ExtractAudio" -> "Đang chuyển sang MP3…"
            "VideoRemuxer", "VideoConvertor" -> "Đang chuyển định dạng…"
            else -> "Đang hoàn thiện tệp…"
        }
    }

    fun toJson(): JSONObject = synchronized(lock) {
        JSONObject().apply {
            put("id", id)
            put("url", url)
            put("quality", quality)
            put("state", state)
            put("message", message)
            put("progress", Math.round(progress * 10) / 10.0)
            putOpt("speed", speed)
            putOpt("eta", eta)
            put("downloaded", downloaded)
            putOpt("total", total)
            putOpt("info", info)
            putOpt("file", fileInfo)
            putOpt("error", error)
            putOpt("error_detail", errorDetail)
        }
    }

    companion object {
        val ACTIVE_STATES = setOf("queued", "analyzing", "downloading", "processing")
    }
}

object JobManager {
    private const val TAG = "TaiVideo"
    private const val JOB_TTL_MS = 2 * 60 * 60 * 1000L
    private const val PROGRESS =
        "download:TVPROG %(progress.downloaded_bytes)s|%(progress.total_bytes)s|%(progress.total_bytes_estimate)s|" +
            "%(progress.speed)s|%(progress.eta)s|%(info.format_id)s|%(progress.status)s"
    private const val PP_PROGRESS = "postprocess:TVPP %(progress.postprocessor)s|%(progress.status)s"
    private const val INFO =
        "video:TVINFO %(.{title,fulltitle,thumbnail,duration,uploader,channel,creator,uploader_id,extractor_key," +
            "webpage_url,view_count,upload_date,width,height,is_live,format_id,filesize,filesize_approx})j"
    private const val FORMATS = "video:TVFMT %(requested_formats.:.{format_id,filesize,filesize_approx})j"

    private val jobs = ConcurrentHashMap<String, Job>()
    private val pool = Executors.newFixedThreadPool(2)
    private val janitor = Executors.newSingleThreadScheduledExecutor()
    private lateinit var appCtx: Context
    private lateinit var root: File

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        root = File(appCtx.cacheDir, "jobs")
        root.deleteRecursively()
        root.mkdirs()
        janitor.scheduleWithFixedDelay({ cleanup() }, 5, 5, TimeUnit.MINUTES)
    }

    fun get(id: String): Job? = jobs[id]

    fun activeJobs(): List<Job> = jobs.values.filter { it.active && !it.cancelled }.sortedBy { it.created }

    fun create(url: String, quality: String): Job {
        val id = UUID.randomUUID().toString().replace("-", "")
        val job = Job(id, url, quality, File(root, id))
        jobs[id] = job
        pool.execute { run(job) }
        DownloadService.refresh(appCtx)
        return job
    }

    fun cancel(id: String) {
        val job = jobs[id] ?: return
        job.cancelled = true
        YoutubeDL.getInstance().destroyProcessById(id)
        if (!job.active || job.state == "queued") {
            jobs.remove(id)
            job.dir.deleteRecursively()
        }
    }

    private fun cleanup() {
        val now = System.currentTimeMillis()
        jobs.values.filter { !it.active && now - it.created > JOB_TTL_MS }.forEach {
            jobs.remove(it.id)
            it.dir.deleteRecursively()
        }
    }

    /** Tạo lệnh yt-dlp cho một lượt tải (tách riêng để kiểm thử được trên máy tính). */
    internal fun buildRequest(job: Job, cacheDir: File): YoutubeDLRequest {
        val req = YoutubeDLRequest(job.url)
        req.addOption("--no-playlist")
        req.addOption("--playlist-items", "1")
        req.addOption("--newline")
        req.addOption("--progress")
        req.addOption("--no-simulate")
        req.addOption("--no-mtime")
        req.addOption("--no-warnings")
        req.addOption("-o", File(job.dir, "media.%(ext)s").absolutePath)
        req.addOption("--cache-dir", File(cacheDir, "yt-dlp-cache").absolutePath)
        req.addOption("--socket-timeout", 30)
        req.addOption("--retries", 5)
        req.addOption("--fragment-retries", 10)
        req.addOption("--concurrent-fragments", 4)
        if (job.quality == "audio") {
            req.addOption("-f", "ba/b")
            req.addOption("-x")
            req.addOption("--audio-format", "mp3")
            req.addOption("--audio-quality", "192K")
        } else {
            // Ưu tiên độ phân giải cao nhất trong giới hạn, sau đó ưu tiên H.264 + AAC để MP4 mở được ở mọi nơi.
            val res = if (job.quality == "best") "res" else "res:${job.quality}"
            req.addOption("-S", "$res,fps,vcodec:h264,acodec:aac")
            req.addOption("-f", "bv*+ba/b")
            req.addOption("--merge-output-format", "mp4/mkv")
            req.addOption("--remux-video", "flv>mp4/ts>mp4")
        }
        req.addOption("--progress-template", PROGRESS)
        req.addOption("--progress-template", PP_PROGRESS)
        req.addOption("--print", INFO)
        req.addOption("--print", FORMATS)
        req.addOption("--print", "after_move:TVFILE %(filepath)s")
        return req
    }

    private fun run(job: Job) {
        if (job.cancelled) return
        try {
            if (!Engine.ready) job.message = "Đang khởi động bộ tải (lần đầu mất vài giây)…"
            if (!Engine.awaitReady()) {
                throw FriendlyException(
                    "Không khởi động được bộ tải video. Hãy tắt hẳn ứng dụng rồi mở lại.",
                    Engine.initError ?: "init failed",
                )
            }
            if (job.cancelled) return markCancelled(job)
            job.dir.mkdirs()
            job.state = "analyzing"
            job.message = "Đang phân tích link…"
            val req = buildRequest(job, appCtx.cacheDir)
            Engine.binaryLock.readLock().withLock {
                YoutubeDL.getInstance().execute(req, job.id, true) { _, _, line ->
                    job.handleLine(line)
                    if (job.cancelled || job.isLive) YoutubeDL.getInstance().destroyProcessById(job.id)
                }
            }
            if (job.cancelled) return markCancelled(job)
            if (job.isLive) throw liveError()
            finish(job)
        } catch (e: YoutubeDL.CanceledException) {
            if (job.isLive && !job.cancelled) fail(job, liveError()) else markCancelled(job)
        } catch (e: Exception) {
            if (job.cancelled) markCancelled(job) else fail(job, e)
        } finally {
            job.finishedAt = System.currentTimeMillis()
            DownloadService.onJobFinished(appCtx, job)
        }
    }

    private fun liveError() = FriendlyException(
        "Không hỗ trợ tải video đang phát trực tiếp. Hãy thử lại khi buổi phát đã kết thúc.", "live",
    )

    private fun markCancelled(job: Job) {
        job.state = "cancelled"
        job.message = "Đã hủy"
        job.dir.deleteRecursively()
    }

    private fun fail(job: Job, e: Exception) {
        val raw = Texts.cleanError(
            synchronized(job.errorLines) { job.errorLines.lastOrNull() } ?: e.message ?: e.toString(),
        )
        job.error = (e as? FriendlyException)?.friendly ?: Texts.friendlyError(raw)
        job.errorDetail = raw.ifEmpty { null }
        job.state = "error"
        job.message = job.error ?: "Lỗi"
        Log.w(TAG, "job ${job.id} failed: $raw")
    }

    private val SKIP_SUFFIX = listOf(".part", ".ytdl", ".temp", ".json")
    private val NOT_VIDEO = setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "bmp", "html", "htm", "txt", "json")
    private val AUDIO = setOf("mp3", "m4a", "opus", "ogg", "wav", "aac", "flac")

    private fun finish(job: Job) {
        var path = job.outputPath?.let(::File)?.takeIf { it.isFile }
            ?: job.dir.listFiles()
                ?.filter { f -> f.isFile && f.name != "thumb" && SKIP_SUFFIX.none { f.name.endsWith(it) } }
                ?.maxByOrNull { it.length() }
            ?: throw FriendlyException("Không tìm thấy video nào trong link này.", "no media file produced")

        val ext = path.extension.lowercase().ifEmpty { "mp4" }
        if (ext in NOT_VIDEO) {
            throw FriendlyException(
                "Link này là ảnh hoặc trang thường, không chứa video. Hãy dán link của một video.",
                "not a video file (.$ext)",
            )
        }
        val isAudio = job.quality == "audio" || ext in AUDIO
        val filename = Texts.safeFilename(job.title, ext)
        // Đặt tên tệp theo tiêu đề để khi chia sẻ sang ứng dụng khác vẫn đúng tên.
        File(job.dir, filename).let { nice -> if (nice != path && path.renameTo(nice)) path = nice }

        var width = job.info?.optInt("width", 0) ?: 0
        var height = job.info?.optInt("height", 0) ?: 0
        val needDuration = job.info != null && !job.info!!.has("duration")
        if ((!isAudio && (width == 0 || height == 0)) || needDuration) {
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(path.absolutePath)
                if (width == 0) width = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                if (height == 0) height = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val ms = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                if (needDuration && ms != null && ms > 0) synchronized(job.lock) { job.info?.put("duration", ms / 1000.0) }
            } catch (_: Exception) {
            } finally {
                try { mmr.release() } catch (_: Exception) {}
            }
        }
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: if (isAudio) "audio/mpeg" else "video/mp4"
        val size = path.length()
        synchronized(job.lock) {
            job.file = path
            job.downloaded = size
            job.total = size
            job.fileInfo = JSONObject().apply {
                put("ext", ext)
                put("size", size)
                put("is_audio", isAudio)
                if (!isAudio && width > 0) put("width", width)
                if (!isAudio && height > 0) put("height", height)
                put("mime", mime)
                put("filename", filename)
            }
            job.progress = 100.0
            job.state = "done"
            job.message = "Hoàn tất"
        }
    }
}
