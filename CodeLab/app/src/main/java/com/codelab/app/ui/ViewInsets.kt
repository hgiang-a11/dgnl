package com.codelab.app.ui

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.doOnAttach

/**
 * Yêu cầu hệ thống gửi lại thông tin vùng bị che (thanh trạng thái, bàn phím...).
 * Màn hình mới chỉ được gắn vào cửa sổ sau onViewCreated, nên phải đợi gắn xong mới yêu cầu.
 */
fun View.requestInsetsWhenAttached() {
    if (isAttachedToWindow) {
        ViewCompat.requestApplyInsets(this)
    } else {
        doOnAttach { ViewCompat.requestApplyInsets(it) }
    }
}
