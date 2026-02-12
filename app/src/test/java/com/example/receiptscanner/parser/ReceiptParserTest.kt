package com.example.receiptscanner.parser

import kotlin.test.Test
import kotlin.test.assertEquals

class ReceiptParserTest {

    private val parser = ReceiptParser()

    @Test
    fun `parse prefers total over subtotal`() {
        val parsed = parser.parse(
            """
            CORNER STORE
            SUBTOTAL 14.95
            TOTAL 16.20
            """.trimIndent()
        )

        assertEquals("16.20", parsed.total?.toPlainString())
    }

    @Test
    fun `parse supports amount due label`() {
        val parsed = parser.parse(
            """
            BAKERY
            Amount Due: $12.50
            """.trimIndent()
        )

        assertEquals("12.50", parsed.total?.toPlainString())
    }
}
