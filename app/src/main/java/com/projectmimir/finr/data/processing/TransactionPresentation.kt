package com.projectmimir.finr

import java.math.BigDecimal
import java.time.YearMonth

fun buildUiItems(transactions: List<TransactionEntity>): List<UiItem> {
    val itemsFromDb = transactions.mapNotNull { txn ->
        val amountValue = parseAmountValue(txn.amount) ?: return@mapNotNull null
        UiItem.Transaction(
            data = txn,
            date = smsLocalDate(txn.dateMillis),
            amountValue = amountValue
        )
    }.sortedByDescending { it.data.dateMillis }

    if (itemsFromDb.isEmpty()) return emptyList()

    val items = ArrayList<UiItem>(itemsFromDb.size * 3)

    val monthGroups = itemsFromDb.groupBy { YearMonth.from(it.date) }
    for ((month, monthItems) in monthGroups) {
        var monthDebit = BigDecimal.ZERO
        var monthCredit = BigDecimal.ZERO
        monthItems.forEach { item ->
            if (item.data.txn.equals(AppText.DEBIT, ignoreCase = true)) {
                monthDebit = monthDebit.add(item.amountValue)
            } else {
                monthCredit = monthCredit.add(item.amountValue)
            }
        }
        items.add(UiItem.MonthlySummary(month, monthDebit, monthCredit))

        val dayGroups = monthItems.groupBy { it.date }
        for ((day, dayItems) in dayGroups) {
            var dayDebit = BigDecimal.ZERO
            var dayCredit = BigDecimal.ZERO
            dayItems.forEach { item ->
                if (item.data.txn.equals(AppText.DEBIT, ignoreCase = true)) {
                    dayDebit = dayDebit.add(item.amountValue)
                } else {
                    dayCredit = dayCredit.add(item.amountValue)
                }
            }

            items.add(UiItem.DayHeader(day))
            items.add(UiItem.DailySummary(day, dayDebit, dayCredit))
            dayItems.forEach { items.add(it) }
        }
    }

    return items
}

fun categoryNamesFromDb(categories: List<CategoryEntity>): List<String> {
    return categories.map { it.name }.distinct().ifEmpty { listOf("Unclassified") }
}

fun subcategoriesFor(categories: List<CategoryEntity>, category: String): List<String> {
    return categories.filter { it.name == category }
        .map { it.subcategory }
        .filter { it.isNotBlank() }
}

fun transactionToJson(txn: TransactionEntity): String {
    return """
        {
          "id": "${txn.id}",
          "message": "${txn.message}",
          "amount": "${txn.amount}",
          "txn": "${txn.txn}",
          "txn_channel": "${txn.txnChannel}",
          "bank": "${txn.bank}",
          "bank_logo": "${txn.bankLogo}",
          "bank_card_number": "${txn.bankCardNumber}",
          "txn_class": "${txn.txnClass}",
          "date": "${formatSmsDate(txn.dateMillis)}",
          "time": "${txn.time}",
          "address": "${txn.address}"
        }
    """.trimIndent()
}

fun txnClassId(category: String, subcategory: String): String = "$category|$subcategory"

fun categoryForTxnClass(categories: List<CategoryEntity>, txnClass: String): CategoryEntity {
    return categories.firstOrNull { it.id == txnClass }
        ?: categories.lastOrNull { it.name.equals(AppText.MISC, ignoreCase = true) }
        ?: categories.lastOrNull { it.name.equals("Miscellaneous", ignoreCase = true) }
        ?: categories.lastOrNull()
        ?: CategoryEntity(
            id = txnClassId(AppText.MISC, AppText.UNCAT),
            name = AppText.MISC,
            subcategory = AppText.UNCAT,
            type = "Other",
            emoji = ""
        )
}
