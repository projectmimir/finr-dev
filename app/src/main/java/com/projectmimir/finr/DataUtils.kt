package com.projectmimir.finr

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.provider.Telephony
import java.io.BufferedReader
import java.io.InputStreamReader
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val TRANSACTION_KEYWORD_HITS = listOf(
    "txn", "transaction", "debited", "debit", "credited", "credit", "spent",
    "withdrawn", "withdrawal", "paid", "payment", "purchase", "received",
    "sent", "upi", "card", "a/c", "acct", "account", "balance", "hdfc"
)

val NON_TRANSACTION_BLOCKLIST = listOf(
    "reward points credited",
    "e-mandate",
    "cdsl",
    "shares",
    "otp",
    "razorpay",
    "payment link",
    "amount due",
    "order#",
    "disconnection",
    "ignore if paid",
    "pay now",
    "retry",
    "pnr",
    "pay immediately"
)

private fun parseSenderCsvLine(line: String): List<String> {
    val out = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        if (c == '"') {
            if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                current.append('"')
                i++
            } else {
                inQuotes = !inQuotes
            }
        } else if (c == ',' && !inQuotes) {
            out.add(current.toString().trim())
            current.clear()
        } else {
            current.append(c)
        }
        i++
    }
    out.add(current.toString().trim())
    return out
}

private fun senderEntitiesFromCsv(context: Context): List<SenderEntity> {
    val stream = runCatching { context.assets.open("senders.csv") }.getOrNull() ?: return emptyList()
    val rows = mutableListOf<SenderEntity>()
    BufferedReader(InputStreamReader(stream)).use { reader ->
        var isFirstLine = true
        reader.forEachLine { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEachLine
            if (isFirstLine) {
                isFirstLine = false
                return@forEachLine
            }
            val cols = parseSenderCsvLine(line)
            if (cols.isEmpty()) return@forEachLine
            val senderId = cols.getOrNull(0).orEmpty().trim().uppercase(Locale.getDefault())
            if (senderId.isBlank()) return@forEachLine
            val senderName = cols.getOrNull(1).orEmpty().trim()
            val senderLogo = cols.getOrNull(2).orEmpty().trim()
            rows.add(SenderEntity(senderId = senderId, senderName = senderName, senderLogo = senderLogo))
        }
    }
    return rows
}

suspend fun seedSenders(context: Context, db: AppDatabase) {
    withContext(Dispatchers.IO) {
        val rows = senderEntitiesFromCsv(context)
        if (rows.isNotEmpty()) {
            db.senderDao().upsertAll(rows)
        }
    }
}

fun extractSenderId(address: String): String? {
    val tokens = address.split("-")
        .map { it.trim() }
        .filter { it.isNotBlank() }
    if (tokens.size < 2) return null
    val penultimate = tokens[tokens.size - 2]
    return penultimate.ifBlank { null }
}

private fun senderDirectoryKey(senderId: String): String = senderId.uppercase(Locale.getDefault())

private fun senderLookup(address: String, senderDirectory: Map<String, SenderEntity>): SenderEntity? {
    val senderId = extractSenderId(address) ?: return null
    return senderDirectory[senderDirectoryKey(senderId)]
}

fun inferLogoFromBankName(bank: String): String {
    val normalized = bank.trim().uppercase(Locale.getDefault())
    return when {
        normalized.startsWith("HDFC") -> "hdfc_logo"
        normalized.startsWith("AMEX") -> "amex_logo"
        normalized.startsWith("ICICI") -> "icici_logo"
        normalized.startsWith("STANCHART") -> "stanchart_logo"
        normalized.startsWith("AXIS") -> "axis_logo"
        else -> ""
    }
}

