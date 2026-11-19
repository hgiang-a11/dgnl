package com.dgnl.taivideo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextsTest {
    @Test
    fun friendlyErrors() {
        assertTrue(Texts.friendlyError("Unsupported URL: https://x.com").contains("chưa được hỗ trợ"))
        assertTrue(Texts.friendlyError("Sign in to confirm you're not a bot").contains("xác minh"))
        assertTrue(Texts.friendlyError("Private video. Sign in").contains("riêng tư"))
        assertTrue(Texts.friendlyError("Video unavailable").contains("không tồn tại"))
        // "webpage" không được hiểu nhầm thành giới hạn độ tuổi
        assertTrue(Texts.friendlyError("Unable to download webpage: timed out").contains("kết nối"))
    }

    @Test
    fun cleanError() {
        assertEquals("Video unavailable", Texts.cleanError("ERROR: [youtube] dQw4w9WgXcQ: Video unavailable\nmore"))
    }

    @Test
    fun filenames() {
        assertEquals("Hạ Long đẹp! (1).mp4", Texts.safeFilename("Hạ Long: đẹp! (1)".replace(":", ""), "mp4"))
        assertEquals("a b.mp3", Texts.safeFilename("a/b", "mp3"))
        assertEquals("video.mp4", Texts.safeFilename("  ...  ", "mp4"))
        val cd = Texts.contentDisposition("attachment", "Đẹp quá.mp4")
        assertTrue(cd.startsWith("attachment; filename=\"Dep qua.mp4\"; filename*=UTF-8''"))
        assertTrue(cd.contains("%C4%90%E1%BA%B9p%20qu%C3%A1.mp4"))
    }

    @Test
    fun platformAndDate() {
        assertEquals("YouTube", Texts.platformName("Youtube", null))
        assertEquals("X (Twitter)", Texts.platformName("Twitter", null))
        assertEquals("example.com", Texts.platformName("Generic", "https://www.example.com/v.mp4"))
        assertEquals("31/01/2024", Texts.formatDate("20240131"))
    }
}
