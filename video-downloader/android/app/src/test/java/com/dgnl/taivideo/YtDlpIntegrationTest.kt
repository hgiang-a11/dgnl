package com.dgnl.taivideo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Chạy yt-dlp thật (trên máy tính) với đúng tham số ứng dụng Android tạo ra, rồi đưa từng
 * dòng kết quả vào Job.handleLine giống thư viện youtubedl-android (gộp stdout + stderr,
 * tách dòng theo \r hoặc \n). Cần biến môi trường TV_YTDLP và TV_MEDIA_BASE.
 */
class YtDlpIntegrationTest {
    private val ytdlp = System.getenv("TV_YTDLP")
    private val base = System.getenv("TV_MEDIA_BASE")

    private class Run(val job: Job, val exit: Int, val states: List<String>, val progress: List<Double>, val messages: List<String>)

    private fun run(path: String, quality: String): Run {
        assumeTrue("Đặt TV_YTDLP và TV_MEDIA_BASE để chạy", ytdlp != null && base != null)
        val dir = Files.createTempDirectory("tvjob").toFile()
        val job = Job("test", "$base/$path", quality, dir)
        val args = JobManager.buildRequest(job, dir).buildCommand()
        val proc = ProcessBuilder(listOf(ytdlp!!) + args).redirectErrorStream(true).start()
        val states = mutableListOf<String>()
        val progress = mutableListOf<Double>()
        val messages = mutableListOf<String>()
        proc.inputStream.reader(Charsets.UTF_8).use { r ->
            val line = StringBuilder()
            while (true) {
                val c = r.read()
                if (c == -1) break
                if (c == '\r'.code || c == '\n'.code) {
                    job.handleLine(line.toString())
                    line.setLength(0)
                    if (states.lastOrNull() != job.state) states += job.state
                    if (messages.lastOrNull() != job.message) messages += job.message
                    progress += job.progress
                } else {
                    line.append(c.toChar())
                }
            }
        }
        return Run(job, proc.waitFor(), states, progress, messages)
    }

    @Test
    fun dashVideoIsMergedIntoMp4() {
        val r = run("dash/manifest.mpd", "720")
        assertEquals(0, r.exit)
        assertTrue(r.states.toString(), r.states.contains("downloading"))
        assertTrue(r.states.toString(), r.states.contains("processing"))
        assertTrue(r.messages.toString(), r.messages.contains("Đang ghép hình ảnh và âm thanh…"))
        val out = File(r.job.outputPath!!)
        assertTrue(out.isFile)
        assertEquals("mp4", out.extension)
        assertEquals(720, r.job.info!!.getInt("height"))
        assertEquals("manifest", r.job.info!!.getString("title"))
        assertTrue("progress ${r.job.progress}", r.job.progress >= 90)
        assertEquals(r.progress, r.progress.sorted()) // tiến độ không bao giờ lùi
    }

    @Test
    fun pageWithVideoReportsSizeAndSpeed() {
        val r = run("page.html", "1080")
        assertEquals(0, r.exit)
        val info = r.job.info!!
        assertTrue(info.getString("title").startsWith("Video mẫu có tiếng Việt"))
        assertEquals(true, info.getBoolean("has_thumbnail"))
        assertNotNull(r.job.thumbUrl)
        assertNotNull(r.job.total)
        assertTrue(r.job.downloaded > 4_000_000)
        assertTrue(File(r.job.outputPath!!).isFile)
    }

    @Test
    fun audioIsConvertedToMp3() {
        val r = run("sample.mp4", "audio")
        assertEquals(0, r.exit)
        assertTrue(r.messages.toString(), r.messages.contains("Đang chuyển sang MP3…"))
        assertEquals("mp3", File(r.job.outputPath!!).extension)
    }

    @Test
    fun missingVideoGivesFriendlyError() {
        val r = run("khong-co.mp4", "720")
        assertNotEquals(0, r.exit)
        val last = r.job.errorLines.lastOrNull()
        assertNotNull(last)
        assertTrue(Texts.friendlyError(Texts.cleanError(last)).contains("không tồn tại"))
    }
}
