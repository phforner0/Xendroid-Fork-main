package xendroid.compose.core

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

fun diagnosticsShareIntent(context: Context, file: File): Intent {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
    return Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("XenDroid diagnostics", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }, "Share XenDroid diagnostics")
}
