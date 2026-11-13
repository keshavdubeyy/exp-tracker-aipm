// Reads SMS on the phone only. Full SMS text never leaves the device and is never logged
// (AGENTS.md rule 3).
package com.yourteam.expensetracker.sms

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One inbox message. Kept in memory only. */
data class SmsMessage(
    val id: Long,
    val address: String,
    val body: String,
    val dateMillis: Long,
)

class SmsReader(private val context: Context) {

    /**
     * Reads content://sms/inbox from the last [days] days, newest first (MVP M2: 90 days, never the whole
     * inbox, which can hold years of payments). Caller must hold READ_SMS.
     */
    suspend fun readInbox(days: Long): List<SmsMessage> {
        val from = System.currentTimeMillis() - days * 86_400_000L
        return read("${Telephony.Sms.DATE} >= ?", arrayOf(from.toString()))
    }

    /** Messages received between [from] and [to] (epoch millis), newest first. */
    suspend fun readBetween(from: Long, to: Long): List<SmsMessage> =
        read("${Telephony.Sms.DATE} BETWEEN ? AND ?", arrayOf(from.toString(), to.toString()))

    private suspend fun read(selection: String?, args: Array<String>?): List<SmsMessage> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
        )
        val messages = mutableListOf<SmsMessage>()
        context.contentResolver.query(
            INBOX_URI,
            projection,
            selection,
            args,
            "${Telephony.Sms.DATE} DESC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addressCol = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyCol = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateCol = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (cursor.moveToNext()) {
                messages += SmsMessage(
                    id = cursor.getLong(idCol),
                    address = cursor.getString(addressCol).orEmpty(),
                    body = cursor.getString(bodyCol).orEmpty(),
                    dateMillis = cursor.getLong(dateCol),
                )
            }
        }
        messages
    }

    private companion object {
        val INBOX_URI: Uri = Uri.parse("content://sms/inbox")
    }
}
