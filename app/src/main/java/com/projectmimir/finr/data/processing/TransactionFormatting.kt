package com.projectmimir.finr

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatSmsDate(dateMillis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    val date = Instant.ofEpochMilli(dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    return date.format(formatter)
}

fun formatSmsTime(dateMillis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    val time = Instant.ofEpochMilli(dateMillis).atZone(ZoneId.systemDefault()).toLocalTime()
    return time.format(formatter)
}

fun smsLocalDate(dateMillis: Long): LocalDate {
    return Instant.ofEpochMilli(dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
}

fun parseAmountValue(amount: String): BigDecimal? {
    val digits = amount.replace("₹", "").replace(",", "").trim()
    return digits.toBigDecimalOrNull()
}

fun formatCurrency(value: BigDecimal): String {
    val formatter = NumberFormat.getNumberInstance(Locale.getDefault())
    formatter.maximumFractionDigits = 2
    formatter.minimumFractionDigits = 2
    return "₹" + formatter.format(value.setScale(2, RoundingMode.HALF_UP))
}

fun sanitizeAmountInput(input: String): String {
    val cleaned = input.filter { it.isDigit() || it == '.' }
    val dotIndex = cleaned.indexOf('.')
    if (dotIndex < 0) return cleaned
    val intPart = cleaned.substring(0, dotIndex)
    val decimalRaw = cleaned.substring(dotIndex + 1).replace(".", "")
    val decimalPart = decimalRaw.take(2)
    return if (decimalPart.isEmpty()) "$intPart." else "$intPart.$decimalPart"
}

fun parseDateTimeMillisOrNull(dateText: String, timeText: String): Long? {
    return try {
        val date = LocalDate.parse(dateText.trim(), DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        val time = LocalTime.parse(timeText.trim(), DateTimeFormatter.ofPattern("HH:mm"))
        LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    } catch (_: Throwable) {
        null
    }
}

fun withDate(currentMillis: Long, newDateMillis: Long): Long {
    val zone = ZoneId.systemDefault()
    val current = Instant.ofEpochMilli(currentMillis).atZone(zone)
    val newDate = Instant.ofEpochMilli(newDateMillis).atZone(zone).toLocalDate()
    return current.withYear(newDate.year)
        .withMonth(newDate.monthValue)
        .withDayOfMonth(newDate.dayOfMonth)
        .toInstant()
        .toEpochMilli()
}

fun withTime(currentMillis: Long, hour: Int, minute: Int): Long {
    val zone = ZoneId.systemDefault()
    val current = Instant.ofEpochMilli(currentMillis).atZone(zone)
    return current.withHour(hour)
        .withMinute(minute)
        .withSecond(0)
        .withNano(0)
        .toInstant()
        .toEpochMilli()
}
