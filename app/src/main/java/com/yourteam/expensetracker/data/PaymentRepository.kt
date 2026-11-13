package com.yourteam.expensetracker.data

import com.yourteam.expensetracker.insights.StartedPayment
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.yourteam.expensetracker.categoriser.AiCategoriser
import com.yourteam.expensetracker.categoriser.AiHint
import com.yourteam.expensetracker.categoriser.AiRequest
import com.yourteam.expensetracker.categoriser.Categories
import com.yourteam.expensetracker.categoriser.CategoryGuess
import com.yourteam.expensetracker.categoriser.CategoryMemory
import com.yourteam.expensetracker.categoriser.CategoryMetrics
import com.yourteam.expensetracker.categoriser.CategoryRecord
import com.yourteam.expensetracker.categoriser.Confidence
import com.yourteam.expensetracker.categoriser.Metric
import com.yourteam.expensetracker.categoriser.Categoriser
import com.yourteam.expensetracker.categoriser.CustomCategory
import com.yourteam.expensetracker.categoriser.PaymentFacts
import com.yourteam.expensetracker.insights.BankCredit
import com.yourteam.expensetracker.insights.Payment
import com.yourteam.expensetracker.insights.Received
import com.yourteam.expensetracker.insights.Reconciler
import com.yourteam.expensetracker.insights.UserAnswer
import com.yourteam.expensetracker.notifications.PaymentCheck
import kotlinx.coroutines.sync.withLock
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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.async

