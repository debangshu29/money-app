# Money — roadmap

## How the app is organised (from 1.5 — every new feature follows this)

| Place | Answers | Holds | Gets later |
|---|---|---|---|
| **Home** | How am I doing today? | Safe to spend today + budget bar · Today / This month (money in & out) · balances (3) · latest 5 · slim strips only when something needs you | nothing new — Home stays short |
| **Activity** | What did I pay? | List ⇄ Calendar, search, filters, + cash spend · tap a payment → Split / lend | — |
| **Scan & Pay** | Pay someone | round button in the middle | — |
| **Insights** | Where does my money go? | Ask bar · month view + savings score + 📄 report · categories · day by day · top places · where you spend · prices you pay · charges & fees | — |
| **Plan** | What should I organise? | Everyday: Budget · Bills & subscriptions · Accounts · Cash wallet · Renewals & reminders · Refunds — Grow: Goals · Challenges — Together: People · Net worth — Tools: Tax helper · Credit cards · Loans & EMIs · Cashback & rewards | new tiles go into one of these groups |
| **Settings** (gear) | How should it behave? | capture, remember payments, alerts (incl. fraud watch), privacy, backups, data, updates | — |

Design rules: one main number per card · details open on tap · a new feature = a new Plan tile or Insights card, never a new Home card · plain words, no finance jargon · every card has an empty state that says what to do.

Built one version at a time; each version is checked on the phone before the next one starts.

| Version | Theme | Features | Status |
|---|---|---|---|
| **1.0** | Automatic tracking | bank SMS + UPI app notifications, counted once, missed-SMS balance check, smart categories that learn, Home / Activity / Insights / Ask, repeating payments, card bills, budgets, alerts, evening summary, app lock, hide amounts, encrypted backups on the phone, CSV export, widget, in-app updates | ✅ built (12 Nov 2026) |
| **1.1** | Budget | start a budget with an amount + start date (calendar) + length (month / 2 weeks / week / custom), leave out categories, repeat, Home budget card, “where it's going”, day-by-day, payments since start, “where the extra went”, past budgets, 50/80/100% alerts, end summary, widget shows budget left, Play Protect help | ✅ built (12 Nov 2026) |
| **1.2** | New Home + fewer “Unrecorded” | Home order Budget → Balances → Month (in/out) → Today (in/out) → Latest → Coming up → Needs a look; smooth green→maroon budget bar; an eye per section; tap Money in / out for where it came from / went; missed-SMS check links app-only payments, fixes out-of-order SMS, hides double counts, links debit cards to their account, labels bank charges & interest; “Sort them out” with SMS clues and one-tap answers; list of bank messages that couldn't be read | ✅ built (12 Nov 2026) |
| **1.3** | Never forget a payment | 📍 location of each payment + map button + area name (switchable); 🔔 “Who was it?” notification you type into; 🧠 name a UPI ID / QR once; 🤔 habit guess “Probably …?”; 📷 bill photo | ✅ built (12 Nov 2026) |
| **1.4** | Scan & Pay | round raised Scan & Pay button in the bottom bar; scan a UPI / Bharat QR → name, category, note → UPI app opens filled in → the bank's message gets it all; “Pay in my app” fallback; type a UPI ID (long-press) | ✅ built (12 Nov 2026) |
| **1.4.1** | Fix wrong accounts | long-press an account: rename, correct the balance, merge two accounts, delete (with or without its payments), restore in Settings | ✅ built (12 Nov 2026) |
| **1.5** | Daily helper + makeover | new layout (Home · Activity · Scan & Pay · Insights · Plan); Safe to spend today; cash wallet; spending calendar; “Where you spend” places; Ask moved into Insights; Accounts and Bills & subscriptions screens; category limits inside Budget | ✅ built (13 Nov 2026) |
| **1.6** | People & goals | 👥 People: split / lend / borrow from any payment, settles itself when the money comes back, WhatsApp reminder with your UPI ID · 🏆 Goals with “on track?” from what you usually save · 🔥 Challenges (no-spend, no delivery, no shopping, daily limit) with streaks · 🗑️ subscription cleaner (yearly cost, price rises, “still using it?”) | ✅ built (13 Nov 2026, released as 1.8) |
| **1.7** | Smart insights | savings score · monthly report PDF (Documents/Money/Reports) · prices you pay (same place, 3 months vs 3 before) · charges & fees for the financial year · 🛡️ fraud watch (night, foreign, big first payment) with a Settings switch | ✅ built (13 Nov 2026, released as 1.8) |
| **1.8** | Bigger picture + final polish | 💎 net worth (fills itself + gold / FD / loans you add, month by month) · 🧾 tax helper (80C, 80D, NPS, rent, 80G; leave out / add; CSV) · 💳 credit cards (limit used, bill, statement day, best card today) · Plan grouped into Everyday / Grow / Together / Tools | ✅ built (13 Nov 2026) |

