package com.dgnl.taivideo

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.json.JSONObject
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private lateinit var web: WebView
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var pageReady = false
    private var pendingSaveId: String? = null

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val storagePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val id = pendingSaveId
            pendingSaveId = null
            if (id == null) return@registerForActivityResult
            if (granted) doSave(id) else notifyJs("saved", false, "Cần cho phép quyền lưu tệp để lưu video vào máy.")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        root = FrameLayout(this)
        web = WebView(this)
        root.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
        applyBars(isSystemDark())

        // Chừa chỗ cho thanh trạng thái, thanh điều hướng và bàn phím.
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }

        setupWebView()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    customView != null -> hideCustomView()
                    web.canGoBack() -> web.goBack()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })

        loadHome(sharedUrl(intent))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val url = sharedUrl(intent) ?: return
        if (pageReady) {
            web.evaluateJavascript("window.taiVideoStart && window.taiVideoStart(${JSONObject.quote(url)})", null)
        } else {
            loadHome(url)
        }
    }

    override fun onResume() {
        super.onResume()
        App.inForeground = true
        web.onResume()
    }

    override fun onPause() {
        App.inForeground = false
        web.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        root.removeView(web)
        web.destroy()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (pageReady) web.evaluateJavascript("window.taiVideoSyncTheme && window.taiVideoSyncTheme()", null)
    }

    // ------------------------------------------------------------ WebView

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = true
            allowFileAccess = false
            allowContentAccess = false
            setSupportZoom(false)
            builtInZoomControls = false
        }
        web.overScrollMode = View.OVER_SCROLL_NEVER
        web.addJavascriptInterface(Bridge(), "TaiVideoApp")

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                if (u.host == "127.0.0.1") return false
                // Link ra ngoài: mở bằng trình duyệt.
                try { startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (_: Exception) {}
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                pageReady = true
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                recreate()
                return true
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = view
                customCallback = callback
                view.setBackgroundColor(Color.BLACK)
                (window.decorView as FrameLayout).addView(
                    view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                )
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }

            override fun onHideCustomView() = hideCustomView()
        }

        web.setDownloadListener { url, _, _, _, _ ->
            Regex("/api/jobs/([0-9a-f]+)/download").find(url)?.let { requestSave(it.groupValues[1]) }
        }
    }

    private fun hideCustomView() {
        val v = customView ?: return
        (window.decorView as FrameLayout).removeView(v)
        customView = null
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        customCallback?.onCustomViewHidden()
        customCallback = null
    }

    private fun loadHome(sharedUrl: String?) {
        val server = (application as App).server
        val base = server.baseUrl
        val target = base + "/" + (sharedUrl?.let { "?url=" + Uri.encode(it) } ?: "")
        pageReady = false
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setCookie(base, "${LocalServer.COOKIE}=${server.token}; path=/") {
                flush()
                web.post { web.loadUrl(target) }
            }
        }
    }

    private fun sharedUrl(intent: Intent?): String? {
        intent ?: return null
        val text = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
        return Texts.extractUrl(text)
    }

    // ------------------------------------------------------------ Giao diện hệ thống

    private fun isSystemDark() =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun applyBars(dark: Boolean) {
        val bg = if (dark) 0xFF0B0D16.toInt() else 0xFFF6F7FB.toInt()
        root.setBackgroundColor(bg)
        web.setBackgroundColor(bg)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    // ------------------------------------------------------------ Lưu / chia sẻ

    private fun requestSave(id: String) {
        if (JobManager.get(id)?.file == null) {
            notifyJs("saved", false, "Video chưa sẵn sàng.")
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingSaveId = id
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        doSave(id)
    }

    private fun doSave(id: String) {
        val job = JobManager.get(id) ?: return notifyJs("saved", false, "Không tìm thấy video (có thể đã hết hạn).")
        thread(name = "save-$id") {
            try {
                val folder = MediaSaver.save(this, job)
                val where = if (job.fileInfo?.optBoolean("is_audio") == true) "trình nghe nhạc" else "Thư viện ảnh"
                notifyJs("saved", true, "Đã lưu vào $folder – xem trong $where")
            } catch (e: Exception) {
                notifyJs("saved", false, "Không lưu được video: ${e.message ?: "lỗi không rõ"}")
            }
        }
    }

    private fun shareJob(id: String) {
        val job = JobManager.get(id)
        val file = job?.file
        if (file == null || !file.isFile) {
            Toast.makeText(this, "Video chưa sẵn sàng", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
            val send = Intent(Intent.ACTION_SEND)
                .setType(job.fileInfo?.optString("mime") ?: "video/*")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(send, "Chia sẻ video"))
        } catch (e: Exception) {
            Toast.makeText(this, "Không chia sẻ được: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences("taivideo", Context.MODE_PRIVATE)
        if (prefs.getBoolean("asked_notifications", false)) return
        prefs.edit().putBoolean("asked_notifications", true).apply()
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun notifyJs(event: String, ok: Boolean, message: String) {
        val payload = JSONObject().put("ok", ok).put("message", message)
        runOnUiThread {
            web.evaluateJavascript("window.__tvNative && window.__tvNative(${JSONObject.quote(event)}, $payload)", null)
        }
    }

    /** Các hàm giao diện web gọi được qua window.TaiVideoApp. */
    inner class Bridge {
        @JavascriptInterface
        fun save(id: String) = runOnUiThread { requestSave(id) }

        @JavascriptInterface
        fun share(id: String) = runOnUiThread { shareJob(id) }

        @JavascriptInterface
        fun readClipboard(): String {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip ?: return ""
            if (clip.itemCount == 0) return ""
            return clip.getItemAt(0).coerceToText(this@MainActivity)?.toString().orEmpty()
        }

        @JavascriptInterface
        fun copyText(text: String) = runOnUiThread {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("link", text))
        }

        @JavascriptInterface
        fun onDownloadStarted() = runOnUiThread { askNotificationPermission() }

        @JavascriptInterface
        fun setDarkMode(dark: Boolean) = runOnUiThread { applyBars(dark) }

        @JavascriptInterface
        fun isSystemDark(): Boolean = this@MainActivity.isSystemDark()

        @JavascriptInterface
        fun appVersion(): String = BuildConfig.VERSION_NAME
    }
}
