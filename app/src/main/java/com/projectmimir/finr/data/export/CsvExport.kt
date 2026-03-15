package com.projectmimir.finr

import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

fun monthLabel(month: Int): String {
    return Month.of(month).getDisplayName(TextStyle.FULL, Locale.getDefault())
}

fun csvEscape(raw: String): String {
    val needsQuotes = raw.contains(",") || raw.contains("\"") || raw.contains("\n") || raw.contains("\r")
    val escaped = raw.replace("\"", "\"\"")
    return if (needsQuotes) "\"$escaped\"" else escaped
}

fun transactionsToCsv(
    transactions: List<TransactionEntity>,
    categories: List<CategoryEntity>
): String {
    val headers = listOf(
        "Transaction Date",
        "Transaction Time",
        "Debit Amount (INR)",
        "Credit Amount (INR)",
        "Category",
        "Subcategory",
        "Transaction Channel",
        "Source Message"
    )

    val rows = transactions
        .sortedByDescending { it.dateMillis }
        .map { txn ->
            val cls = categoryForTxnClass(categories, txn.txnClass)
            val isCredit = txn.txn.equals(AppText.CREDIT, ignoreCase = true)
            val debitAmount = if (isCredit) "" else txn.amount
            val creditAmount = if (isCredit) txn.amount else ""
            listOf(
                formatSmsDate(txn.dateMillis),
                txn.time,
                debitAmount,
                creditAmount,
                cls.name,
                cls.subcategory,
                txn.txnChannel,
                txn.message
            ).joinToString(",") { csvEscape(it) }
        }

    return buildString {
        append(headers.joinToString(",") { csvEscape(it) })
        append('\n')
        rows.forEachIndexed { index, row ->
            append(row)
            if (index < rows.lastIndex) append('\n')
        }
    }
}
