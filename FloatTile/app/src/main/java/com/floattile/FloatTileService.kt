package com.floattile

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Ô trong bảng cài đặt nhanh (Quick Settings = bảng kéo từ trên màn hình xuống).
 * Lớp gốc dùng chung; mỗi "chỗ" 1..10 là một lớp con nhỏ ở cuối file.
 * Hệ thống gọi onClick() mỗi khi bạn bấm vào ô.
 */
abstract class SlotTileService(private val slot: Int) : TileService() {

    override fun onTileAdded() {
        super.onTileAdded()
        TileStore.setAdded(slot, true)
        refreshTile()
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        TileStore.setAdded(slot, false)
    }

    /** Gọi mỗi khi bảng cài đặt nhanh được kéo xuống và ô hiện ra. */
    override fun onStartListening() {
        super.onStartListening()
        TileStore.setAdded(slot, true)
        refreshTile()
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val cfg = TileStore.get(slot)
        val target = cfg?.let { TargetApp.resolve(this, it) }
        // Tên ô = tên bạn đặt trong FloatTile.
        tile.label = cfg?.name ?: getString(R.string.tile_unconfigured, slot)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Dòng phụ dưới tên ô: tên ứng dụng sẽ được mở.
            tile.subtitle = when {
                cfg == null -> getString(R.string.tile_subtitle_setup)
                target == null -> getString(R.string.tile_subtitle_missing)
                else -> target.label
            }
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
            .putExtra(LaunchActivity.EXTRA_SLOT, slot)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+: bắt buộc dùng bản startActivityAndCollapse(PendingIntent).
            // PendingIntent = "lời nhắn mở màn hình" đóng gói sẵn, hệ thống sẽ thay mặt ta gửi đi.
            // Mỗi ô dùng một mã riêng (requestCode = số ô) để các lời nhắn không đè lên nhau.
            // FLAG_IMMUTABLE: không ai sửa được nội dung lời nhắn này.
            val pending = PendingIntent.getActivity(
                this, slot, intent,
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

// Mỗi lớp dưới đây là một "chỗ" ô riêng trên bảng cài đặt nhanh.
// Chỗ số 1 giữ tên lớp cũ (FloatTileService) để ô bạn đã thêm từ bản trước vẫn chạy.
class FloatTileService : SlotTileService(1)
class FloatTileService2 : SlotTileService(2)
class FloatTileService3 : SlotTileService(3)
class FloatTileService4 : SlotTileService(4)
class FloatTileService5 : SlotTileService(5)
class FloatTileService6 : SlotTileService(6)
class FloatTileService7 : SlotTileService(7)
class FloatTileService8 : SlotTileService(8)
class FloatTileService9 : SlotTileService(9)
class FloatTileService10 : SlotTileService(10)
