# Money — roadmap

## How the app is organised (from 1.5 — every new feature follows this)

| Place | Answers | Holds | Gets later |
|---|---|---|---|
| **Home** | How am I doing today? | Safe to spend today + budget bar · Today / This month (money in & out) · balances (3) · latest 5 · slim strips only when something needs you | nothing new — Home stays short |
| **Activity** | What did I pay? | List ⇄ Calendar, search, filters, + cash spend | split a payment (1.6) |
| **Scan & Pay** | Pay someone | round button in the middle | — |
| **Insights** | Where does my money go? | Ask bar · month view · categories · day by day · top places · where you spend | monthly report PDF, personal inflation, hidden charges (1.7) |
| **Plan** | What should I organise? | tiles: Budget (+ category limits) · Bills & subscriptions · Accounts · Cash wallet | Goals, People (split / lend), Challenges (1.6) · Net worth, Tax, Cards (1.8) |
| **Settings** (gear) | How should it behave? | capture, remember payments, alerts, privacy, backups, data, updates | fraud watch switches (1.7) |

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
| **1.5** | Daily helper + makeover | new layout (Home · Activity · Scan & Pay · Insights · Plan); Safe to spend today; cash wallet; spending calendar; “Where you spend” places; Ask moved into Insights; Accounts and Bills & subscriptions screens; category limits inside Budget | ✅ built (13 Nov 2026) — waiting for your check |
| 1.6 | People & goals | Split & IOU · lend / borrow · goals · no-spend challenges · subscription cleaner | planned |
| 1.7 | Smart insights | monthly report PDF · personal inflation · hidden charges · fraud watch | planned |
| 1.8 | Bigger picture | net worth · tax helper · credit card helper | planned |

## Ideas for later (not started)
- Bank statement PDF import (fills names + Unrecorded from the statement's UPI lines)
- Failed-payment refund tracker
- Read bank statement PDFs / emailed statements to fill old gaps
- Split with friends & lend / borrow tracker
- Net worth (FDs, mutual funds, gold) from statement SMS
- Spending goals and savings pots
- Trips (group a holiday's spending)
- On-device AI answers for longer questions
