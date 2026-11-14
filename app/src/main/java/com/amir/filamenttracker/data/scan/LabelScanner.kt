package com.amir.filamenttracker.data.scan

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

data class ScanResult(val text: String, val barcodes: List<String>)

/** Reads the text and the barcodes of a label photo, on the device. */
@Singleton
class LabelScanner @Inject constructor(@ApplicationContext private val context: Context) {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val barcodeScanner by lazy { BarcodeScanning.getClient() }

    suspend fun scan(uri: Uri): ScanResult {
        val image = InputImage.fromFilePath(context, uri)
        val text = recognizer.process(image).await().text
        val barcodes = barcodeScanner.process(image).await()
            .mapNotNull { it.rawValue?.takeIf { value -> value.isNotEmpty() } }
            .distinct()
        return ScanResult(text, barcodes)
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
