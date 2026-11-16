package com.amir.filamenttracker.domain.amazon

import java.net.URI

private val TRACKING_ID = Regex("""Tracking ID:\s*([A-Za-z0-9]+)""")
private val ORDER_NUMBER = Regex("""\d{3}-\d{7}-\d{7}""")

/** Tracking ids found on a "track package" page, in order, without duplicates. */
fun parseTrackingIds(text: String): List<String> =
    TRACKING_ID.findAll(text).map { it.groupValues[1] }.distinct().toList()

/** True only for a bare Amazon order number (three digit groups), no surrounding whitespace. */
fun isAmazonOrderNumber(value: String): Boolean = ORDER_NUMBER.matches(value)

/**
 * Parses scheme, host and path only. Everything from the first '?' or '#' is dropped first, because
 * Chromium leaves characters such as | { ^ unescaped in queries and java.net.URI rejects them.
 */
private fun parseUrl(url: String): URI? =
    try { URI(url.substringBefore('#').substringBefore('?')) } catch (_: Exception) { null }

/** True for https URLs on amazon.com or its subdomains. Anything else must not be navigated to. */
fun isAmazonUrl(url: String): Boolean {
    val uri = parseUrl(url) ?: return false
    if (uri.scheme != "https") return false
    val host = uri.host?.lowercase() ?: return false
    return host == "amazon.com" || host.endsWith(".amazon.com")
}

/** True when the URL path belongs to Amazon's sign-in flow (/ap/ or /ax/). */
fun isSignInUrl(url: String): Boolean {
    val path = parseUrl(url)?.path ?: return false
    return path.startsWith("/ap/") || path.startsWith("/ax/")
}

/** True for the signed-in order list page, decided on the parsed path (not on the query, which a sign-in redirect carries). */
fun isOrderListUrl(url: String): Boolean {
    if (!isAmazonUrl(url) || isSignInUrl(url)) return false
    val path = parseUrl(url)?.path ?: return false
    return path.startsWith("/gp/css/order-history") || path.startsWith("/your-orders/orders")
}

/** True for the details page of an order: an Amazon URL whose path is an order-details path. */
fun isOrderDetailsUrl(url: String): Boolean {
    if (!isAmazonUrl(url)) return false
    return parseUrl(url)?.path?.contains("/order-details") == true
}

/**
 * The tracking links that may be followed: only when the finished page really is the details page
 * of [orderNumber] (an unknown id can redirect elsewhere), and only Amazon https links.
 */
fun acceptedTrackLinks(finishedUrl: String, pageText: String, orderNumber: String, links: List<String>): List<String> {
    if (!isOrderDetailsUrl(finishedUrl) || orderNumber !in pageText) return emptyList()
    return links.filter(::isAmazonUrl)
}
