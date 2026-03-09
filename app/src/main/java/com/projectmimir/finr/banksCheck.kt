package com.projectmimir.finr

private const val HDFC_LOGO_TOKEN_PREFIX = "[HDFC_LOGO:"
private const val HDFC_LOGO_TOKEN_SUFFIX = "]"
private val BANK_RESULT_REGEX = Regex("""^(.+?)(?:\s+CARD)?\s+(\d{4,5})$""", RegexOption.IGNORE_CASE)

fun hdfcCheck(messageBody: String): String? {
    val patterns = listOf(
        // "HDFC Bank A/C *" followed by 4 numbers
        Regex("""\bHDFC\s+Bank\s+A/C\s+\*(\d{4})\b""", RegexOption.IGNORE_CASE),
        // "HDFC Bank A/c XX" followed by 4 numbers
        Regex("""\bHDFC\s+Bank\s+A/c\s+XX\s*(\d{4})\b""", RegexOption.IGNORE_CASE)
    )

    val cardPattern = Regex("""\bOn\s+HDFC\s+Bank\s+Card\s+(\d{4})\b""", RegexOption.IGNORE_CASE)

    for (pattern in patterns) {
        val match = pattern.find(messageBody) ?: continue
        val last4 = match.groupValues.getOrNull(1)?.trim().orEmpty()
        if (last4.length == 4) return "HDFC $last4"
    }
    val cardMatch = cardPattern.find(messageBody)
    if (cardMatch != null) {
        val last4 = cardMatch.groupValues.getOrNull(1)?.trim().orEmpty()
        if (last4.length == 4) return "HDFC CARD $last4"
    }

    return null
}

fun amexCheck(messageBody: String): String? {
    // Example expected format: "AMEX card ** 12345" (case-insensitive, flexible spaces).
    val pattern = Regex("""\bAMEX\s+card\s+\*{2}\s*(\d{4,5})\b""", RegexOption.IGNORE_CASE)
    val match = pattern.find(messageBody) ?: return null
    val cardDigits = match.groupValues.getOrNull(1)?.trim().orEmpty()
    return if (cardDigits.length in 4..5) "AMEX $cardDigits" else null
}

fun iciciCheck(messageBody: String): String? {
    // Expected format: "ICICI Bank Card XX4009" (case-insensitive).
    val pattern = Regex("""\bICICI\s+Bank\s+Card\s+XX(\d{4})\b""", RegexOption.IGNORE_CASE)
    val match = pattern.find(messageBody) ?: return null
    val last4 = match.groupValues.getOrNull(1)?.trim().orEmpty()
    return if (last4.length == 4) "ICICI $last4" else null
}

fun standardCharteredCheck(messageBody: String): String? {
    // Expected format: "StanChart Card No XX3815" (case-insensitive).
    val pattern = Regex("""\bStanChart\s+Card\s+No\s+XX(\d{4})\b""", RegexOption.IGNORE_CASE)
    val match = pattern.find(messageBody) ?: return null
    val last4 = match.groupValues.getOrNull(1)?.trim().orEmpty()
    return if (last4.length == 4) "STANCHART $last4" else null
}

fun axisCheck(messageBody: String): String? {
    // Expected format: "Axis Bank Card no. XX5924" (case-insensitive).
    val pattern = Regex("""\bAxis\s+Bank\s+Card\s+no\.\s+XX(\d{4})\b""", RegexOption.IGNORE_CASE)
    val match = pattern.find(messageBody) ?: return null
    val last4 = match.groupValues.getOrNull(1)?.trim().orEmpty()
    return if (last4.length == 4) "AXIS $last4" else null
}

fun applyHdfcBranding(messageBody: String): String {
    val detected = hdfcCheck(messageBody) ?: return messageBody
    val last4 = detected.takeLast(4)
    val token = "$HDFC_LOGO_TOKEN_PREFIX$last4$HDFC_LOGO_TOKEN_SUFFIX"
    // Replace "CARD" (+ optional trailing 4-digit card number) with inline logo token.
    val cardWordWithDigits = Regex("""(?i)\bCARD\b(?:\s+\d{4})?""")
    return cardWordWithDigits.replace(messageBody, token)
}

fun parseHdfcLogoToken(messageBody: String): Pair<String, String?> {
    val tokenRegex = Regex("""\[HDFC_LOGO:(\d{4})\]""")
    val match = tokenRegex.find(messageBody) ?: return messageBody to null
    val last4 = match.groupValues[1]
    val textWithoutToken = messageBody.replace(tokenRegex, "HDFC $last4")
    return textWithoutToken to last4
}

fun checkAllBanks(messageBody: String): String? {
    return hdfcCheck(messageBody)
        ?: amexCheck(messageBody)
        ?: iciciCheck(messageBody)
        ?: standardCharteredCheck(messageBody)
        ?: axisCheck(messageBody)
}

fun bankDetailsFromCheckResult(bankCheckResult: String?): Pair<String, String> {
    val hit = bankCheckResult?.trim().orEmpty()
    if (hit.isBlank()) return "" to ""
    val match = BANK_RESULT_REGEX.find(hit) ?: return "" to ""
    val bank = match.groupValues[1].trim()
    val last4 = match.groupValues[2].trim()
    return bank to last4
}

fun bankDetailsFromMessage(messageBody: String): Pair<String, String> {
    return bankDetailsFromCheckResult(checkAllBanks(messageBody))
}
