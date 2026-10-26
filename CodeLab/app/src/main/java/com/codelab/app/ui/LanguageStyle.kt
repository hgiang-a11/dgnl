package com.codelab.app.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.codelab.app.model.Language

/** Chữ viết tắt hiển thị trên huy hiệu của từng ngôn ngữ. */
val Language.badgeText: String
    get() = when (this) {
        Language.PYTHON -> "Py"
        Language.JAVASCRIPT -> "JS"
        Language.CPP -> "C++"
        Language.C -> "C"
        Language.JAVA -> "Java"
    }

/** Màu nhận diện của từng ngôn ngữ (theo màu quen thuộc trên GitHub). */
val Language.brandColor: Int
    get() = Color.parseColor(
        when (this) {
            Language.PYTHON -> "#3572A5"
            Language.JAVASCRIPT -> "#F1E05A"
            Language.CPP -> "#F34B7D"
            Language.C -> "#A8B9CC"
            Language.JAVA -> "#B07219"
        }
    )

/** Biến một TextView (có nền bg_badge) thành huy hiệu màu của ngôn ngữ. */
fun TextView.styleAsBadge(language: Language) {
    text = language.badgeText
    val color = language.brandColor
    backgroundTintList = ColorStateList.valueOf(color)
    // Nền sáng (JS, C) dùng chữ tối; nền tối dùng chữ trắng cho dễ đọc
    setTextColor(if (ColorUtils.calculateLuminance(color) > 0.5) Color.parseColor("#1B2029") else Color.WHITE)
}

/** Biến một TextView (có nền bg_pill) thành nhãn "Offline" hoặc "Cần mạng". */
fun TextView.styleAsModePill(language: Language) {
    val color = Color.parseColor(if (language.runsOffline) "#3FB950" else "#D29922")
    text = if (language.runsOffline) "Offline" else "Cần mạng"
    setTextColor(color)
    backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 38))
}
