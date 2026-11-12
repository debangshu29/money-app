# Money — automatic spending tracker for Android

**Latest: version 1.1 — Budget.** Download: https://github.com/debangshu29/money-app/releases/download/latest/Money.apk

You never type an amount. Money reads your **bank SMS** and **payment notifications** (Google Pay, PhonePe, Paytm, CRED, bank apps) on your phone and fills everything in by itself: amount, who you paid, category, account and balance.

**Private:** everything stays on the phone. No account, no server, no cloud. The internet is used only to download new versions of the app from this repo. Backups go to the phone's own storage (`Documents/Money/Backups`), locked with your password.

## Budget (version 1.1)

1. **Home → ＋ Set a budget** (or Insights → Budget).
2. Enter the amount (e.g. ₹30,000), pick the **start date** (today, the 1st, or any date from the calendar), and how long it runs (1 month, 2 weeks, 1 week, or until a date).
3. Optional: **Don't count** some categories (e.g. Rent, EMI, Investments) and **repeat** it automatically.
4. Tap **Start budget**. From that date every payment counts against it, automatically.

While it runs, Home shows **₹ left · ₹ a day · days left**, a bar that turns orange at 80% and red when over, and *Where your budget is going* by category. The Budget screen adds day-by-day bars, every payment since the start, *Where the extra went* when you're over, and **Past budgets**. Alerts at 50%, 80%, 100%, and a summary when it ends. Category limits (e.g. Food ₹6,000 a month) are still there under Insights → Budget.

## What it does (version 1.0)

| | |
|---|---|
| **Automatic capture** | Bank SMS (as they arrive, plus a catch-up scan of the inbox every few hours), UPI app notifications, and bank SMS shown by the Messages app as a backup. 35+ banks, cards and wallets: SBI, HDFC, ICICI, Axis, Kotak, PNB, BOB, Canara, Union, UCO, Indian Bank, Bandhan, IDFC, Yes, IndusInd, Paytm… |
| **Counted once** | The same payment seen in an SMS *and* a Google Pay notification is merged (matched by UPI reference, amount, time and account). |
| **Missed SMS?** | Every SMS that shows a balance is checked: previous balance ± everything in between = new balance? If not, an **“Unrecorded”** entry shows the difference under *Needs a look*. It disappears by itself if the late SMS turns up. |
| **Ignores the noise** | OTPs, offers, loan ads, payment *requests*, “will be debited” reminders, failed transactions, and SMS from phone numbers (often fake “credited” messages). |
| **Smart categories** | Swiggy → Food, Uber → Transport, Jio → Recharge, CESC → Bills… Friends' UPI IDs → People. Change one and choose “all Swiggy payments” — new ones follow automatically. |
| **Home** | This month's spending vs. the same day last month, where the month is heading, budget left per day, today, money in, *Needs a look*, bills & renewals coming up, balances of every account, latest payments. |
| **Insights** | Any month: spent / money in / saved %, ring chart by category, day-by-day bars, top places, repeating payments (with price increases), budgets. |
| **Repeating payments** | Subscriptions, EMIs, rent, SIPs found by themselves, with the next expected date and a reminder 2 days before. |
| **Card bills** | Statement SMS → amount due + due date → reminder 3 days before (stops once a payment to that card arrives). |
| **Alerts** | Charged twice, unusual payments, budget at 80% / 100%, bills, renewals, evening summary (time is your choice). |
| **Ask** | “food this month”, “biggest expenses last month”, “how many times Uber”, “compare with last month”, “subscriptions”, “balance” — answered on the phone. |
| **Activity** | Every transaction by day, search (name, amount, note), filters, tap to see the original SMS, rename, recategorise, add a note, mark “not spending” (own-account transfers, card bill payments) or hide. Cash: **+** to add a cash spend. |
| **Privacy** | App lock (fingerprint / face / PIN), hide amounts (eye button), block screenshots. |
| **Backups** | Daily, automatic, encrypted (AES-256, your password), newest 7 kept in `Documents/Money/Backups`. Restore from any `.mbak` file. Export to CSV for Excel. |
| **Widget** | Today · this month · total balance on the home screen. |
| **Updates** | Same as the Note app: popup → Update → Install. Your data stays. |

## Install

1. On your phone open **https://github.com/debangshu29/money-app/releases/latest** and download **Money.apk**.
2. Tap it → allow *Install unknown apps* if asked → Install.
   - **Play Protect says “App blocked”?** In India Google blocks apps from outside the Play Store that read SMS or notifications. Play Store → profile picture → Play Protect → ⚙ → turn off *Scan apps with Play Protect* → install → **turn it back on**. Never do this because someone on a call asks you to.
3. Open Money and follow the checklist:
   - **Read bank SMS** → Allow
   - **Read payment notifications** → find *Money* → switch on
   - **Alerts**, **Keep working in the background** (and *Autostart* on Xiaomi / Oppo / Realme / Vivo)
   - **Bring in past messages** (3 / 6 / 12 months / everything)
   - **Backup password**

### “Restricted setting” / no permission popup?
Android 13+ protects SMS and notification access for apps installed from a file. Once: **Settings → Apps → Money → ⋮ (top right) → Allow restricted settings**, then go back and tap Allow again.

## Make sure messages keep coming
- Banks send an SMS for every debit/credit by default. If one bank's payments never show up, turn on SMS alerts for that account in its app or netbanking.
- Keep notifications **on** inside Google Pay / PhonePe / Paytm.
- If a message is still missed, the balance check catches it (see *Missed SMS?* above).

## Folder contents (`D:\money`)

```
D:\money
├── README.md            ← this file
├── ROADMAP.md           ← what's built, what's next
├── whats-new.txt        ← shown in the update popup
├── BUILD_APK.bat        ← only for a PC with Android Studio (GitHub builds the APK anyway)
├── keystore\money.p12   ← the app's signing key (made by GitHub, locked with your secret password)
└── app\src\main\
    ├── java\com\debangshu\money\
    │   ├── SmsParser.java     reads bank SMS (amount, account, who, balance, reference, card bills)
    │   ├── NotifParser.java   reads Google Pay / PhonePe / Paytm / bank-app notifications
    │   ├── Ledger.java        counted-once, missed-SMS check, repeating payments, totals, forecast
    │   ├── Cats.java          categories and the automatic guess
    │   ├── Ask.java           answers questions about your money
    │   ├── Engine.java        message → transaction, inbox catch-up
    │   ├── Store.java         saves everything on the phone (one JSON file, app-private)
    │   ├── Backup*.java       encrypted backups to Documents/Money/Backups, CSV export
    │   ├── Alerts.java        notifications, evening summary, reminders
    │   ├── SmsReceiver / PayListener / BgJob / DailyReceiver   background parts
    │   ├── MainActivity + HomePage / ActivityPage / InsightsPage / AskPage   the 4 tabs
    │   └── SetupActivity / SettingsActivity / TxnSheet / LockActivity / Widget / Updater
    ├── res\    colours, icons, widget layout
    └── AndroidManifest.xml
```

## How the APK is built
Every push to `main` → GitHub Actions builds and signs `Money.apk` → publishes it to **Releases** and the `updates` branch (the app checks there for new versions). The signing key is created by GitHub on the first build and locked with your repo Secret **`MONEY_KEY_PASSWORD`** (a password only you know); without it builds are test-only and not published.
