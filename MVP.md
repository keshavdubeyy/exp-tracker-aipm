# MVP.md — Expense Tracker (Android)

The single source of truth for what v1 includes. If it isn't in **Section 2**, it isn't MVP.

**Product in one line:** an Android app that captures your online payments automatically (bank SMS + Scan & Pay), sorts them into categories with rules and AI, flags anything that needs a check, and tells you where your money went.

**Progress:** 0 / 24 done  ← update this line whenever a box is ticked

---

## 0. Rules for agents (Antigravity, Claude Code) and teammates

Copy these lines into `AGENTS.md` / `CLAUDE.md` as well.

1. **Before starting any task, read MVP.md.** Find the item ID (M1–M22) the task belongs to and name it in your plan.
2. **If the task matches no item in Section 2, or matches Section 3, stop before writing code** and reply:
   > ⚠️ **Outside MVP:** "<task>" is not in MVP.md (closest: <item or "none">). Options: (a) skip it, (b) add it to the Parking lot, (c) do it anyway. Which one?
   Do nothing else until the person answers. If they choose (b), add it to Section 5 with today's date.
3. **When an item's acceptance checks all pass, tick its box** `[x]`, update the Progress line, and add a line to the Changelog (Section 6).
4. **Never tick an item that is only partly done.** Write what's missing under it as `- TODO: …`.
5. Follow `DESIGN.md` for look, `FEEDBACK.md` for confirmations and haptics, `PAYMENT_CARD_HANDOFF.md` for payment cards.

---

## 1. MVP boundaries

| In | Out |
|---|---|
| Android, one user, data stored on the phone | iOS, web, multiple users, cloud sync |
| Dark theme | Light theme |
| Online payments from bank SMS + Scan & Pay | Bank email, Account Aggregator, statement upload (next) |
| AI only picks categories and phrases answers | AI computing any number, financial advice |
| Install by APK for test users | Play Store release |

**Ship gate (from Activity 3):** grounded answers 100% (no invented numbers) · zero auto-fails · category accuracy ≥ 85% · over-asks ≤ 30% on the golden set.

---

## 2. MVP items

### A. Capture

- [ ] **M1 · SMS permission + onboarding**
  - Explains "We only read bank payment messages" before asking.
  - Denied → Home shows a persistent "SMS access is off · Turn on" row.
- [ ] **M2 · Inbox import (first run)**
  - Imports the last 90 days of bank SMS. *(decision: 90 days — change here if needed)*
  - Shows progress, then "Imported N payments".
- [ ] **M3 · Live capture**
  - New bank SMS becomes a payment within seconds (BroadcastReceiver).
  - Inbox re-scanned on every app open to catch anything missed.
- [ ] **M4 · Sender filter + ignore list**
  - Only bank/payment sender IDs. Ignores OTP, promotions, balance alerts, reminders, failed/declined, UPI collect requests.
- [ ] **M5 · Parser**
  - Extracts amount (paise), date (from SMS text), payee, VPA, account/card last 4, reference no., mode.
  - Kinds: SPEND, REFUND, REVERSAL, SELF_TRANSFER, CARD_BILL_PAYMENT, SAVINGS, ATM_CASH, INCOME, UPI_LITE_TOPUP, UPI_LITE_SUMMARY.
  - No amount or no reference → not recorded; counted as "unreadable".
  - Unit tests with ≥ 30 masked real SMS from the team.
- [ ] **M6 · De-duplication**
  - Same reference number = one payment across all sources.
