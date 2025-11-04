package com.lifetracker.app.export.dashboard

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File

class DashboardExportRepository(private val context: Context) {

    fun save(
        baseFileName: String,
        format: DashboardExportFormat,
        bytes: ByteArray
    ): ExportedFile {
        val documentsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        val exportDir = File(documentsDir, EXPORT_DIRECTORY).apply { mkdirs() }
        val file = File(exportDir, "$baseFileName.${format.fileExtension}")
        file.outputStream().use { stream ->
            stream.write(bytes)
            stream.flush()
        }

        val authority = "${context.packageName}.export.fileprovider"
        val uri: Uri = FileProvider.getUriForFile(context, authority, file)
        return ExportedFile(uri = uri, mimeType = format.mimeType, fileName = file.name)
    }

    data class ExportedFile(
        val uri: Uri,
        val mimeType: String,
        val fileName: String
    )

    private companion object {
        private const val EXPORT_DIRECTORY = "dashboard_exports"
    }
}
