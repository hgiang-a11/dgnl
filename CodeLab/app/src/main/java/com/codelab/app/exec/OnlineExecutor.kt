package com.codelab.app.exec

import android.content.Context
import com.codelab.app.model.Language
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Bộ chạy ONLINE — dùng cho C++, C, Java (những ngôn ngữ không dịch offline được).
 *
 * Code được gửi lên trình dịch miễn phí Wandbox (wandbox.org, không cần đăng ký) để dịch
 * và chạy, rồi lấy kết quả về. Nếu KHÔNG có mạng, app báo rõ "Không có kết nối Internet".
 */
class OnlineExecutor(
    private val context: Context,
    private val language: Language
) : CodeExecutor {

    /** Lỗi khi máy chủ trả về mã HTTP không thành công. */
    private class HttpError(val code: Int, val body: String) : IOException("HTTP $code")

    override fun execute(code: String, mode: ExecMode, input: InputProvider?): ExecResult {
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
            // Bước 2: chọn trình dịch phù hợp trên Wandbox
            val compiler = findCompiler()
                ?: return ExecResult(
                    "⚠ Máy chủ dịch Wandbox hiện không có trình dịch ${language.displayName}.",
                    isError = true
                )

            // Bước 3: dịch và chạy; nếu chương trình cần nhập thì hỏi người dùng
            // ngay ở khung kết quả rồi chạy tiếp (xem OnlineSession)
            OnlineSession(language, code, mode, input) { preparedCode, options, stdin ->
                val body = JSONObject()
                    .put("compiler", compiler)
                    .put("code", preparedCode)
                    .put("stdin", stdin)
                    .put("save", false)
                if (options.isNotEmpty()) body.put("compiler-option-raw", options)
                JSONObject(post(WandboxApi.COMPILE_URL, body.toString()))
            }.run()
        } catch (e: HttpError) {
            if (e.code == 429 || e.code == 503) {
                ExecResult(
                    "⚠ Máy chủ dịch đang quá tải hoặc bạn bấm chạy quá nhiều lần liên tục.\n" +
                        "Vui lòng đợi khoảng 1 phút rồi thử lại.",
                    isError = true
                )
            } else {
                // Có thể trình dịch đã nhớ không còn trên máy chủ -> lần sau chọn lại
                forgetCompiler()
                ExecResult(
                    "⚠ Máy chủ dịch báo lỗi (mã ${e.code}). Vui lòng thử lại sau.\n" +
                        e.body.take(300),
                    isError = true
                )
            }
        } catch (e: IOException) {
            ExecResult(
                "⚠ Không kết nối được tới máy chủ dịch Wandbox.\n" +
                    "Kiểm tra lại mạng rồi thử lại.\n" +
                    "Chi tiết: ${e.message}",
                isError = true
            )
        } catch (e: Exception) {
            ExecResult("⚠ Lỗi khi dịch online: ${e.message}", isError = true)
        }
    }

    // ---------- Chọn trình dịch (nhớ lại vài ngày để đỡ tải danh sách nhiều lần) ----------

    private val prefs by lazy {
        context.getSharedPreferences("wandbox", Context.MODE_PRIVATE)
    }
    private val prefKey get() = "compiler_${language.id}"

    private fun findCompiler(): String? {
        val saved = prefs.getString(prefKey, null)
        val savedAt = prefs.getLong("${prefKey}_at", 0L)
        if (saved != null && System.currentTimeMillis() - savedAt < CACHE_MILLIS) return saved

        val list = try {
            JSONArray(get(WandboxApi.LIST_URL))
        } catch (e: Exception) {
            // Không tải được danh sách: dùng tạm trình dịch đã nhớ hoặc tên mặc định
            return saved ?: language.preferredCompilers.firstOrNull()
        }

        val picked = WandboxApi.pickCompiler(list, language) ?: return null
        prefs.edit()
            .putString(prefKey, picked)
            .putLong("${prefKey}_at", System.currentTimeMillis())
            .apply()
        return picked
    }

    private fun forgetCompiler() {
        prefs.edit().remove(prefKey).remove("${prefKey}_at").apply()
    }

    // ---------- Gọi mạng ----------

    private fun get(url: String): String =
        call(Request.Builder().url(url).get().build())

    private fun post(url: String, json: String): String =
        call(Request.Builder().url(url).post(json.toRequestBody(JSON_TYPE)).build())

    private fun call(request: Request): String {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw HttpError(response.code, text)
            return text
        }
    }

    companion object {
        private const val CACHE_MILLIS = 3L * 24 * 60 * 60 * 1000   // 3 ngày
        private val JSON_TYPE = "application/json".toMediaType()

        // Dùng chung một kết nối cho cả app để nhanh hơn
        private val client: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)      // dịch Java có thể mất vài chục giây
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "CodeLab-Android/1.0")
                        .build()
                )
            }
            .build()
    }
}
