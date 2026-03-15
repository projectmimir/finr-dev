package com.projectmimir.finr

import android.content.Context
import android.provider.Telephony
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

internal fun senderDirectoryKey(senderId: String): String = senderId.uppercase(Locale.getDefault())

internal fun senderLookup(address: String, senderDirectory: Map<String, SenderEntity>): SenderEntity? {
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
