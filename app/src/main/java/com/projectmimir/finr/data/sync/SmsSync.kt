package com.projectmimir.finr

import android.content.Context
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
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

fun transactionId(message: SmsMessage): String {
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
        if (!isTxnCheck(msg, senderDirectory)) return@forEach
        if (isExplicitlyNonTransaction(msg.body)) return@forEach
        val senderRecord = senderLookup(msg.address, senderDirectory) ?: return@forEach
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
        val inferredBank = senderRecord.senderName.takeIf { it.isNotBlank() } ?: bankFromMessage
        val inferredBankLogo = senderRecord.senderLogo.trim().ifBlank { inferLogoFromBankName(inferredBank) }

        entities.add(
            TransactionEntity(
                id = id,
                address = msg.address,
                message = msg.body,
                amount = amount,
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
    if (isExplicitlyNonTransaction(msg.body)) return false
    val sender = msg.address.trim()
    val hasValidSuffix = sender.endsWith("-S", ignoreCase = true) || sender.endsWith("-T", ignoreCase = true)
    if (!hasValidSuffix) return false
    val senderId = extractSenderId(sender) ?: return false
    return senderDirectory.containsKey(senderDirectoryKey(senderId))
}

fun isTransactional(msg: SmsMessage): Boolean {
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
            val senderRecord = senderLookup(txn.address, senderDirectory) ?: return@mapNotNull null
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