fun readSmsSince(context: Context, sinceDateMillisExclusive: Long?): List<SmsMessage> {
    // Pull only newer inbox messages when a watermark is available.
    val uri = Telephony.Sms.Inbox.CONTENT_URI
    val projection = arrayOf(
        Telephony.Sms.ADDRESS,
        Telephony.Sms.BODY,
        Telephony.Sms.DATE
    )

    val selection = if (sinceDateMillisExclusive != null) "${Telephony.Sms.DATE} > ?" else null
    val selectionArgs = if (sinceDateMillisExclusive != null) arrayOf(sinceDateMillisExclusive.toString()) else null

    val cursor = context.contentResolver.query(
        uri,
        projection,
        selection,
        selectionArgs,
        "${Telephony.Sms.DATE} DESC"
    )

    if (cursor == null) return emptyList()

    val addressIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
    val bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY)
    val dateIndex = cursor.getColumnIndex(Telephony.Sms.DATE)

    val items = ArrayList<SmsMessage>(cursor.count.coerceAtLeast(0))
    cursor.use {
        while (it.moveToNext()) {
            val address = if (addressIndex >= 0) it.getString(addressIndex) else ""
            val body = if (bodyIndex >= 0) it.getString(bodyIndex) else ""
            val date = if (dateIndex >= 0) it.getLong(dateIndex) else 0L
            items.add(SmsMessage(address = address, body = body, dateMillis = date))
        }
    }

    return items
}

fun categoryEntitiesFromOptions(): List<CategoryEntity> {
    // Seed supports slash-delimited subcategories; expand into one row per option.
    return categorySeedList.flatMap { seed ->
        seed.subcategory.split("/").map { raw ->
            val sub = raw.trim()
            CategoryEntity(
                id = "${seed.name}|$sub",
                name = seed.name,
                subcategory = sub,
                type = seed.type,
                emoji = seed.emoji
            )
        }
    }
}

suspend fun seedCategories(db: AppDatabase) {
    withContext(Dispatchers.IO) {
        db.categoryDao().upsertAll(categoryEntitiesFromOptions())
    }
}

fun transactionId(message: SmsMessage): String {
    // Deterministic ID prevents duplicates across repeated sync runs.
    val input = "${message.address}|${message.dateMillis}|${message.body}"
    return sha256Hex(input)
}

private fun sha256Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}

private fun normalizeAmountForDedupe(amount: String): String {
    return parseAmountValue(amount)
        ?.setScale(2, RoundingMode.HALF_UP)
        ?.toPlainString()
        ?: amount.trim()
}

private fun dedupeBucketKey(dateMillis: Long, amount: String, txn: String): String {
    val day = smsLocalDate(dateMillis)
    val normalizedTxn = txn.trim().lowercase(Locale.getDefault())
    val normalizedAmount = normalizeAmountForDedupe(amount)
    return "$day|$normalizedAmount|$normalizedTxn"
}

suspend fun syncSmsToDb(context: Context, db: AppDatabase): Int {
    return withContext(Dispatchers.IO) {
        val latest = db.transactionDao().getLatestDateMillis()
        // Incremental sync: read only messages after latest stored timestamp.
        val sms = readSmsSince(context, latest)
        upsertIncomingSms(db, sms)
    }
}

suspend fun upsertIncomingSms(db: AppDatabase, sms: List<SmsMessage>): Int {
    return withContext(Dispatchers.IO) {
        if (sms.isEmpty()) return@withContext 0
        val existing = db.transactionDao().getAllOnce().associateBy { it.id }
        val categories = db.categoryDao().getAllOnce()
        val validTxnClassIds = categories.map { it.id }.toSet()
        if (validTxnClassIds.isEmpty()) return@withContext 0
        val fallbackTxnClassId = categories.lastOrNull {
            it.name.equals(AppText.MISC, ignoreCase = true) &&
                it.subcategory.equals(AppText.UNCAT, ignoreCase = true)
        }?.id ?: categories.last().id
        val senderDirectory = db.senderDao()
            .getAllOnce()
            .associateBy { senderDirectoryKey(it.senderId) }
        val entities = smsToTransactionEntities(
            sms = sms,
            existing = existing,
            senderDirectory = senderDirectory,
            validTxnClassIds = validTxnClassIds,
            fallbackTxnClassId = fallbackTxnClassId
        )
        if (entities.isNotEmpty()) {
            db.transactionDao().upsertAll(entities)
        }
        entities.size
    }
}

