package com.lifetracker.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifetracker.app.settings.NaturalLanguageSettingsManager
import com.lifetracker.app.util.NaturalLanguageConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.LinkedHashMap
import java.util.LinkedHashSet

data class RelativeDayEntry(
    val id: Long,
    val label: String,
    val offset: String
)

data class SettingsUiState(
    val eventPrefixInput: String = "",
    val reminderPrefixInput: String = "",
    val dueHourInput: String = "",
    val dueMinuteInput: String = "",
    val reminderLeadMinutesInput: String = "",
    val relativeDayEntries: List<RelativeDayEntry> = emptyList(),
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val generalSectionExpanded: Boolean = true,
    val dictionarySectionExpanded: Boolean = true,
    val analyticsGuideSectionExpanded: Boolean = true,
    val advancedSectionExpanded: Boolean = false
)

class SettingsViewModel(
    private val settingsManager: NaturalLanguageSettingsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private var entryIdCounter = 0L

    init {
        applyConfigToState(settingsManager.getCurrentConfig(), markDirty = false)
    }

    fun onEventPrefixChange(value: String) {
        _uiState.update {
            it.copy(
                eventPrefixInput = value,
                isDirty = true,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun onReminderPrefixChange(value: String) {
        _uiState.update {
            it.copy(
                reminderPrefixInput = value,
                isDirty = true,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun onDueHourChange(value: String) {
        _uiState.update {
            it.copy(
                dueHourInput = value,
                isDirty = true,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun onDueMinuteChange(value: String) {
        _uiState.update {
            it.copy(
                dueMinuteInput = value,
                isDirty = true,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun onReminderLeadMinutesChange(value: String) {
        _uiState.update {
            it.copy(
                reminderLeadMinutesInput = value,
                isDirty = true,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun onToggleGeneralSection() {
        _uiState.update { it.copy(generalSectionExpanded = !it.generalSectionExpanded) }
    }

    fun onToggleDictionarySection() {
        _uiState.update { it.copy(dictionarySectionExpanded = !it.dictionarySectionExpanded) }
    }

    fun onToggleAnalyticsGuideSection() {
        _uiState.update { it.copy(analyticsGuideSectionExpanded = !it.analyticsGuideSectionExpanded) }
    }

    fun onToggleAdvancedSection() {
        _uiState.update { it.copy(advancedSectionExpanded = !it.advancedSectionExpanded) }
    }

    fun onAddRelativeDayEntry() {
        val newEntry = RelativeDayEntry(
            id = ++entryIdCounter,
            label = "",
            offset = "0"
        )
        _uiState.update {
            it.copy(
                relativeDayEntries = it.relativeDayEntries + newEntry,
                isDirty = true,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun onRemoveRelativeDayEntry(id: Long) {
        _uiState.update {
            it.copy(
                relativeDayEntries = it.relativeDayEntries.filterNot { entry -> entry.id == id },
                isDirty = true,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun onRelativeDayLabelChange(id: Long, value: String) {
        _uiState.update {
            it.copy(
                relativeDayEntries = it.relativeDayEntries.map { entry ->
                    if (entry.id == id) entry.copy(label = value) else entry
                },
                isDirty = true,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun onRelativeDayOffsetChange(id: Long, value: String) {
        _uiState.update {
            it.copy(
                relativeDayEntries = it.relativeDayEntries.map { entry ->
                    if (entry.id == id) entry.copy(offset = value) else entry
                },
                isDirty = true,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun resetChanges() {
        applyConfigToState(settingsManager.getCurrentConfig(), markDirty = false)
    }

    fun saveChanges() {
        val state = _uiState.value

        val eventChars = parsePrefixInput(state.eventPrefixInput)
        if (eventChars.isEmpty()) {
            setError("イベント用プレフィックスは1文字以上で入力してください")
            return
        }

        val reminderChars = parsePrefixInput(state.reminderPrefixInput)
        if (reminderChars.isEmpty()) {
            setError("リマインド用プレフィックスは1文字以上で入力してください")
            return
        }

        val dueHour = state.dueHourInput.toIntOrNull()
        if (dueHour == null || dueHour !in 0..23) {
            setError("期限の時刻（時間）は0〜23の範囲で入力してください")
            return
        }

        val dueMinute = state.dueMinuteInput.toIntOrNull()
        if (dueMinute == null || dueMinute !in 0..59) {
            setError("期限の時刻（分）は0〜59の範囲で入力してください")
            return
        }

        val leadMinutes = state.reminderLeadMinutesInput.toLongOrNull()
        if (leadMinutes == null || leadMinutes < 0) {
            setError("リマインダーのリードタイムは0以上の整数で入力してください")
            return
        }

        val relativeDayMap = LinkedHashMap<String, Long>()
        state.relativeDayEntries.forEach { entry ->
            val label = entry.label.trim()
            if (label.isEmpty()) {
                setError("辞書のキーワードは空にできません")
                return
            }
            val offset = entry.offset.trim().toLongOrNull()
            if (offset == null) {
                setError("辞書の「${label}」は整数でオフセットを入力してください")
                return
            }
            relativeDayMap[label] = offset
        }
        if (relativeDayMap.isEmpty()) {
            setError("辞書に最低1件はエントリを設定してください")
            return
        }

        val newConfig = NaturalLanguageConfig.ParserConfig(
            eventPrefixes = eventChars,
            reminderPrefixes = reminderChars,
            segmentTerminators = NaturalLanguageConfig.parserConfig.segmentTerminators,
            defaultDueHour = dueHour,
            defaultDueMinute = dueMinute,
            defaultReminderLeadMinutes = leadMinutes,
            relativeDayMappings = relativeDayMap
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null, successMessage = null) }
            settingsManager.updateConfig(newConfig)
            applyConfigToState(settingsManager.getCurrentConfig(), markDirty = false)
            _uiState.update { it.copy(isSaving = false, successMessage = "保存しました") }
        }
    }

    private fun applyConfigToState(
        config: NaturalLanguageConfig.ParserConfig,
        markDirty: Boolean
    ) {
        entryIdCounter = 0
        _uiState.update {
            it.copy(
                eventPrefixInput = config.eventPrefixes.joinToString(""),
                reminderPrefixInput = config.reminderPrefixes.joinToString(""),
                dueHourInput = config.defaultDueHour.toString(),
                dueMinuteInput = config.defaultDueMinute.toString(),
                reminderLeadMinutesInput = config.defaultReminderLeadMinutes.toString(),
                relativeDayEntries = config.relativeDayMappings.entries.map { (label, offset) ->
                    RelativeDayEntry(
                        id = ++entryIdCounter,
                        label = label,
                        offset = offset.toString()
                    )
                },
                isDirty = markDirty,
                isSaving = false,
                errorMessage = null,
                successMessage = null,
                advancedSectionExpanded = false
            )
        }
    }

    private fun setError(message: String) {
        _uiState.update {
            it.copy(
                errorMessage = message,
                successMessage = null
            )
        }
    }

    private fun parsePrefixInput(input: String): List<Char> {
        val set = LinkedHashSet<Char>()
        input.forEach { ch ->
            if (!ch.isWhitespace()) {
                set.add(ch)
            }
        }
        return set.toList()
    }
}
