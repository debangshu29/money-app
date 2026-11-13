# Money — handoff for a new Claude chat

**Read this file first, then README.md and ROADMAP.md.** It holds everything from earlier chats that isn't obvious from the code.
Last updated: 14 Nov 2026, after version **2.6** (versionCode 23).

## Who and how we work
- Owner: **Debangshu Ghosh**, Kolkata. Claude writes all the code; Debangshu supervises and tests on his phone.
- Repo: `github.com/debangshu29/money-app`. A copy of the code lives on his PC in **D:\money** "so I can check whenever needed and continue your work".
- Before a big change, **say the plan in plain words first** and wait for "ok" when the change is large or the idea is new. Small fixes: just do them.
- He talks in plain, non-technical language (often voice-typed). Answer simply; no jargon.

## What he wants from the app (standing preferences)
- **Grow content must be explained like to a 5-year-old** (laddoos, piggy banks, money trees, umbrellas…), with clickable things that first TEACH (story + example with the user's own numbers + what changes if they follow it) and only then let them choose. Put new knowledge text in `Explain.TOPICS` and show it with `Explain.box(...)`. Lessons in 2.7 must follow this style.
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

## The agreed roadmap (he said OK on 13 Nov 2026)
He wants **Grow to be ~40% of the app** — the most impactful part. Every tool must be **interactive** (sliders, live graphs) and easy to understand.
- **2.3 Grow tab & goals — DONE**: 5th tab; Goals 2.0 (GoalSetup 3 steps, tap the bank account, live status from the account's balance, pace from daily snapshots, what-if sliders: save more / spend less on a category, projection graph `Charts.Line`, photo, pause, add/take out, history); split my saving; last month's leftover card; festival planner (`GoalMath.FESTIVALS` dates 2025–2028 — extend the table later); where goal money could sit; badges (`Goals.BADGE_LIST`) + celebrations + a notification when an SMS pushes a goal over 25/50/75/100%.
- **2.4 Save more — DONE** (`SaveMath` tested by T18, `MoneyFacts`, RuleActivity, HealthActivity, LeaksActivity, WaitListActivity, HabitActivity, FutureActivity, SalarySplitActivity, `Slider`, hours/jar sheets in GrowPage, 2 new challenges, 5 new badges): 50/30/20 (+70/20/10, pay yourself first) · leak finder · wait-list (7/30 days) · more challenges (no-delivery week, 52-week, ₹100/day) · hours of work · money health score (tap to see why) · true price of a habit (cut slider → 10-year value) · runway meter · month-end forecast · salary-day split (instant notification on salary SMS) · future you / freedom number · round-up jar · goal impact on unusual big payments.
- **2.5 Calculators & play tools — DONE** (`CalcMath` tested by T19, `Calcs` = 20 tools as data, `CalcActivity`, `CalcHub`; PPF default 7.1% (Oct–Dec 2026), NPS non-govt up to 80% lump sum since Dec 2025 — update these defaults when rules change) (all sliders + live graphs, reuse `Charts.Line`): SIP / step-up · lump sum · FD · RD · PPF · NPS · gold · compare all (FD/RD/PPF/bonds/gold/MF/stocks with risk, lock-in, liquidity) · cost of waiting · money time machine (inflation on his own spends) · snowball switch (interest on interest on/off) · investment race · market crash test · tax & inflation eat · fees · credit card minimum-due trap · no-cost EMI check · lock-in timeline · rent vs buy · prepay loan or invest · share result as picture. Rates are editable examples; always "for learning, not financial advice". No internet.
- **2.6 Three languages — DONE (part 1)**: `Lang` + `assets/lang/strings.tsv` (English⇥Hindi⇥Bengali; code keeps English, `Lang.t` translates at display time through `Ui.text`/LText, LSwitch, Popup, toasts, Alerts, Reasons, charts). About 1,060 texts done: tabs, Home, Grow, goals, Save more, Explain, all calculators, challenges. Bengali uses Kolkata wording and normal 0–9 digits. Picker on tour page 1 and Settings. Voice: `Lang.speech()` + `Voice.local()` (basic). To find what is still English: `python3 tools/lang_missing.py` (lists texts per file; regexes/pref keys/file names in that list are not real text — skip them). To add one: append a line to strings.tsv.
- **2.6.1 Languages part 2 — NEXT**: the remaining ~1,100 texts (Activity, Insights, Plan, Settings, TxnSheet, budget, people, reminders, reports…). He'll check Bengali; someone else Hindi. Do this BEFORE lessons so lessons are written in 3 languages.
- **2.7 Learn**: 60+ one-minute lessons (FD, RD, PPF, NPS, bonds, stocks, MF, SIP, index funds, gold, insurance basics, emergency fund, inflation, compounding, risk, diversification, KYC/demat, tax basics, scams like UPI collect-request fraud) · each with "Try it" → the matching calculator · "Watch videos" button opening a YouTube search in the user's language (e.g. "FD kya hai") + RBI/SEBI investor-education links · myth busters · quiz/flashcards · tap-to-explain words · 7-day "money basics" course.
- **2.8 Motivation**: levels Saver → Builder → Investor · more badges · no-spend calendar · Money Wrapped (yearly story) · simple mode for elders.
Build one version at a time; he tests each.

## Waiting on him
- Feedback and screenshots on 2.2–2.5 (Grow tab, Save more tools, calculators).
- D:\money: he still needs to delete by hand `NetWorthActivity.java` and `Rewards.java` (and the old `Goals` activity is now a data class — nothing to delete). device_bash on his PC failed to start, so deletes can't be done from here.
