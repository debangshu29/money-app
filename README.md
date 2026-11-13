# Money — automatic spending tracker for Android

**Latest: version 2.1.1 — payments you add yourself come from the exact account (Cash, Federal ••1234…), balances update right away, and deleting puts the money back.** Download: https://github.com/debangshu29/money-app/releases/download/latest/Money.apk

You never type an amount. Money reads your **bank SMS** and **payment notifications** (Google Pay, PhonePe, Paytm, CRED, bank apps) on your phone and fills everything in by itself: amount, who you paid, category, account and balance.

**Private:** everything stays on the phone. No account, no server, no cloud. The internet is used only to download new versions of the app from this repo. Backups go to the phone's own storage (`Documents/Money/Backups`), locked with your password.

## Finding your way (version 1.9)

| Tab | What's there |
|---|---|
| 🏠 **Home** | **Safe to spend today** (with a budget) or *Spent this month* · Today ⇄ Month: Money out / Money in (tap for where it went) · Balances · Latest 5 · strips for “needs a look” and bills due soon |
| 📄 **Activity** | **List ⇄ Calendar** — the calendar colours each day by spending (green within your daily budget share, red over); tap a day for its payments · search · filters · **+** for a cash spend |
| ⭕ **Pay & note** | the round button: pick a UPI app, pay, then note who & what for |
| 📊 **Insights** | **Ask** bar · any month: spent / in / saved + **savings score** + **📄 Report** (PDF) · categories · day by day · top places · **Where you spend** · **Prices you pay** · **Charges & fees** |
| 🧩 **Plan** | *Everyday:* Budget · Bills & subscriptions · Accounts · Cash wallet · Renewals & reminders · Refunds — *Grow:* Goals · Challenges — *Together:* People · Net worth — *Tools:* Tax helper · Credit cards · Loans & EMIs · Cashback & rewards |

**Safe to spend today** = (budget left at the start of today − repeating bills still to come) ÷ days left − what you've spent today.

**Cash wallet** (Plan → Cash wallet): ATM withdrawals go into the wallet instead of counting as spending. Every few days Money asks “how much cash is left?” (type it in the notification); the difference counts as cash spent. You can still note a cash spend with **+** in Activity.

## Finding your way (version 2.1)

| Where | What's there |
|---|---|
| 🏠 **Home** | Safe to spend / this month · quick actions (**Add · Say it · Accounts · Ask**) · today / month spending · balances · latest · a slim strip only when something needs you |
| 📋 **Activity** | every payment — list or calendar, search, filters |
| ⭕ **Pay & note** | the round button: pick a UPI app, pay, then note who & what for (long-press: add by hand) |
| 📊 **Insights** | Ask bar · month summary + savings score · where it went · day by day · **More insights** (top places, map, prices you pay, charges & fees, monthly PDF) |
| 🧩 **Plan** | budget card · **Needs you** (late refunds, renewals, card bills — only when there are any) · round icons: **Money** (Accounts, Cash, Bills, Cards) · **Life** (People, Reminders, Refunds, Loans) · **Grow** (Goals, Challenges, Net worth, Rewards) · **Tools** (Tax, Statement, Add payment, Settings) |
| ⚙️ **Settings** | status card (“All set” / what to fix) + 6 groups: Payment capture · Questions & reminders · Names & places · Privacy & security · Backup & data · Updates & about |

## Banks (version 2.0.1)

**SMS / notifications:** every bank that sends SMS with an amount — including SBI, HDFC, ICICI, Axis, Kotak, PNB, BOB, Canara, Union Bank, IDFC First, Yes, IndusInd, IDBI, Bank of India, Indian Bank, UCO, Central Bank, Bandhan, AU, Federal / Jupiter / Fi, RBL, SC, Citi, HSBC, Paytm Payments, Airtel Payments, IPPB, KVB, South Indian, City Union, DBS, Equitas, Ujjivan, IOB, Bank of Maharashtra, Punjab & Sind, J&K, Karnataka Bank, DCB, Jana, TMB, Saraswat, Fino, Jio Payments, NSDL Payments, Suryoday, ESAF, Utkarsh, Dhanlaxmi, CSB, Kerala Gramin.

**Jupiter:** debits come by SMS from *Federal Bank*, small credits from *Jupiter* (often with the account number fully hidden) — Money puts both on your one Federal/Jupiter account (rename it to “Jupiter” from Accounts if you like).