private fun smsToTransactionEntities(
    sms: List<SmsMessage>,
    existing: Map<String, TransactionEntity>,
    senderDirectory: Map<String, SenderEntity>,
    validTxnClassIds: Set<String>,
    fallbackTxnClassId: String
): List<TransactionEntity> {
    val miscClassId = "Miscellaneous|Uncategorised"
    val existingByBucket = mutableMapOf<String, MutableSet<String>>()
    existing.values.forEach { txn ->
        val key = dedupeBucketKey(
            dateMillis = txn.dateMillis,
            amount = txn.amount,
            txn = txn.txn
        )
        existingByBucket.getOrPut(key) { mutableSetOf() }.add(txn.message)
    }
    val batchByBucket = mutableMapOf<String, MutableSet<String>>()
    val entities = mutableListOf<TransactionEntity>()

    sms.forEach { msg ->
        // Sender allowlist is the first gate for transaction SMS identification.
        if (!isTxnCheck(msg, senderDirectory)) return@forEach
        if (isExplicitlyNonTransaction(msg.body)) return@forEach
        val senderRecord = senderLookup(msg.address, senderDirectory)
        if (senderRecord == null) return@forEach
        // Bank format checks are evaluated only for sender IDs explicitly present in sender list.
        val bankCheckResult = checkAllBanks(msg.body)
        val type = classifyTransaction(msg) ?: return@forEach
        val amount = extractAmount(msg.body, bankCheckResult) ?: return@forEach
        val txnValue = if (type == TransactionType.CREDIT) AppText.CREDIT else AppText.DEBIT
        val bucketKey = dedupeBucketKey(
            dateMillis = msg.dateMillis,
            amount = amount,
            txn = txnValue,
        )
        val messageText = msg.body
        val existingBucket = existingByBucket[bucketKey]
        if (existingBucket != null && existingBucket.contains(messageText)) return@forEach
        val batchBucket = batchByBucket.getOrPut(bucketKey) { mutableSetOf() }
        if (!batchBucket.add(messageText)) return@forEach

        val id = transactionId(msg)
        val prior = existing[id]
        val inferredClass = autoTxnClassForMessage(msg.body) ?: miscClassId
        val normalizedTxnClass = when {
            prior?.txnClass != null && validTxnClassIds.contains(prior.txnClass) -> prior.txnClass
            validTxnClassIds.contains(inferredClass) -> inferredClass
            validTxnClassIds.contains(fallbackTxnClassId) -> fallbackTxnClassId
            else -> validTxnClassIds.first()
        }
        val inferredChannel = inferTxnChannel(msg.body, bankCheckResult)
        val (bankFromMessage, inferredBankCardNumber) = bankDetailsFromCheckResult(bankCheckResult)
        val inferredBank = senderRecord?.senderName?.takeIf { it.isNotBlank() } ?: bankFromMessage
        val inferredBankLogo = if (senderRecord != null) {
            senderRecord.senderLogo.trim()
        } else {
            inferLogoFromBankName(inferredBank)
        }

        entities.add(
            TransactionEntity(
                id = id,
                address = msg.address,
                message = msg.body,
                amount = amount,
                // Preserve user-edited txn/class when the same message is reprocessed.
                txn = prior?.txn ?: txnValue,
                txnChannel = prior?.txnChannel ?: inferredChannel,
                bank = prior?.bank ?: inferredBank,
                bankLogo = prior?.bankLogo ?: inferredBankLogo,
                bankCardNumber = prior?.bankCardNumber ?: inferredBankCardNumber,
                txnClass = normalizedTxnClass,
                dateMillis = msg.dateMillis,
                time = formatSmsTime(msg.dateMillis)
            )
        )
    }
    return entities
}

