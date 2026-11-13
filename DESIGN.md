# DESIGN.md — Expense Tracker (Android)

Design rules for the app. Put this file at the project root so every teammate and coding agent (Antigravity, Claude Code) follows the same system. When a screen and this file disagree, this file wins.

**Look in one line:** a dark, calm, wallet-style app. Near-black background, soft rounded cards with no borders or shadows, one blue accent, and one big number at the top of every main screen.

**Platform:** Android, Jetpack Compose. Design frame 412 × 915 dp. Follows the system theme: light and dark.

---

## 1. Colours

The app follows the phone's light / dark setting. Same tokens, two palettes (`ui/theme/Color.kt`).

| Token | Dark | Light | Use |
|---|---|---|---|
| `bg` | `#0B0B0D` | `#F4F4F6` | Screen background |
| `surface` | `#1C1C1F` | `#FFFFFF` | Cards, list rows, search bar, inactive tabs, sheets |
| `surfaceRaised` | `#2A2A2E` | `#E9E9ED` | Icon circles, chips on cards, dividers |
| `textPrimary` | `#F4F4F6` | `#0B0B0D` | Titles, amounts, row titles |
| `textSecondary` | `#A1A1A8` | `#5C5C64` | Subtitles, labels, timestamps, placeholders |
| `accent` | `#98BBF4` | `#2D5FC4` | Active tab, + (add payment) button, links, "needs a tag" |
| `onAccent` | `#0B0B0D` | `#FFFFFF` | Text and icons on `accent` |
| `positive` | `#3DD68C` | `#0F7040` | Spent less, all matched |
| `negative` | `#FF6B6B` | `#C62828` | Spent more, money to check |
| `warning` | `#FFB547` | `#8A5A00` | Budget 90–99% used (Feature 3). Under 90% uses `accent`, 100%+ uses `negative`; always with words |

Light uses deeper shades of the same blue, green and red: the dark-theme shades fall below 4.5:1 on white.

Rules
- One accent only. Do not add more brand colours.
- **Colour never carries meaning alone.** Every green or red value also says it in words: "₹412 less", "₹37 to check", "All matched".
- Spending going *down* is good (green); spending going *up* is a warning (red). This is the opposite of a stock app; label it in words every time.
- No gradients, no shadows, no borders on cards. Depth comes from `bg` → `surface` → `surfaceRaised`.
- One exception: loading skeletons. Blocks are `surface` on the background and `surfaceRaised` inside cards, with a 240 dp band of white at 6% sweeping left to right every 1.4 s (`ui/theme/Skeleton.kt`). Never on real content.
- Text must reach 4.5:1 contrast on its background. `textSecondary` on `surface` passes; don't go dimmer.

```kotlin
// ui/theme/Color.kt
val Bg = Color(0xFF0B0B0D)
val Surface = Color(0xFF1C1C1F)
val SurfaceRaised = Color(0xFF2A2A2E)
val TextPrimary = Color(0xFFF4F4F6)
val TextSecondary = Color(0xFFA1A1A8)
val Accent = Color(0xFF98BBF4)
val OnAccent = Color(0xFF0B0B0D)
val Positive = Color(0xFF3DD68C)
val Negative = Color(0xFFFF6B6B)
```

---

## 2. Typography

Font: **DM Sans** (Google Fonts) for everything. Weights 400, 500 and 600 only — no bold.

Hierarchy comes from weight as much as size: **600** only for the hero, titles and amounts on stat cards; **500** for row names, row amounts, buttons, tabs and chips; **400** for everything you read. Not everything should look like a heading.

| Style | Size / line height | Weight | Use |
|---|---|---|---|
| `hero` | 40 / 46 sp, letter-spacing −0.8 | 600 | The one big amount per screen |
| `sectionTitle` | 18 / 24 sp | 600 | "Payments ›", "Categories ›", sheet and screen titles |
| `onboardingTitle` | 24 / 30 sp (Welcome: 28 / 34) | 600 | Onboarding headlines only (ONBOARDING.md) |
| `statValue` | 17 / 22 sp | 600 | Amount on category / stat cards |
| `rowTitle` | 15 / 20 sp | 500 | Payee name, card title, row amount |
| `tab` | 14 / 18 sp | 500 | Pill tabs, period switcher, button labels |
| `body` | 13 / 18 sp | 400 | Row subtitles, explanations |
| `chip` | 12 / 16 sp | 500 | Badges, delta chips, source labels |

Rules
- One `hero` per screen, never two.
- Amounts always use the ₹ sign and Indian digit grouping: ₹1,240 · ₹12,400 · ₹1,24,000.
- Debits shown as `−₹86` (true minus sign), credits as `+₹5,000`.
- Minimum text size 12 sp.
- Sizes are tuned for narrow phones (~354 dp wide), not only the 412 dp design frame.

---

## 3. Shape and spacing

