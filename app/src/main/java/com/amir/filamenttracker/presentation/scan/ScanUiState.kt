package com.amir.filamenttracker.presentation.scan

sealed interface ScanUiState {
    data object Idle : ScanUiState
    data object Reading : ScanUiState
    data class Result(val match: ScanMatch, val barcode: String?, val productTitle: String? = null) : ScanUiState
}
