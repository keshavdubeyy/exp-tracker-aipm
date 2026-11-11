# MVP.md — Expense Tracker (Android)

The single source of truth for what v1 includes. If it isn't in **Section 2**, it isn't MVP.

**Product in one line:** an Android app that captures your online payments automatically (bank SMS + Scan & Pay), sorts them into categories with rules and AI, flags anything that needs a check, and tells you where your money went.

**Progress:** 0 / 23 done  ← update this line whenever a box is ticked

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
  - Top pill tabs: Home · Review · Insights.
  - Hero: spent this week + change vs last week in words.
  - "To check · N · ₹X" row (hidden when 0).
  - Payments (latest 3) and Categories carousel.
  - Bottom: "Ask about your spending" bar + Scan & Pay button.
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
  - GPay reader: accessibility service limited to the GPay package; never reads password fields; asks "Did it go through?" after the PIN screen closes.
  - UPI app alerts: notification listener, payment-app allow-list only.
  - Nothing from either leaves the phone.

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
| | | |

---

## 7. Deviations from MVP (feasibility)

What is built today that contradicts Section 2 or 3, and why. Audited 9 Nov 2026 against the code.
**Decision** is for the team: *Keep* (update Section 2 to match), *Change* (make the code match MVP), or *Park* (move to Section 5).

### 7a. Built, but listed as Not MVP or not in Section 2

| # | What's built | MVP says | Why (feasibility) | Decision |
|---|---|---|---|---|
| D1 | **GPay screen reader** (Accessibility service): sees the UPI PIN screen and GPay's "Payment to X ₹N Paid" chat lines | Capture = bank SMS + Scan & Pay only | GPay sends no notification when you pay, and bank SMS can arrive late or be missed. Without the reader, small GPay payments weren't captured at all in testing. Note: Play Store restricts Accessibility use (MVP installs by APK, so OK for v1) | — |
| D2 | **"Did it go through?" pop-up** over GPay, ~3 s after the PIN screen closes (also on Back / Home / swipe-away); switches to "Payment saved" if the bank SMS or GPay record arrives while it's up. Every PIN screen is a new attempt unless an unanswered one to the same payee + amount exists (fixed 10 Nov: retries after an answered attempt were dropped) | M7 asks only for an in-app sheet after Scan & Pay | GPay's success screen is an animation with no readable text, so we can't see the result; asking right away is the only reliable signal | — |
| D3 | **Push notification + end-of-day reminder** for unanswered payments (WorkManager) | "Push notifications or reminders" is Not MVP (§3) | People close the pop-up and forget; without a nudge the payment stays unconfirmed and out of totals | — |
| D4 | **UPI app notification listener** (GPay, PhonePe, Paytm, BHIM, CRED) | Not in Section 2 | Catches "money received" and some app alerts SMS doesn't cover. Currently only shown as rows, not used for totals | — |
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
| D13 | M5 Parser | Amount, date, payee, VPA, last 4, ref no., mode; 10 kinds | Amount only (debits); last 4 for Profile. No payee/VPA/ref/kinds; credits ignored | Not built yet; refs/kinds needed for D9 and Insights | — |
| D14 | M8 Categories | 17 categories, merchant map + shop words | 8 categories + Other, payee keyword rules; user can type a custom name under Other | 17-category list not provided yet | — |
| D15 | M9 / M11 Payee memory + Add category sheet | Suggested chips, "Always use for X" toggle (on), Save | Choice **always** applies to the payee (no toggle), no suggestions; confirm button "Update category" | No AI suggestions yet (M10); toggle not built | — |
| D16 | M12 Needs-check rules | Untagged SPEND, possible duplicates, balance gap, UPI Lite | Only: started payment with no confirmation, and "you said No but bank debited" | Duplicates need refs (D9/D13); balance gap needs balances parsed | — |
| D17 | M13 Needs-check sheet | One issue → fix; several → checklist; "Same payment / Paid twice / Looks fine" | "Needs check" opens the Pending sheet per canvas: question, inset payment card marked Pending, "Your bank hasn't confirmed it yet…", **Not paid / Yes, paid** (wording now matches M7 + FEEDBACK F10). No checklist, no duplicate / "Looks fine" options | Follows D16: only one kind of issue exists today | — |
| D18 | M14 Home | Latest **3** payments; "To check · N · ₹X" row | Latest **5**; check row is a banner "● N payments need a check · Review ›" (no ₹ amount, per canvas); tabs now match (Home · Review · Insights) | Small; easy to align | — |
| D19 | M15 Payments list | 4 card types | Debit cards only (no Received/refund cards) | Needs credits from D13 | — |
| D20 | M16 Insights | Week/Month toggle, by category, "Not counted as spending" card | Month only, by payee, 7-day bars; old layout | Not rebuilt yet | — |
| D21 | M17 Ask | AI phrases code-computed numbers; corrects wrong numbers; mentions unchecked | Keyword matching + fixed sentences, no AI; doesn't correct numbers in the question | No backend yet (M19) | — |
| D22 | M18 Profile | Accounts, source status + last sync, "Delete all my data", version | Accounts + link to Sources; no last sync, no delete, no version | Not built yet | — |
| D23 | M21 Privacy | "Delete all my data" wipes everything | Missing | Not built yet | — |
| D24 | DESIGN/MVP | Weights 400/600/700; hero 64 sp | 400/600 only, hero 48 sp (user request, narrow phones ~354 dp) | DESIGN.md already updated | Keep |
| D25 | M22 Feedback | Bottom snackbar; changes apply instantly | Top banner; data-changing buttons spin 1.2 s, date change shows the skeleton 1.2 s (user request, 2026-11-09) | FEEDBACK.md §2/§2a updated | Keep |
| D26 | M22 Feedback | F12 pull to refresh had no haptic | Light ticks follow the pull, firmer tick at "Release to refresh", click on release; no dot before "Up to date" (user request, 2026-11-10) | FEEDBACK.md F12a/F12b + §3 Release added | Keep |

### 7c. Not started

M10 AI categoriser, M19 Backend, M20 Evals. M22 Feedback + haptics is in progress (`ui/feedback/`).
