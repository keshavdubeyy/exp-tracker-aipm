# Expense Tracker

An Android app that reads bank payment messages (SMS) and UPI app notifications,
sorts your spending into categories, and shows where your money goes.
Everything stays on the phone. Nothing is uploaded.

**You do not need an Android phone to try it.** Android Studio comes with a
free "pretend phone" (called an *emulator*) that runs on your computer.

---

## What you need

- A Mac or Windows laptop with about **15 GB free space** and **8 GB RAM** or more.
- **Android Studio** (free). Get the latest version: https://developer.android.com/studio
- **Git**. On Mac, type `git --version` in Terminal. If it is missing, your Mac
  will offer to install it. On Windows, get it from https://git-scm.com

That's all. Android Studio already includes Java and everything else the app needs.

---

## Step 1: Download the code

Open **Terminal** (Mac) or **Git Bash** (Windows) and type:

```sh
git clone https://github.com/keshavdubeyy/exp-tracker-aipm.git
```

This makes a folder called `exp-tracker-aipm` with the code inside.

## Step 2: Open it in Android Studio

1. Open Android Studio.
2. Click **Open**, choose the `exp-tracker-aipm` folder, and click **Open**.
3. Wait. The first time takes **5–15 minutes**: it downloads what the app needs.
   You'll see a progress bar at the bottom. Wait until it stops.
4. If a yellow bar asks you to install something (like an "SDK"), click
   **Install** and accept.

## Step 3: Make a pretend phone (one time only)

1. In Android Studio, open the menu **Tools → Device Manager**.
2. Click the **+** button, then **Create Virtual Device**.
3. Pick **Pixel 8** (any Pixel is fine) and click **Next**.
4. Pick the newest Android version in the list. If it shows a download icon,
   click it and wait. Then click **Next → Finish**.

## Step 4: Start the app

1. At the top of Android Studio, make sure your pretend phone is selected in the
   device box, and **app** is selected next to it.
2. Click the green **▶ Run** button.
3. The pretend phone opens in a window and the app starts on it. The first run
   takes a few minutes.

To start the app again later, just click **▶ Run** again.

---

## Step 5: Try the app with made-up payments

The pretend phone has no real bank messages, so turn on sample data:

1. Go through the welcome screens. When it asks for SMS access, tap **Allow**.
2. Look for a small bubble that says **Sample off** on the screen.
   Tap it so it says **Sample on**.
3. Now every screen fills with made-up payments. Try Home, Review, Insights and Ask.

**Want to send a fake bank SMS?** In the emulator window, click **⋯** (More) →
**Phone**. Type a sender like `AD-HDFCBK`, and a message like
`Rs 250.00 debited from a/c XX1234 to VPA shop@upi on 01-11-26.`, then click
**Send Message**. The payment should show up in the app.

---

## Getting the latest changes

When the team pushes new code, update your copy:

```sh
cd exp-tracker-aipm
git pull
```

Then click **▶ Run** in Android Studio again.

---

## For developers (optional, command line)

These do the same thing as the ▶ button, from Terminal. A pretend phone or a
real phone must already be running and connected.

```sh
./gradlew :app:installDebug   # install the app (easy to debug, a bit slow to draw)
./gradlew :app:installFast    # install a faster version, with Sample data
./gradlew :app:testDebugUnitTest  # run the tests
```

On Windows, write `gradlew.bat` instead of `./gradlew`.

If Terminal says Java is missing, use the Java that came with Android Studio:

```sh
# Mac
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

---

## Something not working?

| Problem | What to do |
|---|---|
| Red errors right after opening the project | Wait for the progress bar to finish, then **File → Sync Project with Gradle Files**. |
| "SDK not found" or "SDK 37 missing" | Click the **Install** link in the error, or open **Tools → SDK Manager** and tick the newest Android version. |
| The ▶ button is grey | Pick your pretend phone in the device box at the top. |
| The pretend phone is very slow | Close other apps. Give it a minute after it first starts. |
| Install fails with "version downgrade" | On the pretend phone, uninstall the app (press and hold its icon → Uninstall) and click ▶ again. |

---

## Rules for the team

Read [AGENTS.md](AGENTS.md) before changing code. The short version:

- **Never commit real SMS messages** or screenshots of them. Use made-up ones.
- All money math is plain code in `insights/`. AI never does the math.
- Every screen follows [DESIGN.md](DESIGN.md). Every button follows [FEEDBACK.md](FEEDBACK.md).
- What we're building now is in [MVP.md](MVP.md).