| Token | Value |
|---|---|
| Card corner radius | 24 dp |
| Day-strip / small tile radius | 16 dp |
| Pills (tabs, chips, buttons, search bar) | fully round (50%) |
| Delta chip radius | 8 dp |
| Icon / avatar circle | 44 dp |
| + (add payment) and Scan & Pay buttons | 52 dp circle |
| Pill buttons (full-width or paired) and text fields | 48 dp tall |
| Screen side padding | 16 dp |
| Gap between sections | 22 dp |
| Gap between cards in a list | 8–10 dp |
| Card inner padding | 12–16 dp vertical, 16 dp horizontal |

Spacing uses a 4 dp grid. Touch targets are at least 44 dp.

---

## 4. Components

### 4.1 Profile button + bottom navigation bar
- Top left of each main tab: avatar circle (44 dp, `accent` fill, person icon in `onAccent`) → Profile. Hides while content scrolls down.
- Bottom navigation bar on the main tabs: **Home · Insights · Budgets · Review**, equal widths, `bg` fill, no border.
- Each item: outline icon (22 dp) over its label (`chip`).
- Active: icon sits in a 60 × 32 dp `accent` pill, icon `onAccent`, label `textPrimary`. Inactive: icon and label `textSecondary`.
- Review shows the number of payments to check as a small `accent` badge (hidden at 0).
- Sub-screens (Payments, Profile, details…) hide both and show their own Back.

### 4.2 Period / account switcher
Text button in `textSecondary`, 16 sp / 600, with a ⌄ chevron. Example: "Spent this week · 2–8 Nov ⌄". Opens a bottom sheet (This week / This month / custom; All accounts / per account).

### 4.3 Hero amount + delta
- `hero` amount.
- Below: delta text in `positive`/`negative` + a filled delta chip (`−9.6%`) + "than last week" in `textSecondary`.

### 4.4 Summary row card
Full-width `surface` card: icon circle (`surfaceRaised`) · label (`rowTitle`) · value right-aligned. Whole card is tappable. Example: "To check · 4 · ₹553" → Review.

### 4.5 Section header
`sectionTitle` + › chevron. Tapping it opens the full list.

### 4.6 Payment row card
`surface` card, 24 dp radius:
- Left: 44 dp circle with two initials (`surfaceRaised`): "Chai Point" → CP, "Papa" → PA.
- Middle: payee (`rowTitle`) and subtitle `Category · time` (`body`, `textSecondary`).
  - Unsure category shows **Add category** in `accent`.
  - Money received shows **Received** instead of a category.
- Right: signed amount (`rowTitle`): `−₹86` for money out, `+₹5,000` for money in.
- If the payment needs an answer, **Needs check** (`chip`, `negative`) sits under the amount. Tapping it opens the "Did it go through?" bottom sheet over the current screen; the rest of the card opens the payment's details.
- The source (Bank SMS, GPay…) is shown on the details screen and in the sheet, not on the row.

### 4.7 Stat card (horizontal carousel)
170 dp wide `surface` card, 16 dp padding: label (15/600), value (`statValue`), delta in words (`chip`, coloured). Scrolls sideways; the next card peeks at the edge.

### 4.8 Floating button: Add payment
Bottom right of Home, just above the navigation bar: the screen's one primary action.
- Round + button, 52 dp, `accent` fill, + icon in `onAccent`, content description "Add a payment".
- No Scan & Pay button on Home (removed 13 Nov 2026); the scanner still opens from the home-screen shortcut.
  Opens the Add a payment sheet: amount (required), paid to, category, day (no future days).
  Saved payments show the source *Added by you* and merge with a later bank SMS for the same payment.

### 4.9 Chips / badges
13 sp / 600, fully round, padding 3 × 10 dp.
- Neutral: `surfaceRaised` + `textPrimary`.
- Accent: `accent` + `onAccent` (e.g. "Paid here").

### 4.10 Icons
**Hugeicons**, free Stroke Rounded set (MIT, `licenses/HUGEICONS_LICENSE.md`): outline, 1.5 stroke on a 24 grid, rounded caps, shown at 20–26 dp. No emoji, no Material icons. Icon-only buttons need a content description.
All icons live in `ui/theme/HugeIcons.kt`, generated by `tools/gen_hugeicons.py` from `@hugeicons/core-free-icons`; to add one, add it to the list in the script and regenerate. `AppIcons` and `CategoryIcons` point at it.
Each predefined category has its own icon (`CategoryIcons`), shown on transaction rows and in the category chooser; custom categories use the tag icon.

### 4.11 Button hierarchy
- **Primary** — one per screen or sheet: `accent` fill pill, `onAccent` label, 48 dp tall.
- **Secondary** — `surfaceRaised` fill pill, `textPrimary` label, 48 dp tall (e.g. "View supporting transactions").
- **Link** — plain `tab` text, no fill or border, 44 dp touch target (`LinkButton`). Use it for low-priority actions such as rating an insight: **Helpful** · **Not relevant** in `accent` on the left, **Dismiss** in `textSecondary` on the right.

---

## 5. Home screen (v1 = Option A, "Spend-first")

Top to bottom:

