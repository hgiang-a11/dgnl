package com.floattile

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Ô trong bảng cài đặt nhanh (Quick Settings = bảng kéo từ trên màn hình xuống).
 * Hệ thống gọi onClick() mỗi khi bạn bấm vào ô.
 */
class FloatTileService : TileService() {

    override fun onTileAdded() {
        super.onTileAdded()
        refreshTile()
    }

    /** Gọi mỗi khi bảng cài đặt nhanh được kéo xuống và ô hiện ra. */
    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val target = TargetApp.resolve(this)
        tile.label = getString(R.string.tile_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Dòng phụ dưới tên ô: tên ứng dụng sẽ được mở.
            tile.subtitle = target?.label ?: getString(R.string.tile_subtitle_missing)
        }
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile)
        tile.state = Tile.STATE_INACTIVE   // ô dạng "nút bấm", không có trạng thái bật/tắt
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        // Nếu máy đang khóa: yêu cầu mở khóa trước rồi mới chạy.
        if (isLocked) unlockAndRun { openLauncher() } else openLauncher()
    }

    private fun openLauncher() {
        val intent = Intent(this, LaunchActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+: bắt buộc dùng bản startActivityAndCollapse(PendingIntent).
            // PendingIntent = "lời nhắn mở màn hình" đóng gói sẵn, hệ thống sẽ thay mặt ta gửi đi.
            // FLAG_IMMUTABLE: không ai sửa được nội dung lời nhắn này.
            val pending = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pending)
        } else {
            // Android 13 trở xuống: dùng bản nhận Intent (đã cũ nhưng vẫn chạy).
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
