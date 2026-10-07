package com.example.todoapplication.ui.tiles

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.todoapplication.R
import com.example.todoapplication.data.notifications.FocusSession
import com.example.todoapplication.ui.navigation.AppIntents

/*
 * [TẦNG UI · QUICK SETTINGS] Hai ô trong bảng cài đặt nhanh (kéo thanh thông báo xuống):
 *  - "Tập trung": đang chạy → bấm để dừng; chưa chạy → mở màn Tập trung và tự bắt đầu phiên 25 phút.
 *  - "Thêm việc": mở thanh tạo nhanh.
 * Ô mở Activity bằng startActivityAndCollapse (Android 14+ bắt buộc dùng PendingIntent), nên phiên tập trung luôn
 * được bắt đầu khi app đang ở tiền cảnh → được phép mở Foreground Service.
 */

/** Mở app với "đường dẫn" [open] rồi thu gọn bảng cài đặt nhanh. */
@SuppressLint("StartActivityAndCollapseDeprecated")
private fun TileService.openApp(open: String, requestCode: Int) {
    val intent = AppIntents.main(this, open).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (Build.VERSION.SDK_INT >= 34) {
        startActivityAndCollapse(
            PendingIntent.getActivity(this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        )
    } else {
        @Suppress("DEPRECATION")
        startActivityAndCollapse(intent)
    }
}

class FocusTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        FocusSession.init(this)
        val running = FocusSession.state.value?.let { it.endAt > System.currentTimeMillis() } == true
        qsTile?.apply {
            state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "Tập trung"
            if (Build.VERSION.SDK_INT >= 29) subtitle = if (running) "Đang chạy · bấm để dừng" else "25 phút"
            icon = Icon.createWithResource(this@FocusTileService, R.drawable.ic_shortcut_focus)
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        FocusSession.init(this)
        val running = FocusSession.state.value?.let { it.endAt > System.currentTimeMillis() } == true
        if (running) {
            FocusSession.stop(this)
            onStartListening()
        } else {
            openApp(AppIntents.OPEN_FOCUS_START, REQUEST_FOCUS)
        }
    }

    private companion object {
        const val REQUEST_FOCUS = 9101
    }
}

class QuickAddTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "Thêm việc"
            icon = Icon.createWithResource(this@QuickAddTileService, R.drawable.ic_shortcut_add)
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        openApp(AppIntents.OPEN_ADD, REQUEST_ADD)
    }

    private companion object {
        const val REQUEST_ADD = 9102
    }
}
