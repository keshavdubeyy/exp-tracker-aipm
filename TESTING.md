# TESTING.md — Sending builds to testers

Testers get builds through **Firebase App Distribution**: an email with an install link, and a new email for every update. Free, up to 500 testers.

The build sent is **fast**: optimised like a release, signed with the debug key, and it has the **Sample data** switch.

---

## One-time setup (project owner)

1. **Create the Firebase project.** Go to [console.firebase.google.com](https://console.firebase.google.com) → *Add project* → any name (Analytics not needed).
2. **Add the Android app.** Click the Android icon. Package name: `com.yourteam.expensetracker`. Skip the `google-services.json` and SDK steps: App Distribution doesn't need them.
3. **Copy the App ID.** *Project settings* → *Your apps* → **App ID** (looks like `1:1234567890:android:abc123def456`).
4. **Add testers.** *Release & Monitor* → *App Distribution* → *Get started* → *Testers & Groups* → create a group, e.g. `testers`, and add emails. (Or list emails in step 5 instead.)
5. **Add to `local.properties`** (it's in `.gitignore`, so it stays on your Mac):
   ```properties
   firebase.appId=1:1234567890:android:abc123def456
   firebase.groups=testers
   # or, instead of a group:
   # firebase.testers=a@example.com,b@example.com
   ```
6. **Sign in once:** `firebase login` (opens the browser).
7. **Turn on the in-app update prompt.** Tester builds check Firebase when the app opens and show *New version available* → install in place.
   - *Project settings* → *General*: copy the **Project ID** and the **Web API key**.
   - Turn on the **Firebase App Testers API** for the project: [console.cloud.google.com/apis/library/firebaseapptesters.googleapis.com](https://console.cloud.google.com/apis/library/firebaseapptesters.googleapis.com) (pick your project → *Enable*).
   - Add to `local.properties` next to `firebase.appId`:
     ```properties
     firebase.projectId=your-project-id
     firebase.apiKey=AIza…
     ```
   Without these the app simply never checks. The check runs at most every 6 hours, only after onboarding, and only in the **fast** build (the one this script sends).

## Sending a build

```sh
./scripts/distribute.sh "Added the + button and light mode"
```

The script builds the APK with a new version number (so it installs as an update), uploads it, and emails testers with your note. Keep the note to what changed and what to try.

> After a Firebase build is on **your own** phone, `./gradlew :app:installFast` fails with a version downgrade error (Firebase builds have a higher version number). Uninstall the app first, or install a tester build instead.

---

## For testers (forward this part)

**Install**
1. Open the invite email from Firebase → *Get started* → accept.
2. Follow the link on your Android phone and install the **App Tester** app when asked. It lists builds and tells you about updates.
3. Download *Expense Tracker*. Allow "Install unknown apps" for App Tester when Android asks.

**Updates**
From this build on, the app tells you when there's a newer version: open it and tap **Update** on the *New version available* box. The first time, it asks you to sign in with the Google account the invite was sent to (once only). If you tap *Not now*, it asks again after a few hours.

**One extra step (Android 13 and newer)**
Android blocks some settings for apps installed outside the Play Store. Before turning on notification access or the accessibility setting:
> Settings → Apps → Expense Tracker → ⋮ (top right) → **Allow restricted settings**

Without this, the switches stay greyed out and GPay / PhonePe payments won't be captured.

**Your data**
The app reads bank payment SMS and UPI app notifications **on your phone only**. Your messages, payee names and amounts are never uploaded.
If you tap **Share counts** when asked, once a day the app sends the team anonymous counts (for example "30 payments sorted automatically, 27 kept", "app opened on 4 of the last 7 days", "budget set: yes") under a scrambled tester ID that isn't linked to your number or account (it stays the same if you reinstall, so you're counted once). You can see exactly what's sent, or turn it off, in Profile › tap Version 7× › Developer. Turn on **Sample data** (bubble at the bottom right) to try every screen with made-up payments.

**Please try**
- [ ] First open: grant SMS access, see your payments load (a grey skeleton first)
- [ ] Pull down on Home to refresh
- [ ] Tap the date under the big amount, try *Last 7 days* and a custom range, then a range with no payments
- [ ] Tap **+**, add a payment, then tap **Undo** in the message
- [ ] Tap a payment → change its category
- [ ] Answer "Did this payment go through?" if you see one
- [ ] Switch your phone between light and dark mode
- [ ] Open Review, Insights and Ask

**Reporting a problem**
A screenshot plus one line: what you did, what you expected, what happened. Don't send screenshots that show other people's names or your full SMS text.
