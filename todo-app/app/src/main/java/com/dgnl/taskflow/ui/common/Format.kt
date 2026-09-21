package com.dgnl.taskflow.ui.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val VN: Locale = Locale("vi", "VN")
private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", VN)
private val shortDayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM", VN)
private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy", VN)
private val longDayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", VN)

fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

fun epochDayToMillisUtc(epochDay: Long): Long = epochDay * 86_400_000L

fun millisUtcToEpochDay(millis: Long): Long = Math.floorDiv(millis, 86_400_000L)

fun formatDay(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(dayFormatter)

fun formatDayShort(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(shortDayFormatter)

fun formatDateTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime().format(dateTimeFormatter)

/** Mo ta ngan gon ve han: "Hôm nay", "Còn 3 ngày", "Trễ 2 ngày"... */
fun describeDue(dueEpochDay: Long, today: Long = todayEpochDay()): String {
    val diff = dueEpochDay - today
    return when {
        diff == 0L -> "Hôm nay"
        diff == 1L -> "Ngày mai"
        diff == -1L -> "Trễ 1 ngày"
        diff < -1L -> "Trễ ${-diff} ngày"
        diff in 2..6 -> "Còn $diff ngày"
        else -> formatDayShort(dueEpochDay)
    }
}

/** Mo ta ngay bat dau. */
fun describeStart(startEpochDay: Long, today: Long = todayEpochDay()): String {
    val diff = startEpochDay - today
    return when {
        diff == 0L -> "Bắt đầu hôm nay"
        diff == 1L -> "Bắt đầu ngày mai"
        diff > 1L -> "Bắt đầu sau $diff ngày"
        diff == -1L -> "Bắt đầu hôm qua"
        else -> "Bắt đầu ${formatDayShort(startEpochDay)}"
    }
}

/** Vi du: "Thứ hai, 21/09/2026". */
fun formatToday(): String =
    LocalDate.now().format(longDayFormatter).replaceFirstChar { it.uppercase() }

fun pluralTask(count: Int): String = "$count việc"
