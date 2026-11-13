package com.yourteam.expensetracker.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.yourteam.expensetracker.BuildConfig
import com.yourteam.expensetracker.MainActivity
import com.yourteam.expensetracker.R
import com.yourteam.expensetracker.categoriser.CategoryGuess
import com.yourteam.expensetracker.categoriser.CategoryMemory
import com.yourteam.expensetracker.categoriser.PaymentFacts
import com.yourteam.expensetracker.data.PaymentRepository
import com.yourteam.expensetracker.insights.BankCredit
import com.yourteam.expensetracker.insights.Money
import com.yourteam.expensetracker.sms.SenderFilter
import com.yourteam.expensetracker.sms.SmsMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Feature 1 Flow 2 / Flow 3. Money recorded from any source goes through
 * [PaymentRepository.autoCategoriseNow]; only payments still under 30% come here, and we ask the
 * user to confirm our best guess ("Yes", the next guess, or "Something else").
 * Payments from GPay / PhonePe get their category chips in the "did it go through?" pop-up
 * instead. Ignoring either is fine: the payment waits in Review under "need a category". No
 * daily reminders. The same payee and amount is asked about once. The SMS text is never stored
 * or logged (AGENTS.md rule 3).
 */
object CategoryPrompt {
    private const val CHANNEL_ASK = "category_questions"
    private const val CHANNEL_DAILY = "category_daily_summary"
    private const val CHANNEL_SAVED = "payments_saved"
    const val ACTION_PICK = "com.yourteam.expensetracker.PICK_CATEGORY"
    /** Only payments from the last 30 minutes are asked about right away; older ones wait for the daily summary. */
    const val RECENT_MS = 30 * 60_000L
    const val EXTRA_KEY = "payment_key"
    /** True on the "money received" question: the key is a credit's. */
    const val EXTRA_INCOME = "income"
    const val EXTRA_PAYEE = "payee"
    const val EXTRA_VPA = "vpa"
    const val EXTRA_CATEGORY = "category"
    const val EXTRA_NOTIFICATION = "notification_id"
    /** MainActivity extra: which tab to open ("review"). */
    const val EXTRA_OPEN = "open"
    private const val DAILY_ID = 30_000
    private const val PREFS = "category_prompt"
    private const val KEY_ASKED = "asked"

    /** A new bank SMS: load it, then the usual pass categorises it (and asks only if unsure). */
    suspend fun onNewSms(context: Context, sms: List<SmsMessage>) {
        if (sms.none(SenderFilter::keep)) return
        val repo = PaymentRepository.get(context)
        repo.refresh() // live capture: the new payment shows in the app without a pull
        withTimeoutOrNull(20_000) {
            delay(200)
            repo.refreshing.first { !it }
            delay(1_000) // let the payment list pick up the new SMS
            repo.autoCategoriseNow()
        }
    }

