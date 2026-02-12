package com.example.receiptscanner.parser

import com.example.receiptscanner.model.ParsedReceipt
import java.math.BigDecimal

/**
 * Lightweight parser used by the scanner pipeline.
 */
class ReceiptParser {
    private val totalRegex = Regex(
        """^\s*(?:total|amount due|balance due)\b\s*[:]?\s*\$?([0-9]+(?:\.[0-9]{2})?)\b""",
        RegexOption.IGNORE_CASE
    )
    private val dateRegex = Regex("""\b(\d{1,2}[/-]\d{1,2}[/-]\d{2,4})\b""")

    fun parse(rawText: String): ParsedReceipt {
        val lines = rawText
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val merchant = lines.firstOrNull { line ->
            line.length > 2 && line.any { it.isLetter() } && !line.contains("receipt", ignoreCase = true)
        }

        val total = lines
            .firstNotNullOfOrNull { line ->
                totalRegex.find(line)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toBigDecimalOrNull()
            }

        val date = dateRegex.find(rawText)
            ?.groupValues
            ?.getOrNull(1)

        return ParsedReceipt(
            merchantName = merchant,
            total = total,
            purchaseDate = date,
            rawText = rawText
        )
    }

    private fun String.toBigDecimalOrNull(): BigDecimal? = runCatching { BigDecimal(this) }.getOrNull()
}