1. **Profile button** (top left). Bottom navigation bar with Home active.
2. **Switcher** — "Spent this week · 2–8 Nov ⌄".
3. **Hero** — `₹3,897`, with "₹412 less · −9.6% · than last week".
   With a monthly budget set, the hero is a **budget card** instead (`surface`, 24 dp corners, 20 dp padding): "Spent in November so far" (`textSecondary`), the month's spend as the `hero` amount, a progress bar (colour by level: accent / warning / negative, on a `surfaceRaised` track), then "of ₹25,000 budget" on the left and "18 days left" on the right. Over budget: "₹500 over your ₹25,000 budget" in `negative`. Below, two equal pill buttons on `surfaceRaised`: **View budgets** and **View insights**.
4. **Summary row** — "To check · 4 · ₹553" (balance gaps + UPI Lite + unsure payments) → Review.
5. **Transactions** — title, then below it a full-width **All transactions | By category** pill toggle (two equal halves, selected half on `surfaceRaised`). One `surface` card (24 dp corners) of the latest 10 transactions, money out and money in, rows split by a 1 dp `surfaceRaised` line. Each row: the category's outline icon in a 44 dp `surfaceRaised` rounded square (tag icon while it has no category), payee, "Fri, 13 Nov · 5:50 pm", then on the right the signed amount (−₹200 / +₹500) over a chip: the category name, **Needs review** (`warning`, opens the category sheet) when it has none, or **Needs check** (`negative`) while it waits for "Did it go through?". Money received shows its category like any other row. "By category" shows the same rows in one card per category ("FOOD & DINING · 3"), Needs review first. "See all transactions" (`accent`) opens Payments.
6. **Categories ›** — horizontal stat cards (Food & dining ₹1,240 · ₹180 more; Transport ₹540 · ₹90 less; …).
7. **Floating button** — + (add payment), bottom right, `accent`.

States
- **First open / importing:** hero shows "Importing your payments…" with a progress line; sections show placeholder cards.
- **Nothing to check:** hide the summary row (don't show "0").
- **No payments this week:** hero `₹0`, Payments shows "No payments yet this week" + a hint to tap + to add one.
- **Source disconnected (e.g. SMS permission revoked):** summary row turns into "SMS access is off · Turn on" → Sources.

Alternatives on the design canvas (not v1): **B · Accounts-first** (per-account spend with match status) and **C · Today feed** (today's total, 7-day strip, today's timeline).

---

## 5a. Insights screen

Top to bottom:

1. **Header** — "Insights" (`sectionTitle`), under it the dates and what they're compared with: "1–13 Nov 2026 · vs 1–13 Oct ⌄" (tap → date sheet). Round refresh button (44 dp, `surface`) on the right.
2. **Period tabs** — Monthly · Weekly · Daily · Custom, one `surface` pill with the active tab on `surfaceRaised`.
3. **Summary card** — 2 × 2: Total expenses, Total income, Net cash flow, Transactions (payments out + money received). Net cash flow is `positive` with "+" and the words "More came in", or `negative` with "−" and "More went out". Then a line: "Highest spending category: **Food & Dining**".
4. **Category-wise spending card** — caps label "CATEGORY-WISE SPENDING" with "Tap to see payments" on the right. Each row: name, "₹4,350 · 40%", then an 8 dp `accent` bar on a `surfaceRaised` track (length relative to the biggest category). Top 5, then "All categories". Unsorted money shows as a `surfaceRaised` note: "₹350 is waiting for a category in Review. It is included in total expenses but not in any category above." (tap → Review).
5. **Insights** — title with "Updated after your latest payment". Up to 3 cards, each: kind chip (Increased in `warning`, Decreased in `positive`, Pattern in `accent`, Big share / Repeat neutral) and the dates on the right; title; the card's value large with "vs ₹3,200 in 1–13 Oct" when there's a comparison; one line of detail; **View supporting transactions** (full-width `surfaceRaised` pill); then **Helpful · Not relevant · Dismiss** pills sharing the row. Rating hides the card with a confirmation and Undo.
6. Footnote: "Insights explain your spending. They don't give financial advice or recommendations."

All numbers come from `insights/`. The section stays titled "Insights". When the AI has worded a card (Feature 2, `data/InsightWriter.kt`), its title and detail line are the AI's and a `textSecondary` line under the detail says "Words by AI · numbers worked out by the app". The AI never changes the value, the chip, the comparison line or the dates; wording that adds a number or a cause, prediction or advice word is rejected (`insights/InsightWordingCheck.kt`) and the card keeps the app's words.

---

## 6. Content rules

- Every number on screen is computed by code from recorded transactions. The AI only writes words.
- Say where data came from (source label) and what's missing ("₹37 to check") instead of hiding gaps.
- Plain, short labels: "To check", not "Unreconciled transactions".
- Dates: "Mon 9 Nov", "2–8 Nov", times "9:42 pm".
- Never show full account numbers; last 4 only (XX1234).

---

## 7. Do / Don't

**Do**
- Keep one big number per screen and let it breathe.
- Use `surface` cards with 24 dp corners for every grouped item.
- Put the source on every payment.
- Write every delta in words.

**Don't**
- Add borders, shadows or gradients to cards.
- Introduce a second accent colour.
- Use green/red without words.
- Copy another app's logo, mascot, icons or exact brand colours. This system borrows layout patterns only.