    /** Asks the user to confirm our best guess for a payment we couldn't place (under 30%). */
    fun askToConfirm(context: Context, paymentKey: String, facts: PaymentFacts, guess: CategoryGuess) {
        val fingerprint = fingerprint(facts, paymentKey)
        if (wasAsked(context, fingerprint)) return
        if (!PaymentCheck.canNotify(context)) { debugLog("can't notify"); return } // Review still has it
        ensureChannels(context)
        val id = 20_000 + (paymentKey.hashCode() and 0x7fffffff) % 9_000
        val amount = Money.formatWhole(facts.paise)
        // Not sure (three tiers): ask with no guess. An instruction-like payee has no suggestions at all.
        val best = guess.suggestions.firstOrNull()?.takeIf { guess.confidence != com.yourteam.expensetracker.categoriser.Confidence.LOW }
        val title = facts.payee?.let { "$amount paid to $it" } ?: "$amount paid"
        val text = "What was it for?" + (best?.let { " Looks like $it." } ?: "") + (facts.payee?.let { " We'll remember it for $it." } ?: "")
        val builder = NotificationCompat.Builder(context, CHANNEL_ASK)
            .setSmallIcon(R.drawable.ic_stat_payment)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openReview(context, id))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        listOf(best to best, guess.suggestions.getOrNull(1)?.takeIf { best != null }.let { it to it }).forEachIndexed { i, (label, category) ->
            if (label == null || category == null) return@forEachIndexed
            val intent = Intent(context, CategoryActionReceiver::class.java).setAction(ACTION_PICK)
                .putExtra(EXTRA_KEY, paymentKey).putExtra(EXTRA_PAYEE, facts.payee).putExtra(EXTRA_VPA, facts.vpa)
                .putExtra(EXTRA_CATEGORY, category).putExtra(EXTRA_NOTIFICATION, id)
            builder.addAction(0, label, PendingIntent.getBroadcast(context, id * 4 + i, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        builder.addAction(0, if (best == null) "Choose" else "Other", openReview(context, id * 4 + 3))
        @Suppress("MissingPermission") // checked in canNotify()
        NotificationManagerCompat.from(context).notify(id, builder.build())
        markAsked(context, fingerprint) // only once it's really been shown
        debugLog("asked to confirm")
    }

    /** Money received we couldn't place (no sender name): ask once, with our best guess to confirm. */
    fun askIncome(context: Context, credit: BankCredit, guess: CategoryGuess) {
        val fingerprint = "in|${credit.key}".hashCode().toString()
        if (wasAsked(context, fingerprint)) return
        if (!PaymentCheck.canNotify(context)) return // it shows in the app without a category
        ensureChannels(context)
        val id = 20_000 + (credit.key.hashCode() and 0x7fffffff) % 9_000
        val best = guess.suggestions.first()
        val title = "${Money.formatWhole(credit.paise)} received" + (credit.from?.let { " from $it" } ?: "")
        val builder = NotificationCompat.Builder(context, CHANNEL_ASK)
            .setSmallIcon(R.drawable.ic_stat_payment)
            .setContentTitle(title)
            .setContentText("What was it for? Looks like $best.")
            .setContentIntent(openReview(context, id))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        listOfNotNull(best to best, guess.suggestions.getOrNull(1)?.let { it to it }, guess.suggestions.getOrNull(2)?.let { it to it })
            .forEachIndexed { i, (label, category) ->
                val intent = Intent(context, CategoryActionReceiver::class.java).setAction(ACTION_PICK)
                    .putExtra(EXTRA_KEY, credit.key).putExtra(EXTRA_INCOME, true)
                    .putExtra(EXTRA_CATEGORY, category).putExtra(EXTRA_NOTIFICATION, id)
                builder.addAction(0, label, PendingIntent.getBroadcast(context, id * 4 + i, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            }
        @Suppress("MissingPermission") // checked in canNotify()
        NotificationManagerCompat.from(context).notify(id, builder.build())
        markAsked(context, fingerprint)
        debugLog("asked about money received")
    }

    /**
     * A payment we found and filed on our own (30% sure or more). One quiet notification so the
     * user always knows it was recorded: "₹20 paid to Vindya Canteen" / "Saved under Food · from Navi alert".
     * Never sent twice for the same payment, nor after we already asked about it.
     */
    fun notifySaved(
        context: Context,
        paymentKey: String,
        facts: PaymentFacts,
        category: String,
        source: String,
        /** Feature 3: the budget alert for this same payment ("90% of your Food & Dining budget used · ₹500 left"). */
        budgetLine: (() -> String?)? = null,
    ): Boolean {
        val fingerprint = fingerprint(facts, paymentKey)
        if (wasAsked(context, fingerprint)) return false
        if (!PaymentCheck.canNotify(context)) return false
        ensureChannels(context)
        val id = 20_000 + (paymentKey.hashCode() and 0x7fffffff) % 9_000
        val amount = Money.formatWhole(facts.paise)
        val budget = budgetLine?.invoke()
        if (budget != null) {
            // One notification for both (bd9): "₹350 at Zomato → Food & Dining".
            postSaved(
                context, id,
                title = (facts.payee?.let { "$amount at $it" } ?: amount) + " → $category",
                text = budget,
                looksRight = looksRight(context, id, paymentKey, facts, category),
            )
        } else {
            postSaved(
                context, id,
                title = facts.payee?.let { "$amount paid to $it" } ?: "$amount paid",
                text = "Saved under $category · from $source",
                looksRight = looksRight(context, id, paymentKey, facts, category),
            )
        }
        markAsked(context, fingerprint)
        debugLog("told saved")
        return true
    }

    /** "Looks right": keeps the category we picked, the same as choosing it in the app. */
    private fun looksRight(context: Context, id: Int, paymentKey: String, facts: PaymentFacts, category: String): PendingIntent {
        val intent = Intent(context, CategoryActionReceiver::class.java).setAction(ACTION_PICK)
            .putExtra(EXTRA_KEY, paymentKey).putExtra(EXTRA_PAYEE, facts.payee).putExtra(EXTRA_VPA, facts.vpa)
            .putExtra(EXTRA_CATEGORY, category).putExtra(EXTRA_NOTIFICATION, id)
        return PendingIntent.getBroadcast(context, id * 4 + 2, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** Money received that we filed on our own: "₹20 received from Atharva" / "Saved under Transfers · from GPay alert". */
    fun notifyIncomeSaved(context: Context, credit: BankCredit, category: String) {
        val fingerprint = "in|${credit.key}".hashCode().toString()
        if (wasAsked(context, fingerprint)) return
        if (!PaymentCheck.canNotify(context)) return
        ensureChannels(context)
        val id = 20_000 + (credit.key.hashCode() and 0x7fffffff) % 9_000
        val source = if (credit.source == BankCredit.SOURCE_SMS) "bank SMS" else "${credit.source} alert"
        postSaved(
            context, id,
            title = "${Money.formatWhole(credit.paise)} received" + (credit.from?.let { " from $it" } ?: ""),
            text = "Saved under $category · from $source",
        )
        markAsked(context, fingerprint)
    }

    private fun postSaved(context: Context, id: Int, title: String, text: String, looksRight: PendingIntent? = null) {
        val n = NotificationCompat.Builder(context, CHANNEL_SAVED)
            .setSmallIcon(R.drawable.ic_stat_payment)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openReview(context, id))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .apply { if (looksRight != null) addAction(0, "Looks right", looksRight) }
            .addAction(0, "Change", openReview(context, id * 4 + 3))
            .build()
        @Suppress("MissingPermission") // checked in canNotify()
        NotificationManagerCompat.from(context).notify(id, n)
    }

    /** Daily reminders were dropped: we only ask when a payment needs the user. Removes the old job. */
    fun cancelDaily(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork("daily-category-summary")
        NotificationManagerCompat.from(context).cancel(DAILY_ID)
        context.getSystemService(NotificationManager::class.java).deleteNotificationChannel(CHANNEL_DAILY)
    }

    private fun openReview(context: Context, requestCode: Int) = PendingIntent.getActivity(
        context, requestCode,
        Intent(context, MainActivity::class.java).putExtra(EXTRA_OPEN, "review")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Same payee and amount = same question; unnamed payments use their own key. No names stored: a hash. */
    private fun fingerprint(facts: PaymentFacts, paymentKey: String): String =
        (CategoryMemory.payeeKeys(facts.payee, facts.vpa).firstOrNull()?.let { "$it|${facts.paise}" } ?: paymentKey).hashCode().toString()

    /** The pop-up showed category choices for this payment: don't also send a notification. */
    fun markAskedFor(context: Context, facts: PaymentFacts, paymentKey: String) = markAsked(context, fingerprint(facts, paymentKey))

    private fun asked(context: Context): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ASKED, "").orEmpty().split('\n').filter(String::isNotEmpty)

    private fun wasAsked(context: Context, fingerprint: String) = fingerprint in asked(context)

    /** Keeps the last 200 fingerprints. */
    private fun markAsked(context: Context, fingerprint: String) {
        val list = asked(context)
        if (fingerprint in list) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ASKED, (list + fingerprint).takeLast(200).joinToString("\n")).apply()
    }

    private fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ASK, "Category questions", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Asks what a new payment was for when we can't tell."
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SAVED, "Payments saved", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Tells you each payment we found and where we filed it."
            },
        )
    }

    internal fun debugLog(message: String) {
        if (BuildConfig.TEST_BUILD) android.util.Log.d("CategoryPrompt", message) // counts and outcomes only
    }
}

/** New SMS: hands bank payment messages to [CategoryPrompt]. */
class SmsArrivalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        // Long messages arrive in parts; join them per sender.
        val messages = parts.groupBy { it.originatingAddress.orEmpty() }.map { (address, ps) ->
            SmsMessage(-1, address, ps.joinToString("") { it.messageBody.orEmpty() }, ps.first().timestampMillis)
        }
        val result = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                delay(4_000) // the SMS app saves it to the inbox first, so the refresh can read it
                CategoryPrompt.onNewSms(context, messages)
            } finally {
                result.finish()
            }
        }
    }
}

/** A button on the "was it X?" notification: saves it for the payment (and the payee, if named). */
class CategoryActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CategoryPrompt.ACTION_PICK) return
        val category = intent.getStringExtra(CategoryPrompt.EXTRA_CATEGORY) ?: return
        val key = intent.getStringExtra(CategoryPrompt.EXTRA_KEY).orEmpty()
        val facts = PaymentFacts(intent.getStringExtra(CategoryPrompt.EXTRA_PAYEE), intent.getStringExtra(CategoryPrompt.EXTRA_VPA))
        NotificationManagerCompat.from(context).cancel(intent.getIntExtra(CategoryPrompt.EXTRA_NOTIFICATION, 0))
        val result = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val repo = PaymentRepository.get(context)
                if (intent.getBooleanExtra(CategoryPrompt.EXTRA_INCOME, false)) repo.categoriseIncomeFromPrompt(key, category)
                else repo.categoriseFromPrompt(key, facts, category)
                CategoryPrompt.debugLog("picked from notification")
            } finally {
                result.finish()
            }
        }
    }
}

/** Kept only so an already-scheduled daily job from an older version finishes quietly. */
class DailyCategoryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = Result.success()
}
