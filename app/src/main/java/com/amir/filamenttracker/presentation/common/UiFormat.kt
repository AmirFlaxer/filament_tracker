package com.amir.filamenttracker.presentation.common

import com.amir.filamenttracker.data.db.SpoolRow
import com.amir.filamenttracker.domain.AlertKind
import com.amir.filamenttracker.domain.Forecast
import com.amir.filamenttracker.domain.model.SpoolStatus
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun SpoolStatus.label(): String = when (this) {
    SpoolStatus.ORDERED -> "הוזמן"
    SpoolStatus.SEALED -> "סגור"
    SpoolStatus.IN_USE -> "בשימוש"
    SpoolStatus.OPEN_STORED -> "פתוח באחסון"
    SpoolStatus.FINISHED -> "הסתיים"
}

fun SpoolRow.statusText(): String =
    if (amsSlot != null) "${status.label()} · חריץ $amsSlot" else status.label()

fun countLabel(status: SpoolStatus, n: Int): String = when (status) {
    SpoolStatus.IN_USE -> "$n בשימוש"
    SpoolStatus.OPEN_STORED -> if (n == 1) "1 פתוח" else "$n פתוחים"
    SpoolStatus.SEALED -> if (n == 1) "1 סגור" else "$n סגורים"
    SpoolStatus.ORDERED -> "$n בדרך"
    SpoolStatus.FINISHED -> if (n == 1) "1 הסתיים" else "$n הסתיימו"
}

private val dateFormat = DateTimeFormatter.ofPattern("d/M/yyyy")
private val dateTimeFormat = DateTimeFormatter.ofPattern("d/M/yyyy HH:mm")

fun Instant.formatDate(): String = atZone(ZoneId.systemDefault()).format(dateFormat)
fun Instant.formatDateTime(): String = atZone(ZoneId.systemDefault()).format(dateTimeFormat)
fun LocalDate.formatDate(): String = format(dateFormat)

fun Duration.formatInterval(): String {
    val days = toDays()
    return when {
        days <= 1 -> "בערך סליל ביום"
        days < 14 -> "סליל כל ~$days ימים"
        else -> "סליל כל ~${days / 7} שבועות"
    }
}

fun AlertKind.title(): String = when (this) {
    AlertKind.MISSING -> "חסר"
    AlertKind.RUNNING_LOW -> "הולך להיות חסר"
    AlertKind.ON_THE_WAY -> "בדרך"
}

/** Null when there is nothing worth showing. */
fun Forecast.label(): String? = when (this) {
    Forecast.NotEnoughHistory -> null
    Forecast.OutOfStock -> "אזל מהמלאי"
    Forecast.Soon -> "צפי סיום: בקרוב"
    is Forecast.At -> "צפי סיום: ${date.formatDate()}"
}
