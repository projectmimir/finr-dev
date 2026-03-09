package com.projectmimir.finr

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

private const val DAILY_SUMMARY_CHANNEL_ID = "daily_summary_channel"
private const val DAILY_SUMMARY_NOTIFICATION_ID = 2230
private const val DAILY_SUMMARY_REQUEST_CODE = 2230
private const val ARROW_OUT = "↗"
private const val ARROW_IN = "↙"
private const val NOTIF_DEBIT_COLOR = "#EF476F"
private const val NOTIF_CREDIT_COLOR = "#06D6A0"

object DailySummaryScheduler {
    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = dailySummaryPendingIntent(context)
        val triggerAt = nextTriggerMillis()

        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } catch (_: SecurityException) {
            // Fallback for devices restricting exact alarms.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun nextTriggerMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone)
        var candidate = LocalDateTime.of(LocalDate.now(zone), LocalTime.of(22, 30))
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusDays(1)
        }
        return candidate.atZone(zone).toInstant().toEpochMilli()
    }

    private fun dailySummaryPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, DailySummaryAlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            DAILY_SUMMARY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

class DailySummaryAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                postDailySummaryNotification(context)
            } finally {
                // Schedule the next day's 22:30 alarm after each run.
                DailySummaryScheduler.schedule(context)
                pendingResult.finish()
            }
        }
    }
}

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        DailySummaryScheduler.schedule(context)
    }
}

private suspend fun postDailySummaryNotification(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }

    val zone = ZoneId.systemDefault()
    val start = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
    val end = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val today = AppDatabase.getInstance(context).transactionDao().getInRange(start, end)

    var debitTotal = BigDecimal.ZERO
    var creditTotal = BigDecimal.ZERO
    today.forEach { txn ->
        val amount = parseAmountValue(txn.amount) ?: BigDecimal.ZERO
        if (txn.txn.equals(AppText.DEBIT, ignoreCase = true)) {
            debitTotal = debitTotal.add(amount)
        } else {
            creditTotal = creditTotal.add(amount)
        }
    }

    ensureChannel(context)
    val outLine = "$ARROW_OUT ${AppText.DAILY_NOTIF_SENT_PREFIX}${formatCurrency(debitTotal)}"
    val inLine = "$ARROW_IN ${AppText.DAILY_NOTIF_RECEIVED_PREFIX}${formatCurrency(creditTotal)}"
    val preview = "$outLine  |  $inLine"
    val styledSummary = SpannableStringBuilder()
        .append(outLine)
        .append("\n")
        .append(inLine)
        .apply {
            // Match in-app summary semantics: red up-right for debit, green down-left for credit.
            setSpan(
                ForegroundColorSpan(Color.parseColor(NOTIF_DEBIT_COLOR)),
                0,
                outLine.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            setSpan(
                StyleSpan(Typeface.BOLD),
                0,
                outLine.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            val inStart = outLine.length + 1
            val inEnd = inStart + inLine.length
            setSpan(
                ForegroundColorSpan(Color.parseColor(NOTIF_CREDIT_COLOR)),
                inStart,
                inEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            setSpan(
                StyleSpan(Typeface.BOLD),
                inStart,
                inEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

    val openAppIntent = Intent(context, MainActivity::class.java)
    val openAppPending = PendingIntent.getActivity(
        context,
        0,
        openAppIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, DAILY_SUMMARY_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_finr)
        .setContentTitle(AppText.DAILY_NOTIF_TITLE)
        .setContentText(preview)
        .setStyle(NotificationCompat.BigTextStyle().bigText(styledSummary))
        .setAutoCancel(true)
        .setContentIntent(openAppPending)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()

    NotificationManagerCompat.from(context).notify(DAILY_SUMMARY_NOTIFICATION_ID, notification)
}

private fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(
        DAILY_SUMMARY_CHANNEL_ID,
        AppText.DAILY_NOTIF_CHANNEL_NAME,
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
        description = AppText.DAILY_NOTIF_CHANNEL_DESC
    }
    manager.createNotificationChannel(channel)
}
