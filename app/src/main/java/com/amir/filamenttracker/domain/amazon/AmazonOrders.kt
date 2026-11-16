package com.amir.filamenttracker.domain.amazon

import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.TextStyle
import java.util.Locale

/** One product line of an order: how many units and the product title. */
data class AmazonItem(val quantity: Int, val title: String)

/** One order read from the Amazon order-history page text. */
data class AmazonOrder(
    val orderNumber: String,
    val orderedOn: LocalDate,
    val status: String,
    val items: List<AmazonItem>,
)

/** True when the order status line starts with "Delivered". */
val AmazonOrder.isDelivered: Boolean get() = status.startsWith("Delivered")

private val CLOSED_PREFIXES = listOf("Delivered", "Cancelled", "Canceled", "Return", "Refund")

/** True for an order that is over: delivered, cancelled, returned or refunded. Never worth importing. */
val AmazonOrder.isClosed: Boolean get() = CLOSED_PREFIXES.any { status.startsWith(it) }

private const val ANCHOR = "ORDER PLACED"
private const val PAGE_END = "Back to top"
private const val DETAILS_PREFIX = "View order details"
private const val ALEXA_LINE = "Ask Alexa about this order"
private const val ORDER_NUMBER_PREFIX = "ORDER #"
private const val RETURN_PREFIX = "Return or replace items:"

private val ACTION_LINES = setOf(
    "Buy it again",
    "Track package",
    "View or edit order",
    "Ask Product Question",
    "Write a product review",
    "Get product support",
    "Return or replace items",
    "Share gift receipt",
    "More options",
)

private val dateFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)

/**
 * Parses the text of the order-history page. Returns null when there is no
 * "ORDER PLACED" anchor at all (a changed layout or an interstitial page),
 * so the caller can tell that apart from a page with zero orders.
 */
fun parseAmazonOrders(text: String): List<AmazonOrder>? {
    val lines = text.lines().map { it.trim() }
    val anchors = lines.indices.filter { lines[it] == ANCHOR }
    if (anchors.isEmpty()) return null
    return anchors.mapIndexedNotNull { i, start ->
        val end = anchors.getOrNull(i + 1) ?: lines.size
        parseBlock(lines.subList(start + 1, end))
    }
}

private fun parseBlock(rawBlock: List<String>): AmazonOrder? {
    val pageEnd = rawBlock.indexOf(PAGE_END)
    val block = if (pageEnd >= 0) rawBlock.subList(0, pageEnd) else rawBlock
    val date = block.firstOrNull()?.let {
        try { LocalDate.parse(it, dateFormat) } catch (e: DateTimeParseException) { null }
    } ?: return null
    val number = block.firstOrNull { it.startsWith(ORDER_NUMBER_PREFIX) }
        ?.removePrefix(ORDER_NUMBER_PREFIX)?.trim()
        ?.takeIf { it.isNotEmpty() } ?: return null
    val detailsAt = block.indexOfFirst { it.startsWith(DETAILS_PREFIX) }
    if (detailsAt < 0) return null
    val rest = block.drop(detailsAt + 1)
    val statusAt = rest.indexOfFirst { it.isNotEmpty() && it != ALEXA_LINE }
    if (statusAt < 0) return null

    val items = mutableListOf<AmazonItem>()
    var pendingQuantity: Int? = null
    for (line in rest.drop(statusAt + 1)) {
        when {
            line.isEmpty() || line in ACTION_LINES || line.startsWith(RETURN_PREFIX) -> Unit
            line.all { it.isDigit() } -> pendingQuantity = line.toIntOrNull()
            else -> {
                items += AmazonItem(pendingQuantity ?: 1, line)
                pendingQuantity = null
            }
        }
    }
    return AmazonOrder(number, date, rest[statusAt], items)
}

private val MONTHS: Map<String, Int> = buildMap {
    for (m in Month.values()) {
        put(m.getDisplayName(TextStyle.FULL, Locale.US).lowercase(Locale.US), m.value)
        put(m.getDisplayName(TextStyle.SHORT, Locale.US).lowercase(Locale.US), m.value)
    }
}

private val DATE_AT_END = Regex("""([A-Za-z]+)\.?\s+(\d{1,2})\s*$""")

/** The expected arrival window; a single date has [start] equal to [end]. */
data class DateRange(val start: LocalDate, val end: LocalDate)

private val RANGE_SPLIT = Regex("""\s+[-\u2013]\s+""")
private val DATE_AT_START = Regex("""^([A-Za-z]+)\.?\s+(\d{1,2})\b""")

private fun yearFor(month: Int, orderedOn: LocalDate) = if (month < orderedOn.monthValue) orderedOn.year + 1 else orderedOn.year

/**
 * The expected arrival window from a status line such as "Arriving November 24 - December 1",
 * "Arriving today", "Arriving tomorrow" or "Arriving Saturday" (the next such day strictly after
 * [today]). Null when the status carries no arrival. A month earlier than the order month means
 * next year; a window whose end falls before its start moves the end into the following year.
 */
fun expectedRange(status: String, orderedOn: LocalDate, today: LocalDate): DateRange? {
    val s = status.trim()
    if (!s.startsWith("Arriving")) return null
    val body = s.removePrefix("Arriving").trim()
    if (body.equals("today", ignoreCase = true)) return DateRange(today, today)
    if (body.equals("tomorrow", ignoreCase = true)) return today.plusDays(1).let { DateRange(it, it) }
    val weekday = DayOfWeek.values().firstOrNull {
        it.getDisplayName(TextStyle.FULL, Locale.US).equals(body, ignoreCase = true)
    }
    if (weekday != null) {
        val delta = (weekday.value - today.dayOfWeek.value + 7) % 7
        return today.plusDays(if (delta == 0) 7L else delta.toLong()).let { DateRange(it, it) }
    }
    val parts = body.split(RANGE_SPLIT)
    val endMatch = DATE_AT_END.find(parts.last()) ?: return null
    val endMonth = MONTHS[endMatch.groupValues[1].lowercase(Locale.US)] ?: return null
    val endDay = endMatch.groupValues[2].toInt()
    try {
        var end = LocalDate.of(yearFor(endMonth, orderedOn), endMonth, endDay)
        if (parts.size < 2) return DateRange(end, end)
        val startText = parts.first().trim()
        val startMatch = DATE_AT_START.find(startText)
        val start = when {
            startMatch != null -> {
                val m = MONTHS[startMatch.groupValues[1].lowercase(Locale.US)] ?: return null
                LocalDate.of(yearFor(m, orderedOn), m, startMatch.groupValues[2].toInt())
            }
            startText.all { it.isDigit() } -> LocalDate.of(end.year, endMonth, startText.toInt())
            else -> return null
        }
        if (end.isBefore(start)) end = end.plusYears(1)
        return DateRange(start, end)
    } catch (e: DateTimeException) {
        return null
    }
}

/** The later end of [expectedRange], or null when the status carries no arrival. */
fun expectedDate(status: String, orderedOn: LocalDate, today: LocalDate): LocalDate? =
    expectedRange(status, orderedOn, today)?.end