fun isTxnCheck(msg: SmsMessage, senderDirectory: Map<String, SenderEntity> = emptyMap()): Boolean {
    // Sender-based allowlist gate: sender ID must be present in local sender directory.
    if (isExplicitlyNonTransaction(msg.body)) return false
    val sender = msg.address.trim()
    val hasValidSuffix = sender.endsWith("-S", ignoreCase = true) || sender.endsWith("-T", ignoreCase = true)
    if (!hasValidSuffix) return false
    val senderId = extractSenderId(sender) ?: return false
    return senderDirectory.containsKey(senderDirectoryKey(senderId))
}

fun isTransactional(msg: SmsMessage): Boolean {
    // Legacy content-based heuristic retained for fallback/experiments.
    if (isExplicitlyNonTransaction(msg.body)) return false
    val text = (msg.body + " " + msg.address).lowercase()

    val keywordHits = TRANSACTION_KEYWORD_HITS.any { text.contains(it) }

    val amountHit = hasCurrency(text)

    return keywordHits || amountHit
}

fun isExplicitlyNonTransaction(message: String): Boolean {
    val text = message.lowercase(Locale.getDefault())
    return NON_TRANSACTION_BLOCKLIST.any { text.contains(it) }
}

fun autoTxnClassForMessage(message: String): String? {
    val text = message.lowercase(Locale.getDefault())
    return when {
        text.contains("swiggy") -> txnClassId("Dining Out", "Restaurants")
        text.contains("blinkit") -> txnClassId("Groceries", "Supplies")
        text.contains("fastag") -> txnClassId("Transportation", "Transit")
        text.contains("urbancompany") -> txnClassId("Housing", "Maintenance")
        else -> null
    }
}

fun inferTxnChannel(message: String, bankCheckResult: String? = checkAllBanks(message)): String {
    val byUpi = message.contains("by upi", ignoreCase = true)
    val hasBankCard = !bankCheckResult.isNullOrBlank()
    return buildList {
        if (byUpi) add(AppText.TXNC_UPI)
        if (hasBankCard) add(AppText.TXNC_CARD)
    }.joinToString(",")
}

private fun normalizedTxnChannel(existing: String, inferred: String): String {
    val tokens = existing
        .split(",")
        .map { it.trim().uppercase(Locale.getDefault()) }
        .filter { it == AppText.TXNC_UPI || it == AppText.TXNC_CARD }
        .toMutableSet()
    if (tokens.isEmpty()) {
        inferred
            .split(",")
            .map { it.trim().uppercase(Locale.getDefault()) }
            .filter { it == AppText.TXNC_UPI || it == AppText.TXNC_CARD }
            .forEach { tokens.add(it) }
    }
    return buildList {
        if (tokens.contains(AppText.TXNC_UPI)) add(AppText.TXNC_UPI)
        if (tokens.contains(AppText.TXNC_CARD)) add(AppText.TXNC_CARD)
    }.joinToString(",")
}

suspend fun backfillBankColumns(db: AppDatabase): Int {
    return withContext(Dispatchers.IO) {
        val missing = db.transactionDao().getWithoutBankInfo()
        if (missing.isEmpty()) return@withContext 0
        val senderDirectory = db.senderDao()
            .getAllOnce()
            .associateBy { senderDirectoryKey(it.senderId) }
        val patched = missing.mapNotNull { txn ->
            val senderRecord = senderLookup(txn.address, senderDirectory)
            if (senderRecord == null) return@mapNotNull null
            val (bankFromMessage, inferredCard) = bankDetailsFromMessage(txn.message)
            val inferredBank = senderRecord.senderName.takeIf { it.isNotBlank() } ?: bankFromMessage
            val inferredLogo = senderRecord.senderLogo.trim().ifBlank { inferLogoFromBankName(inferredBank) }
            val inferredChannel = inferTxnChannel(txn.message)
            val newBank = if (txn.bank.isBlank()) inferredBank else txn.bank
            val newBankLogo = if (txn.bankLogo.isBlank()) inferredLogo else txn.bankLogo
            val newBankCard = if (txn.bankCardNumber.isBlank()) inferredCard else txn.bankCardNumber
            val newChannel = normalizedTxnChannel(txn.txnChannel, inferredChannel)
            if (newBank != txn.bank || newBankLogo != txn.bankLogo || newBankCard != txn.bankCardNumber || newChannel != txn.txnChannel) {
                txn.copy(
                    txnChannel = newChannel,
                    bank = newBank,
                    bankLogo = newBankLogo,
                    bankCardNumber = newBankCard
                )
            } else {
                null
            }
        }
        if (patched.isNotEmpty()) db.transactionDao().upsertAll(patched)
        patched.size
    }
}

