package com.dgnl.taivideo

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException

/** Chép video/âm thanh đã tạo vào thư viện chung của máy (Movies/TaiVideo hoặc Music/TaiVideo). */
object MediaSaver {
    private const val FOLDER = "TaiVideo"

    /** Trả về tên thư mục đã lưu, để báo cho người dùng. */
    fun save(ctx: Context, job: Job): String {
        val file = job.file ?: throw IOException("Video chưa sẵn sàng")
        val fi = job.fileInfo ?: throw IOException("Video chưa sẵn sàng")
        val isAudio = fi.optBoolean("is_audio")
        val mime = fi.optString("mime", if (isAudio) "audio/mpeg" else "video/mp4")
        val name = fi.optString("filename", file.name)
        val base = if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = ctx.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$base/$FOLDER")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val collection = if (isAudio) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            var folder = "$base/$FOLDER"
            var uri: Uri? = try { resolver.insert(collection, values) } catch (e: Exception) { null }
            if (uri == null) { // định dạng lạ: lưu vào Tải xuống
                folder = "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER"
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
                uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            }
            uri ?: throw IOException("Không tạo được tệp trong thư viện")
            try {
                (resolver.openOutputStream(uri) ?: throw IOException("Không mở được tệp")).use { out ->
                    file.inputStream().use { it.copyTo(out, 256 * 1024) }
                }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            return folder
        }

        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(base), FOLDER)
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Không tạo được thư mục $base/$FOLDER")
        val stem = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "mp4")
        var target = File(dir, name)
        var i = 1
        while (target.exists()) target = File(dir, "$stem (${i++}).$ext")
        file.copyTo(target)
        MediaScannerConnection.scanFile(ctx, arrayOf(target.absolutePath), arrayOf(mime), null)
        return "$base/$FOLDER"
    }
}
