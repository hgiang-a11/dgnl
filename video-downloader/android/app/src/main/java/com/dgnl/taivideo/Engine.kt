package com.dgnl.taivideo

import android.content.Context
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

/**
 * Bộ tải: giải nén Python + yt-dlp + ffmpeg ở lần chạy đầu, rồi tự cập nhật yt-dlp
 * lên bản mới nhất (các trang video thay đổi thường xuyên nên bản cũ hay bị lỗi).
 */
object Engine {
    private const val TAG = "TaiVideo"
    private const val PREFS = "taivideo"
    private const val UPDATE_EVERY_MS = 6 * 60 * 60 * 1000L
    private const val RELEASE_API = "https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest"

    private val initLatch = CountDownLatch(1)

    @Volatile var ready = false
        private set
    @Volatile var initError: String? = null
        private set
    @Volatile var version: String = ""
        private set
    @Volatile var updating = false
        private set

    /** Mỗi lượt tải giữ khóa đọc; việc thay tệp yt-dlp mới cần khóa ghi (đợi các lượt tải xong). */
    val binaryLock = ReentrantReadWriteLock()

    fun start(ctx: Context) {
        val app = ctx.applicationContext
        thread(name = "engine-init", isDaemon = true) {
            try {
                YoutubeDL.getInstance().init(app)
                FFmpeg.getInstance().init(app)
                ready = true
            } catch (e: Throwable) {
                initError = e.message ?: e.toString()
                Log.e(TAG, "init failed", e)
            } finally {
                initLatch.countDown()
            }
            if (ready) {
                version = prefs(app).getString("ytdlp_version", null) ?: readVersion().orEmpty()
                try {
                    maybeUpdate(app)
                } catch (e: Throwable) {
                    Log.w(TAG, "update failed", e)
                } finally {
                    updating = false
                }
            }
        }
    }

    fun awaitReady(): Boolean {
        initLatch.await()
        return ready
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun ytdlpFile(ctx: Context) =
        File(ctx.noBackupFilesDir, "${YoutubeDL.baseName}/${YoutubeDL.ytdlpDirName}/${YoutubeDL.ytdlpBin}")

    private fun readVersion(): String? = try {
        binaryLock.readLock().withLock {
            val req = YoutubeDLRequest(emptyList<String>()).addOption("--version")
            YoutubeDL.getInstance().execute(req, null, false, null).out.trim().lineSequence().firstOrNull()
        }
    } catch (e: Exception) {
        Log.w(TAG, "version failed", e)
        null
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "TaiVideo-Android")
        }

    private fun maybeUpdate(ctx: Context) {
        val p = prefs(ctx)
        val now = System.currentTimeMillis()
        if (now - p.getLong("last_update_check", 0) < UPDATE_EVERY_MS) return
        updating = true

        val release = open(RELEASE_API).run {
            setRequestProperty("Accept", "application/vnd.github+json")
            inputStream.bufferedReader().use { JSONObject(it.readText()) }
        }
        p.edit().putLong("last_update_check", now).apply()
        val tag = release.getString("tag_name")
        if (tag == version) return

        var binUrl: String? = null
        var sumsUrl: String? = null
        val assets = release.getJSONArray("assets")
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            when (a.optString("name")) {
                "yt-dlp" -> binUrl = a.optString("browser_download_url")
                "SHA2-256SUMS" -> sumsUrl = a.optString("browser_download_url")
            }
        }
        if (binUrl == null || sumsUrl == null) return

        val expected = open(sumsUrl).inputStream.bufferedReader().use { r ->
            r.readLines().map { it.trim().split(Regex("\\s+")) }
                .firstOrNull { it.size == 2 && it[1].removePrefix("*") == "yt-dlp" }?.get(0)?.lowercase()
        } ?: return

        val target = ytdlpFile(ctx)
        val tmp = File(target.parentFile, "yt-dlp.new")
        val digest = MessageDigest.getInstance("SHA-256")
        open(binUrl).inputStream.use { input ->
            tmp.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    digest.update(buf, 0, n)
                    out.write(buf, 0, n)
                }
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual != expected || tmp.length() < 1_000_000) {
            tmp.delete()
            Log.w(TAG, "checksum mismatch, update skipped")
            return
        }

        // Chờ tới khi không còn lượt tải nào đang dùng tệp cũ rồi mới thay.
        val lock = binaryLock.writeLock()
        while (!lock.tryLock(5, TimeUnit.SECONDS)) { /* đợi */ }
        try {
            if (!tmp.renameTo(target)) {
                target.delete()
                if (!tmp.renameTo(target)) throw IllegalStateException("rename failed")
            }
            version = tag
            p.edit().putString("ytdlp_version", tag).apply()
            // giữ đồng bộ với chỗ lưu phiên bản của thư viện youtubedl-android
            ctx.getSharedPreferences("youtubedl-android", Context.MODE_PRIVATE).edit().putString("dlpVersion", tag).apply()
            Log.i(TAG, "yt-dlp updated to $tag")
        } finally {
            lock.unlock()
        }
    }
}
