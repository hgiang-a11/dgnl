package com.floattile

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.ParcelFileDescriptor
import moe.shizuku.server.IShizukuService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

/** Kết quả chạy một lệnh shell. */
data class ShellResult(val exitCode: Int, val output: String) {
    val ok: Boolean get() = exitCode == 0 && !looksLikeError(output)

    companion object {
        fun looksLikeError(text: String): Boolean =
            text.contains("Error", ignoreCase = false) ||
                text.contains("Exception") ||
                text.contains("Unknown command", ignoreCase = true)
    }
}

/**
 * Mọi thứ liên quan tới Shizuku.
 * Shizuku là ứng dụng mã nguồn mở, sau khi được bật qua "Gỡ lỗi không dây" (hoặc root)
 * sẽ cho các ứng dụng khác (khi bạn đồng ý) chạy lệnh với quyền ADB.
 */
object ShizukuHelper {
    const val PACKAGE = "moe.shizuku.privileged.api"
    const val REQUEST_CODE = 4242

    enum class Status { NOT_INSTALLED, NOT_RUNNING, TOO_OLD, NO_PERMISSION, READY }

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** Shizuku đang chạy và đã gửi "binder" (kênh giao tiếp) sang ứng dụng này chưa. */
    fun isRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        false
    }

    fun status(context: Context): Status {
        if (!isRunning()) return if (isInstalled(context)) Status.NOT_RUNNING else Status.NOT_INSTALLED
        return try {
            when {
                Shizuku.isPreV11() -> Status.TOO_OLD
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> Status.READY
                else -> Status.NO_PERMISSION
            }
        } catch (t: Throwable) {
            Status.NOT_RUNNING
        }
    }

    fun describe(status: Status): String = when (status) {
        Status.NOT_INSTALLED -> "❌ Chưa cài ứng dụng Shizuku"
        Status.NOT_RUNNING -> "⚠️ Đã cài Shizuku nhưng Shizuku chưa chạy (cần bấm Khởi động trong Shizuku)"
        Status.TOO_OLD -> "⚠️ Phiên bản Shizuku quá cũ, hãy cập nhật"
        Status.NO_PERMISSION -> "⚠️ Shizuku đang chạy nhưng FloatTile chưa được cấp quyền"
        Status.READY -> "✅ Shizuku sẵn sàng (chạy với quyền ${uidLabel()})"
    }

    fun uidLabel(): String = try {
        when (val uid = Shizuku.getUid()) {
            0 -> "root"
            2000 -> "ADB/shell"
            else -> "uid $uid"
        }
    } catch (t: Throwable) {
        "?"
    }

    /** Người dùng đã từng chọn "Từ chối và không hỏi lại" chưa. */
    fun permissionPermanentlyDenied(): Boolean = try {
        Shizuku.shouldShowRequestPermissionRationale()
    } catch (t: Throwable) {
        false
    }

    /**
     * Khi ứng dụng vừa khởi động (ví dụ do bấm ô cài đặt nhanh), binder của Shizuku tới chậm
     * một chút. Hàm này chờ tối đa [timeoutMs] mili-giây.
     */
    suspend fun awaitBinder(timeoutMs: Long = 2000): Boolean {
        if (isRunning()) return true
        val received = CompletableDeferred<Unit>()
        val listener = Shizuku.OnBinderReceivedListener { received.complete(Unit) }
        try {
            Shizuku.addBinderReceivedListenerSticky(listener)
            withTimeoutOrNull(timeoutMs) { received.await() }
        } catch (t: Throwable) {
            // bỏ qua: coi như không có Shizuku
        } finally {
            runCatching { Shizuku.removeBinderReceivedListener(listener) }
        }
        return isRunning()
    }

    /** Hiện hộp thoại xin quyền của Shizuku và chờ người dùng trả lời. */
    suspend fun requestPermission(): Boolean {
        val result = CompletableDeferred<Boolean>()
        val listener = Shizuku.OnRequestPermissionResultListener { code, grant ->
            if (code == REQUEST_CODE) result.complete(grant == PackageManager.PERMISSION_GRANTED)
        }
        return try {
            Shizuku.addRequestPermissionResultListener(listener)
            Shizuku.requestPermission(REQUEST_CODE)
            result.await()
        } catch (t: Throwable) {
            false
        } finally {
            runCatching { Shizuku.removeRequestPermissionResultListener(listener) }
        }
    }

    // ---------------------------------------------------------------------------------
    // Chạy lệnh shell qua UserService (dịch vụ chạy trong tiến trình có quyền ADB).
    // ---------------------------------------------------------------------------------

    @Volatile
    private var service: IShellService? = null
    private var pending: CompletableDeferred<IShellService?>? = null
    private val mutex = Mutex()

    private val serviceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(BuildConfig.APPLICATION_ID, ShellService::class.java.name))
            .daemon(false)                  // tự tắt khi FloatTile tắt
            .processNameSuffix("shell")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE) // đổi phiên bản → Shizuku khởi động lại dịch vụ mới
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val s = if (binder != null && binder.pingBinder()) IShellService.Stub.asInterface(binder) else null
            service = s
            pending?.complete(s)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    /** Lý do lần gần nhất không dựng được tiến trình phụ (để hiện trong báo cáo). */
    @Volatile
    var lastServiceError: String? = null
        private set

    /** Đường chạy lệnh của lần gần nhất: "trực tiếp" hoặc "tiến trình phụ". */
    @Volatile
    var lastExecPath: String? = null
        private set

    private suspend fun obtainService(timeoutMs: Long): IShellService? = mutex.withLock {
        val current = service
        if (current != null && current.asBinder().pingBinder()) return@withLock current
        val waiter = CompletableDeferred<IShellService?>()
        pending = waiter
        try {
            withContext(Dispatchers.Main) { Shizuku.bindUserService(serviceArgs, connection) }
        } catch (t: Throwable) {
            lastServiceError = "Shizuku từ chối dựng tiến trình phụ: $t"
            return@withLock null
        }
        val s = withTimeoutOrNull(timeoutMs) { waiter.await() }
        if (s == null) {
            lastServiceError = "Chờ ${timeoutMs / 1000} giây mà Shizuku không dựng xong tiến trình phụ."
        }
        s
    }

    /**
     * Đường 1 – chạy trực tiếp: nhờ chính dịch vụ Shizuku (đang chạy với quyền ADB) tạo tiến trình
     * "sh -c <lệnh>", qua hàm newProcess trong giao diện công khai IShizukuService của Shizuku.
     * Không cần dựng thêm tiến trình phụ nên ít chỗ hỏng hơn.
     */
    private fun execDirect(command: String): ShellResult {
        val binder = Shizuku.getBinder() ?: throw IllegalStateException("chưa nhận được kết nối từ Shizuku")
        val shizuku = IShizukuService.Stub.asInterface(binder)
        // "exec 2>&1": gộp thông báo lỗi vào cùng luồng kết quả cho dễ đọc.
        val remote = shizuku.newProcess(arrayOf("sh", "-c", "exec 2>&1; $command"), null, null)
            ?: throw IllegalStateException("Shizuku không tạo được tiến trình")
        try {
            // Đóng đầu vào: lệnh không cần nhập gì, tránh bị treo chờ dữ liệu.
            runCatching { remote.outputStream?.close() }
            val output = ParcelFileDescriptor.AutoCloseInputStream(remote.inputStream)
                .bufferedReader().use { it.readText() }
            val code = remote.waitFor()
            return ShellResult(code, output)
        } finally {
            runCatching { remote.destroy() }
        }
    }

    /** Đường 2 – tiến trình phụ (UserService). Trả về null nếu không kết nối được. */
    private suspend fun execViaService(command: String, connectTimeoutMs: Long): ShellResult? {
        val s = obtainService(connectTimeoutMs) ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val raw = s.exec(command) ?: ""
                val nl = raw.indexOf('\n')
                if (nl < 0) {
                    ShellResult(raw.trim().toIntOrNull() ?: -1, "")
                } else {
                    ShellResult(raw.substring(0, nl).trim().toIntOrNull() ?: -1, raw.substring(nl + 1))
                }
            } catch (t: Throwable) {
                service = null
                lastServiceError = "Tiến trình phụ lỗi khi chạy lệnh: $t"
                null
            }
        }
    }

    /**
     * Chạy [command] với quyền ADB. Cần Shizuku ở trạng thái READY.
     * Thử đường 1 (trực tiếp) trước, hỏng thì thử đường 2 (tiến trình phụ).
     */
    suspend fun exec(command: String, connectTimeoutMs: Long = 8000): ShellResult {
        if (!isRunning()) return ShellResult(-1, "Shizuku chưa chạy")

        val direct = withContext(Dispatchers.IO) { runCatching { execDirect(command) } }
        direct.getOrNull()?.let {
            lastExecPath = "trực tiếp"
            return it
        }
        val directError = direct.exceptionOrNull()?.toString() ?: "?"

        execViaService(command, connectTimeoutMs)?.let {
            lastExecPath = "tiến trình phụ"
            return it
        }
        lastExecPath = null
        return ShellResult(
            -1,
            "Không chạy được lệnh qua Shizuku.\n" +
                "• Đường trực tiếp: $directError\n" +
                "• Đường tiến trình phụ: ${lastServiceError ?: "?"}"
        )
    }

    /** Phiên bản Shizuku (ứng dụng và dịch vụ) để ghi vào báo cáo. */
    fun versionInfo(context: Context): String {
        val app = try {
            context.packageManager.getPackageInfo(PACKAGE, 0).versionName ?: "?"
        } catch (e: PackageManager.NameNotFoundException) {
            "chưa cài"
        }
        val server = try {
            Shizuku.getVersion().toString()
        } catch (t: Throwable) {
            "?"
        }
        return "ứng dụng Shizuku $app, dịch vụ API $server"
    }
}
