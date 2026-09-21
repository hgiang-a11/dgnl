package com.dgnl.taskflow.ui.theme

import androidx.compose.ui.graphics.Color

// Nen den chuyen nghiep
val Ink = Color(0xFF07080B)          // nen chinh
val Surface1 = Color(0xFF0E1015)     // the / cot
val Surface2 = Color(0xFF141821)     // the noi len
val Surface3 = Color(0xFF1B2029)     // trang thai nhan
val Line = Color(0xFF232935)         // duong vien
val LineSoft = Color(0xFF191E27)

val TextHi = Color(0xFFF1F4F9)
val TextMid = Color(0xFFA4AEBF)
val TextLow = Color(0xFF6C7788)

val Danger = Color(0xFFFF5470)
val Warn = Color(0xFFF5A524)
val Good = Color(0xFF2ECC8F)

// Mau cua tung trang thai
val StatusTodoColor = Color(0xFF7C8AA3)
val StatusDoingColor = Color(0xFFF5A524)
val StatusDoneColor = Color(0xFF2ECC8F)

// Mau nhan cho tung danh sach
val AccentColors = listOf(
    Color(0xFF4C8DFF), // xanh duong
    Color(0xFF8B5CF6), // tim
    Color(0xFF2ECC8F), // xanh la
    Color(0xFFF5A524), // cam
    Color(0xFFFF5470), // hong
    Color(0xFF22C1DC), // xanh ngoc
    Color(0xFFE2E8F0)  // bac
)

val AccentNames = listOf("Xanh dương", "Tím", "Xanh lá", "Cam", "Hồng", "Ngọc", "Bạc")

fun accentAt(index: Int): Color = AccentColors[((index % AccentColors.size) + AccentColors.size) % AccentColors.size]
