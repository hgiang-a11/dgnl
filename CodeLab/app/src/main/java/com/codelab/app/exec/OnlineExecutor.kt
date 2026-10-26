package com.codelab.app.exec

import android.content.Context
import com.codelab.app.model.Language
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Bộ chạy ONLINE — dùng cho C++, C, Java (những ngôn ngữ không dịch offline được).
 *
 * Code được gửi lên trình dịch miễn phí Piston (emkc.org) để dịch và chạy,
 * rồi lấy kết quả về. Nếu KHÔNG có mạng, app sẽ báo rõ "Không có kết nối Internet".
 */
class OnlineExecutor(
    private val context: Context,
    private val language: Language
) : CodeExecutor {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    override fun execute(code: String, mode: ExecMode): ExecResult {
        // Bước 1: kiểm tra mạng
        if (!NetworkUtil.isOnline(context)) {
            return ExecResult(
                "⚠ Không có kết nối Internet.\n\n" +
                    "Ngôn ngữ ${language.displayName} cần mạng để dịch và chạy. " +
                    "Vui lòng bật Wi-Fi hoặc dữ liệu di động rồi thử lại.\n\n" +
                    "(Mẹo: Python và JavaScript chạy được hoàn toàn offline.)",
                isError = true,
                requiresInternet = true
            )
        }

        return try {
            val responseText = sendRequest(code)
            parseResponse(responseText, mode)
        } catch (e: Exception) {
            ExecResult(
                "⚠ Không kết nối được tới trình dịch online.\n" +
                    "Chi tiết: ${e.message}",
                isError = true
            )
        }
    }

    /** Gửi code lên Piston và trả về chuỗi JSON kết quả. */
    private fun sendRequest(code: String): String {
        val fileName = when (language) {
            Language.JAVA -> "Main.java"   // Java cần đúng tên lớp Main
            else -> "main.${language.extension}"
        }

        val fileObj = JSONObject()
            .put("name", fileName)
            .put("content", code)

        val body = JSONObject()
            .put("language", language.pistonLang)
            .put("version", language.pistonVersion)
            .put("files", JSONArray().put(fileObj))
            .put("stdin", "")
            .toString()

        val request = Request.Builder()
            .url("https://emkc.org/api/v2/piston/execute")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw RuntimeException("Máy chủ trả về mã ${response.code}. $text")
            }
            return text
        }
    }

    /** Đọc kết quả JSON và tách phần lỗi dịch / kết quả chạy. */
    private fun parseResponse(json: String, mode: ExecMode): ExecResult {
        val root = JSONObject(json)

        // Giai đoạn dịch (một số ngôn ngữ như Python không có phần này)
        val compile = root.optJSONObject("compile")
        val compileErr = compile?.optString("stderr").orEmpty().trim()
        val compileCode = compile?.optInt("code", 0) ?: 0

        // Nếu chỉ Build để xem lỗi
        if (mode == ExecMode.BUILD_ONLY) {
            return if (compileErr.isNotEmpty() || compileCode != 0) {
                ExecResult("Lỗi khi dịch:\n$compileErr", isError = true)
            } else {
                ExecResult("✓ Dịch thành công, không có lỗi.", isError = false)
            }
        }

        // Nếu dịch lỗi thì dừng, hiện lỗi
        if (compileErr.isNotEmpty() && compileCode != 0) {
            return ExecResult("Lỗi khi dịch:\n$compileErr", isError = true)
        }

        // Giai đoạn chạy
        val run = root.optJSONObject("run")
        val stdout = run?.optString("stdout").orEmpty()
        val stderr = run?.optString("stderr").orEmpty()
        val runCode = run?.optInt("code", 0) ?: 0

        val sb = StringBuilder()
        if (stdout.isNotEmpty()) sb.append(stdout)
        if (stderr.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append(stderr)
        }
        val text = sb.toString().ifEmpty { "(chương trình không in ra gì)" }

        return ExecResult(text, isError = runCode != 0)
    }
}
