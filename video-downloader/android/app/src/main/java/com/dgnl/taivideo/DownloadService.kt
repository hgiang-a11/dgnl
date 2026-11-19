package com.dgnl.taivideo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Dịch vụ chạy nền: giữ cho việc tải tiếp tục khi người dùng chuyển sang ứng dụng khác,
 * kèm thông báo tiến độ. Tự tắt khi không còn video nào đang tải.
 */
class DownloadService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (update()) handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildProgress(this, JobManager.activeJobs())
        try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
            ServiceCompat.startForeground(this, PROGRESS_ID, notification, type)
        } catch (e: Exception) {
            Log.w(TAG, "startForeground failed", e)
            stopSelf()
            return START_NOT_STICKY
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_NOT_STICKY
    }

    private fun update(): Boolean {
        val active = JobManager.activeJobs()
        if (active.isEmpty()) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return false
        }
        manager(this).notify(PROGRESS_ID, buildProgress(this, active))
        return true
    }

    override fun onTimeout(startId: Int) {
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "TaiVideo"
        private const val CHANNEL_PROGRESS = "progress"
        private const val CHANNEL_DONE = "done"
        private const val PROGRESS_ID = 1001

        private fun manager(ctx: Context) = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        fun createChannels(ctx: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val m = manager(ctx)
            m.createNotificationChannel(
                NotificationChannel(CHANNEL_PROGRESS, "Đang tải video", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Hiện tiến độ khi đang tạo video"
                    setShowBadge(false)
                },
            )
            m.createNotificationChannel(
                NotificationChannel(CHANNEL_DONE, "Tải xong", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Báo khi video đã sẵn sàng"
                },
            )
        }

        /** Bật dịch vụ nếu đang có video cần tải. */
        fun refresh(ctx: Context) {
            if (JobManager.activeJobs().isEmpty()) return
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, DownloadService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "cannot start service", e) // vẫn tải được khi ứng dụng đang mở
            }
        }

        /** Khi tải xong mà người dùng đang ở ứng dụng khác thì báo một thông báo. */
        fun onJobFinished(ctx: Context, job: Job) {
            if (App.inForeground || job.state == "cancelled") return
            val ok = job.state == "done"
            val n = NotificationCompat.Builder(ctx, CHANNEL_DONE)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(if (ok) "Video đã sẵn sàng" else "Không tải được video")
                .setContentText(if (ok) job.title else (job.error ?: job.url))
                .setContentIntent(openApp(ctx))
                .setAutoCancel(true)
                .build()
            try {
                manager(ctx).notify(job.id.hashCode(), n)
            } catch (_: SecurityException) {
            }
        }

        private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun buildProgress(ctx: Context, active: List<Job>): Notification {
            val job = active.firstOrNull()
            val b = NotificationCompat.Builder(ctx, CHANNEL_PROGRESS)
                .setSmallIcon(R.drawable.ic_notification)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setContentIntent(openApp(ctx))
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            if (job == null) return b.setContentTitle("Đang chuẩn bị…").setProgress(0, 0, true).build()
            b.setContentTitle(job.info?.optString("title")?.takeIf { it.isNotEmpty() } ?: job.message)
            if (job.state == "downloading" && job.progress > 0) {
                val pct = job.progress.toInt()
                val speed = job.speed?.let { " · ${formatBytes(it.toLong())}/s" }.orEmpty()
                b.setContentText("Đang tạo video… $pct%$speed").setProgress(100, pct, false)
            } else {
                b.setContentText(job.message).setProgress(0, 0, true)
            }
            if (active.size > 1) b.setSubText("${active.size} video")
            return b.build()
        }

        private fun formatBytes(n: Long): String {
            val units = arrayOf("B", "KB", "MB", "GB")
            var v = n.toDouble()
            var i = 0
            while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
            return if (i == 0) "$n B" else String.format(java.util.Locale.US, "%.1f %s", v, units[i])
        }
    }
}