**Small payments without SMS:** some banks skip SMS for small UPI payments (HDFC: up to ₹100 sent / ₹500 received; Kotak the same) — the UPI app's notification, the bank's e-mail alert, “Paid anyone?” and the monthly statement fill that gap.

**Statements tested on these layouts:** SBI · Union Bank (Vyom, with S.No / Tran Id, “(Dr)/(Cr)”, amounts on the next line) · Kotak (“06 Sep, 2024”, +/− amounts) · Federal Bank / Jupiter (UPIOUT / UPIIN) · HDFC · ICICI · Axis · Canara (multi-line rows) · PNB (amount before the description) · CSV / Excel from any bank's net banking. Any other bank: if the PDF isn't understood, the CSV / Excel download always works.

**For friends & family:** install the same APK — setup asks for SMS + notification access, everything stays on their phone, and updates come the same way.

## Remember why you paid (version 2.0)

| What | How it works |
|---|---|
| **Pay & note** (middle button) | Shows every UPI app on your phone → tap one → pay there as usual → back in Money: **“Paid ₹__ to __ for __?”** with one-tap reasons (and the people / places you pay often). If the bank SMS or app notification already came, it only asks *what for*. Long-press the button: add a payment by hand. Also on the widget and the quick-settings tile. |
| **“What for?” right away** | The moment a payment is caught: **“₹1 to Ramesh — what for?”** with 2 one-tap reasons + ✏️ Type. **Only when it's unusual** — a new person or place, or a much bigger amount than usual. Regulars (the same kirana for milk) get their usual note by themselves, no question. |
| **“Paid anyone on Google Pay?”** | With **Usage access** (Settings → *“Paid anyone?” after using a UPI app*), Money sees *when* a UPI app was open — never what was on screen. About a minute after you leave it, if no payment message came, it asks; type “50 Ramesh for test” in the notification. Visits under 12 seconds (a balance check) are ignored. |
| **Check with a bank statement** | Settings / Accounts → *Check with a bank statement*, or share the file to Money. PDF (password asked, not saved), CSV or Excel (.xlsx). Money matches every row, **adds what it missed** (name, UPI ID and the note you typed in the UPI app, when your bank prints it), fills in missing details, and lists anything Money has that the bank doesn't. A reminder comes early each month. |
| **Names from contacts** | UPI IDs that are just a phone number (9830012345@ybl) get the name from your contacts — looked up on the phone, never stored. |

**Answering in a notification:** “Who was it?” takes a reason too — *Ramesh - test*.

## No payment slips through (version 1.9.2)

**Why Money can't pay directly (1.9.2 → 2.0):** UPI apps (Google Pay, PhonePe…) only fully trust payments that start inside them; a payment link sent by another app is often refused (“limit”, “security reasons”) unless that app is a registered payment partner. So Money doesn't try: you pay in your app, Money asks *what for* (2.0: **Pay & note**).

1. (1.9.2) Scan & Pay in Money → name, category, note — replaced in 2.0 by **Pay & note** (no scanning in Money).
2. **Open Google Pay** (or the app you picked once) → scan the same QR there and pay.
3. Back in Money: **“Paid ₹__ to Sharma Kirana?”** → Yes → saved right away. If the bank SMS / app notification comes later, it joins this entry (no double).

**Catching payments that send nothing:**

| Way | What it catches | Turn on |
|---|---|---|
| **Bank alert e-mails** | banks that e-mail small UPI payments instead of texting (HDFC) — read from Gmail's notification, only bank senders | on by default (Settings) |
| **Add it yourself** | + → *UPI / bank*, or 🎤 “500 to Ramesh on Google Pay” | — |
| **Weekly balance check** | type your balance once a week (notification) → anything not explained becomes *Unrecorded* while you remember | on by default; any time: Accounts → long-press → *Check balance* |

## How quickly Money catches a payment (version 1.9.1)

| Source | When |
|---|---|
| Bank SMS | the moment it arrives; the inbox is also re-checked every time you open Money and every 3 hours in the background |
| Payment-app notification (Google Pay, PhonePe, Paytm, super.money…) | the moment it's posted — needs **notification access** on. When you open Money it also re-reads payment notifications still in the shade |
| Neither came | the next bank SMS with a balance shows a gap → an **Unrecorded** entry you can name |

