# ONBOARDING.md — First-run flow (Android)

Spec for the new-user onboarding. Pairs with `DESIGN.md` (look), `FEEDBACK.md` (confirmations, haptics), `MVP.md` (scope).
Design reference: canvas row **Onboarding** (screens `ob1`–`ob8`).

**Goal:** a new user sees their real spending in about a minute. Only SMS is required; everything else is optional and can be turned on later in **Profile › Sources**.

---

## 1. Flow at a glance

```
[System splash]
      ↓
ob1 Welcome ──Get started──▶ ob2 SMS explainer ──Allow──▶ (Android SMS pop-ups)
                                                     │ granted            │ denied
                                                     ▼                    ▼
                                         ob3 Importing + notif card   denied once → stay on ob2 (can retry)
                                                     │                denied permanently → ob8 SMS denied
                                                     ▼
                                         ob3b Sort past payments ──Sort the rest──▶ one payee at a time
                                                     │ Skip                              │ done / Skip the rest
                                                     ▼                                   ▼
                                         ob4 First look
                                                     ▼
                                         ob5 Catch more (optional) ──Turn on──▶ ob6 explainer sheet ──▶ Settings
                                                     │                                   │ switch greyed out?
                                                     │                                   ▼
                                                     │                          ob7 Restricted-settings help
                                                     ▼
                                                   Home
```

Progress dots: ob2 = step 1, ob3–ob4 (incl. ob3b) = step 2, ob5 = step 3. ob1 has none.

---

## 2. Screens

### Splash (system)
- Android 12+ system splash via `androidx.core:core-splashscreen`.
- Background `#0B0B0D`, app icon centred. No custom splash screen.
- Keep it on screen only while reading the "onboarding done?" flag (`setKeepOnScreenCondition`).
- Onboarding done → Home. Not done → ob1.

### ob1 · Welcome
| Element | Copy / behaviour |
|---|---|
| Title | "See where your money goes. Automatically." |
| Subtitle | "We read your bank payment messages and sort your spending for you. No typing." |
| 3 rows (icon + text) | "UPI, cards and net banking" · "Your messages stay on your phone" · "Set up in about a minute" |
| Primary button | **Get started** → ob2 |
| Footnote | "No sign-up needed" |

### ob2 · SMS explainer (before the system pop-up)
| Element | Copy / behaviour |
|---|---|
| Back | → ob1 |
| Illustration | Sample bank SMS → arrow → payment card (static, decorative) |
| Title | "Let us read your bank messages" |
| Subtitle | "That's how payments show up here on their own." |
| 3 checks | "Only messages from banks" · "We skip OTPs and personal chats" · "Messages never leave your phone" |
| Primary button | **Allow SMS access** → request `READ_SMS` + `RECEIVE_SMS` together |
| Footnote | "Android will ask you to confirm next" |

**Result handling**
- Both granted → ob3.
- Denied, and `shouldShowRequestPermissionRationale(READ_SMS) == true` → stay on ob2; button still works (Android will ask again).
- Denied, and rationale is `false` (permanently denied) → ob8.
- `READ_SMS` granted but `RECEIVE_SMS` denied → continue to ob3; Profile › Sources shows "Live capture off" (new payments appear when the app opens).

### ob3 · Importing + notifications card
| Element | Copy / behaviour |
|---|---|
| Back | None (import is running) |
| Label | "Reading the last 90 days…" |
| Big number | Live count of payments found; label "payments found so far" (`aria-live` / TalkBack polite announcements, max every 2 s) |
| Progress | Bar + month chips ("Aug ✓", "Sep ✓", "Oct…", "Nov") |
| Notifications card (Android 13+ only) | Title "While you wait: want a nudge?" · body "Only when a payment needs you, like "Did your ₹40 to Raju go through?". Never ads." · buttons **Not now** / **Allow** (Allow → request `POST_NOTIFICATIONS`). Card disappears after either choice. Hidden below Android 13. |
| Bottom button | Disabled "Almost done…" while importing → enabled **Continue** → ob4 |

**Import rules (MVP M2)**
- Reads bank SMS from the last 90 days, runs the parser, saves to Room. Runs in a coroutine/Worker so it survives rotation.
- If the import finishes in under 1.5 s, still show the screen for 1.5 s so the count is readable.
- **0 payments found** → replace the count with "No bank payment messages found in the last 90 days" and offer **Continue anyway**. ("Upload a bank statement instead" is parked in MVP.md §5; hidden until built.)
- Error → "Something went wrong reading messages" + **Try again**.

### ob3b · Sort past payments
The inbox found at setup is sorted the same way as new payments (Feature 1 §3.2, MVP M8 + M10). Counts come from `insights/PastSort.kt`; the AI only picks categories.