/** Everything the screens show, built from one place so Home, Review, Insights and Detail agree. */
data class PaymentSnapshot(
    /** Bank SMS that passed [SenderFilter] (debits and others). Kept in memory only. */
    val bankSms: List<SmsMessage>,
    val notifications: List<UpiNotification>,
    /** One entry per real payment, evidence linked one-to-one. Newest first. */
    val payments: List<Payment>,
    /** Money that came in (bank credit SMS and UPI app "paid you" alerts, merged). Never part of [payments] or any spending total. Newest first. */
    val received: List<BankCredit> = emptyList(),
    /** The user's saved category choices (see [CategoryMemory] for the keys). */
    val memory: CategoryMemory = CategoryMemory(),
    /** Categories the user made (Feature 1 §3.6). */
    val customCategories: List<CustomCategory> = emptyList(),
    /** AI suggestions per payee key, kept on the phone after the server answered (Feature 1 §3.2). */
    val aiHints: Map<String, AiHint> = emptyMap(),
) {
    private val smsById by lazy { bankSms.associateBy { it.id } }

    /** Expense categories the pickers offer: predefined + the user's own. */
    val expenseChoices: List<String> by lazy { Categories.expenseChoices(customCategories) }

    /** The user's most used expense categories, most used first: ranks suggestions. */
    private val popular: List<String> by lazy {
        memory.choices.filterKeys { !it.startsWith("in-") }.values.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key }
    }

    private val guesses = java.util.concurrent.ConcurrentHashMap<String, CategoryGuess>()
    private val incomeGuesses = java.util.concurrent.ConcurrentHashMap<String, CategoryGuess>()

    /** Category, confidence and top 3 suggestions for a payment (Feature 1 §3.2–3.3). */
    fun guessFor(payment: Payment): CategoryGuess = guesses.getOrPut(payment.key) {
        val facts = factsOf(payment)
        val ai = CategoryMemory.payeeKeys(facts.payee, facts.vpa).firstNotNullOfOrNull { aiHints[it] }
        Categoriser.decide(payment.key, facts, memory, ai, popular, expenseChoices)
    }

    /** The decided category; null = "Add category" (still counted in every total). */
    fun categoryOf(payment: Payment): String? = guessFor(payment).category

    /** Income category for money received (Feature 1 §3.7). */
    fun incomeGuessFor(credit: BankCredit): CategoryGuess = incomeGuesses.getOrPut(credit.key) {
        Categoriser.decideIncome(credit.key, credit.kind, credit.from, memory)
    }

    fun smsFor(payment: Payment): SmsMessage? = payment.sms?.let { smsById[it.smsId] }

    companion object {
        fun factsOf(p: Payment) = PaymentFacts(p.payee, p.sms?.vpa, p.paise, p.at, p.sms?.method ?: if (p.started != null || p.chat != null) "UPI" else null)
    }
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
    /** Money in added by hand while Sample data is on, kept in memory only. */
    private val sampleManualIn = MutableStateFlow<List<BankCredit>>(emptyList())
    private var nextSampleId = -10_000L
    /** Made-up SMS from Developer › Test an SMS, mixed into the real payments (test builds). */
    val testSms = TestSmsStore.get(context)

    /** Category choices, kept on the phone only (keys: [CategoryMemory]). Sample data uses an in-memory copy. */
    private val categoryPrefs = context.getSharedPreferences("categories", Context.MODE_PRIVATE)
    private val categoryChoices = MutableStateFlow(loadChoices())
    private val sampleCategories = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Categories the user made: one string per category, "name" or "name\u0000parent". */
    private val customPrefs = context.getSharedPreferences("custom_categories", Context.MODE_PRIVATE)
    private val customCategories = MutableStateFlow(loadCustom())
    private val sampleCustom = MutableStateFlow<List<CustomCategory>>(emptyList())

    /** Merged names: payee key ("payee:…" / "vpa:…") → the one name to show. See [mergePayees]. */
    private val aliasPrefs = context.getSharedPreferences("payee_aliases", Context.MODE_PRIVATE)
    private val aliases = MutableStateFlow(aliasPrefs.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap())
    /** Duplicate suggestions the user said "Not the same" to ([DuplicateGroup.id]). */
    private val duplicatePrefs = context.getSharedPreferences("duplicates", Context.MODE_PRIVATE)

    /** AI suggestions per payee key: "c1|c2|c3|1" (1 = confident). Written by [AiCategoriser]. */
    private val aiPrefs = context.getSharedPreferences("ai_hints", Context.MODE_PRIVATE)
    private val aiHints = MutableStateFlow(loadAiHints())

    fun setSampleMode(on: Boolean) {
        prefs.edit().putBoolean(KEY_SAMPLE, on).apply()
        sampleAnswers.value = emptyMap() // start the demo fresh each time
        sampleCategories.value = emptyMap()
        sampleCustom.value = emptyList()
        sampleManual.value = emptyList()
        sampleManualIn.value = emptyList()
        _sampleMode.value = on
    }


    /**
     * Null until SMS have been read once. Computed once and shared by every screen, so switching
     * tabs shows the latest list immediately instead of re-running the matching each time.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val snapshot: StateFlow<PaymentSnapshot?> = _sampleMode.flatMapLatest { sample ->
        val base = if (sample) combine(sampleAnswers, sampleManual, sampleManualIn) { a, m, mi ->
            SampleData.snapshot(a, manual = m).let { s -> s.copy(received = receivedWithManual(s.received, emptyList(), mi)) }
        } else realSnapshot()
        combine(
            base,
            if (sample) sampleCategories else categoryChoices,
            if (sample) sampleCustom else customCategories,
            aiHints,
            aliases,
        ) { snap, choices, custom, ai, names ->
            snap?.let { withAliases(it, names) }?.copy(memory = CategoryMemory(choices), customCategories = custom, aiHints = ai)
        }
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * Saves [category] for [payment] (Feature 1 §3.5, always learn).
     * [onlyThis] false: it becomes the payee's default, so this and future payments to them use it.
     * [onlyThis] true (or a payment with no payee name): only this payment changes.
     * Returns an undo that puts back exactly the previous choices (FEEDBACK.md §5).
     */
    fun setCategory(payment: Payment, category: String, onlyThis: Boolean = false): () -> Unit {
        recordPick(payment, category)
        val facts = PaymentSnapshot.factsOf(payment)
        val txn = CategoryMemory.txnKey(payment.key)
        val payeeKeys = CategoryMemory.payeeKeys(facts.payee, facts.vpa)
        val changes: Map<String, String?> =
            if (onlyThis || payeeKeys.isEmpty()) mapOf(txn to category)
            else payeeKeys.associateWith { category } + (txn to null) // the new default replaces a one-off
        return applyChoices(changes)
    }

    /**
     * For payments seen outside the app (a new bank SMS): what the categoriser makes of [facts]
     * with the user's real saved choices. Never uses Sample data.
     */
    fun guessForNew(facts: PaymentFacts, paymentKey: String = ""): CategoryGuess {
        val custom = customCategories.value
        val choices = categoryChoices.value
        val popular = choices.filterKeys { !it.startsWith("in-") }.values.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key }
        val ai = CategoryMemory.payeeKeys(facts.payee, facts.vpa).firstNotNullOfOrNull { aiHints.value[it] }
        return Categoriser.decide(paymentKey, facts, CategoryMemory(choices), ai, popular, Categories.expenseChoices(custom))
    }

    /** Saves [category] as the payee's default from a notification button (real data only). */
    fun setPayeeDefault(facts: PaymentFacts, category: String) {
        val keys = CategoryMemory.payeeKeys(facts.payee, facts.vpa)
        if (keys.isEmpty()) return
        categoryPrefs.edit().apply { keys.forEach { putString(it, category) } }.apply()
        categoryChoices.update { it + keys.associateWith { category } }
    }

    /**
     * A button on the "was it X?" notification. Same as picking it in the app (payee default when
     * named, metrics updated); falls back to the payee default if the payment isn't loaded.
     */
    suspend fun categoriseFromPrompt(paymentKey: String, facts: PaymentFacts, category: String) {
        if (_sampleMode.value) return
        val snap = kotlinx.coroutines.withTimeoutOrNull(10_000) { snapshot.first { it != null } }
        val payment = snap?.payments?.find { it.key == paymentKey }
        if (payment != null) setCategory(payment, category) else setPayeeDefault(facts, category)
    }

    /** A button on the "money received" question: same as picking it in the app. */
    suspend fun categoriseIncomeFromPrompt(creditKey: String, category: String) {
        if (_sampleMode.value) return
        val snap = kotlinx.coroutines.withTimeoutOrNull(10_000) { snapshot.first { it != null } }
        snap?.received?.find { it.key == creditKey }?.let { setIncomeCategory(it, category) }
    }

    /** Income category for money received; same rules as [setCategory]. */
    fun setIncomeCategory(credit: BankCredit, category: String, onlyThis: Boolean = false): () -> Unit {
        val txn = CategoryMemory.incomeTxnKey(credit.key)
        val payee = CategoryMemory.incomePayeeKey(credit.from)
        val changes: Map<String, String?> =
            if (onlyThis || payee == null) mapOf(txn to category) else mapOf(payee to category, txn to null)
        return applyChoices(changes)
    }

    /** Writes [changes] (null = remove) and returns an undo that restores the previous values. */
    private fun applyChoices(changes: Map<String, String?>): () -> Unit {
        val flow = if (_sampleMode.value) sampleCategories else categoryChoices
        val before = changes.keys.associateWith { flow.value[it] }
        writeChoices(changes)
        return { writeChoices(before) }
    }

    private fun writeChoices(changes: Map<String, String?>) {
        if (_sampleMode.value) {
            sampleCategories.update { m -> m.toMutableMap().apply { changes.forEach { (k, v) -> if (v == null) remove(k) else put(k, v) } } }
            return
        }
        categoryPrefs.edit().apply { changes.forEach { (k, v) -> if (v == null) remove(k) else putString(k, v) } }.apply()
        categoryChoices.update { m -> m.toMutableMap().apply { changes.forEach { (k, v) -> if (v == null) remove(k) else put(k, v) } } }
    }

    // ---- Duplicate names and categories ----

    /** Shows every merged name as the one the user chose: "ASHATA S" and "Ashata" become "Ashata Sharma". */
    private fun withAliases(s: PaymentSnapshot, names: Map<String, String>): PaymentSnapshot {
        if (names.isEmpty()) return s
        fun canonical(payee: String?, vpa: String?) = CategoryMemory.payeeKeys(payee, vpa).firstNotNullOfOrNull { names[it] }
        return s.copy(
            payments = s.payments.map { p -> canonical(p.payee, p.sms?.vpa)?.takeIf { it != p.payee }?.let { p.copy(payee = it) } ?: p },
            received = s.received.map { c -> canonical(c.from, null)?.takeIf { it != c.from }?.let { c.copy(from = it) } ?: c },
        )
    }

    /** Every payee and sender name in use, with UPI IDs and counts: the input for duplicate checks. */
    fun nameUses(): List<com.yourteam.expensetracker.categoriser.NameUse> {
        val s = snapshot.value ?: return emptyList()
        val out = mutableMapOf<String, Pair<MutableSet<String>, Int>>()
        s.payments.forEach { p ->
            val n = p.payee?.takeIf { it.isNotBlank() && '@' !in it } ?: return@forEach
            val e = out[n] ?: (mutableSetOf<String>() to 0)
            p.sms?.vpa?.lowercase()?.let { e.first += it }
            out[n] = e.first to e.second + 1
        }
        s.received.forEach { c ->
            val n = c.from?.takeIf { it.isNotBlank() && '@' !in it } ?: return@forEach
            val e = out[n] ?: (mutableSetOf<String>() to 0)
            out[n] = e.first to e.second + 1
        }
        return out.map { (n, e) -> com.yourteam.expensetracker.categoriser.NameUse(n, e.first, e.second) }
    }

    /** How many saved choices use each category name. */
    fun categoryUses(): Map<String, Int> = categoryChoices.value.values.groupingBy { it }.eachCount()

    /**
     * Every expense category name in use: the picker list, plus names still saved on payments
     * but no longer in the list (renamed or deleted earlier). Income categories are left out.
     */
    fun allExpenseCategories(): List<String> {
        val picker = snapshot.value?.expenseChoices ?: Categories.expenseChoices(currentCustom())
        val income = Categories.income.toSet()
        val orphans = categoryChoices.value.filterKeys { !it.startsWith("in-") }.values
            .filter { it !in picker && it !in income }.distinct()
        return picker + orphans
    }

    fun rejectedDuplicates(): Set<String> = duplicatePrefs.getStringSet("rejected", emptySet()).orEmpty()

    /** "Not the same": never suggested again. */
    fun rejectDuplicate(id: String): () -> Unit {
        val before = rejectedDuplicates()
        duplicatePrefs.edit().putStringSet("rejected", before + id).apply()
        return { duplicatePrefs.edit().putStringSet("rejected", before).apply() }
    }

    /**
     * Shows [members] as one person called [into] everywhere, and moves their saved category to
     * [into] (the one used most, if they differ). Returns the undo.
     */
    fun mergePayees(members: List<String>, vpas: Set<String>, into: String): () -> Unit {
        val keys = (members + into).flatMap { CategoryMemory.payeeKeys(it, null) } + vpas.map { "vpa:${it.lowercase()}" }
        val newAliases = keys.distinct().associateWith { into }
        val beforeAliases = aliases.value

        // The merged person keeps the category used most among the old names.
        val choices = categoryChoices.value
        val target = CategoryMemory.payeeKeys(into, null).firstOrNull()
        val changes = mutableMapOf<String, String?>()
        if (target != null && choices[target] == null) {
            keys.mapNotNull { choices[it] }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key?.let { changes[target] = it }
        }
        val incomeTarget = CategoryMemory.incomePayeeKey(into)
        if (incomeTarget != null && choices[incomeTarget] == null) {
            members.mapNotNull { CategoryMemory.incomePayeeKey(it)?.let(choices::get) }.groupingBy { it }.eachCount()
                .maxByOrNull { it.value }?.key?.let { changes[incomeTarget] = it }
        }
        val beforeChoices = changes.keys.associateWith { choices[it] }

        writeAliases(beforeAliases + newAliases)
        if (changes.isNotEmpty()) writeChoices(changes)
        return { writeAliases(beforeAliases); if (beforeChoices.isNotEmpty()) writeChoices(beforeChoices) }
    }

    private fun writeAliases(map: Map<String, String>) {
        aliasPrefs.edit().clear().apply { map.forEach { (k, v) -> putString(k, v) } }.apply()
        aliases.value = map
    }

    /**
     * Moves every payment in [members] to [into] and deletes the other names the user made.
     * Built-in categories are never deleted. Returns the undo.
     */
    fun mergeCategories(members: List<String>, into: String): () -> Unit {
        val others = members.filter { it != into }.toSet()
        val flow = if (_sampleMode.value) sampleCategories else categoryChoices
        val moved = flow.value.filterValues { it in others }.mapValues { into as String? }
        val beforeChoices = moved.keys.associateWith { flow.value[it] }
        val beforeCustom = currentCustom()
        val keepParent = beforeCustom.firstOrNull { it.name in others && it.parent != null }?.parent
        writeCustom(
            beforeCustom.filterNot { it.name in others }
                .map { if (it.name == into && it.parent == null && keepParent != null) it.copy(parent = keepParent) else it },
        )
        writeChoices(moved)
        return { writeCustom(beforeCustom); writeChoices(beforeChoices) }
    }

    // ---- Custom categories (Feature 1 §3.6) ----

    /** Adds a category; null if the name is empty or already used. Returns the undo. */
    fun addCustomCategory(name: String, parent: String?): (() -> Unit)? {
        val clean = Categories.cleanName(name) ?: return null
        val all = currentCustom()
        if (clean.equals(Categories.OTHERS, true) || (Categories.expense + Categories.income + all.map { it.name }).any { it.equals(clean, true) }) return null
        val before = all
        writeCustom(all + CustomCategory(clean, parent?.takeIf { it in Categories.expense }))
        return { writeCustom(before) }
    }

    /**
     * Renames and/or re-parents a category the user made. Payments using the old name move to
     * the new one. Null if the new name is empty or taken. Returns the undo.
     */
    fun editCustomCategory(old: String, newName: String, parent: String?): (() -> Unit)? {
        val clean = Categories.cleanName(newName) ?: return null
        val all = currentCustom()
        if (all.none { it.name == old }) return null
        val taken = (Categories.expense + Categories.income + all.map { it.name }.filter { it != old }).any { it.equals(clean, true) }
        if (taken) return null
        val beforeCustom = all
        val flow = if (_sampleMode.value) sampleCategories else categoryChoices
        val moved = flow.value.filterValues { it == old }.mapValues { clean as String? }
        val beforeChoices = moved.keys.associateWith { flow.value[it] }
        writeCustom(all.map { if (it.name == old) CustomCategory(clean, parent?.takeIf { p -> p in Categories.expense }) else it })
        writeChoices(moved)
        return { writeCustom(beforeCustom); writeChoices(beforeChoices) }
    }

    /**
     * Deletes a category the user made. Payments using it move to its parent, or back to
     * "Add category" if it has none. Returns the undo.
     */
    fun deleteCustomCategory(name: String): () -> Unit {
        val all = currentCustom()
        val parent = all.firstOrNull { it.name == name }?.parent
        val flow = if (_sampleMode.value) sampleCategories else categoryChoices
        val moved = flow.value.filterValues { it == name }.mapValues { parent }
        val beforeChoices = moved.keys.associateWith { flow.value[it] }
        writeCustom(all.filterNot { it.name == name })
        writeChoices(moved)
        return { writeCustom(all); writeChoices(beforeChoices) }
    }

    private fun currentCustom() = if (_sampleMode.value) sampleCustom.value else customCategories.value

    private fun writeCustom(list: List<CustomCategory>) {
        if (_sampleMode.value) { sampleCustom.value = list; return }
        customPrefs.edit().clear().putStringSet(KEY_CUSTOM, list.map { c -> c.parent?.let { "${c.name}\u0000$it" } ?: c.name }.toSet())
            .putString(KEY_CUSTOM_ORDER, list.joinToString("\u0001") { it.name }).apply()
        customCategories.value = list
    }

    private fun loadCustom(): List<CustomCategory> {
        val items = customPrefs.getStringSet(KEY_CUSTOM, emptySet()).orEmpty().map {
            val parts = it.split('\u0000')
            CustomCategory(parts[0], parts.getOrNull(1))
        }
        val order = customPrefs.getString(KEY_CUSTOM_ORDER, "").orEmpty().split('\u0001')
        return items.sortedBy { order.indexOf(it.name).let { i -> if (i < 0) Int.MAX_VALUE else i } }
    }

    // ---- AI hints (Feature 1 §3.2 step 4) ----

    /** Saves what the AI suggested for a payee, so it's asked once per payee. */
    fun saveAiHint(payeeKey: String, hint: AiHint) {
        val value = (hint.suggestions.take(3) + if (hint.confident) "1" else "0").joinToString("|")
        aiPrefs.edit().putString(payeeKey, value).apply()
        aiHints.update { it + (payeeKey to hint) }
    }

    private fun loadAiHints(): Map<String, AiHint> = aiPrefs.all.mapNotNull { (k, v) ->
        val parts = (v as? String)?.split('|') ?: return@mapNotNull null
        if (parts.size < 2) return@mapNotNull null
        k to AiHint(parts.dropLast(1), parts.last() == "1")
    }.toMap()

    /**
     * Reads saved choices, upgrading ones saved before 13 Nov 2026 ("payee:<lower name>",
     * "key:<payment>", old category names) to today's keys and names, once.
     */
    private fun loadChoices(): Map<String, String> {
        val raw = categoryPrefs.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()
        if (categoryPrefs.getInt(KEY_CHOICES_VERSION, 1) >= 2) return raw
        val upgraded = raw.entries.mapNotNull { (k, v) ->
            val key = when {
                k.startsWith("key:") -> CategoryMemory.txnKey(k.removePrefix("key:"))
                k.startsWith("payee:") -> CategoryMemory.payeeKeys(k.removePrefix("payee:"), null).firstOrNull() ?: return@mapNotNull null
                else -> k
            }
            key to Categories.upgrade(v)
        }.toMap()
        categoryPrefs.edit().clear().apply { upgraded.forEach { (k, v) -> putString(k, v) } }.putInt(KEY_CHOICES_VERSION, 2).apply()
        return upgraded
    }

    // ---- Feature 1 §7 metrics: how each payment was first placed and what the user did ----

    private val metricPrefs = context.getSharedPreferences("category_metrics", Context.MODE_PRIVATE)
    private val records = MutableStateFlow(
        metricPrefs.all.mapNotNull { (k, v) -> (v as? String)?.let(CategoryRecord::decode)?.let { k to it } }.toMap(),
    )
    /** The success metrics, computed from [records] (counts only). Shown on the Developer screen. */
    val metrics: StateFlow<List<Metric>> = records.map { CategoryMetrics.compute(it.values) }
        .stateIn(scope, SharingStarted.Eagerly, CategoryMetrics.compute(emptyList()))

    /** Counts behind [metrics] for the daily tester upload (MVP M24). Real data only, never Sample. */
    fun categoryCounts(): Map<String, Int> = CategoryMetrics.counts(records.value.values)

    /** How this payment's category was first placed and picked (metrics only). */
    fun categoryRecord(paymentKey: String): CategoryRecord? = records.value[paymentKey]

    /** Real payments for the daily upload, even while Sample data is on screen. Null until SMS are read. */
    fun realSnapshotNow(): PaymentSnapshot? = if (_sampleMode.value) null else snapshot.value

    /** Notes how every newly seen payment was placed, the first time we see it (real data only). */
    private fun startMetrics() {
        scope.launch {
            snapshot.collect { snap ->
                if (snap == null || _sampleMode.value) return@collect
                // Feature 1 "Captured within 1 minute": when each new bank SMS first shows up here.
                val now = System.currentTimeMillis()
                snap.payments.forEach { p -> p.sms?.let { com.yourteam.expensetracker.metrics.CaptureLog.smsSeen(context, it.smsId, it.at, now) } }
                val known = records.value
                val fresh = snap.payments.filter { it.key !in known && !TestSmsStore.isTest(it.sms?.smsId) }.associate { p ->
                    val g = snap.guessFor(p)
                    p.key to CategoryRecord(g.basis, g.category != null, g.category)
                }
                // The AI answers a few seconds after a payment first shows up, then files its pick: count that as automatic.
                val aiFiled = snap.payments.mapNotNull { p ->
                    val r = known[p.key] ?: return@mapNotNull null
                    CategoryMetrics.onAiFiled(r, snap.guessFor(p)).takeIf { it != r }?.let { p.key to it }
                }.toMap()
                val changed = fresh + aiFiled
                if (changed.isEmpty()) return@collect
                metricPrefs.edit().apply { changed.forEach { (k, r) -> putString(k, r.encode()) } }.apply()
                records.update { it + changed }
            }
        }
    }

    private fun recordPick(payment: Payment, category: String) {
        if (_sampleMode.value) return
        val record = records.value[payment.key] ?: return
        val shown = snapshot.value?.guessFor(payment) // the guesses on screen, before this pick is saved
        val updated = CategoryMetrics.onUserPick(record, category, shown?.suggestions.orEmpty(), shown?.basis)
        if (updated == record) return
        metricPrefs.edit().putString(payment.key, updated.encode()).apply()
        records.update { it + (payment.key to updated) }
    }

    init { startMetrics() }

    /** Payee keys already sent to the AI this session (answered or failed), so each is asked once. */
    private val aiTried = java.util.Collections.synchronizedSet(HashSet<String>())
    private val autoLock = kotlinx.coroutines.sync.Mutex()

    /**
     * Every time money is recorded, from any source (bank SMS, UPI app notification, the GPay
     * "did it go through?" answer, GPay chat), the payment list changes and this runs (debounced).
     * Never for Sample data.
     */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun startAutoCategorise() {
        scope.launch {
            snapshot.debounce(3_000).collect { backfillPastOnce(); autoCategoriseNow() }
        }
    }

    /**
     * Testers who onboarded before onboarding asked the AI about the whole inbox (ob3b) only got 10 payees a run,
     * so most of their history never had an AI answer. Once per install, after onboarding, do that full pass in the
     * background. Marked done only when it got answers (or had nothing to ask), so a first run offline tries again.
     */
    private suspend fun backfillPastOnce() {
        if (prefs.getBoolean(KEY_PAST_AI_DONE, false) || _sampleMode.value || !AiCategoriser.enabled) return
        if (!com.yourteam.expensetracker.ui.screens.onboarding.Onboarding.isComplete(context)) return // onboarding does its own
        val before = aiHints.value.size
        var total = 0
        askAiForAllPast { _, t -> total = t }
        if (total == 0 || aiHints.value.size > before) prefs.edit().putBoolean(KEY_PAST_AI_DONE, true).apply()
    }

    /**
     * Feature 1 §3.2 for new payments: saved choice → known merchant → shop words → AI. Anything
     * scoring 30% or more is categorised on its own (it just shows in the list). Below that we
     * ask once, with our best guess to confirm, via [com.yourteam.expensetracker.notifications.CategoryPrompt].
     */
    suspend fun autoCategoriseNow() = autoLock.withLock {
        val snap = snapshot.value ?: return@withLock
        if (_sampleMode.value) return@withLock
        // 1. AI for payees we couldn't place (at most 10 per run, each payee once ever).
        if (AiCategoriser.enabled) {
            val todo = AiCategoriser.pick(
                snap.payments.map { PaymentSnapshot.factsOf(it) to guessForNew(PaymentSnapshot.factsOf(it), it.key) },
                haveHint = { it in aiHints.value },
                tried = aiTried,
            )
            val past = categoryChoices.value.filterKeys { it.startsWith("payee:") }
                .map { (k, v) -> k.removePrefix("payee:") to v }.take(20)
            if (com.yourteam.expensetracker.BuildConfig.TEST_BUILD) android.util.Log.d("AiCategoriser", "payments=${snap.payments.size} toAsk=${todo.size}")
            todo.forEach { (key, facts) ->
                aiTried += key
                AiCategoriser.ask(AiRequest(key, facts, snap.expenseChoices, past))?.let { saveAiHint(key, it) }
            }
        }
        // 2. Recent payments still under 30% (with the AI's answer included): ask the user to confirm.
        val since = System.currentTimeMillis() - com.yourteam.expensetracker.notifications.CategoryPrompt.RECENT_MS
        val recent = snap.payments.filter { it.at >= since && !it.needsAnswer && !it.conflict }
        var unsure = 0
        // Feature 3: budget alerts due now. One that belongs to a payment we're about to report is merged into that notification.
        val budgetPass = com.yourteam.expensetracker.notifications.BudgetAlerts.start(context, snap)
        recent.forEach { p ->
            val facts = PaymentSnapshot.factsOf(p)
            val guess = guessForNew(facts, p.key)
            if (guess.category == null) {
                unsure++
                com.yourteam.expensetracker.notifications.CategoryPrompt.askToConfirm(context, p.key, facts, guess)
            } else if ((p.sms != null || p.chat != null) && p.at >= System.currentTimeMillis() - 10 * 60_000L) {
                // Found by a bank SMS or app alert (not typed in by the user): say it was saved.
                val source = p.chat?.source ?: "bank SMS"
                val top = Categories.parentOf(guess.category, customCategories.value)
                com.yourteam.expensetracker.notifications.CategoryPrompt.notifySaved(
                    context, p.key, facts, guess.category, source,
                    budgetLine = budgetPass?.let { pass -> { pass.takeMergedLine(top, p.key) } },
                )
            }
        }
        budgetPass?.finish() // the rest go out on their own
        // 3. Money received: same rule. Filed when sure; asked once when not.
        val memory = CategoryMemory(categoryChoices.value)
        snap.received.filter { it.at >= since }.forEach { c ->
            val guess = Categoriser.decideIncome(c.key, c.kind, c.from, memory)
            if (guess.category == null) {
                unsure++
                com.yourteam.expensetracker.notifications.CategoryPrompt.askIncome(context, c, guess)
            } else if (c.at >= System.currentTimeMillis() - 10 * 60_000L) {
                com.yourteam.expensetracker.notifications.CategoryPrompt.notifyIncomeSaved(context, c, guess.category)
            }
        }
        if (com.yourteam.expensetracker.BuildConfig.TEST_BUILD) android.util.Log.d("AiCategoriser", "recent=${recent.size} received=${snap.received.count { it.at >= since }} unsure=$unsure")
    }

    /**
     * Onboarding ob3b: asks the AI about every payee in the imported inbox that rules couldn't place, not just 10,
     * [AiCategoriser.MAX_PER_RUN] at a time until none are left. [onProgress] gets (asked, total) payees.
     * Only "Sure" answers get filed (Categoriser); the rest keep the AI's picks as suggestions. Never for Sample data.
     */
    suspend fun askAiForAllPast(onProgress: (asked: Int, total: Int) -> Unit) = kotlinx.coroutines.withContext(Dispatchers.IO) {
        autoLock.withLock { askAiForAllPastLocked(onProgress) }
    }

    private suspend fun askAiForAllPastLocked(onProgress: (asked: Int, total: Int) -> Unit) {
        if (!AiCategoriser.enabled || _sampleMode.value) return
        val snap = snapshot.value ?: return
        fun todo() = AiCategoriser.pick(
            snap.payments.map { PaymentSnapshot.factsOf(it) to guessForNew(PaymentSnapshot.factsOf(it), it.key) },
            haveHint = { it in aiHints.value },
            tried = aiTried,
        )
        val total = snap.payments.asSequence()
            .map { PaymentSnapshot.factsOf(it) to guessForNew(PaymentSnapshot.factsOf(it), it.key) }
            .filter { (_, g) -> g.confidence != Confidence.HIGH }
            .mapNotNull { (f, _) -> CategoryMemory.payeeKeys(f.payee, f.vpa).firstOrNull() }
            .filter { it !in aiHints.value && it !in aiTried }
            .distinct().count()
        var asked = 0
        onProgress(0, total)
        while (true) {
            val batch = todo()
            if (batch.isEmpty()) break
            val past = categoryChoices.value.filterKeys { it.startsWith("payee:") }
                .map { (k, v) -> k.removePrefix("payee:") to v }.take(20)
            kotlinx.coroutines.coroutineScope {
                batch.map { (key, facts) ->
                    aiTried += key
                    async { AiCategoriser.ask(AiRequest(key, facts, snap.expenseChoices, past))?.let { saveAiHint(key, it) } }
                }.forEach { it.await(); asked++; onProgress(minOf(asked, total), total) }
            }
        }
    }

    init { startAutoCategorise() }

    /** Test builds only: how the parser read SMS from the last 3 hours, with the text masked. */
    private fun logRecentForDebug(sms: List<SmsMessage>) {
        if (!com.yourteam.expensetracker.BuildConfig.TEST_BUILD) return
        val since = System.currentTimeMillis() - 3 * 60 * 60 * 1000L
        sms.filter { it.dateMillis >= since }.forEach { m ->
            android.util.Log.d(
                "SmsDebug",
                "from=${m.address} kept=${SenderFilter.keep(m)} at=${java.text.SimpleDateFormat("HH:mm").format(m.dateMillis)} " +
                    "read=${com.yourteam.expensetracker.parser.SmsAmountParser.direction(m.body)} " +
                    "masked: ${com.yourteam.expensetracker.parser.SmsMasker.masked(m.body)}",
            )
        }
    }

    private fun realSnapshot(): Flow<PaymentSnapshot?> = combine(
        bankSms,
        db.upiNotificationDao().observeAll(),
        db.screenPaymentDao().observeAll(),
        db.pendingPaymentDao().observeAll(),
        testSms.messages,
    ) { inbox, notes, screens, pending, tests ->
        inbox ?: return@combine null
        // Test SMS (test builds only) go through the same filter as the inbox.
        val sms = if (com.yourteam.expensetracker.BuildConfig.TEST_BUILD && tests.isNotEmpty())
            (inbox + SenderFilter.filter(tests)).sortedByDescending { it.dateMillis } else inbox
        if (com.yourteam.expensetracker.BuildConfig.TEST_BUILD) {
            // Counts only: how many credits each source gave, and app alerts that look re-posted.
            val smsIn = sms.mapNotNull(PaymentInputs::credit)
            val appIn = notes.mapNotNull(PaymentInputs::credit).sortedBy { it.at }
            val reposts = appIn.zipWithNext().count { (a, b) -> a.paise == b.paise && a.from == b.from && b.at - a.at < 10 * 60_000 }
            val week = System.currentTimeMillis() - 7 * 86_400_000L
            android.util.Log.d(
                "SmsDebug",
                "credits sms=${smsIn.size} app=${appIn.size} (7d: sms=${smsIn.count { it.at > week }} app=${appIn.count { it.at > week }}) " +
                    "repostLike=$reposts gapsSec=${appIn.zipWithNext().filter { (a, b) -> a.paise == b.paise && a.from == b.from && b.at - a.at < 10 * 60_000 }.map { (a, b) -> (b.at - a.at) / 1000 }} " +
                    "sameRowText=${notes.groupBy { it.packageName + it.title + it.text }.values.count { it.size > 1 }} merged=${Received.merge(smsIn, appIn).size} notesSaved=${notes.size}",
            )
        }
        PaymentSnapshot(
            bankSms = sms,
            notifications = notes,
            payments = Reconciler.reconcile(
                started = pending.mapNotNull(PaymentInputs::started),
                // GPay chat lines plus "Paid ₹N to X" alerts from any payment app (PhonePe, Navi, Paytm, …).
                chats = screens.mapNotNull(PaymentInputs::chat) + notes.mapNotNull(PaymentInputs::paid),
                debits = sms.mapNotNull(PaymentInputs::debit),
            ),
            received = receivedWithManual(
                sms.mapNotNull(PaymentInputs::credit),
                notes.mapNotNull(PaymentInputs::credit),
                pending.mapNotNull(PaymentInputs::manualCredit),
            ),
        )
    }

    /**
     * Money received from every source. Money in added by hand to a bank account merges with the
     * bank's credit SMS like an app alert would (same amount, close in time), so it counts once;
     * cash never merges.
     */
    private fun receivedWithManual(sms: List<BankCredit>, apps: List<BankCredit>, manual: List<BankCredit>): List<BankCredit> {
        val (cash, bank) = manual.partition { it.source == BankCredit.SOURCE_CASH }
        return (Received.merge(sms, apps + bank) + cash).sortedByDescending { it.at }
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
                bankSms.value = SenderFilter.filter(reader.readInbox(IMPORT_DAYS))
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
                if (loadedWithPermission) reader.readInbox(IMPORT_DAYS).also(::logRecentForDebug).let(SenderFilter::filter) else emptyList()
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
    fun addManual(paise: Long, payee: String, at: Long, category: String?, moneyIn: Boolean = false, cash: Boolean = false): () -> Unit {
        if (moneyIn) return addManualIn(paise, payee, at, category, cash)
        val name = payee.trim()
        fun paymentFor(id: Long) = StartedPayment(id, paise, name, at, UserAnswer.YES, manual = true, app = if (cash) StartedPayment.CASH else PendingPayment.APP_MANUAL)
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
                    appName = if (cash) PendingPayment.APP_MANUAL_CASH else PendingPayment.APP_MANUAL,
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

    /** Money in typed in by hand ([cash] = received in cash). Undo removes it again. */
    private fun addManualIn(paise: Long, payee: String, at: Long, category: String?, cash: Boolean): () -> Unit {
        val name = payee.trim().takeIf(String::isNotEmpty)
        val source = if (cash) BankCredit.SOURCE_CASH else BankCredit.SOURCE_MANUAL
        fun creditFor(id: Long) = BankCredit(id, paise, at, name, refund = false, source = source)

        if (_sampleMode.value) {
            val id = nextSampleId--
            sampleManualIn.update { it + creditFor(id) }
            val undoCategory = category?.let { setIncomeCategory(creditFor(id), it) }
            return {
                sampleManualIn.update { list -> list.filterNot { it.smsId == id } }
                undoCategory?.invoke()
            }
        }

        val dao = db.pendingPaymentDao()
        var insertedId: Long? = null
        var undoCategory: (() -> Unit)? = null
        val save = scope.launch {
            val id = dao.insert(
                PendingPayment(
                    dedupeKey = "manual-in-${java.util.UUID.randomUUID()}",
                    appName = if (cash) PendingPayment.APP_MANUAL_IN_CASH else PendingPayment.APP_MANUAL_IN,
                    amountText = "${paise / 100}.${(paise % 100).toString().padStart(2, '0')}",
                    payee = name.orEmpty(),
                    detectedAt = at,
                    status = PendingPayment.STATUS_USER_CONFIRMED,
                ),
            )
            insertedId = id
            undoCategory = category?.let { setIncomeCategory(creditFor(id), it) }
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
     * Scan & Pay (M7): saves the payment as started *before* the UPI app opens, so it's never lost
     * if the user goes Home instead of coming back. [category] (optional) becomes the payee's default.
     * Returns the started payment's id.
     */
    suspend fun startScanPayment(payee: String, vpa: String, amountText: String, category: String?, appName: String): Long {
        val id = db.pendingPaymentDao().insert(
            PendingPayment(
                dedupeKey = "scan-${java.util.UUID.randomUUID()}",
                appName = appName, // the UPI app they paid with: shown on the payment card
                amountText = amountText,
                payee = payee,
                detectedAt = System.currentTimeMillis(),
            ),
        )
        category?.let { setPayeeDefault(PaymentFacts(payee, vpa), it) }
        return id
    }

    /** True once a bank SMS, app alert or the user's answer has settled started payment [id]. */
    suspend fun isSettled(id: Long): Boolean {
        val status = db.pendingPaymentDao().get(id)?.status ?: return true
        if (status != PendingPayment.STATUS_PENDING) return true
        return snapshot.value?.payments?.firstOrNull { it.key == "started-$id" }?.let { !it.needsAnswer } ?: false
    }

    /**
     * "Yes, paid" / "No" / "It was paid" from any screen.
     * Returns an undo that restores the previous answer (FEEDBACK.md §5).
     */
    fun answer(
        pendingId: Long,
        yes: Boolean,
        via: com.yourteam.expensetracker.metrics.CaptureLog.Via = com.yourteam.expensetracker.metrics.CaptureLog.Via.APP,
    ): () -> Unit {
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
            com.yourteam.expensetracker.metrics.CaptureLog.answered(context, pendingId, via)
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
        private const val KEY_CUSTOM = "custom"
        private const val KEY_PAST_AI_DONE = "past_ai_backfill_done"
        private const val KEY_CUSTOM_ORDER = "custom_order"
        private const val KEY_CHOICES_VERSION = "__version"
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
