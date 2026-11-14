package com.amir.filamenttracker.presentation.scan

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

/** The one fixed file the camera writes to; the same Uri is rebuilt after the activity is recreated. */
fun labelFile(context: Context): File = File(context.cacheDir, "scans").apply { mkdirs() }.resolve("label.jpg")

fun labelUri(context: Context): Uri {
    val file = labelFile(context)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/** Returns a function that opens the camera and reports the photo's Uri, or null when the user cancels. */
@Composable
fun rememberLabelCapture(onResult: (Uri?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        currentOnResult(if (saved) labelUri(context) else null)
    }
    return { launcher.launch(labelUri(context)) }
}
