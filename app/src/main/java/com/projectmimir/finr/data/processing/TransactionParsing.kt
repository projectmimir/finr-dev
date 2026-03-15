package com.projectmimir.finr

import java.math.BigDecimal

fun hasCurrency(text: String): Boolean {
    val pattern1 = Regex("""(₹|rs\.?|inr|usd|\$|eur|gbp)\s*\d""", RegexOption.IGNORE_CASE)
    val pattern2 = Regex("""\d+([,.]\d+)?\s*(rs|inr|usd|eur|gbp)""", RegexOption.IGNORE_CASE)

    return pattern1.containsMatchIn(text) || pattern2.containsMatchIn(text)
}

private fun isLikelyMoneyToken(raw: String): Boolean {
    val cleaned = raw.replace(",", "").trim()
    if (cleaned.isBlank()) return false
    if (cleaned.all { it.isDigit() } && cleaned.length > 6) return false
    val value = cleaned.toBigDecimalOrNull() ?: return false
    return value > BigDecimal.ZERO
}

fun extractAmount(text: String, bankCheckResult: String? = checkAllBanks(text)): String? {
    val stanchartHit = bankCheckResult?.startsWith("STANCHART", ignoreCase = true) == true
    if (stanchartHit) {
        val stanchartAmountPattern = Regex(
            """\bXX\d+\s+on\s+\d{2}/\d{2}/\d{2,4}\s+for\s+INR\s*([0-9][0-9,]*(?:\.\d{1,2})?)\b""",
            RegexOption.IGNORE_CASE
        )
        val stanchartMatch = stanchartAmountPattern.find(text)
        if (stanchartMatch != null) {
            return "₹${stanchartMatch.groupValues[1]}"
        }
    }

    val prefixed = Regex("""(?:₹|rs\.?|inr)\s*([0-9][0-9,]*(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
    val prefixedMatch = prefixed.find(text)
    if (prefixedMatch != null && isLikelyMoneyToken(prefixedMatch.groupValues[1])) {
        return "₹${prefixedMatch.groupValues[1]}"
    }

    val suffixed = Regex("""\b([0-9][0-9,]*(?:\.\d{1,2})?)\s*(?:rs\.?|inr)\b""", RegexOption.IGNORE_CASE)
    val suffixedMatch = suffixed.find(text)
    if (suffixedMatch != null && isLikelyMoneyToken(suffixedMatch.groupValues[1])) {
        return "₹${suffixedMatch.groupValues[1]}"
    }

    val contextual = Regex(
        """\b(?:amount|amt|debited|credited|spent|paid|payment|received|withdrawn|for)\b[^0-9₹]{0,20}(?:₹|rs\.?|inr)?\s*([0-9][0-9,]*(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )
    val contextualMatch = contextual.find(text)
    if (contextualMatch != null && isLikelyMoneyToken(contextualMatch.groupValues[1])) {
        return "₹${contextualMatch.groupValues[1]}"
    }

    return null
}

fun classifyTransaction(msg: SmsMessage): TransactionType? {
    val text = msg.body.lowercase()
    val creditHits = listOf("credited", "received", "deposited", "refund").any { text.contains(it) }
    val debitHits = listOf(
        "debited",
        "on hdfc",
        "deducted",
        "spent",
        "sent",
        "withdrawn",
        "paid",
        "payment",
        "purchase",
        "using stanchart",
        "card no xx"
    ).any { text.contains(it) }
    val bankUsageDebitHit = checkAllBanks(msg.body) != null &&
        Regex("""\bfor\s+inr\s*[0-9]""", RegexOption.IGNORE_CASE).containsMatchIn(msg.body)

    return when {
        creditHits && !debitHits -> TransactionType.CREDIT
        (debitHits || bankUsageDebitHit) && !creditHits -> TransactionType.DEBIT
        else -> null
    }
}
