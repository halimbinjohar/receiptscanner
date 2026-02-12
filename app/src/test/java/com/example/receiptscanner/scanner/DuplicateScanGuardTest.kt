package com.example.receiptscanner.scanner

import com.example.receiptscanner.model.ParsedReceipt
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DuplicateScanGuardTest {

    @Test
    fun `same receipt in same bucket is duplicate`() {
        val guard = DuplicateScanGuard(bucketSeconds = 30)
        val receipt = sampleReceipt()
        val now = Instant.parse("2024-01-01T10:15:18Z")

        assertFalse(guard.isDuplicate(receipt, now))
        assertTrue(guard.isDuplicate(receipt, now.plusSeconds(5)))
    }

    @Test
    fun `same receipt in different bucket is accepted`() {
        val guard = DuplicateScanGuard(bucketSeconds = 10)
        val receipt = sampleReceipt()
        val now = Instant.parse("2024-01-01T10:15:18Z")

        assertFalse(guard.isDuplicate(receipt, now))
        assertFalse(guard.isDuplicate(receipt, now.plusSeconds(12)))
    }

    private fun sampleReceipt() = ParsedReceipt(
        merchantName = "Coffee Lab",
        total = BigDecimal("8.75"),
        purchaseDate = "01/01/2024",
        rawText = "raw"
    )
}