| Phase | Copy / behaviour |
|---|---|
| Working (only when AI is on) | "Sorting your payments", "{asked} of {total}" payees checked by AI, progress bar. The app asks the AI about **every** payee the rules couldn't place, 10 at a time, until none are left. Only payee name, UPI ID, amount, hour and method are sent, never the SMS. Disabled "Sorting…" button; "Skip this step" link. |
| Result | "Sorted on their own" + "{sorted} of {total}" + "past payments have a category, {N} of them by AI". Card: up to 6 sorted payees (name, category · N payments · Known shop / From its name / AI) + "and N more payees". Red dot line "**N payments** to M payees need you…" when any are left. |
| Result buttons | Some left: **Sort the rest now** + "Skip, I'll do it later" → ob4. None left: **Continue** → ob4. |
| Sort the rest | One payee per screen, most payments first: "3 of 9", payee, "N payments · ₹X · last on 4 Nov", "AI suggests" (or "Suggested") with its top 3, "Something else" → all categories. Picking saves it as the payee's default (all their past and future payments); a payment with no payee name changes only itself. "Skip this one" / "Skip the rest". Finishing the list → ob4. |

Only "Sure" answers are filed on their own; "Fairly sure" ones are offered here as suggestions. Skipped payees stay "Add category" in Review. Feeds the dashboard group *Smart categorisation › History at install* (`past_sorted`, `past_sorted_auto`, `past_sorted_ai`).

### ob4 · First look
| Element | Copy / behaviour |
|---|---|
| Label | "You spent online in the last 90 days" |
| Big number | Total spend (₹, Indian grouping), computed in `insights/` |
| Sub | "across {N} payments from {A} bank accounts" |
| Card | "Where most of it went": top 3 categories with bars |
| Needs-category line | Red dot + "**{N} payments** need a category. You can do that later, a few at a time." Hidden if 0. |
| Primary button | **Continue** → ob5 |

All numbers are real imported data. Never show sample numbers here.

### ob5 · Catch more payments (optional)
| Element | Copy / behaviour |
|---|---|
| Title | "Catch the payments banks don't text about" |
| Subtitle | "Optional. You can turn these on later in Profile." |
| Card 1 | **GPay payments** + "Recommended" badge · "Catch small GPay payments and confirm them right after you pay." · **Turn on** → ob6 |
| Card 2 | **UPI app alerts** · "Catch money you receive in GPay, PhonePe, Paytm, BHIM and CRED." · **Turn on** → short explainer sheet (same pattern as ob6) → notification-listener settings |
| Card "on" state | Button replaced by green pill "✓ On" |
| Primary button | **Done** → Home (mark onboarding complete) |
| Secondary | "Skip for now" → Home (mark onboarding complete) |

**Re-check on return:** in `ON_RESUME`, re-read both states and update the cards.
- Accessibility service enabled? → check `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` contains our component.
- Notification listener enabled? → `NotificationManagerCompat.getEnabledListenerPackages(context).contains(packageName)`.

### ob6 · GPay reader explainer (bottom sheet)
| Element | Copy / behaviour |
|---|---|
| Title | "Turn on the GPay reader" |
| 3 checks | "We only look at the GPay app" · "We never read your UPI PIN" · "Nothing leaves your phone" |
| Box text | "In Settings, Android will show a strong warning. That's normal for this kind of setting." |
| Steps | 1. Tap **Downloaded apps › Expense Tracker** 2. Switch it **on** 3. Tap **Allow**, then come back here |
| Primary | **Open Settings** → `Settings.ACTION_ACCESSIBILITY_SETTINGS` |
| Link | "Switch is greyed out?" → ob7 |
| Close | → ob5 |

**Make the promises true in code**
- Accessibility service config: `android:packageNames` limited to the GPay package only.
- Ignore any node where `isPassword == true`; never read, store or log PIN screens' input.
- Process events on the phone only; nothing from this service is sent to the backend.

**UPI app alerts sheet** (same layout): title "Turn on UPI app alerts" · checks "We only read payment apps" · "We ignore chats and other apps" · "Nothing leaves your phone" · steps "Find Expense Tracker → switch it on → Allow" · **Open Settings** → `ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS` with our component (Android 11+), else `ACTION_NOTIFICATION_LISTENER_SETTINGS`. In the listener, ignore notifications from any package not on the payment-app allow-list.

### ob7 · "Switch greyed out?" help (bottom sheet)
Shown from the ob6 link, **and automatically** if the user returns from Settings and the service is still off on Android 13+ after one attempt.

