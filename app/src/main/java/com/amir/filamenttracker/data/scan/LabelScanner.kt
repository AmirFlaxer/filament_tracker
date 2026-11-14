package com.amir.filamenttracker.data.scan

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

data class ScanResult(val text: String, val barcodes: List<String>)

/** Reads the text and the barcodes of a label photo, on the device. */
@Singleton
class LabelScanner @Inject constructor(@ApplicationContext private val context: Context) {

    // The ML Kit clients are intentionally never closed: this singleton lives as long as the app.
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val barcodeScanner by lazy { BarcodeScanning.getClient() }

    suspend fun scan(uri: Uri): ScanResult {
        val image = withContext(Dispatchers.IO) { decode(uri) }
        val text = recognizer.process(image).await().text
        val barcodes = barcodeScanner.process(image).await()
            .mapNotNull { it.rawValue?.takeIf { value -> value.isNotEmpty() } }
            .distinct()
        return ScanResult(text, barcodes)
    }

    /** Decodes the photo with its long side at most [MAX_SIDE] pixels, rotated upright, so a huge photo cannot exhaust memory. */
    private fun decode(uri: Uri): InputImage {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longSide = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longSide / sample > MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("לא ניתן לקרוא את התמונה")
        val rotation = resolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
        return InputImage.fromBitmap(bitmap, rotation)
    }

    private companion object {
        const val MAX_SIDE = 2000
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
