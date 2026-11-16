package com.amir.filamenttracker.domain.amazon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmazonTrackingTest {

    @Test
    fun `fixture yields its single tracking id`() {
        val text = javaClass.getResource("/amazon/track-package.txt")!!.readText()
        assertEquals(listOf("RU0000000000Z"), parseTrackingIds(text))
    }

    @Test
    fun `several tracking ids keep order`() {
        assertEquals(listOf("A1", "B2"), parseTrackingIds("Tracking ID: A1\n...\nTracking ID: B2"))
    }

    @Test
    fun `duplicates are dropped`() {
        assertEquals(listOf("A1"), parseTrackingIds("Tracking ID: A1\nTracking ID: A1"))
    }

    @Test
    fun `text without tracking id is empty`() {
        assertEquals(emptyList<String>(), parseTrackingIds("Shipped with someone"))
    }

    @Test
    fun `order number format`() {
        assertTrue(isAmazonOrderNumber("111-1234567-7654321"))
        assertFalse(isAmazonOrderNumber("RU0000000000Z"))
        assertFalse(isAmazonOrderNumber("2300000000"))
        assertFalse(isAmazonOrderNumber(" 111-1234567-7654321"))
    }

    @Test
    fun `amazon urls`() {
        assertTrue(isAmazonUrl("https://www.amazon.com/ap/signin?x=1"))
        assertTrue(isAmazonUrl("https://amazon.com/"))
        assertFalse(isAmazonUrl("https://evil.com/?u=amazon.com"))
        assertFalse(isAmazonUrl("https://amazon.com.evil.io/"))
        assertFalse(isAmazonUrl("http://www.amazon.com/"))
        assertFalse(isAmazonUrl("https://www.amazon.co.uk/"))
        assertFalse(isAmazonUrl("javascript:alert(1)"))
    }

    @Test
    fun `sign in urls`() {
        assertTrue(isSignInUrl("https://www.amazon.com/ap/signin?x=1"))
        assertTrue(isSignInUrl("https://www.amazon.com/ax/claim"))
        assertFalse(isSignInUrl("https://www.amazon.com/gp/css/order-history"))
    }

    @Test
    fun `order list urls`() {
        assertTrue(isOrderListUrl("https://www.amazon.com/gp/css/order-history"))
        assertTrue(isOrderListUrl("https://www.amazon.com/your-orders/orders?ref=x"))
        assertFalse(isOrderListUrl("https://www.amazon.com/ap/signin?openid.return_to=https%3A%2F%2Fwww.amazon.com%2Fgp%2Fcss%2Forder-history"))
        assertFalse(isOrderListUrl("https://evil.com/gp/css/order-history"))
    }

    @Test
    fun `amazon url with unescaped query characters is accepted`() {
        assertTrue(isAmazonUrl("https://www.amazon.com/ap/signin?openid.return_to=a|b&x={1}&y=^z#frag"))
        assertTrue(isSignInUrl("https://www.amazon.com/ap/signin?x=a|b"))
        assertTrue(isOrderListUrl("https://www.amazon.com/gp/css/order-history?ref=a|b{c}"))
    }

    @Test
    fun `lookalike hosts stay rejected`() {
        assertFalse(isAmazonUrl("https://amazon.com@evil.com/"))
        assertFalse(isAmazonUrl("https://evilamazon.com/"))
        assertFalse(isAmazonUrl("https://amazon.com.evil.com/?a|b"))
        assertFalse(isAmazonUrl("https://evil.com\\@amazon.com/"))
        assertFalse(isAmazonUrl("https://amazon.com./"))
    }

    private val orderNo = "111-0000001-0000001"
    private val detailsUrl = "https://www.amazon.com/your-orders/order-details?orderID=$orderNo"
    private val link = "https://www.amazon.com/progress-tracker/package?orderId=$orderNo"

    @Test
    fun `track links are kept on the details page of that order`() {
        assertEquals(listOf(link), acceptedTrackLinks(detailsUrl, "Order # $orderNo\nTrack package", orderNo, listOf(link)))
    }

    @Test
    fun `track links are ignored when the page lacks the order number`() {
        assertEquals(emptyList<String>(), acceptedTrackLinks(detailsUrl, "Your Orders\nOrder # 111-0000009-0000009", orderNo, listOf(link)))
    }

    @Test
    fun `track links are ignored when the page is not an order details page`() {
        val other = "https://www.amazon.com/gp/css/order-history"
        assertEquals(emptyList<String>(), acceptedTrackLinks(other, "Order # $orderNo", orderNo, listOf(link)))
        assertEquals(emptyList<String>(), acceptedTrackLinks("https://evil.com/your-orders/order-details", "Order # $orderNo", orderNo, listOf(link)))
    }

    @Test
    fun `non amazon track links are dropped`() {
        val links = listOf("javascript:alert(1)", "https://evil.com/t", link, "http://www.amazon.com/t")
        assertEquals(listOf(link), acceptedTrackLinks(detailsUrl, "Order # $orderNo", orderNo, links))
    }
}
