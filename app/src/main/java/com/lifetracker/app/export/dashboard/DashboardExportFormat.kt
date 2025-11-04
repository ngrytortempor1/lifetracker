package com.lifetracker.app.export.dashboard

enum class DashboardExportFormat(
    val fileExtension: String,
    val mimeType: String
) {
    CSV("csv", "text/csv"),
    PDF("pdf", "application/pdf")
}
