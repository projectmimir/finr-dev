package com.projectmimir.finr

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

data class SmsMessage(
    val address: String,
    val body: String,
    val dateMillis: Long
)

enum class TransactionType {
    CREDIT,
    DEBIT
}

sealed class UiItem {
    data class Transaction(val data: TransactionEntity, val date: LocalDate, val amountValue: BigDecimal) : UiItem()
    data class DayHeader(val date: LocalDate) : UiItem()
    data class DailySummary(val date: LocalDate, val debitTotal: BigDecimal, val creditTotal: BigDecimal) : UiItem()
    data class MonthlySummary(val month: YearMonth, val debitTotal: BigDecimal, val creditTotal: BigDecimal) : UiItem()
}

data class CategorySeed(val name: String, val subcategory: String, val type: String, val emoji: String)

val categorySeedList = listOf(
    CategorySeed("Housing", "Rent/Mortgage/Maintenance", "Essential", "🏠"),
    CategorySeed("Utilities", "Electric/Water/Gas", "Essential", "💡"),
    CategorySeed("Groceries", "Food/Supplies", "Essential", "🛒"),
    CategorySeed("Transportation", "Fuel/Transit/Maintenance/Cab/Auto", "Essential", "🚗"),
    CategorySeed("Communication", "Phone/Internet", "Essential", "📶"),
    CategorySeed("Insurance", "Health/Auto/Home", "Essential", "🛡️"),
    CategorySeed("Healthcare", "Medical/Dental/Pharmacy", "Essential", "🏥"),
    CategorySeed("Entertainment", "Movies/Concerts/Hobbies", "Discretionary", "🎬"),
    CategorySeed("Dining Out", "Restaurants/Coffee", "Discretionary", "🍛"),
    CategorySeed("Subscriptions", "Streaming/Gym/Software", "Discretionary", "📺"),
    CategorySeed("Personal Care", "Hair/Grooming/Toiletries", "Discretionary", "🧴"),
    CategorySeed("Shopping", "Clothing/Electronics", "Discretionary", "🛍️"),
    CategorySeed("Travel", "Vacation/Hotels", "Discretionary", "✈️"),
    CategorySeed("Debt Repayment", "Credit Cards/Loans", "Financial", "💳"),
    CategorySeed("Savings & Investments", "Emergency/Retirement", "Financial", "📈"),
    CategorySeed("Gifts & Donations", "Holidays/Charity", "Financial", "🎁"),
    CategorySeed("Pets", "Food/Vet/Insurance", "Specialized", "🐾"),
    CategorySeed("Childcare/Education", "Daycare/Tuition", "Specialized", "🎓"),
    CategorySeed("Professional Services", "Legal/Financial", "Specialized", "💼"),
    CategorySeed("Miscellaneous", "Uncategorised/Settlement", "Other", "📦")
)
