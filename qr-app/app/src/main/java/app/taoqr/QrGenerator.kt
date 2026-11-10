package app.taoqr

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrGenerator {
    /** Byte capacity of the largest QR code (version 40) at error correction level M. */
    const val MAX_BYTES = 2331

    private const val WHITE = -0x1 // 0xFFFFFFFF

    fun byteLength(text: String): Int = text.toByteArray(Charsets.UTF_8).size

    /**
     * Renders [text] as a QR code in [color] on a white background, about [targetSize] px wide.
     * Each module is an exact block of pixels so the image stays sharp.
     * Returns null when the text cannot be encoded (for example when it is too long).
     */
    fun render(text: String, color: Int, targetSize: Int = 1024, quietZone: Int = 4): Bitmap? {
        val hints = buildMap<EncodeHintType, Any> {
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
            put(EncodeHintType.MARGIN, quietZone)
            // Plain ASCII needs no charset marker; anything else (e.g. Vietnamese) is sent as UTF-8.
            if (text.any { it.code > 0x7F }) put(EncodeHintType.CHARACTER_SET, "UTF-8")
        }
        val matrix = try {
            QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
        } catch (e: Exception) {
            return null
        }

        val modules = matrix.width
        val scale = maxOf(1, targetSize / modules)
        val size = modules * scale
        val pixels = IntArray(size * size)
        val row = IntArray(size)
        for (y in 0 until modules) {
            for (x in 0 until modules) {
                row.fill(if (matrix[x, y]) color else WHITE, x * scale, (x + 1) * scale)
            }
            for (dy in 0 until scale) {
                System.arraycopy(row, 0, pixels, (y * scale + dy) * size, size)
            }
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }
}
