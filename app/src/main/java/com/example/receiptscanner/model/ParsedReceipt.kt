package com.example.receiptscanner.model

import java.math.BigDecimal
import java.time.Instant

data class ParsedReceipt(
    val merchantName: String?,
    val total: BigDecimal?,
    val purchaseDate: String?,
    val rawText: String,
    val scannedAt: Instant = Instant.now()
) {
    val isComplete: Boolean
        get() = !merchantName.isNullOrBlank() && total != null && !purchaseDate.isNullOrBlank()
}
