package com.projectmimir.finr

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceivedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                // Receiver can run before MainActivity startup seeding; ensure FK parents exist.
                seedCategories(db)
                seedSenders(context, db)
                val mapped = messages.map { sms ->
                    SmsMessage(
                        address = sms.displayOriginatingAddress.orEmpty(),
                        body = sms.displayMessageBody.orEmpty(),
                        dateMillis = sms.timestampMillis
                    )
                }
                upsertIncomingSms(db, mapped)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
