package app.taoqr

import android.graphics.Bitmap
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class QrLogicTest {

    private fun decode(bitmap: Bitmap): String {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        val hints = mapOf(DecodeHintType.PURE_BARCODE to true)
        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), hints).text
    }

    @Test
    fun linkRoundTrips() {
        val link = "https://facebook.com/trangcuaban?ref=qr"
        val bitmap = QrGenerator.render(link, QrColors.first().argb)!!
        assertEquals(link, decode(bitmap))
    }

    @Test
    fun vietnameseTextRoundTripsInEveryColour() {
        val text = "Xin chào! Đây là mã QR có dấu tiếng Việt: ăâđêôơư ẤẦẨẪẬ 😀"
        QrColors.forEach { colour ->
            val bitmap = QrGenerator.render(text, colour.argb)!!
            assertEquals("colour ${colour.name}", text, decode(bitmap))
        }
    }

    @Test
    fun textAtCapacityEncodesAndTooLongTextFails() {
        assertTrue(QrGenerator.render("a".repeat(QrGenerator.MAX_BYTES), 0xFF000000.toInt()) != null)
        assertNull(QrGenerator.render("a".repeat(QrGenerator.MAX_BYTES + 1), 0xFF000000.toInt()))
    }

    @Test
    fun linksGetHttpsWhenMissing() {
        assertEquals("https://facebook.com/abc", LinkUtils.normalize("  facebook.com/abc "))
        assertEquals("http://example.com", LinkUtils.normalize("http://example.com"))
        assertEquals("mailto:ban@example.com", LinkUtils.normalize("mailto:ban@example.com"))
        assertEquals("", LinkUtils.normalize("   "))
    }

    @Test
    fun linkValidation() {
        assertTrue(LinkUtils.looksValid("https://zalo.me/0123456789"))
        assertFalse(LinkUtils.looksValid("https://xin chao"))
        assertTrue(LinkUtils.isSingleUrl("youtube.com/watch?v=abc"))
        assertFalse(LinkUtils.isSingleUrl("Xem video này nhé youtube.com"))
        assertEquals(
            listOf("https://vt.tiktok.com/ZS123/"),
            LinkUtils.findHttpUrls("Xem video này nhé: https://vt.tiktok.com/ZS123/."),
        )
    }
}
