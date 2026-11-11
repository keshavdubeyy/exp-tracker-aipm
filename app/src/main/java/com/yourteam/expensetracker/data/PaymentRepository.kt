package com.yourteam.expensetracker.data

import com.yourteam.expensetracker.insights.StartedPayment
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.yourteam.expensetracker.categoriser.Categoriser
import com.yourteam.expensetracker.insights.Payment
import com.yourteam.expensetracker.insights.Reconciler
import com.yourteam.expensetracker.insights.UserAnswer
import com.yourteam.expensetracker.notifications.PaymentCheck
import com.yourteam.expensetracker.sms.SenderFilter
import com.yourteam.expensetracker.sms.SmsMessage
import com.yourteam.expensetracker.sms.SmsReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the screens show, built from one place so Home, Review, Insights and Detail agree. */
data class PaymentSnapshot(
    /** Bank SMS that passed [SenderFilter] (debits and others). Kept in memory only. */
    val bankSms: List<SmsMessage>,
    val notifications: List<UpiNotification>,
    /** One entry per real payment, evidence linked one-to-one. Newest first. */
    val payments: List<Payment>,
    /** Categories the user picked: "payee:<name>" or "key:<payment key>" -> category. */
    val categoryChoices: Map<String, String> = emptyMap(),
) {
    private val smsById by lazy { bankSms.associateBy { it.id } }

    /** The user's choice for this payee (or payment) first, then keyword rules; null = no category yet. */
    fun categoryOf(payment: Payment): String? =
        categoryChoices[PaymentRepository.choiceKey(payment)] ?: Categoriser.categorise(payment.payee)
    fun smsFor(payment: Payment): SmsMessage? = payment.sms?.let { smsById[it.smsId] }
}

/** App-wide source of payments. SMS are read once and on refresh; the rest is live from Room. */
class PaymentRepository private constructor(private val context: Context) {

