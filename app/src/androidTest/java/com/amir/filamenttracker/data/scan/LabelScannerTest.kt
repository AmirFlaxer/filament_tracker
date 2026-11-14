package com.amir.filamenttracker.data.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.amir.filamenttracker.domain.readLabel
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelScannerTest {

    @Test
    fun xznPhotoYieldsLabelText() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = File(context.cacheDir, "xzn_label_test.jpg")
        instrumentation.context.assets.open("xzn_label.jpg").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }

        try {
            val result = LabelScanner(context).scan(Uri.fromFile(file))
            println("OCR_TEXT_BEGIN\n${result.text}\nOCR_TEXT_END barcodes=${result.barcodes}")

            assertTrue(result.text, result.text.contains("XZN", ignoreCase = true))
            assertTrue(result.text, result.text.contains("Silk Gold", ignoreCase = true))
            val reading = readLabel(result.text, emptyList(), listOf("PLA", "PETG"), emptyList())
            assertEquals("PLA", reading.family)
            assertEquals("Silk", reading.finish)
            assertEquals("XZN", reading.brand)
        } finally {
            file.delete()
        }
    }

    @Test
    fun bigPhotoIsDownscaledAndStillRead() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = File(context.cacheDir, "xzn_label_big_test.jpg")
        try {
            val small = instrumentation.context.assets.open("xzn_label.jpg").use { BitmapFactory.decodeStream(it) }
            val big = Bitmap.createScaledBitmap(small, 4000, 3000, true)
            file.outputStream().use { big.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            val result = LabelScanner(context).scan(Uri.fromFile(file))
            assertTrue(result.text, result.text.contains("XZN", ignoreCase = true))
            assertTrue(result.text, result.text.contains("Silk Gold", ignoreCase = true))
        } finally {
            file.delete()
        }
    }
}