| Element | Copy / behaviour |
|---|---|
| Title | "Switch greyed out? One extra step" |
| Body | "Android locks this for apps installed outside the Play Store until you allow it once." |
| Steps | 1. Open **App info** 2. Tap **⋮** at the top right 3. Tap **Allow restricted settings** 4. Come back and tap **Try again** |
| Buttons | **Try again** (→ reopen ob6) · **Open App info** (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`, `package:` URI) |

### ob8 · SMS denied (permanent)
| Element | Copy / behaviour |
|---|---|
| Title | "SMS access is off" |
| Body | "Without it we can't find your payments. Android won't ask again, so it has to be turned on in Settings." |
| Steps | 1. Tap **Permissions** 2. Tap **SMS** 3. Choose **Allow**, then come back |
| Primary | **Open Settings** → App details settings |
| Secondary | "Upload a bank statement instead": parked (MVP.md §5), hidden until built |
| On return | If SMS now granted → ob3 automatically |

---

## 3. Not part of onboarding

- **Battery / auto-launch settings.** Never a step. Show as help in **Profile › Sources** only when a source stops working (e.g. no accessibility or listener events for 24 h while enabled): "GPay reader stopped. Your phone may be closing the app in the background. **Fix it**" → brand-specific steps.
- **Camera, contacts, location, storage, "display over other apps".** Not requested.
- **Sign-up / login.** None in MVP.

---

## 4. State to store (DataStore)

| Key | Meaning |
|---|---|
| `onboarding_complete` | true after ob5 Done or Skip |
| `sms_denied_count` | for analytics only; permanent denial is detected via rationale, not this count |
| `notif_asked` | true once the notifications card was answered (don't show again) |
| `extras_attempted_gpay` / `extras_attempted_listener` | used to auto-show ob7 after a failed attempt |
| `import_last_run_at` | for re-scan logic (M3) |
| `sort_done` | true once ob3b was finished or skipped |

If the app is killed mid-onboarding, resume at the first unfinished step: no SMS → ob2; SMS but import not done → ob3; import done but sort not done → ob3b; else → ob4.

---

## 5. Feedback and haptics (adds to FEEDBACK.md)

| # | Event | Feedback | Haptic |
|---|---|---|---|
| F28 | SMS granted | Move to ob3 (no message) | Confirm |
| F29 | SMS denied (can retry) | Stay on ob2, no message | none |
| F30 | Import finished | Count stops, button becomes **Continue** | Confirm |
| F31 | Notifications allowed / Not now | Card disappears | Tick |
| F32 | Extra source turned on (detected on return) | Card shows "✓ On" | Confirm |
| F33 | Returned from Settings, source still off | Card unchanged; ob7 auto-shown on Android 13+ after one attempt | none |
| F35 | Sort past payments: AI finished | Count stops, results show | Confirm |
| F36 | Sort past payments: category picked | 1.2 s loading on the row, then "Saved. Future payments to X will be Y" + Undo, next payee | Confirm |
| F37 | Sort past payments: Skip / Sort the rest / Something else | Moves on | Tick |
| F34 | Onboarding complete | Home appears (skeleton if still loading) | none |

---

## 6. Accessibility

- Every screen has one heading (`semantics { heading() }`) and the primary button is the last focus stop.
- Progress dots read as "Step 1 of 3".
- Illustrations are decorative (`contentDescription = null`).
- Live count announced politely, not on every increment.
- All text scales to 200% without clipping; screens scroll if needed.

---

## 7. Funnel metrics (for capstone evals)

Log on-device (no personal data): `ob_started`, `sms_explainer_shown`, `sms_granted`, `sms_denied`, `sms_permanently_denied`, `import_done(count)`, `import_zero`, `notif_granted`, `gpay_reader_on`, `listener_on`, `restricted_help_shown`, `ob_complete(skipped_extras: Boolean)`, plus time from `ob_started` to `ob_complete`.

---

## 8. Acceptance checks

- [ ] Fresh install: Welcome → SMS → Import → Sort past payments → First look → Extras → Home works with only SMS granted.
- [ ] Denying SMS once keeps the user on ob2; denying permanently opens ob8; granting in Settings returns to ob3 automatically.
- [ ] Notifications card appears only on Android 13+ and only once.
- [ ] ob3b: with AI on, every payee rules can't place is asked (not just 10); only Sure answers are filed; Skip at any point goes to ob4 and the rest stay in Review.
- [ ] ob3b: picking a category for a payee files all their past payments at once.
- [ ] ob4 shows real numbers from the import; hides the "needs a category" line when 0.
- [ ] 0 payments imported shows the empty variant (statement upload parked).
- [ ] Turning on either extra and returning updates its card to "✓ On" without restarting the app.
- [ ] On a sideloaded Android 13+ build, a failed accessibility attempt shows ob7 automatically.
- [ ] Accessibility service only receives events from GPay and never reads password fields.
- [ ] Killing the app mid-flow resumes at the right step.
- [ ] Second launch after completion goes straight from splash to Home.

---

## 9. Scope note

`MVP.md` covers SMS permission + onboarding (M1), import (M2) and, since 10 Nov 2026, the **GPay reader** and **UPI app alerts** (M23), so ob5–ob7 are in scope. Statement upload is parked.

---

## 10. Prompt for Antigravity / Claude Code

```
Read DESIGN.md, FEEDBACK.md, MVP.md and ONBOARDING.md. Build the onboarding
flow in ui/screens/onboarding/ exactly as ONBOARDING.md sections 2–5 describe:
system splash, ob1–ob8, permission handling (including permanent SMS denial),
re-checking extra sources on resume, resume-where-left-off from DataStore, and
the 0-payments and error variants of the import screen. Restrict the
accessibility service to the GPay package and ignore password fields. Use real
imported data on the First look screen. Add the F28–F34 feedback events.
Then build, install on my phone, and walk me through the acceptance checks in
section 8 one by one.
```
