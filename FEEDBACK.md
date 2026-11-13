# FEEDBACK.md — Confirmations and haptics

When the app should confirm an action, how, and with which vibration. Implements MVP item **M22**.

Code: `ui/feedback/Feedback.kt` (helper + top banner host) and `ui/feedback/PaymentActions.kt` (payment actions with message + Undo).

---

## 1. Principles

1. **Confirm what the user did, not what the app did quietly.** A payment captured in the background updates the list; it does not pop a message.
2. **If the change is already visible, keep the message short.** The card updating is the main feedback; the top banner adds Undo.
3. **Anything reversible gets Undo.** Anything irreversible gets a confirm dialog *before*, not a message after.
4. **Errors stay until handled.** Success messages disappear on their own; errors show inline with a retry.
5. **One haptic per action, never for background events.** Haptics confirm a deliberate tap, not every pixel of feedback.
6. **Messages say what happened in words.** "Saved as Groceries", not "Success!".

---

## 2. Which component to use

| Component | Use for | Never use for |
|---|---|---|
| **Top banner** (slides down from the top, short) | Confirming a user action, offering Undo | Errors that block progress |
| **Inline message** (in the card / screen) | Errors with retry, permission off, empty states | Routine success |
| **Full screen** | Scan & Pay result (success / failed / pending) | Anything else |
| **Dialog** | Before irreversible actions (delete all data) | Confirming after the fact |
| **Android Toast** | Not used (can't be styled or undone) | — |

### Top banner spec (matches DESIGN.md)
- Slides down from the top, 8 dp under the status bar, 16 dp from screen sides. It covers the top tabs briefly and never the Ask bar or a bottom CTA.
- Background `surfaceRaised #2A2A2E`, corner 16 dp. Icon 20 dp: a check in `positive` for success, a warning in `negative` for failure. Words always carry the meaning.
- Text `body` in `textPrimary`; **Undo** in `tab` style, `accent`.
- Duration: 4 s with Undo; 3 s without. One at a time; a new one replaces the old. Tap to dismiss.
- A live region, so TalkBack reads it out.

### 2a. Loading step (every action button)
- A button that changes data (Save/Update category, Yes paid, Not paid, It was paid) shows a 20 dp spinner in place of its label for **1.2 s** (`ACTION_DELAY_MS`). While it spins, the other buttons in the sheet are disabled.
- Then the sheet closes, the screen shows the change, and the top banner confirms it. No second modal.
- Changing what's shown (Apply on the date sheet) shows the screen's **skeleton** for 1.2 s, then the new numbers. No message (F15).
- Buttons that only navigate or close (Change category, See how we know, Not now, Skip, Close) don't load.
- The save itself is on-device and instant. The pause only makes the change read as deliberate, so keep it at one constant.

---

## 3. Haptic types

| Name in this doc | When | Compose (`LocalHapticFeedback`) | View fallback (`performHapticFeedback`) |
|---|---|---|---|
| **Confirm** | An action completed successfully | `HapticFeedbackType.Confirm` | `HapticFeedbackConstants.CONFIRM` (API 30+), else `VIRTUAL_KEY` |
| **Reject** | An action failed or was refused | `HapticFeedbackType.Reject` | `HapticFeedbackConstants.REJECT` (API 30+), else `LONG_PRESS` |
| **Pull** | Each step while pulling to refresh | `HapticFeedbackType.SegmentFrequentTick` | `SEGMENT_FREQUENT_TICK` (API 34+), else `TEXT_HANDLE_MOVE` (27+), else `CLOCK_TICK` |
| **Release** | Letting go of a pull-to-refresh past the line | `HapticFeedbackType.VirtualKey` | `VIRTUAL_KEY` |
| **Tick** | Selecting a chip, switching a tab or period | `HapticFeedbackType.SegmentTick` | `CLOCK_TICK` |
| **Toggle** | Turning a switch on / off | `HapticFeedbackType.ToggleOn` / `ToggleOff` | `CLOCK_TICK` |

- The newer Compose haptic types need a recent Compose version; check what yours offers and use the View fallback otherwise.
- `performHapticFeedback` already respects the user's system "touch feedback" setting. Don't force vibration with `Vibrator`.

---

## 4. Event table

| # | Event | What the user sees | Message | Undo | Haptic |
|---|---|---|---|---|---|
| F1 | Category saved (Add category sheet) | Button loads, sheet closes, card updates, "Needs check" disappears | "Saved as Family & friends" | Yes | Confirm |
| F2 | Category saved with "Always use for X" on | Same as F1 | "Saved. Future payments to Ananya S will be Family & friends" | Yes (undoes both) | Confirm |
| F3 | Last payment in the review flow categorised | Inline "All caught up" state | — | — | Confirm |
| F4 | "Looks fine, mark as checked" | Card loses "Needs check" | "Marked as checked" | Yes | Confirm |
| F5 | Duplicate → "Same payment" | One card removed, totals update | "Merged into one payment" | Yes | Confirm |
| F6 | Duplicate → "Paid twice" | Both cards stay, flag cleared | "Kept both payments" | Yes | Confirm |
| F7 | Balance gap → "Count as small spends" | Gap row disappears, total updates | "Added ₹37 as small spends" | Yes | Confirm |
| F8 | Scan & Pay success | Full "Recorded instantly" screen | — | — | Confirm |
| F9 | Scan & Pay failed or cancelled | Full screen "Payment didn't go through. Nothing was recorded." | — | — | Reject |
| F10 | Scan & Pay result unknown | "Did this payment go through?" sheet (canvas: v4 · Did it go through?). Closing it keeps the payment **Pending** and it updates itself when the bank confirms | — | — | none until answered |
| F10a | Pending → "Yes, paid" | Button loads, sheet closes, card shows as a normal payment | "Recorded ₹40 to Raju Tea Stall" | Yes | Confirm |
| F10b | Pending → "Not paid" | Button loads, sheet closes, card removed, totals unchanged | "Removed. Nothing was recorded" | Yes | Confirm |
| F10c | Pending confirmed later by bank SMS | Card switches from Pending to normal | — | — | none (background event) |
| F11 | First import finished | Hero fills in, banner "Imported 142 payments" | — | — | Confirm |
| F12 | Pull to refresh / rescan finished | List updates | "2 new payments" or "Up to date" | — | none |
| F12a | Pull to refresh: pulling down, then passing the line ("Pull to refresh" → "Release to refresh") | Spinner fills in, then label changes | — | — | Pull steps (10 light ticks following the finger, only while pulling down), then Tick at the line |
| F12b | Pull to refresh: let go past the line | Spinner + "Checking messages…" | — | — | Release |
| F13 | New payment captured while app is open | Card slides in at top of the list | — | — | none (background event) |
| F14 | Selecting a category chip | Chip highlights | — | — | Tick |
| F15 | Switching week / month or a top tab | Date change: skeleton 1.2 s, then content. Tab: content changes | — | — | Tick |
| F16 | "Always use for X" switch | Switch moves | — | — | Toggle |
| F17 | Copy (any copy button) | — | "Copied" (only below Android 13, which shows its own) | — | Tick |
| F18 | Ask: answer arrives | Answer appears | — | — | none |
| F19 | Ask: backend unreachable | Inline "Couldn't answer right now. Try again" + Retry | — | — | Reject |
| F20 | AI categoriser unreachable | Payment stays "Add category" (silent fallback) | — | — | none |
| F21 | SMS permission denied | Persistent inline row "SMS access is off · Turn on" | — | — | none |
| F22 | "Delete all my data" | Dialog first: "Delete all payments and settings? This can't be undone." → then onboarding | "All data deleted" | No | Confirm (after delete) |
| F23 | Unreadable SMS found | Counted in Profile › Sources ("2 messages couldn't be read") | — | — | none |
| F28 | Onboarding: SMS granted | Moves to Importing (no message) | — | — | Confirm |
| F29 | Onboarding: SMS denied, can ask again | Stays on the SMS explainer | — | — | none |
| F30 | Onboarding: import finished | Count stops, button becomes **Continue** | — | — | Confirm |
| F31 | Onboarding: notifications Allow / Not now | Card disappears | — | — | Tick |
| F32 | Onboarding: extra source turned on (seen on return) | Card shows "✓ On" | — | — | Confirm |
| F33 | Onboarding: back from Settings, source still off | Card unchanged; "Switch greyed out?" help opens on Android 13+ | — | — | none |
| F35 | Onboarding: sorting past payments finished | Count stops, results show | — | — | Confirm |
| F36 | Onboarding: category picked for a past payee | 1.2 s loading on the row, then next payee | "Saved. Future payments to X will be Y" | Yes | Confirm |
| F37 | Onboarding: Skip / Sort the rest / Something else | Moves on | — | — | Tick |
| F34 | Onboarding complete | Home appears (skeleton while loading) | — | — | none |
| F24 | Payment added by hand (+ button on Home) | Sheet closes, payment appears with source "Added by you", totals update | "Added ₹40 to Raju Tea Stall" | Yes (removes it) | Confirm |

---

## 5. Undo rules

- Undo restores the exact previous state, including payee memory ("Always use") and totals.
- Undo is available only while the banner is visible (4 s). After that, the user changes it from the payment detail.
- Implement as: apply the change immediately, keep the previous state in memory, revert on Undo. Don't delay the save until the banner closes (the 1.2 s loading step comes before the save, not after).

---

## 6. Suggested helper

One place to fire feedback so every screen behaves the same:

```kotlin
interface Feedback {
    fun success(message: String? = null, undo: (() -> Unit)? = null)   // Confirm haptic + top banner
    fun failure(message: String? = null)                                // Reject haptic (+ banner only if no inline error)
    fun tick()                                                          // Tick haptic, no message
    fun toggle(on: Boolean)                                             // Toggle haptic, no message
}
```

---

## 7. Acceptance checks

- [ ] Every event F1–F24 behaves exactly as in the table.
- [ ] No banner or haptic fires for background events (F12 result, F13, F18, F20, F21, F23). The pull gesture itself ticks as it moves and at the line (F12a), and clicks on release (F12b). Springing back on its own is silent.
- [ ] Undo works for F1, F2, F4, F5, F6, F7 and fully restores totals.
- [ ] Banners slide in from the top and never cover the bottom CTA or Ask bar.
- [ ] Every data-changing button shows its spinner for 1.2 s and can't be tapped twice.
- [ ] With system touch feedback turned off, no vibration happens.
- [ ] TalkBack announces every banner.