    private val db = AppDatabase.get(context)
    private val reader = SmsReader(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val bankSms = MutableStateFlow<List<SmsMessage>?>(null)
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _sampleMode = MutableStateFlow(prefs.getBoolean(KEY_SAMPLE, false))
    /** True while the screens show made-up [SampleData] instead of the user's payments. */
    val sampleMode: StateFlow<Boolean> = _sampleMode.asStateFlow()
    /** Answers given to sample payments, kept in memory only. */
    private val sampleAnswers = MutableStateFlow<Map<Long, UserAnswer>>(emptyMap())
    /** Payments added by hand while Sample data is on, kept in memory only. */
    private val sampleManual = MutableStateFlow<List<StartedPayment>>(emptyList())
    private var nextSampleId = -10_000L

    /** Categories the user picked, kept on the phone only. Sample data uses an in-memory copy. */
    private val categoryPrefs = context.getSharedPreferences("categories", Context.MODE_PRIVATE)
    private val categoryChoices = MutableStateFlow(
        categoryPrefs.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap(),
    )
    private val sampleCategories = MutableStateFlow<Map<String, String>>(emptyMap())

    fun setSampleMode(on: Boolean) {
        prefs.edit().putBoolean(KEY_SAMPLE, on).apply()
        sampleAnswers.value = emptyMap() // start the demo fresh each time
        sampleCategories.value = emptyMap()
        sampleManual.value = emptyList()
        _sampleMode.value = on
    }

    /**
     * Null until SMS have been read once. Computed once and shared by every screen, so switching
     * tabs shows the latest list immediately instead of re-running the matching each time.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val snapshot: StateFlow<PaymentSnapshot?> = _sampleMode.flatMapLatest { sample ->
        val base = if (sample) combine(sampleAnswers, sampleManual) { a, m -> SampleData.snapshot(a, manual = m) } else realSnapshot()
        combine(base, if (sample) sampleCategories else categoryChoices) { snap, choices ->
            snap?.copy(categoryChoices = choices)
        }
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * Tags this payment, and every other payment to the same payee, with [category].
     * Returns an undo that puts back exactly the previous choice (FEEDBACK.md §5).
     */
    fun setCategory(payment: Payment, category: String): () -> Unit {
        val key = choiceKey(payment)
        if (_sampleMode.value) {
            val before = sampleCategories.value[key]
            sampleCategories.update { it + (key to category) }
            return { sampleCategories.update { if (before == null) it - key else it + (key to before) } }
        }
        val before = categoryChoices.value[key]
        saveCategory(key, category)
        return { saveCategory(key, before) }
    }

    /** null removes the choice, so the payment goes back to the categoriser's guess. */
    private fun saveCategory(key: String, category: String?) {
        if (category == null) {
            categoryPrefs.edit().remove(key).apply()
            categoryChoices.update { it - key }
        } else {
            categoryPrefs.edit().putString(key, category).apply()
            categoryChoices.update { it + (key to category) }
        }
    }

    private fun realSnapshot(): Flow<PaymentSnapshot?> = combine(
        bankSms,
        db.upiNotificationDao().observeAll(),
        db.screenPaymentDao().observeAll(),
        db.pendingPaymentDao().observeAll(),
    ) { sms, notes, screens, pending ->
        sms ?: return@combine null
        PaymentSnapshot(
            bankSms = sms,
            notifications = notes,
            payments = Reconciler.reconcile(
                started = pending.mapNotNull(PaymentInputs::started),
                chats = screens.mapNotNull(PaymentInputs::chat),
                debits = sms.mapNotNull(PaymentInputs::debit),
            ),
        )
    }

    private var loadedWithPermission = false

    private val _import = MutableStateFlow<ImportProgress?>(null)
    /** First-run import (MVP M2, ONBOARDING.md ob3). Null until [importRecent] is called. */
    val importProgress: StateFlow<ImportProgress?> = _import.asStateFlow()

    /**
     * Reads the last 90 days month by month so onboarding can show the count climbing, then
     * loads the inbox for every screen. Shows for at least 1.5 s so the number is readable.
     * Counts only (debit SMS found); never logs message text (AGENTS.md rule 3).
     */
    fun importRecent() {
        val now = _import.value
        if (now != null && !now.failed) return // running or done
        scope.launch {
            val zone = java.time.ZoneId.systemDefault()
            val today = java.time.LocalDate.now()
            val start = today.minusDays(IMPORT_DAYS - 1)
            val months = generateSequence(java.time.YearMonth.from(start)) { it.plusMonths(1) }
                .takeWhile { !it.isAfter(java.time.YearMonth.from(today)) }.toList()
            _import.value = ImportProgress(months, emptySet(), 0, finished = false, failed = false)
            val began = System.currentTimeMillis()
            try {
                var found = 0
                for (m in months) {
                    val from = maxOf(start, m.atDay(1)).atStartOfDay(zone).toInstant().toEpochMilli()
                    val to = minOf(today, m.atEndOfMonth()).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
                    found += SenderFilter.filter(reader.readBetween(from, to)).count { PaymentInputs.debit(it) != null }
                    _import.update { it?.copy(done = it.done + m, found = found) }
                    kotlinx.coroutines.delay(300) // so the count visibly climbs month by month
                }
                loadedWithPermission = hasReadSms()
                bankSms.value = SenderFilter.filter(reader.readInbox())
                val shown = System.currentTimeMillis() - began
                if (shown < 1_500) kotlinx.coroutines.delay(1_500 - shown)
                _import.update { it?.copy(finished = true) }
            } catch (e: Exception) {
                _import.update { it?.copy(failed = true) } // no message contents in logs
            }
        }
    }

    /** Reads SMS if not read yet (or read before READ_SMS was granted). Safe from every screen. */
    fun ensureLoaded() {
        if (bankSms.value == null || (!loadedWithPermission && hasReadSms())) refresh()
    }

    /** Re-reads the SMS inbox (pull to refresh). Without READ_SMS, SMS are just empty. */
    fun refresh() {
        if (_refreshing.value) return
        _refreshing.value = true
        scope.launch {
            loadedWithPermission = hasReadSms()
            bankSms.value = try {
                if (loadedWithPermission) SenderFilter.filter(reader.readInbox()) else emptyList()
            } catch (e: SecurityException) {
                emptyList() // never log message contents (AGENTS.md rule 3)
            }
            _refreshing.value = false
        }
    }

    /**
     * Saves a payment the user typed in (cash, another app). It's already confirmed, so it counts
     * in every total straight away, and a later bank SMS for the same payment merges with it.
     * [category] (optional) is saved like a normal category choice.
     * Returns an undo that removes the payment and puts back the previous category choice.
     */
    fun addManual(paise: Long, payee: String, at: Long, category: String?): () -> Unit {
        val name = payee.trim()
        fun paymentFor(id: Long) = StartedPayment(id, paise, name, at, UserAnswer.YES, manual = true)
            .let { Payment(paise = paise, payee = name.takeIf(String::isNotEmpty), at = at, started = it) }

        if (_sampleMode.value) {
            val id = nextSampleId--
            sampleManual.update { it + paymentFor(id).started!! }
            val undoCategory = category?.let { setCategory(paymentFor(id), it) }
            return {
                sampleManual.update { list -> list.filterNot { it.id == id } }
                undoCategory?.invoke()
            }
        }

        val dao = db.pendingPaymentDao()
        var insertedId: Long? = null
        var undoCategory: (() -> Unit)? = null
        val save = scope.launch {
            val id = dao.insert(
                PendingPayment(
                    dedupeKey = "manual-${java.util.UUID.randomUUID()}",
                    appName = PendingPayment.APP_MANUAL,
                    amountText = "${paise / 100}.${(paise % 100).toString().padStart(2, '0')}",
                    payee = name,
                    detectedAt = at,
                    status = PendingPayment.STATUS_USER_CONFIRMED,
                ),
            )
            insertedId = id
            undoCategory = category?.let { setCategory(paymentFor(id), it) }
        }
        return {
            scope.launch {
                save.join()
                insertedId?.let { dao.delete(it) }
                undoCategory?.invoke()
            }
        }
    }

    /**
     * "Yes, paid" / "No" / "It was paid" from any screen.
     * Returns an undo that restores the previous answer (FEEDBACK.md §5).
     */
    fun answer(pendingId: Long, yes: Boolean): () -> Unit {
        if (_sampleMode.value) {
            val before = sampleAnswers.value[pendingId]
            sampleAnswers.update { it + (pendingId to if (yes) UserAnswer.YES else UserAnswer.NO) }
            return { sampleAnswers.update { if (before == null) it - pendingId else it + (pendingId to before) } }
        }
        val dao = db.pendingPaymentDao()
        // Read the old status and write the new one in the same coroutine, so undo can't race ahead.
        val change = scope.launch {
            val before = dao.get(pendingId)?.status ?: return@launch
            dao.setStatus(pendingId, if (yes) PendingPayment.STATUS_USER_CONFIRMED else PendingPayment.STATUS_DISMISSED)
            previousStatus[pendingId] = before
            PaymentCheck.cancelNotification(context, pendingId)
        }
        return {
            scope.launch {
                change.join()
                previousStatus.remove(pendingId)?.let { dao.setStatus(pendingId, it) }
            }
        }
    }

    /** Status before the last answer, kept only until Undo is used or the app closes. */
    private val previousStatus = java.util.concurrent.ConcurrentHashMap<Long, String>()

    private fun hasReadSms() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    companion object {
        /** Choices follow the payee, so "Raju K" stays Food & dining; unnamed payments use their own key. */
        fun choiceKey(p: Payment): String =
            p.payee?.trim()?.lowercase()?.takeIf(String::isNotEmpty)?.let { "payee:$it" } ?: "key:${p.key}"

        private const val KEY_SAMPLE = "sample_mode"
        const val IMPORT_DAYS = 90L
        @Volatile private var instance: PaymentRepository? = null

        fun get(context: Context): PaymentRepository =
            instance ?: synchronized(this) {
                instance ?: PaymentRepository(context.applicationContext).also { instance = it }
            }
    }
}

/** ONBOARDING.md ob3: months read so far and payments (debit SMS) found. Counts only. */
data class ImportProgress(
    val months: List<java.time.YearMonth>,
    val done: Set<java.time.YearMonth>,
    val found: Int,
    val finished: Boolean,
    val failed: Boolean,
)