- [ ] **M7 · Scan & Pay**
  - Scan a merchant QR → amount + category → hand off to the UPI app → record on success with UPI ref.
  - Failure or cancel → nothing recorded.
  - No result from the UPI app → payment saved as **Pending**; "Did this payment go through?" sheet with only "Not paid" / "Yes, paid". A matching bank SMS later confirms it automatically.
  - TODO: built 13 Nov (scanner, amount + category, hand-off, SUCCESS/FAILURE/no-answer, ask on return, home shortcut); not yet tested on a phone with GPay / PhonePe. UPI ref not stored yet.
  - 13 Nov: the pay sheet now lists installed UPI apps (last used first) and opens the chosen one straight on its payment screen; one result modal (Saved / Didn't go through / Did it go through?) on return from anywhere. Still needs phone testing.

### B. Categorise

- [ ] **M8 · 17 categories + rule categoriser**
  - Merchant map + shop-type words. Non-spending kinds never get a spending category.
- [ ] **M9 · Payee memory**
  - "Always use for <payee>" applies to all future payments to that payee.
- [ ] **M10 · AI categoriser (backend)**
  - Only for payments rules can't label. Returns category + confident/unsure + 2 suggestions.
  - Sends payee name, VPA, amount, time, user's own note, past confirmed categories. Never the full SMS.
- [ ] **M11 · Add category sheet**
  - Per canvas v4 · Tap "Add category": suggested chips, all categories, "Always use" toggle (on), Save.

### C. Needs check

- [ ] **M12 · Needs-check rules**
  - SPEND with no category.
  - Possible duplicate: same payee + same amount within 5 min, different reference.
  - Balance gap: SMS balances don't add up → "₹X to check" for that account.
  - UPI Lite daily summary not yet tagged.
- [ ] **M13 · Needs check sheet**
  - One issue → open that fix directly. Two or more → checklist sheet.
  - "Same payment / Paid twice", "Add category", "Looks fine, mark as checked".

### D. Screens

- [ ] **M14 · Home (Option A)**
  - Profile icon (top left) → accounts, sources, settings.
  - Bottom navigation bar: Home · Insights · Budgets · Review (was top pill tabs; changed 2026-11-13).
  - Hero: spent this week + change vs last week in words.
  - "To check · N · ₹X" row (hidden when 0).
  - Payments (latest 3) and Categories carousel.
  - Bottom right: + (add payment) and Scan & Pay buttons. The "Ask about your spending" bar was removed 2026-11-13.
  - TODO: Budgets tab shows a placeholder until its screen is built.
- [ ] **M15 · Payments list**
  - Today / Yesterday / date headers, 4 card types, red "Needs check", bottom CTA "Add categories to N payments" (hidden at 0).
- [ ] **M16 · Insights**
  - Week / Month toggle, spend by category (largest first), "Not counted as spending" card (transfers, card bill, savings, ATM).
- [ ] **M17 · Ask**
  - Code computes the numbers; AI only phrases them. Corrects wrong numbers in the question. Mentions unchecked payments instead of adding them.
- [ ] **M18 · Profile menu**
  - Accounts found (bank, last 4), source status (SMS on/off, last sync), "Delete all my data", app version.

### E. Platform and quality

- [ ] **M19 · Backend**
  - `/categorise` and `/ask`; LLM key only on the server; request logging without personal data.
- [ ] **M20 · Evals**
  - Golden set ≥ 30 real masked examples; script runs code checks + LLM judge; 3 logged iterations.
- [ ] **M21 · Privacy**
  - Only payment SMS are processed; full SMS never leaves the phone; "Delete all my data" wipes everything.
- [ ] **M22 · Feedback + haptics**
  - Every event in `FEEDBACK.md` implemented as specified.
- [ ] **M23 · Extra sources: GPay reader + UPI app alerts** *(added 10 Nov 2026, was D1/D4)*
  - Optional; offered in onboarding (ONBOARDING.md ob5–ob7) and Profile › Sources.
  - GPay reader: accessibility service limited to the GPay and PhonePe packages *(PhonePe added 12 Nov 2026, user decision)*; never reads password fields; asks "Did it go through?" after the PIN screen closes. Chat-line capture is GPay only.
  - Paytm blocks payments while any non-system accessibility service is on (its own fraud check). Not worked around; Paytm payments rely on bank SMS + Paytm notifications.
  - TODO: confirm PhonePe's PIN screen is recognised on a real payment (masked log `GPayScan`).
  - UPI app alerts: notification listener, payment-app allow-list only.
  - Nothing from either leaves the phone.

### F. Measuring success

- [ ] **M24 · Developer dashboard** *(added 13 Nov 2026, user decision: "do it anyway")*
  - **Phone:** `metrics/` package counts events locally (counts only: never SMS text, payee names or amounts). Tester ID = one-way hash of Android's per-app ID (same across reinstalls, so one phone = one tester), not linked to phone number or account. Hidden Developer screen (tap app version in Profile 7×) shows this phone's counts.
  - **Upload:** once a day (WorkManager), tester builds only, after the tester agrees in onboarding. Retries when offline.
  - **Server:** our own backend (same as M19), Python + FastAPI + SQLite. `POST /metrics` (rejects any unknown field), `POST /evals` (golden-set results from M20). Tester key + rate limit; no IP logging.
  - **Dashboard:** `GET /dashboard`, team password. One card per criterion below: target, current value, pass/fail, number of testers; trend by day and app version; table per tester; latest eval run. All values computed in plain code, never AI.
  - **Privacy:** TESTING.md tester note and M21 updated to say anonymous counts are sent.
  - **Success criteria per feature** *(draft targets, team to confirm)*. Source: 📱 = tester phones, 🧪 = golden set (M20).

    | Feature | Criterion | Target | Source |
    |---|---|---|---|
    | M1 Onboarding | Testers who start setup and reach Home | ≥ 80% | 📱 |
    | M1 Onboarding | Testers who allow SMS | ≥ 90% | 📱 |
    | M1 Onboarding | Median time from Get started to Home | ≤ 2 min | 📱 |
    | M2–M5 Capture | Bank payment SMS that couldn't be read | ≤ 5% | 📱 |
    | M5 Parser | Amount + direction correct | 100% | 🧪 |
    | M6 De-duplication | Payments the tester marks "Paid twice"/"Same payment" | ≤ 2% | 📱 |
    | M7 / M23 Did it go through? | Prompts answered | ≥ 80% | 📱 |
    | M7 / M23 Did it go through? | Payments still Pending after 24 h | ≤ 10% | 📱 |
    | M8–M11 Categorise | Categories kept (not changed by the tester) | ≥ 85% | 📱 + 🧪 |
    | M8–M11 Categorise | Spending with no category | ≤ 10% | 📱 |
    | M12–M13 Needs check | Over-asks ("Looks fine" / "Yes, paid") | ≤ 30% | 📱 + 🧪 |
    | M14–M16 Screens | Testers opening the app on ≥ 3 days a week | ≥ 60% | 📱 |
    | M16 Insights | Testers opening Insights each week | ≥ 50% | 📱 |
    | M17 Ask | Answers with a number the code didn't compute | 0 | 📱 + 🧪 |
    | M17 Ask | Auto-fails (advice, leaked SMS text, broken rule) | 0 | 🧪 |
    | M21 Privacy | Uploads rejected for unexpected fields | 0 | Server |

  - 13 Nov: built. Phone: `metrics/` (random install ID, open/Insights days, onboarding time, daily WorkManager upload after a one-time "Share counts" question; Developer screen shows what's sent, Send now, on/off). Server: `server/metrics.py` (`POST /metrics` counts-only with unknown fields rejected and counted, `GET /dashboard` + `/dashboard.json` behind `DASHBOARD_PASSWORD`; criteria, 14-day trend, per tester, per version). Budgets (M25) criteria added.
  - TODO: deploy the server with a persistent disk; "Same payment / Paid twice" and over-ask counts once M13 exists; `POST /evals` for M20; team confirms the targets above.

### G. Budgets (Feature 3 spec)

- [ ] **M25 · Budgets** *(added 13 Nov 2026, Feature 3 spec; team decision, no AI)*
  - Optional income → overall → category limits (top-level only; custom roll into parent). Over-split warns, never blocks.
  - Same counting as Insights (completed only, each payment once); unsorted and no-budget spend count in the overall only; refunds count in the month they arrive.
  - Days left include today; daily amount = left ÷ days left, rounded down; hidden when over.
  - Alerts: 75% in the app; 90%, 100%, over as one notification each per month (highest only); merged with the "saved as" notification for the same payment; raising a limit re-arms.
  - New month copies last month's amounts with Keep / Edit; past months read only.
  - TODO: on-phone pass of every state (bd1–bd14) and a real 90% / over notification.

---

## 3. Not MVP (agents must warn before touching these)

- iOS app, Shortcuts automation
- Bank email forwarding, Gmail access
- Account Aggregator, bank statement upload
- Budgets, spending limits, overspend alerts
- Push notifications or reminders
- Bill splitting with friends
- Investment / net-worth tracking
- Financial advice of any kind
- Moving money (beyond handing off to a UPI app in Scan & Pay)
- Cash expense entry, manual add
- Home-screen widgets, export to Excel/PDF
- Light theme, custom themes
- Cloud backup, login, multiple devices
- Play Store publishing

---

## 4. Next after MVP (in order)

1. Bank email forwarding (covers payments too small for SMS)
2. Manual add + cash
3. Account Aggregator / statement upload for complete history
4. Budgets and alerts

---

## 5. Parking lot

Ideas raised during the build that are outside MVP. Add with date and who raised it.

| Date | Idea | Raised by | Decision |
|---|---|---|---|
| 2026-11-10 | Bank statement upload as a fallback when SMS is denied or 0 payments are found (ONBOARDING.md ob3 empty state, ob8) | ONBOARDING.md | Parked; link hidden in onboarding until built | |

---

## 6. Changelog

| Date | Item | What changed |
|---|---|---|
| 2026-11-12 | M5, M15, M16 | Bug fix: credit SMS that also said "transferred / paid / sent by" were counted as spending. Parser now decides debit vs credit by the first cue in the SMS and reads who sent a credit. Credits are a separate `received` list (never in spending totals), shown as **+₹ · Received** cards with a Received filter on Payments, and as "Received in <month>" by sender on Insights. Also: GPay chat lines "Payment to you ₹N Paid" (money received) were saved as spending to payee "you"; now skipped. UPI app "X paid you ₹N" notifications now count as Received (paired with the bank credit SMS when both arrive: same amount, ≤ 30 min apart). Home's Payments preview lists money received too (hero total unchanged). M23: screen reader + "Did it go through?" pop-up extended to PhonePe (user chose "do it anyway"); accessibility description updated. M14: Home shows a "Received · N · +₹X" summary row under the hero for the chosen dates (tap → Payments filtered to Received); hero stays spending only. Fix: GPay re-posts the same "paid you" alert ~12 s later; copies within 60 s (same app, sender, amount) now count once. M5 not ticked (payee/VPA/ref/kinds still missing). |
| 2026-11-13 | M5, M8–M13, M19, M24 (Feature 1 spec) | Scope now follows the Feature 1 spec (team decision; Outside-MVP prompt skipped). **M5:** bank debit SMS now give payee, UPI ID and method; credits get a kind (salary/refund/interest/cashback). **M8:** 11 spending + 6 income categories (old names migrated), merchant map + shop words, confidence High/Medium/Low (High ≥ 0.85 auto, else "Add category"). **M9:** a pick becomes the payee's default; "Just this one" changes only that payment. Custom categories with optional parent (add/rename/delete with Undo, Profile › Categories). **M10/M19:** `server/` FastAPI `/categorise` (Claude, categories only, no SMS text, counts-only logs; fake mode without a key); app asks ≤ 10 undecided payees per run, never auto-applies. **M11:** sheet with top 3, View all + search, Add custom. **M12/M13:** Review lists payments needing a category; new-SMS notification with 3 category buttons; daily summary ~20:30. **M24 step 1:** Developer screen (tap Version 7×) with the six Feature 1 metrics from this phone. Nothing ticked. |
| 2026-11-13 | M7, M8, M13 | Scan & Pay built (no screen reading): in-app QR scanner → amount + category → hand-off to the user's UPI app → saved on SUCCESS, nothing on FAILURE, one "Did it go through?" sheet on no answer or on return (skipped if SMS/app alert already confirmed). Payment-app alerts from any app (paid and received) now recorded; money received auto-categorised; auto-file at ≥ 30% confidence, else one notification; 8:30 pm summary removed. Not ticked: needs phone testing. |
| 2026-11-13 | M7 | Scan & Pay: pick the UPI app in the sheet (GPay, PhonePe, Navi…), open it straight on the payment screen; one modal after return (saved, failed, or "Did it go through?"); card shows the real app name. Not ticked: needs phone testing. |
| 2026-11-13 | M7, M23 | Payments with no SMS, without accessibility: Usage access sees only when a UPI app's NPCI PIN screen (GetCredential) opens/closes (GPay, PhonePe, Navi, Paytm). No SMS/alert within 90 s → one notification + the same modal in the app (amount, who, category). Screen reader no longer watches GPay (GPay blocks payments while a reader can see it). Not ticked: needs phone testing. |
| 2026-11-13 | M9, M11, M23 | Notifications: one per payment, title = what happened, description = what we did. PIN-screen question now ~2 s after the PIN screen closes (was 90 s), replaced when an SMS/alert arrives. New "Payments saved" notice for payments/credits filed on their own (last 10 min, once each). Category questions reworded ("₹20 paid to X" / "What was it for? Looks like Food."). Not ticked: needs phone testing. |
| 2026-11-13 | M10 | Duplicates (Profile › Duplicates): same person under different names (same UPI ID, initials, short form, word order) and same category ("P2P" / "Person-to-person", plurals, "&"/"and"), found by rules + AI (POST /dedupe, Haiku, suggestions only). Merge = one name everywhere + one saved category; "Not the same" remembered; Undo on both. Not ticked: needs phone testing. |
| 2026-11-13 | M8, M11 | **Three-tier flow (diagram, team decision — reverses the §3.2 "Medium is filed, no question" spec).** Only **Sure (≥ 0.85)** is filed on its own; **Fairly sure (0.3–0.85)** is no longer auto-filed — it stays uncategorised (still in totals) and offers its best guess; **Not sure (< 0.3)** asks with no guess. `Categoriser.guess`/`decideIncome` gate the category at HIGH; income heuristics (person/company/shop) are now Fairly-sure hints, not filed. Categoriser tests updated. Cards now show a ✦ when the app/AI chose the category (F3), "`<Guess>`? Confirm" for Fairly-sure, "Add category" for Not-sure, and a "✦ means AI picked it" legend. Not ticked: flow only half-built (see TODO). |
| 2026-11-13 | M16 (Feature 2 spec) | **Insights rebuilt as Feature 2 (AI-Powered Spending Insights).** New plain-Kotlin engine `insights/Insights.kt` (AGENTS rule 1): Month/Week/Day/Custom periods with fair same-elapsed-days comparison windows (weeks Mon–Sun), category breakdown with whole-% shares (custom categories rolled into their parent, uncategorised kept as its own "Not sorted yet" bucket, counted in the total but never guessed), "Money in" from received (never in spending), and evidence-backed insight candidates — category up/down, biggest-category share, repeat merchant (with "after 8 pm"), weekend-vs-weekday, custom day-concentration — capped at 3, each with a stable id and deterministic phrasing so the screen works with AI off. Data-sufficiency states kept distinct: insufficient history (in8) vs no meaningful pattern (in9) vs single quiet day (in10). New screen: period tabs, date dropdown + stepper sheet + custom calendar with validation (in6), hero + delta chip (chip hidden when the baseline is too small to be meaningful), "Where it went" card (bars, %, All-categories expand), "What changed" cards, loading skeleton (in7). Drill-downs: category detail (in2, "inside this category" sub-breakdown + payments, tap to re-categorise) and insight detail (in3/F4, "how we worked it out" + contributors + evidence + Helpful/Not relevant/Dismiss, persisted, never-show-again with Undo). Sample data enriched with October history + repeat Swiggy + salary credit so the states demo. 11 engine unit tests; full suite green; main screen verified on device. Not ticked: on-device pass of the drill-downs/calendar still pending; AI reason sentences (server `/categorise`-style call) deferred — deterministic phrasing stands in. |
| 2026-11-13 | M25 (Feature 3 spec) | **Budgets built.** Engine `insights/Budgets.kt` (plain Kotlin, 7 unit tests), storage `data/BudgetStore.kt` (on the phone, per month + alerts sent), alerts `notifications/BudgetAlerts.kt` (merged into the "saved as" notification, now with Looks right / Change), screens `ui/screens/budgets/` (bd1–bd8, bd11–bd14) plugged into the Budgets tab of the bottom bar, Home with a budget (bd10: "Left this month", "N to review", "Close to the limit"). New `warning` amber token. Sample data seeds a budget. Not ticked: needs an on-phone pass. |
| 2026-11-13 | M24 | Tester metrics end to end: phone counts → daily upload (consent first, test builds only) → our FastAPI server (SQLite) → password-protected `/dashboard` with every criterion across all testers, trend, per-tester and per-version tables. TESTING.md tester note updated (anonymous counts are sent). Not ticked: server not deployed yet. |

| 2026-11-13 | M2, M8, M10 | **Onboarding: sort past payments (ob3b).** New step after Import: the AI is asked about every payee in the imported inbox the rules couldn't place (not just 10 per run), only "Sure" answers are filed, then the user sees "{sorted} of {total}" with the sorted payees and can **Sort the rest now** (one payee at a time, AI's top 3 + all categories; a pick files all that payee's past payments) or **Skip**. Counts in `insights/PastSort.kt` (unit test `PastSortTest`). Feeds the *History at install* tiles; no server change. ONBOARDING.md and FEEDBACK.md (F35–F37) updated. Not tested on a phone yet; not ticked. |
| 2026-11-13 | M8, M10, M24 | **History at install.** Checked: payments already in the inbox at install are categorised the same way as new ones (rules straight away; payees the rules can't place go to the AI 10 at a time until all are asked; only "Sure" answers are filed, the rest wait in Review). New dashboard group **Smart categorisation › History at install**: Past payments sorted (≥ 90%, proposed), sorted on their own (track), sorted by AI (track). Phone sends `past_pay`, `past_sorted`, `past_sorted_auto`, `past_sorted_ai` (counts only; "past" = dated before the app's first install time); counting in `CategoryMetrics.pastCounts` with a unit test. Server must be deployed before testers get this build (unknown fields are rejected). Not ticked. |
| 2026-11-13 | M24 | **No duplicate testers.** Tester ID was random and stored in app data, so reinstalling or clearing data made a second tester. Now it's a one-way hash of Android's per-app ID (survives reinstall; changes only on factory reset). Phones on older builds send their old random ID once as `replaces`; the server moves those reports to the new ID. Testers duplicated before this can't be linked. Not ticked. |
| 2026-11-13 | M2, M3 | Bug fix: the app read the **whole** SMS inbox (years on some phones), so old payments and credits showed up in totals and per-payee history. Inbox reads now cover the last 90 days only (`SmsReader.readInbox(IMPORT_DAYS)`), as M2 says. Not ticked: the ₹68 lakh income seen on a tester's phone still needs confirming on that phone. |
| 2026-11-13 | M8, M10, M24 | **AI picks are filed (team decision, reverses the 13 Nov three-tier rule for AI).** Once the AI answers for a payee, its first pick becomes the category, confident or not, marked ✦ on cards and Home ("✦ Food & Dining"); tap to change. Rules still need Sure (≥ 0.85) to file on their own; Review now holds only payments with no AI answer (AI off, failed, no payee). Metrics: a payment the AI files after first sight counts as automatic, so a later change is a correction; new `cat_ai_auto` / `cat_ai_auto_kept` and dashboard card **AI categories kept** (≥ 85%). Dashboard totals relabelled "last 90 days". Tests updated. Not ticked. |
| 2026-11-13 | M24 | Dashboard freshness: phones now also upload when the app is opened (at most once an hour), not only once a day. Reports carry the build number (`0.1.0-455954`), so the Testers page shows who hasn't updated. Cards that need the newest build say so. Not ticked. |
- TODO (Feature 1): parent-category totals in Insights; duplicate detection metric (needs M13 "Same payment / Paid twice"); M24 upload + web dashboard; confidence thresholds to be tuned on the golden set (M20).
- TODO (three-tier flow, in progress): **F4** detail sheet (✦ "AI put this in X", how-sure bar, why bullets, Looks right); **F2** Sure "Added to X · Looks right/Change" notification + guess-less Not-sure notification; **F5** best-guess pop-up on open + **F6** scroll list; "AI sorted X of Y · Review" banner; one-line **AI reasons** (server `/categorise` change); **Cat6** 8:30 pm summary restore; reconcile the written Feature 1 §3.2/§3.3 spec with the diagram.
| 2026-11-13 | M14, M17 | Navigation moved from top pill tabs to a bottom navigation bar: Home · Insights · Budgets · Review (Review keeps its count badge). Profile stays as the avatar top left. Removed the "Ask about your spending" bar from Home; + and Scan & Pay now float bottom right. Ask screen is no longer linked from anywhere. Budgets tab is a placeholder until its screen is built. |
| 2026-11-13 | M14 | Home with a budget now leads with a budget card: "Spent in <month> so far", spent amount, bar, "of ₹X budget" · "N days left", and View budgets / View insights buttons (numbers from `BudgetStatus` in insights/). Without a budget, Home keeps the weekly spend hero. Test-build "Sample" bubble moved up so it no longer covers Scan & Pay. |
| 2026-11-13 | M14 | Home's "Payments ›" list replaced by **Transactions**: latest 10 money-out and money-in rows in one card, category icon on the left, date · time, signed amount and a category / Needs review / Needs check chip; All / By category toggle; "See all transactions" → Payments. Money received shows its category (no "Received" label). With a budget, the list covers this month and now includes money received. |
| 2026-11-13 | — (design) | All icons switched to Hugeicons (free Stroke Rounded set, MIT; license in `licenses/`). Generated into `ui/theme/HugeIcons.kt` by `tools/gen_hugeicons.py`; `AppIcons` / `CategoryIcons` now point at them and Material icons are no longer used. DESIGN.md §4.10 updated (1.5 stroke). |
| 2026-11-13 | M16 | Insights redesigned (DESIGN.md §5a): header with dates "vs" the comparison and a refresh button; Monthly / Weekly / Daily / Custom tabs; summary card (total expenses, total income, net cash flow with words, transactions, highest category); category-wise spending card with "₹X · N%" and bars, plus a note for unsorted money; insight cards with a kind chip, "vs ₹X in <dates>", View supporting transactions, and Helpful / Not relevant / Dismiss (with Undo). New in `insights/`: money-received count, `netPaise`, `transactionCount`. Rating code shared between the screen and the insight drill-down. |
| 2026-11-13 | M24 | Team dashboard on Vercel (`expense-tracker-metrics.vercel.app/dashboard`, Neon Postgres; SQLite stays for local runs). Dashboard now grouped by feature, **Smart categorisation** first with 5 metrics: AI suggestion hit rate (≥ 80%), AI first-guess rate (≥ 50%), Categorisation accuracy (≥ 85%), Suggestion hit rate all sources (≥ 80%), Uncategorised payments (≤ 10%). Phone records which step made the guesses at the moment the user picks (the AI answers seconds after a payment is first seen) and uploads `cat_ai_picked/top3/first`. Removed from the dashboard: auto-categorisation rate, repeat-payee rate (still on the Developer screen). Not ticked: needs real tester data. |
| 2026-11-13 | M24 | Developer › **Test an SMS** (test builds): type a made-up bank SMS, see what the real filter and parser read (money out / in, amount, payee, UPI ID, method, kind, or why it's skipped), and **Add payment** into the real data. Test SMS live in the app's own storage (`TestSmsStore`), never in the phone's SMS inbox, are never uploaded or counted in tester metrics, and each can be deleted (or Delete all), with Undo. |
| 2026-11-13 | M14 | Home: Scan & Pay button removed; + (add payment) is the one primary button, in `accent`. The home-screen shortcut still opens the scanner. |
| 2026-11-13 | M1, M2–M7, M24 | Dashboard has a sidebar, one page per feature: 1 Payment tracking, 2 Smart categorisation, 3 Budgets, 4 Engagement, plus Testers. **Payment tracking** = Onboarding (setup completed ≥ 80%, SMS access ≥ 90%, median setup ≤ 2 min) + Capture (bank SMS read ≥ 95%, payment-app alerts with an amount read ≥ 95%, recorded automatically ≥ 90%, app alerts confirmed by bank SMS (tracked), pending after 24 h ≤ 10%). New phone counts `alert_amount/read`, `pay_30d/auto/alert/alert_sms`. **Tracked so far**: payments counted (`pay_all`) and an all-time ₹ total (`paid_rupees_all`, computed in `insights/Spending.trackedRupees`), sent only after the tester agrees to the new consent wording (v2; earlier testers are asked again) and shown only summed across ≥ 3 testers, never per tester. Not ticked: needs real tester data. |
| 2026-11-13 | M24 | Test builds: the "Sample on/off" switch moved from a floating, draggable bubble into the top bar (right of the profile button, main tabs only), so it no longer covers content. |
| 2026-11-13 | M1, M2 | Bug fix: Home's "Allow SMS" screen and Profile › Sources asked only for READ_SMS, so a tester could end up without RECEIVE_SMS: bank SMS were then picked up only when the app was opened, and no "saved / what was it for?" notification came after a payment. All three places now ask for both and SMS counts as allowed only with both (`Onboarding.SMS_PERMISSIONS`). Dashboard Capture adds "Live SMS capture on" (≥ 95%) and "Payment-app alerts on" (≥ 80%) from new `sms_live` / `alerts_on` flags. Not ticked: needs phone testing. |
| 2026-11-13 | M2–M7, M24 | Capture metrics reworked around "was every payment found on its own, quickly, once, and read well?". Dashboard Capture: Capture switched on (≥ 90%), Found on its own (SMS / alert / Scan & Pay result, ≥ 90%), Added from a notification (tracked), Added after opening the app (tracked), Captured within 1 minute (≥ 90%), Payee identified (≥ 90%), Possible double counts (≤ 2%), Still pending after 24 h. Health checks: payment-looking SMS / alerts read (≥ 99%), replacing the old read rates that counted offers and statements. Phone: `metrics/CaptureLog`, `insights/CaptureChecks`. Not ticked: needs real tester data. |
| 2026-11-13 | M20, M24 | Golden dataset v1.0 (25 Feature 1 cases) runs against the live AI and the app's own `Categoriser`, uploaded to the dashboard's **AI evaluation** page (Run Log metrics vs proposed targets, every case, critical failures, run history). Run 1: top-1 92.9%, top-3 100%, auto-assignment precision 86%, 2 critical failures, both from app rules, not the AI: CAT-020 ("rent" shop word files Rent on its own) and CAT-006 (weak shop word + AI agreement files without confirmation). Feature 2 (15 cases) not runnable: no AI insights step. `server/evals/`, `GoldenF1AppLayerTest`. M20 not ticked: labels pending approval, critical failures open. |
| 2026-11-13 | M20, M24 | Real-world golden coverage: the phone sorts AI-answered payees into golden-dataset scenarios (`GoldenScenario`, name shape only) and uploads counts per scenario; AI evaluation page shows per scenario the golden result, real share, real top-3 and first-guess, and flags kinds with no golden case (UPI ID only, phone-number ID, non-Latin) as v2 candidates. Payee examples stay on the phone (Developer screen). |
| 2026-11-13 | M16, M24 | Dashboard page **3 Insights** (Budgets → 4, Engagement → 5). Phone logs each insight card shown / opened / rated (`metrics/InsightLog.kt`; card IDs stay on the phone, only counts per kind are sent). Reach: Open Insights each week ≥ 50% (moved from Engagement), Something to show ≥ 80%. Usefulness: Cards opened ≥ 30%, Rated helpful ≥ 70%, Hidden or not relevant ≤ 20% (targets proposed). Table by insight kind; note that F2 golden cases run once an AI explanation step exists. |
| 2026-11-13 | M16, M20, M24 (Feature 2) | **AI words on Insights cards + Feature 2 golden eval.** Every card now carries its verified facts (`InsightFacts`, whole ₹ and % from `insights/Insights.kt`). The phone sends them to new `POST /explain` (server/explain.py), whose request is the golden dataset's F2 input field for field; the AI returns a title and one sentence, or no_insight / reject_candidate. A plain-code guard on the server and on the phone (`InsightWordingCheck`) blocks any number not supplied and cause/prediction/advice words; blocked or failed answers keep the app's words. Wording is saved per fingerprint of the facts (same insight reuses it, INS-013). Card note "Words by AI · numbers worked out by the app"; Developer switch to turn it off. Eval: `server/evals/run_f2.sh` runs INS-001–015, plain-code checks + an LLM judge (`POST /evals/judge`, rubric 1–4), uploads to AI evaluation › Feature 2 (Correct decision, Required facts, Numbers grounded, Unsupported claim rate, Evidence validity, Clarity, Critical shown). Insights page adds AI wording: share of cards in AI words, helpful rate AI vs app words, AI hidden rate. |
| 2026-11-13 | M20 (Feature 2) | F2 eval run 2 on the live AI: 15/15 pass, 0 critical (run 1 had 2 server errors from an unsupported `maxItems` in the output schema, also fixed in `/dedupe`, and one "top category" overclaim; guard now blocks ranking words the insight doesn't make). AI evaluation › Feature 2 adds "How each answer is scored" (checks → LLM judge rubric → human review → critical) and Real-world coverage by card kind: repeat merchant, weekend vs weekday and day concentration have no golden case yet (v2 candidates). |
| 2026-11-13 | M20 (Feature 1) | **LLM judge for Feature 1.** F1 rubric adapted from the workbook (0–4): Category correctness, Ranking quality, Uncertainty handling, Evidence restraint, plus acceptable-alternative and needs-human flags; output contract stays automated. `POST /evals/judge` takes `feature: F1/F2` (Claude Opus 5.5); `upload.py` judges every case; dashboard adds 4 judge cards, a Judge C·R·U·E column, "Flagged for human review" and a How-it's-scored panel. Both judges now use the workbook's 0–4 scale. Run R-20261113-1810: C 3.8, R 3.6, U 3.52, E 3.72; judge confirms CAT-020 (0·3·0·0) and CAT-006 (U 1) as app-rule faults. |

---

## 7. Deviations from MVP (feasibility)

What is built today that contradicts Section 2 or 3, and why. Audited 9 Nov 2026 against the code.
**Decision** is for the team: *Keep* (update Section 2 to match), *Change* (make the code match MVP), or *Park* (move to Section 5).

### 7a. Built, but listed as Not MVP or not in Section 2

| # | What's built | MVP says | Why (feasibility) | Decision |
|---|---|---|---|---|
| D1 | **GPay screen reader** (Accessibility service): sees the UPI PIN screen and GPay's "Payment to X ₹N Paid" chat lines | Capture = bank SMS + Scan & Pay only | GPay sends no notification when you pay, and bank SMS can arrive late or be missed. Without the reader, small GPay payments weren't captured at all in testing. Note: Play Store restricts Accessibility use (MVP installs by APK, so OK for v1) | — |
| D2 | **"Did it go through?" pop-up** over GPay and PhonePe (PhonePe added 12 Nov), ~3 s after the PIN screen closes (also on Back / Home / swipe-away); switches to "Payment saved" if the bank SMS or GPay record arrives while it's up. Every PIN screen is a new attempt unless an unanswered one to the same payee + amount exists (fixed 10 Nov: retries after an answered attempt were dropped) | M7 asks only for an in-app sheet after Scan & Pay | GPay's success screen is an animation with no readable text, so we can't see the result; asking right away is the only reliable signal | — |
| D3 | **Push notification + end-of-day reminder** for unanswered payments (WorkManager) | "Push notifications or reminders" is Not MVP (§3) | People close the pop-up and forget; without a nudge the payment stays unconfirmed and out of totals | — |
| D4 | **UPI app notification listener** (GPay, PhonePe, Paytm, BHIM, CRED) | Not in Section 2 | Catches "money received" and some app alerts SMS doesn't cover. "X paid you ₹N" alerts now become Received entries (merged with the bank credit SMS for the same money, never in spending). Other alerts are still only shown as rows, not used for totals | — |
| D5 | **Share screenshot to app** (`ShareActivity`, step 1 only) | Not in Section 2 | Early fallback for shop payments with no chat. Unfinished | — |
| D6 | **Sources screen** (SMS, UPI alerts, GPay reader, notifications on/off) | Source status lives in Profile (M18) | Sources were a 4th top tab; on 9 Nov the tab and the "N sources are off" row on Home were removed. Sources now opens from Profile › Sources. A better place for source status is still to be designed | Changed (interim) |
| D7 | **Sample data switch** (floating pill, test builds only) | Not in Section 2 | Design and demo tool; not shipped in release builds | — |

### 7b. Built differently from the Section 2 item

| # | Item | MVP says | What's built | Why (feasibility) | Decision |
|---|---|---|---|---|---|
| D8 | M7 Scan & Pay | Scan QR in-app → amount + category → hand off to UPI app → record with UPI ref | Button just opens GPay; capture happens through D1/D2 | UPI intent payments from third-party apps are often blocked or fail in GPay/PhonePe for merchant QR, and the app's result isn't reliable; we'd still need D1/D2 | — |
| D9 | M6 De-duplication | Same reference number = one payment | Matched by **same amount within −2 / +30 min**, closest first, one-to-one | PIN screen and GPay chat lines show no reference number; only bank SMS has one | — |
| D10 | M1 Onboarding | Explain, ask for SMS; denied → Home shows persistent "SMS access is off · Turn on" row | One setup screen (10 Nov): SMS + notifications in one tap, then UPI alerts and GPay reader in Settings, one button walks through what's off, optional ones skippable. SMS is required: if it's off, setup shows instead of Home | Android can't grant notification access or Accessibility from a pop-up; the other sources (D1, D3, D4) need asking for somewhere | — |
| D11 | M2 Inbox import | Last 90 days, progress, "Imported N payments" | Reads the whole inbox; no "Imported N" message | Not decided earlier; easy to add the 90-day limit | — |
| D12 | M3 Live capture | BroadcastReceiver turns new SMS into a payment in seconds | RECEIVE_SMS permission declared but **no receiver**; new SMS appear on app open / pull to refresh | Not built yet | — |
| D13 | M5 Parser | Amount, date, payee, VPA, last 4, ref no., mode; 10 kinds | Amount + direction (debit / credit) + sender of credits + refund flag; last 4 for Profile. No payee/VPA/ref/kinds for debits | Not built yet; refs/kinds needed for D9 and Insights | — |
| D14 | M8 Categories | 17 categories, merchant map + shop words | 8 categories + Other, payee keyword rules; user can type a custom name under Other | 17-category list not provided yet | — |
| D15 | M9 / M11 Payee memory + Add category sheet | Suggested chips, "Always use for X" toggle (on), Save | Choice **always** applies to the payee (no toggle), no suggestions; confirm button "Update category" | No AI suggestions yet (M10); toggle not built | — |
| D16 | M12 Needs-check rules | Untagged SPEND, possible duplicates, balance gap, UPI Lite | Only: started payment with no confirmation, and "you said No but bank debited" | Duplicates need refs (D9/D13); balance gap needs balances parsed | — |
| D17 | M13 Needs-check sheet | One issue → fix; several → checklist; "Same payment / Paid twice / Looks fine" | "Needs check" opens the Pending sheet per canvas: question, inset payment card marked Pending, "Your bank hasn't confirmed it yet…", **Not paid / Yes, paid** (wording now matches M7 + FEEDBACK F10). No checklist, no duplicate / "Looks fine" options | Follows D16: only one kind of issue exists today | — |
| D18 | M14 Home | Latest **3** payments; "To check · N · ₹X" row | Latest **5**; check row is a banner "● N payments need a check · Review ›" (no ₹ amount, per canvas); tabs now match (Home · Review · Insights) | Small; easy to align | — |
| D19 | M15 Payments list | 4 card types | Debit cards + Received cards (bank credit SMS, incl. refunds; "Received" filter). No separate refund card yet | Refund card needs REFUND kind from D13 | — |
| D20 | M16 Insights | Week/Month toggle, by category, "Not counted as spending" card | Rebuilt as Feature 2: Month/Week/Day/Custom, by category (parent rollup) with %, comparison + insight cards, drill-down + feedback | Resolved (exceeds M16); on-device drill-down pass pending | — |
| D21 | M17 Ask | AI phrases code-computed numbers; corrects wrong numbers; mentions unchecked | Keyword matching + fixed sentences, no AI; doesn't correct numbers in the question | No backend yet (M19) | — |
| D22 | M18 Profile | Accounts, source status + last sync, "Delete all my data", version | Accounts + link to Sources; no last sync, no delete, no version | Not built yet | — |
| D23 | M21 Privacy | "Delete all my data" wipes everything | Missing | Not built yet | — |
| D24 | DESIGN/MVP | Weights 400/600/700; hero 64 sp | 400/600 only, hero 48 sp (user request, narrow phones ~354 dp) | DESIGN.md already updated | Keep |
| D25 | M22 Feedback | Bottom snackbar; changes apply instantly | Top banner; data-changing buttons spin 1.2 s, date change shows the skeleton 1.2 s (user request, 2026-11-09) | FEEDBACK.md §2/§2a updated | Keep |
| D26 | M22 Feedback | F12 pull to refresh had no haptic | Light ticks follow the pull, firmer tick at "Release to refresh", click on release; no dot before "Up to date" (user request, 2026-11-10) | FEEDBACK.md F12a/F12b + §3 Release added | Keep |

### 7c. Not started

M10 AI categoriser, M19 Backend, M20 Evals. M22 Feedback + haptics is in progress (`ui/feedback/`).