suspend fun recycleTransactions(context: Context, db: AppDatabase) {
    withContext(Dispatchers.IO) {
        db.transactionDao().deleteAll()
    }
    syncSmsToDb(context, db)
}

fun hasCurrency(text: String): Boolean {
    val pattern1 = Regex("""(₹|rs\.?|inr|usd|\$|eur|gbp)\s*\d""", RegexOption.IGNORE_CASE)
    val pattern2 = Regex("""\d+([,.]\d+)?\s*(rs|inr|usd|eur|gbp)""", RegexOption.IGNORE_CASE)

    return pattern1.containsMatchIn(text) || pattern2.containsMatchIn(text)
}

private fun isLikelyMoneyToken(raw: String): Boolean {
    val cleaned = raw.replace(",", "").trim()
    if (cleaned.isBlank()) return false
    // Reject long plain numeric strings that are usually reference IDs.
    if (cleaned.all { it.isDigit() } && cleaned.length > 6) return false
    val value = cleaned.toBigDecimalOrNull() ?: return false
    return value > BigDecimal.ZERO
}

fun extractAmount(text: String, bankCheckResult: String? = checkAllBanks(text)): String? {
    // checking specifically for stanchart
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

    // Deliberately avoid a generic number fallback to prevent transaction/reference IDs
    // from being parsed as amounts.
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

fun buildUiItems(transactions: List<TransactionEntity>): List<UiItem> {
    // Normalize DB rows and emit month/day sections with summaries at the top.
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

data class DbColumnSchema(
    val name: String,
    val type: String
)

data class DbTableSchema(
    val name: String,
    val columns: List<DbColumnSchema>
)

fun readDbSchema(context: Context): List<DbTableSchema> {
    val dbPath = context.getDatabasePath(AppText.DB_NAME)
    if (!dbPath.exists()) return emptyList()

    val database = SQLiteDatabase.openDatabase(
        dbPath.absolutePath,
        null,
        SQLiteDatabase.OPEN_READONLY
    )

    return database.use { db ->
        val tables = mutableListOf<String>()
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('room_master_table')",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                tables.add(cursor.getString(0))
            }
        }

        tables.map { tableName ->
            val columns = mutableListOf<DbColumnSchema>()
            db.rawQuery("PRAGMA table_info($tableName)", null).use { cursor ->
                val nameIdx = cursor.getColumnIndex("name")
                val typeIdx = cursor.getColumnIndex("type")
                while (cursor.moveToNext()) {
                    columns.add(
                        DbColumnSchema(
                            name = if (nameIdx >= 0) cursor.getString(nameIdx) else "",
                            type = if (typeIdx >= 0) cursor.getString(typeIdx) else ""
                        )
                    )
                }
            }
            DbTableSchema(name = tableName, columns = columns)
        }
    }
}

fun monthLabel(month: Int): String {
    return java.time.Month.of(month).getDisplayName(TextStyle.FULL, Locale.getDefault())
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
                cls?.name ?: AppText.MISC,
                cls?.subcategory ?: AppText.UNCAT,
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