| **1.9** | Takes care of the boring stuff | ↩️ failed payment & refund tracker (kept out of spending, auto-linked refund, day-5 / day-10 reminder with the RBI T+1 / T+5 rule) · 🔔 renewals from SMS (mobile plan, insurance, PUC, FASTag low) + your own · ⏰ return & warranty dates (asked after a bill photo) · 🏦 loans & EMIs (months left, interest left, prepay savings, EMI-due reminder, in net worth) · ⭐ cashback & card points · 🎤 say a cash spend · Scan & Pay widget + quick-settings tiles | ✅ built (13 Nov 2026) |
| **1.9.1** | Fixes | Scan & Pay passes the QR untouched + remembered UPI app + “Scan in my app” fallback when an app refuses · re-reads payment notifications still showing · more Google Pay / PhonePe wordings · unread app notifications in “Couldn't read” | ✅ built (13 Nov 2026) |
| **1.9.2** | No payment slips through | Scan & Pay = note in Money → pay in your UPI app → “Paid ₹__?” on return · bank alert e-mails from Gmail notifications · add UPI payments by hand / voice · weekly balance check · noted payments merge with the later SMS | ✅ built (13 Nov 2026) |
| **1.9.3** | Removed | Accessibility “read payment screens” removed at your request (didn't work on your phone) | ✅ built (13 Nov 2026) |
| **2.0** | Remember why you paid | Pay & note (UPI app list → “Paid? ₹ to whom for what”) replaces Scan & Pay · instant “what for?” only for unusual payments + one-tap reasons + auto note for regulars · usage access “Paid anyone on Google Pay?” ~1 min after leaving a UPI app · bank statement check (PDF / CSV / Excel, match / add / not-in-statement) + monthly reminder · names from contacts | ✅ built (13 Nov 2026) |
| **2.0.1** | Your banks | statement layouts: SBI, Union (Vyom), Kotak, IndusInd, Jupiter/Federal + HDFC, ICICI, Axis, Canara, PNB · Jupiter credits/debits on one account · IndusInd / Federal SMS fixes · 18 more banks for friends & family | ✅ built (13 Nov 2026) |
| **2.1** | Tidy & calm | Settings → 6 groups + status card · Plan → budget card, “Needs you”, round icons in Money / Life / Grow / Tools · Home quick actions · Insights “More insights” list | ✅ built (13 Nov 2026) — waiting for your check |
| **2.1.1** | Right account | “Paid from” Cash / exact account (••last4) for added, spoken and noted payments · live balance in Money · delete/hide restores balance & cash wallet · move between accounts · voice “from Federal ending 1234”, asks when two match | ✅ built (13 Nov 2026) — waiting for your check |

## Ideas for later

- Later maybe: calendar as memory (auto note from the event at that time) · photos around the payment time in a review · plan-first list (“milk 60, auto 40”) matched to payments

- ❌ Tried and dropped: reading UPI apps' screens via Accessibility (1.9.2 → removed in 1.9.3) (not started)
- Bank statement PDF import (fills names + Unrecorded from the statement's UPI lines)
- Read bank statement PDFs / emailed statements to fill old gaps
- Investment values (mutual funds, stocks) from statement SMS / emails
- Trips (group a holiday's spending)
- On-device AI answers for longer questions