Some banks don't text small UPI payments (HDFC: no SMS for UPI payments up to ₹100 or money received up to ₹500 — e-mail only). For those, the payment app's notification is the only signal, so keep notification access on and don't swipe the notification away before Money has read it.

## The boring stuff, handled (version 1.9)

- **Refunds** — when a payment fails but money leaves the account (“debited … failed, will be reversed”), Money keeps it out of your spending and watches for it to come back; the refund is linked by itself. If it isn't back in 5 days (and again at 10) you get a reminder with the RBI rule: failed UPI / IMPS transfers must be returned by the next day, shop / card / ATM within 5 days, and after that the bank owes ₹100 a day. Mark any payment yourself: open it → **❌ Payment failed?**
- **Renewals & reminders** — mobile plan expiry (Jio / Airtel / Vi / BSNL), insurance renewal, PUC expiry and FASTag balance are read from SMS. Reminders: plan 3 / 1 / 0 days before, insurance 30 / 7 / 1, PUC 15 / 3 / 0. Add anything else yourself (repeats monthly / yearly).
- **Return & warranty** — after a bill photo (shopping or ₹1,000+), or from a payment → **⏰ Return / warranty**: pick “return by” (7 / 10 / 30 days) and “warranty till” (6 months / 1 / 2 years). Reminded 2 days before the return window ends and 30 / 7 days before the warranty does.
- **Loans & EMIs** — loans appear from EMI payments and lender SMS (“EMI of ₹4,567 received”, “principal outstanding …”). Add what's left + the interest rate once → months left, interest left, and **what a prepayment saves** (₹10k / ₹50k / ₹1L / any). A reminder 2 days before the EMI; loans count in Net worth.
- **Cashback & rewards** — every cashback credit this financial year, where it came from, and each card's reward points balance (from the bank's points SMS).
- **Say a cash spend** — 🎤 in Activity → +, in the cash wallet, on the widget or the tile: “200 for chai”, “spent two hundred and fifty on auto”, “chai 20 and samosa 30”. Uses the phone's own voice typing; Undo right after.
- **Pay & note without opening the app** — long-press the home screen → Widgets → *Money – Pay & note*; or pull down the quick settings → ✏️ edit → drag in **Pay & note** / **Say a payment**. With app lock on, it asks for the lock first.

## People, goals and the bigger picture (version 1.8)

- **Split / lend** — open any payment → *👥 Split / lend* (or *🤝 Borrowed / paid back* on money in). Pick the people; when one of them later sends you that amount, Money marks it settled and doesn't count it as income. Plan → People shows who owes what, with a WhatsApp reminder that includes your UPI ID.
- **Goals** — something to save for, with a date. Money compares what it needs each month with what you usually save (money in − money out, last 3 months).
- **Challenges** — no-spend days, no food delivery, no shopping, or a daily limit; streaks and the last 14 days as dots.
- **Subscription cleaner** — in Bills & subscriptions: what each costs a year, price rises, mark “cancel this” or “still using it”.
- **Savings score & monthly report** — Insights: a 0–100 score and a 📄 PDF of the month saved to `Documents/Money/Reports`.
- **Prices you pay** — the same shop / app costing more than 3 months ago. **Charges & fees** — SMS charges, annual / late fees, penalties this financial year.
- **Fraud watch** — a “Was this you?” alert for payments late at night, abroad / in foreign currency, or big first-time payments (Settings → Alerts).
- **Net worth** — bank + cash + investments + money owed to you − card bills − loans; add gold, FDs or loans once.
- **Tax helper** — April–March: 80C, 80D, NPS, rent, donations found in your payments, with limits; leave out / add; save as CSV. Only for the old regime, and not tax advice.
- **Credit cards** — % of limit used (under 30% is good), latest bill, statement day and the **best card to use today** (longest interest-free time).

## Fixing a wrong account (version 1.4.1)

Long-press (or tap) an account in **Balances**:
- **Correct the balance:** type what your bank app shows; Money uses it from now on and the missed-SMS check restarts from there.
- **Same as another account (merge):** for duplicates (e.g. a debit card shown separately from its bank account) — all its payments move over, and future messages too.
- **Delete account:** keep its payments in your history, or delete them too (for accounts that aren't really yours). Deleted accounts don't come back from new messages; bring one back in *Settings → Your data → Deleted accounts*.

## Scan & Pay (version 1.4 — replaced by Pay & note in 2.0)

Tap the round **Scan & Pay** button in the middle of the bottom bar:
1. Scan the shop's UPI QR (Google's scanner opens — Money itself needs no camera permission).
2. Money shows the shop's **name and UPI ID** (or the name you gave it before), the amount (fixed if the QR has one), a **category**, and an optional note.
3. **Pay** → Google Pay / PhonePe / Paytm opens with everything filled in; you enter your UPI PIN there. Money never sees the PIN.
4. When the bank SMS / app notification arrives, the payment automatically gets that name, category and note — and every future payment to that UPI ID too.

