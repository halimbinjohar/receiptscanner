package com.example.receiptscanner.scanner

import androidx.lifecycle.ViewModel
import com.example.receiptscanner.model.ParsedReceipt
import com.example.receiptscanner.parser.ReceiptParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ReceiptScannerViewModel(
    private val receiptParser: ReceiptParser = ReceiptParser(),
    private val duplicateScanGuard: DuplicateScanGuard = DuplicateScanGuard(),
    private val onAutoSave: (ParsedReceipt) -> Unit = {}
) : ViewModel() {

    data class UiState(
        val isAnalyzing: Boolean = true,
        val latestRawText: String = "",
        val confirmationReceipt: ParsedReceipt? = null,
        val duplicateBlocked: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun onOcrText(text: String) {
        if (!_uiState.value.isAnalyzing || text.isBlank()) return

        val parsed = receiptParser.parse(text)
        val duplicate = if (parsed.isComplete) duplicateScanGuard.isDuplicate(parsed) else false

        val confirmation = if (parsed.isComplete && !duplicate) parsed else null

        _uiState.value = _uiState.value.copy(
            latestRawText = text,
            confirmationReceipt = confirmation,
            duplicateBlocked = duplicate
        )

        if (confirmation != null) {
            onAutoSave(confirmation)
            stopAnalyzing()
        }
    }

    fun stopAnalyzing() {
        _uiState.value = _uiState.value.copy(isAnalyzing = false)
    }

    fun resetDuplicateFlag() {
        if (_uiState.value.duplicateBlocked) {
            _uiState.value = _uiState.value.copy(duplicateBlocked = false)
        }
    }
}
