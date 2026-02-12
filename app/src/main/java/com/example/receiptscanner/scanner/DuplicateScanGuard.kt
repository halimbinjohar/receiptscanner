package com.example.receiptscanner.scanner

import com.example.receiptscanner.model.ParsedReceipt
import java.security.MessageDigest
import java.time.Instant

/**
 * Prevents duplicate auto-saves in a short window by hashing key fields + timestamp bucket.
 */
class DuplicateScanGuard(
    private val bucketSeconds: Long = 30,
    private val maxRecentFingerprints: Int = 100
) {
    private val recentFingerprints = LinkedHashSet<String>()

    @Synchronized
    fun isDuplicate(receipt: ParsedReceipt, now: Instant = Instant.now()): Boolean {
        val fingerprint = fingerprint(receipt, now)
        if (recentFingerprints.contains(fingerprint)) {
            return true
        }

        recentFingerprints += fingerprint
        while (recentFingerprints.size > maxRecentFingerprints) {
            val oldest = recentFingerprints.firstOrNull() ?: break
            recentFingerprints.remove(oldest)
        }
        return false
    }

    internal fun fingerprint(receipt: ParsedReceipt, now: Instant): String {
        val bucket = now.epochSecond / bucketSeconds
        val payload = listOf(
            receipt.merchantName.orEmpty().trim().lowercase(),
            receipt.total?.toPlainString().orEmpty(),
            receipt.purchaseDate.orEmpty().trim(),
            bucket.toString()
        ).joinToString("|")

        return sha256(payload)
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString(separator = "") { "%02x".format(it) }
    }
}
