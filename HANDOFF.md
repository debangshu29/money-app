# Money — handoff for a new Claude chat

**Read this file first, then README.md and ROADMAP.md.** It holds everything from earlier chats that isn't obvious from the code.
Last updated: 13 Nov 2026, after version **2.2** (versionCode 18, commit 336ce16).

## Who and how we work
- Owner: **Debangshu Ghosh**, Kolkata. Claude writes all the code; Debangshu supervises and tests on his phone.
- Repo: `github.com/debangshu29/money-app`. A copy of the code lives on his PC in **D:\money** "so I can check whenever needed and continue your work".
- Before a big change, **say the plan in plain words first** and wait for "ok" when the change is large or the idea is new. Small fixes: just do them.
- He talks in plain, non-technical language (often voice-typed). Answer simply; no jargon.

## What he wants from the app (standing preferences)
- **Fully automatic**: catch payments by itself; typing should be rare.
- **Instant only**: notifications at the moment of payment. **No evening review**. Only ask "what for?" when a payment is **unusual**.
- **Privacy**: everything stays on the phone. Backups go to internal storage, **not** Google Drive. No cloud, no ads. The internet is used only for update checks.
- **Look**: minimal, premium, Google Pay-like. Settings must not be overwhelming. Fewer words. Line icons (2.2).
- Must work for **friends and family** too, with any common Indian bank, not just his.
- Removed on purpose, so don't bring them back: Accessibility screen reading (1.9.3, "doesn't work"), Scan & Pay through UPI intents (became Pay & note), Net worth and Rewards (2.2, "I have to fill net worth myself; don't know rewards").

## His banks (statement and SMS layouts are tuned for these)
SBI, IndusInd, Union Bank, Kotak, Jupiter (Federal Bank; he has **two Federal accounts**).
- Kotak sends SMS only for debits above ₹100 and credits above ₹500.
- Jupiter: debit SMS come from Federal Bank, credit SMS from Jupiter. Both map to "Federal".
- He doesn't use UPI Lite.

## How the app is built
- Pure Java Android, UI written in code (no XML layouts), minSdk 24, Java 8 (no `String.join`, no streams). Only dependency: `com.tom-roush:pdfbox-android:2.0.27.0`.
- Package `com.debangshu.money`, all in `app/src/main/java/com/debangshu/money/`.
- Key pieces:
  - **Capture**: `SmsReceiver`, `PayListener` (notifications, including Gmail bank alerts), `Engine`, `SmsParser`, `NotifParser`.
  - **Joining the same payment from SMS and app**: `Ledger.findTwin`.
  - **Data**: `Store` (JSON, `list/putList/obj/putObj`, flags), `Txn`, `Account`.
  - **Txn sources**: SMS=1, APP=2, GAP=4, MANUAL=8, NOTE=16, STMT=32. NOTE means a bank payment you added yourself; it merges with a later SMS or app message within 3 hours.
  - **Live balance** (`Store.live`): the bank's last balance plus every payment since, so payments you add yourself move the balance and deleting one restores it.
  - **"Paid from"** (`Noted.fromPicker`): tap Cash or an exact account; it shows bank, ••number and balance.
  - **Voice**: `Voice`/`VoiceActivity` understand "50 for Lays, paid from Union Bank", "in cash" and "ending 1234".
  - **Asking**: `Reasons` (the "what for?" and "Paid anyone?" notifications), `UsageWatch` (a UPI app was open but no payment came), `PayNote` (Pay & note).
  - **Statement check**: `StatementParser`, `Reconcile`, `StatementActivity`.
  - **Screens**: `HomePage`, `ActivityPage`, `InsightsPage`, `PlanPage`, `SettingsActivity` (pages), `TutorialActivity` (tour + Settings → Tutorials).
  - **UI helpers**: `Ui` (`navRow`, `action`, `grid`, `iconBadge`, `chevron`, `Popup`).
  - **Icons**: emoji map to Lucide line icons (`res/drawable/ln_*.xml`) through `Ui.iconFor`. Add new icons with a small SVG-to-vector converter: Lucide comes from npm `lucide-static`, then circle/rect/line/polyline elements are turned into paths.

## Release flow (every version)
1. Bump `versionCode`/`versionName` in `app/build.gradle`, and add notes at the **top** of `whats-new.txt`, `README.md` and `ROADMAP.md`.
2. Commit as Debangshu (`debangshu29@users.noreply.github.com`) and push to `main`.
3. GitHub Actions builds and signs the APK. The signing key comes from his own secret (`MONEY_KEY_PASSWORD`): **never print, store or ask for it**.
4. Check `https://raw.githubusercontent.com/debangshu29/money-app/build-log/status.txt` for `success <commit> signed=yes`, then check that `updates/version.json` shows the new version. The app updates itself from the `updates` branch.
5. Copy the changed files and `Money.apk` (from the `updates` branch) to **D:\money** through the desktop-app link. Claude can't delete files there, so tell him which ones to delete by hand.
6. A quick check before pushing: compile against android.jar, and run the desktop tests for the plain-Java parsers (`SmsParser`, `Voice`, `StatementParser`, …).

## What's next: 2.3 "Grow: your money coach" (agreed, not started)
He said: "make it more goal specific … more helpful towards goal … so you can save more money … rules like 50 30 20 … tips … so a user can be guided and track themselves and their money."
Planned:
- **50/30/20 tracker**: spending sorted automatically into Needs (rent, groceries, bills, EMIs, travel to work, medicine) / Wants (food delivery, shopping, fun) / Saved. Each month shown as "Needs 54% · Wants 33% · Saved 13%" with what pushed it over. Other rules to choose from: 70/20/10, 60/20/20, pay yourself first.
- **Goals 2.0**: link a goal to an account (e.g. the second Federal account as a "laptop pot"), "on track / behind by ₹X", the real finish date, and what-ifs ("cut food delivery by ₹1,000 a month and you reach it 2 months sooner").
- **Emergency fund**: 3–6 months of his own Needs spending, as the first goal.
- **Money health check**: one score from savings rate, emergency-fund months, EMIs as a share of income, budget kept and goal progress, each part with one tip.
- **Leak finder**: subscriptions you don't use, bank fees, delivery charges, small repeated spends.
- **Wait-list**: a 7- or 30-day wait before buying something you want.
- **Short lessons using his own numbers**: 50/30/20, emergency fund, pay yourself first, SIP basics, which loan to pay off first, paying the full card bill, rule of 72. These are general guidance, not financial advice.
- Grow then becomes its own Plan section again (right now Plan shows "Grow & tools": Goals, Challenges, Tax, Statement).

## Waiting on him
- Feedback and screenshots on 2.2's new look.
- D:\money was not synced for 2.1–2.2 because the desktop link to the old chat broke. He got `money-update-2.2.zip`. He also needs to delete by hand: `ScanPay.java`, `ScreenWatch.java`, `ScreenParser.java`, `NetWorthActivity.java`, `Rewards.java`, `res\xml\screen_watch.xml`.
