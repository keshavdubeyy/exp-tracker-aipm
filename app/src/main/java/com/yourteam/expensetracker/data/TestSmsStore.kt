package com.yourteam.expensetracker.data

import android.content.Context
import com.yourteam.expensetracker.sms.SmsMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Made-up bank SMS typed into Developer › Test an SMS (test builds). They are mixed into the
 * real payments as if they had arrived, so every screen can be tried on a real phone, and can be
 * deleted again. Kept in this app's storage on the phone only: never written to the phone's SMS
 * inbox, never uploaded, never counted in the tester metrics.
 */
class TestSmsStore private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("test_sms", Context.MODE_PRIVATE)
    private val _messages = MutableStateFlow(load())
    /** Newest first. */
    val messages: StateFlow<List<SmsMessage>> = _messages.asStateFlow()

    fun add(sender: String, body: String, at: Long = System.currentTimeMillis()): SmsMessage {
        val nextId = (_messages.value.minOfOrNull { it.id } ?: ID_BASE + 1) - 1
        val m = SmsMessage(nextId, sender.trim(), body.trim(), at)
        save(listOf(m) + _messages.value)
        return m
    }

    /** Puts back a message removed by [delete] (Undo). */
    fun restore(m: SmsMessage) {
        if (_messages.value.any { it.id == m.id }) return
        save((_messages.value + m).sortedByDescending { it.dateMillis })
    }

    fun delete(id: Long) = save(_messages.value.filterNot { it.id == id })

    fun deleteAll() = save(emptyList())

    private fun save(list: List<SmsMessage>) {
        val json = JSONArray()
        list.forEach { json.put(JSONObject().put("id", it.id).put("from", it.address).put("body", it.body).put("at", it.dateMillis)) }
        prefs.edit().putString(KEY, json.toString()).apply()
        _messages.value = list
    }

    private fun load(): List<SmsMessage> = runCatching {
        val json = JSONArray(prefs.getString(KEY, "[]"))
        (0 until json.length()).map { i ->
            json.getJSONObject(i).let { SmsMessage(it.getLong("id"), it.getString("from"), it.getString("body"), it.getLong("at")) }
        }
    }.getOrDefault(emptyList())

    companion object {
        private const val KEY = "messages"
        /** Test SMS ids sit far below real (positive) and Sample (small negative) ids. */
        private const val ID_BASE = -1_000_000_000L

        fun isTest(smsId: Long?): Boolean = smsId != null && smsId <= ID_BASE

        @Volatile private var instance: TestSmsStore? = null
        fun get(context: Context): TestSmsStore =
            instance ?: synchronized(this) { instance ?: TestSmsStore(context.applicationContext).also { instance = it } }
    }
}
