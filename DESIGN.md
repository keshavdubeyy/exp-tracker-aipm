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

Font: **Inter** (Google Fonts) for everything. Weights 400 and 600 only — no bold.

| Style | Size / line height | Weight | Use |
|---|---|---|---|
| `hero` | 48 / 52 sp, letter-spacing −1 | 600 | The one big amount per screen |
| `sectionTitle` | 24 / 30 sp | 600 | "Payments ›", "Categories ›", screen titles |
| `onboardingTitle` | 28 / 34 sp (Welcome: 34 / 40) | 600 | Onboarding headlines only (ONBOARDING.md) |
| `statValue` | 20 / 26 sp | 600 | Amount on category / stat cards |
| `rowTitle` | 15 / 20 sp | 600 | Payee name, card title, row amount |
| `tab` | 14 / 18 sp | 600 | Pill tabs, period switcher |
| `body` | 13 / 18 sp | 400 | Row subtitles, explanations |
| `chip` | 12 / 16 sp | 600 | Badges, delta chips, source labels |

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
| + (add payment) button | 56 dp circle |
| Screen side padding | 20 dp |
| Gap between sections | 22 dp |
| Gap between cards in a list | 8–10 dp |
| Card inner padding | 14–18 dp vertical, 18–20 dp horizontal |

Spacing uses a 4 dp grid. Touch targets are at least 44 dp.

---

## 4. Components

### 4.1 Top pill tabs
Row at the top: avatar circle (44 dp, `accent` fill, initial in `onAccent`) then pills **Home · Review · Insights · Sources**.
- Active pill: `accent` fill, `onAccent` text.
- Inactive pill: `surface` fill, `textPrimary` text.
- Padding 12 × 20 dp. Scrolls sideways if it overflows.

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

### 4.8 Bottom bar: Ask + Add payment
Pinned at the bottom of Home.
- Search bar: `surface`, fully round, magnifier icon + "Ask about your spending" in `textSecondary`. Opens the Ask screen.
- Round button, 56 dp, `accent` fill, + icon in `onAccent`, content description "Add a payment".
  Opens the Add a payment sheet: amount (required), paid to, category, day (no future days).
  Saved payments show the source *Added by you* and merge with a later bank SMS for the same payment.

### 4.9 Chips / badges
13 sp / 600, fully round, padding 3 × 10 dp.
- Neutral: `surfaceRaised` + `textPrimary`.
- Accent: `accent` + `onAccent` (e.g. "Paid here").

### 4.10 Icons
Simple outline icons, 2 dp stroke, rounded caps, 20–26 dp. No emoji. Icon-only buttons need a content description.

---

## 5. Home screen (v1 = Option A, "Spend-first")

Top to bottom:

1. **Pill tabs** — Home active.
2. **Switcher** — "Spent this week · 2–8 Nov ⌄".
3. **Hero** — `₹3,897`, with "₹412 less · −9.6% · than last week".
4. **Summary row** — "To check · 4 · ₹553" (balance gaps + UPI Lite + unsure payments) → Review.
5. **Payments ›** — latest 2–3 payment rows, each showing source.
6. **Categories ›** — horizontal stat cards (Food & dining ₹1,240 · ₹180 more; Transport ₹540 · ₹90 less; …).
7. **Bottom bar** — "Ask about your spending" + the + (add payment) button.

States
- **First open / importing:** hero shows "Importing your payments…" with a progress line; sections show placeholder cards.
- **Nothing to check:** hide the summary row (don't show "0").
- **No payments this week:** hero `₹0`, Payments shows "No payments yet this week" + a hint to tap + to add one.
- **Source disconnected (e.g. SMS permission revoked):** summary row turns into "SMS access is off · Turn on" → Sources.

Alternatives on the design canvas (not v1): **B · Accounts-first** (per-account spend with match status) and **C · Today feed** (today's total, 7-day strip, today's timeline).

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