If a UPI app refuses payments started from another app, use **Pay in my app**: Money remembers who you're paying, opens your UPI app, you scan there, and the payment is still matched (by amount and time). **Long-press** the button to type a UPI ID instead of scanning.

## Remember every payment (version 1.3)

Turn on in **Settings → Remember every payment**:
- **Save where you paid:** when a payment is caught, Money checks the phone's location once and saves it with the payment. Tap the payment → 📍 *near Garia Station Road* → **Map**. Needs location “Allow all the time” (payments are caught in the background).
- **Show area names:** on = street / area name (uses Google's address lookup); off = map pin only, nothing sent.
- **Ask “Who was it?”:** a payment without a name gets a notification — type the name right there. If Money has a guess from your past payments (same spot, amount, time) it offers **Yes, Sharma Kirana**.
- **Name once:** a name you give to a UPI ID / shop QR is used for every future payment to it, with its category.
- **Bill photo:** open any payment → 📷 Add bill photo (kept inside the app; not included in backups yet).

## Home (version 1.2)

Top to bottom: **Budget** (smooth green → yellow → orange → red → deep-red bar) · **Balances** · **This month** · **Today** · **Latest** · **Coming up** · **Needs a look**. Each section has its own 👁 to hide just its amounts. *This month* and *Today* show **Money out** and **Money in** — tap either to see where the money went / came from, by category, by place, and every payment.

### “Unrecorded” — why it appears and how it's handled
When a bank SMS shows a balance that doesn't add up with the messages before it, money moved without a message. Before calling it “Unrecorded”, Money now tries to explain it:
1. a Google Pay / PhonePe payment that had no SMS → linked to that account
2. two SMS that arrived in the wrong order → re-ordered
3. the same payment counted twice → the extra one is hidden
4. debit-card spends → put on the bank account behind the card
5. small amounts → labelled *Bank / SMS charges (probably)* or *Interest (probably)* without asking

What's left goes to **Needs a look → Sort them out**: it shows bank SMS from that time Money couldn't read (tap **This is it** to add it) and nearby payments, or you answer in one tap (Cash, Rent, Paid someone, To my other account…). *Settings → Bank messages Money couldn't read* lists messages to share so the reader can learn your bank's wording.

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
    │   ├── MainActivity + HomePage / ActivityPage / InsightsPage / PlanPage   the 4 tabs
    │   ├── People / Goals / Challenges / NetWorthActivity / TaxActivity / CardsActivity   Plan screens
    │   ├── Smart.java / Report.java   price changes, fees, savings score, monthly PDF
    │   ├── Notices.java / Voice.java   failed payments, renewals, EMIs, points from SMS · “200 for chai”
    │   ├── Refunds / Reminders / Loans / Rewards   their Plan screens · PayWidget / ScanTile / VoiceTile / VoiceActivity
    │   └── SetupActivity / SettingsActivity / TxnSheet / LockActivity / Widget / Updater
    ├── res\    colours, icons, widget layout
    └── AndroidManifest.xml
```

## How the APK is built
Every push to `main` → GitHub Actions builds and signs `Money.apk` → publishes it to **Releases** and the `updates` branch (the app checks there for new versions). The signing key is created by GitHub on the first build and locked with your repo Secret **`MONEY_KEY_PASSWORD`** (a password only you know); without it builds are test-only and not published.

## Adding a payment yourself (version 2.1.1)
- **Paid from**: pick 💵 Cash or a bank account. Accounts at the same bank show their last digits (Federal ••1234 / Federal ••5678).
- The balance shown in Money for that account drops right away; the Accounts page says “+N you added” until the bank's own balance catches up.
- Delete / hide the entry → the balance goes back. Move it with “Paid from ▾” on the payment.
- Voice: “20 for chips from Federal ending 1234”, “10 for chips in cash”. With two matching accounts it asks which one.
