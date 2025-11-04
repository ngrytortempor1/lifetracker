package com.lifetracker.app.export.dashboard

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifetracker.app.viewmodel.DashboardUiState
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DashboardExportViewModel(
    private val snapshotMapper: DashboardSnapshotMapper,
    private val csvFormatter: DashboardCsvFormatter,
    private val pdfFormatter: DashboardPdfFormatter,
    private val exportRepository: DashboardExportRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardExportUiState())
    val uiState: StateFlow<DashboardExportUiState> = _uiState.asStateFlow()

    private val _shareEvents = MutableSharedFlow<DashboardExportEvent>(extraBufferCapacity = 1)
    val shareEvents: SharedFlow<DashboardExportEvent> = _shareEvents.asSharedFlow()

    fun export(state: DashboardUiState.Ready, format: DashboardExportFormat) {
        if (_uiState.value.isExporting) return
        _uiState.value = _uiState.value.copy(isExporting = true, errorMessage = null)

        viewModelScope.launch {
            try {
                val snapshot = snapshotMapper.map(state)
                val payload = when (format) {
                    DashboardExportFormat.CSV -> csvFormatter.format(snapshot)
                    DashboardExportFormat.PDF -> pdfFormatter.format(snapshot)
                }
                val timestampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                    .withLocale(snapshot.locale)
                val timestamp = timestampFormatter.format(snapshot.generatedAt.atZone(snapshot.timezone))
                val baseName = "dashboard_${format.name.lowercase(Locale.ROOT)}_$timestamp"

                val exported = withContext(ioDispatcher) {
                    exportRepository.save(baseName, format, payload)
                }

                _shareEvents.emit(
                    DashboardExportEvent.Share(
                        uri = exported.uri,
                        mimeType = exported.mimeType,
                        fileName = exported.fileName
                    )
                )
                _uiState.value = _uiState.value.copy(isExporting = false)
            } catch (throwable: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    errorMessage = throwable.message ?: "エクスポートに失敗しました"
                )
            }
        }
    }

    fun clearError() {
        if (_uiState.value.errorMessage != null) {
            _uiState.value = _uiState.value.copy(errorMessage = null)
        }
    }
}

data class DashboardExportUiState(
    val isExporting: Boolean = false,
    val errorMessage: String? = null
)

sealed class DashboardExportEvent {
    data class Share(val uri: Uri, val mimeType: String, val fileName: String) : DashboardExportEvent()
}
