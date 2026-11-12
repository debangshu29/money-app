package com.debangshu.money;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Ask your money": understands everyday questions, all on the phone (nothing is sent anywhere).
 *   how much on food this month · swiggy last month · biggest expense this year · income in october
 *   how many times uber · average per day · top shops · subscriptions · balance · compare with last month
 */
public final class Ask {

    private Ask() {}

    public static final class Answer {
        public String text = "";
        public List<Txn> items = new ArrayList<>();
        public LinkedHashMap<String, Long> table;   // optional: name → amount
    }

    public static final String[] EXAMPLES = {
            "How much did I spend this month?", "Food this month", "Biggest expenses last month", "Swiggy this year",
            "Income this month", "Top shops this month", "How many times Uber?", "Average per day",
            "Compare with last month", "Subscriptions", "Balance", "Spent today"};

    private static final String[][] CAT_WORDS = {
            {Cats.FOOD, "food|eat|eating|restaurant|restaurants|dining|zomato|swiggy|lunch|dinner|breakfast|snacks"},
            {Cats.GROCERY, "grocery|groceries|vegetables|milk|kirana"},
            {Cats.SHOP, "shopping|shop online|clothes|online shopping"},
            {Cats.TRANSPORT, "transport|travel in city|cab|cabs|auto|metro|bus|taxi|commute"},
            {Cats.FUEL, "fuel|petrol|diesel|cng"},
            {Cats.TRAVEL, "travel|trip|trips|flight|flights|hotel|hotels|holiday|vacation"},
            {Cats.BILLS, "bill|bills|electricity|utilities|wifi|broadband|gas"},
            {Cats.RECHARGE, "recharge|recharges|mobile|phone bill"},
            {Cats.SUBS, "subscription spend|ott|streaming"},
            {Cats.FUN, "movie|movies|entertainment|fun|games|gaming"},
            {Cats.HEALTH, "health|medicine|medicines|doctor|hospital|pharmacy|gym|medical"},
            {Cats.EDU, "education|fees|course|courses|books|college|tuition"},
            {Cats.HOME, "rent|home|house"},
            {Cats.EMI, "emi|emis|loan|loans"},
            {Cats.INVEST, "invest|investment|investments|sip|stocks|mutual fund|mutual funds"},
            {Cats.INSURE, "insurance|premium"},
            {Cats.CASH, "cash|atm"},
            {Cats.PEOPLE, "friends|people|family|sent to people|transfers to people"},
            {Cats.PERSONAL, "salon|haircut|grooming|personal care"},
            {Cats.GIFTS, "gift|gifts|donation|donations"},
            {Cats.FEES, "charges|fees and charges|bank charges|penalty"},
    };

    public static Answer answer(String question, List<Txn> all, List<Account> accounts, long now) {
        Answer a = answer0(question, all, accounts, now);
        a.text = a.text.replaceAll("\\s+", " ").replace(" .", ".").replace(" :", ":").trim();
        return a;
    }

    static String onDay(long t) {
        String d = Fmt.day(t);
        return d.equals("Today") || d.equals("Yesterday") ? d.toLowerCase(Locale.ROOT) : "on " + d;
    }

    private static Answer answer0(String question, List<Txn> all, List<Account> accounts, long now) {
        Answer a = new Answer();
        String q = " " + question.toLowerCase(Locale.ROOT).replaceAll("[?!.,]", " ").replaceAll("\\s+", " ").trim() + " ";
        // "compare with last month" = this month against last month
        q = q.replaceAll("\\b(with|to|than|vs|versus|and) (last|previous) (month|week|year)\\b", "$1 this $3");

        // ---- balance
        if (q.matches(".*\\b(balance|balances|how much (money )?(do|have) i (have|got)|money left in)\\b.*") && !q.contains("spent")) {
            long total = 0;
            int n = 0;
            StringBuilder b = new StringBuilder();
            a.table = new LinkedHashMap<>();
            for (Account ac : accounts) {
                if (ac.balance < 0 || ac.card) continue;
                total += ac.balance;
                n++;
                a.table.put(ac.display() + " · " + Fmt.day(ac.balanceTime), ac.balance);
            }
            if (n == 0) a.text = "I haven't seen a balance yet. It appears after your bank's next SMS that shows “Avl Bal”.";
            else b.append("You have ").append(Fmt.rs(total)).append(" across ").append(n).append(n == 1 ? " account" : " accounts")
                    .append(" (from the last message of each bank).");
            if (b.length() > 0) a.text = b.toString();
            return a;
        }

        long[] range = range(q, now);
        String when = rangeName(q, range, now);
        String cat = null;
        for (String[] c : CAT_WORDS) {
            if (Pattern.compile("\\b(?:" + c[1] + ")\\b").matcher(q).find()) { cat = c[0]; break; }
        }
        String shop = merchant(q, all);
        if (shop != null) cat = null;   // "swiggy" means the shop, not all food

        // ---- subscriptions / repeating
        if (q.matches(".*\\b(subscriptions?|recurring|repeating|repeat payments|autopay|monthly payments)\\b.*")) {
            List<Ledger.Recurring> rs = Ledger.recurring(all, now);
            a.table = new LinkedHashMap<>();
            long monthly = 0;
            for (Ledger.Recurring r : rs) {
                a.table.put(r.name + " · " + r.periodName().toLowerCase(Locale.ROOT) + " · next " + Fmt.day(r.next), r.amount);
                monthly += r.amount * 30 / Math.max(1, r.period);
            }
            a.text = rs.isEmpty() ? "No repeating payments found yet. They show up after the second payment to the same place."
                    : rs.size() + (rs.size() == 1 ? " repeating payment" : " repeating payments") + ", about " + Fmt.rs(monthly) + " a month.";
            return a;
        }

        boolean income = q.matches(".*\\b(income|earn|earned|earning|earnings|salary|received|receive|got|credited|credit|came in|money in)\\b.*")
                && !q.matches(".*\\b(spent|spend|paid|pay)\\b.*");

        List<Txn> hits = new ArrayList<>();
        for (Txn t : all) {
            if (t.time < range[0] || t.time >= range[1]) continue;
            if (income ? !t.isIncome() : !t.isSpend()) continue;
            if (cat != null && !cat.equals(t.category)) continue;
            if (shop != null && !t.merchant.equalsIgnoreCase(shop)) continue;
            hits.add(t);
        }
        Collections.sort(hits, (x, y) -> Long.compare(y.time, x.time));
        long total = 0;
        for (Txn t : hits) total += t.amount;
        String what = shop != null ? "at " + shop : cat != null ? "on " + cat : "";
        String verb = income ? "received" : "spent";

        // ---- biggest
        if (q.matches(".*\\b(biggest|largest|highest|most expensive|max|maximum|top expense|top expenses|costliest)\\b.*")) {
            List<Txn> big = new ArrayList<>(hits);
            Collections.sort(big, (x, y) -> Long.compare(y.amount, x.amount));
            a.items = big.subList(0, Math.min(10, big.size()));
            a.text = big.isEmpty() ? "Nothing " + verb + " " + what + " " + when + "."
                    : "Biggest " + (income ? "money in" : "payment") + " " + when + ": " + Fmt.rs(big.get(0).amount)
                      + (big.get(0).merchant.isEmpty() ? "" : " — " + big.get(0).merchant) + " " + onDay(big.get(0).time) + ".";
            return a;
        }

        // ---- how many times
        if (q.matches(".*\\b(how many|how often|count|number of|times)\\b.*")) {
            a.items = hits;
            a.text = hits.size() + (hits.size() == 1 ? " payment " : " payments ") + what + " " + when + ", " + Fmt.rs(total) + " in total.";
            return a;
        }

        // ---- average
        if (q.matches(".*\\b(average|avg|per day|daily|a day|each day)\\b.*")) {
            long days = Math.max(1, Math.round((Math.min(range[1], now) - range[0]) / 86400000.0));
            a.items = hits;
            a.text = "On average you " + (income ? "received " : "spent ") + Fmt.rs(total / days) + " a day " + what + " " + when
                    + " (" + Fmt.rs(total) + " over " + days + (days == 1 ? " day)." : " days).");
            return a;
        }

        // ---- top places / categories
        if (q.matches(".*\\b(top|where|most|which|categories|category|shops|merchants|breakdown|split)\\b.*")) {
            boolean byShop = q.matches(".*\\b(shop|shops|merchant|merchants|places|where|who)\\b.*") || cat != null;
            a.table = new LinkedHashMap<>();
            Map<String, Long> m = byShop ? Ledger.byMerchant(all, range[0], range[1], cat) : Ledger.byCategory(all, range[0], range[1]);
            int i = 0;
            for (Map.Entry<String, Long> e : m.entrySet()) { if (i++ >= 10) break; a.table.put(e.getKey(), e.getValue()); }
            if (m.isEmpty()) a.text = "Nothing spent " + when + ".";
            else {
                Map.Entry<String, Long> first = m.entrySet().iterator().next();
                a.text = "Most of your money " + when + " went " + (byShop ? "to " : "on ") + first.getKey() + ": "
                        + Fmt.rs(first.getValue()) + " of " + Fmt.rs(Ledger.spent(all, range[0], range[1])) + ".";
            }
            return a;
        }

        // ---- compare
        if (q.matches(".*\\b(compare|compared|vs|versus|than last|than previous|difference)\\b.*")) {
            long len = range[1] - range[0];
            long[] prev = previous(range, q);
            long before = 0;
            for (Txn t : all) {
                if (t.time < prev[0] || t.time >= prev[1]) continue;
                if (income ? !t.isIncome() : !t.isSpend()) continue;
                if (cat != null && !cat.equals(t.category)) continue;
                if (shop != null && !t.merchant.equalsIgnoreCase(shop)) continue;
                before += t.amount;
            }
            long diff = total - before;
            a.items = hits;
            String change = before == 0 ? "" : " (" + Math.round(Math.abs(diff) * 100.0 / before) + "% " + (diff >= 0 ? "more" : "less") + ")";
            a.text = Fmt.title(verb) + " " + what + " " + when + ": " + Fmt.rs(total) + ". The period before: " + Fmt.rs(before)
                    + change + "." + (len > 0 && range[1] > now ? " (Up to today in both.)" : "");
            return a;
        }

        a.items = hits;
        if (hits.isEmpty()) a.text = "Nothing " + verb + " " + what + " " + when + ".";
        else a.text = "You " + verb + " " + Fmt.rs(total) + " " + what + " " + when + " (" + hits.size()
                + (hits.size() == 1 ? " payment)." : " payments).");
        a.text = a.text.replaceAll("\\s+", " ").replace(" .", ".");
        return a;
    }

    /** A shop name you have paid, mentioned in the question. */
    static String merchant(String q, List<Txn> all) {
        String best = null;
        for (Txn t : all) {
            String m = t.merchant;
            if (m.length() < 3 || m.startsWith("Shop (QR)") || m.startsWith("Unrecorded")) continue;
            String lm = m.toLowerCase(Locale.ROOT);
            String first = lm.split("[ .]")[0];
            boolean hit = q.contains(" " + lm + " ") || (first.length() >= 4 && q.contains(" " + first + " ")
                    && !first.matches("food|bank|cash|paid|from|shop|store|this|last|month|year|week|today|india|limited|private"));
            if (hit && (best == null || m.length() > best.length())) best = m;
        }
        return best;
    }

    // ------------------------------------------------------------------ dates

    static long[] range(String q, long now) {
        long today = Fmt.startOfDay(now);
        long end = Fmt.addDays(today, 1);
        if (q.contains(" today ")) return new long[]{today, end};
        if (q.contains(" yesterday ")) return new long[]{Fmt.addDays(today, -1), today};
        Matcher m = Pattern.compile("\\b(?:last|past|previous)\\s+([0-9]+)\\s+(day|days|week|weeks|month|months)\\b").matcher(q);
        if (m.find()) {
            int n = Integer.parseInt(m.group(1));
            String u = m.group(2);
            long from = u.startsWith("day") ? Fmt.addDays(today, -n + 1) : u.startsWith("week") ? Fmt.addDays(today, -7 * n + 1)
                    : Fmt.addMonths(today, -n);
            return new long[]{from, end};
        }
        Calendar c = Fmt.cal(today);
        int dow = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;   // Monday = 0
        long week = Fmt.addDays(today, -dow);
        if (q.contains(" this week ")) return new long[]{week, end};
        if (q.contains(" last week ") || q.contains(" previous week ")) return new long[]{Fmt.addDays(week, -7), week};
        long month = Fmt.startOfMonth(now);
        if (q.contains(" last month ") || q.contains(" previous month ")) return new long[]{Fmt.addMonths(month, -1), month};
        Calendar y = Fmt.cal(month);
        y.set(Calendar.MONTH, 0);
        long year = y.getTimeInMillis();
        if (q.contains(" this year ")) return new long[]{year, end};
        if (q.contains(" last year ")) return new long[]{Fmt.addMonths(year, -12), year};
        if (q.matches(".*\\b(all time|ever|so far|till now|overall|since start)\\b.*")) return new long[]{0, end};
        for (int i = 0; i < 12; i++) {
            String full = Fmt.MONTH[i].toLowerCase(Locale.ROOT), shortN = Fmt.MON[i].toLowerCase(Locale.ROOT);
            Matcher mm = Pattern.compile("\\b(?:" + full + "|" + shortN + (i == 4 ? "" : "") + ")\\b(?:\\s+(20[0-9]{2}))?").matcher(q);
            if (i == 4 && !q.matches(".*\\bmay\\b\\s*(20[0-9]{2})?\\s*$") && !q.matches(".*\\b(in|of|during|for) may\\b.*")) continue;  // "may" is also a word
            if (mm.find()) {
                Calendar k = Fmt.cal(month);
                int yr = mm.group(1) != null ? Integer.parseInt(mm.group(1)) : k.get(Calendar.YEAR);
                if (mm.group(1) == null && i > k.get(Calendar.MONTH)) yr--;
                k.set(Calendar.YEAR, yr);
                k.set(Calendar.MONTH, i);
                k.set(Calendar.DAY_OF_MONTH, 1);
                long s = k.getTimeInMillis();
                return new long[]{s, Fmt.addMonths(s, 1)};
            }
        }
        return new long[]{month, end};   // default: this month
    }

    static long[] previous(long[] r, String q) {
        long len = r[1] - r[0];
        boolean monthLike = Fmt.startOfMonth(r[0]) == r[0] && len > 27L * 86400000 && len < 32L * 86400000
                || q.contains(" this month ") || (!q.matches(".*\\b(week|day|days|year|today|yesterday)\\b.*"));
        if (monthLike && Fmt.startOfMonth(r[0]) == r[0]) {
            long ps = Fmt.addMonths(r[0], -1);
            // same number of days into the month, so "this month so far" is fair
            long pe = Math.min(r[0], ps + Math.min(len, r[1] - r[0]));
            return new long[]{ps, pe};
        }
        return new long[]{r[0] - len, r[0]};
    }

    static String rangeName(String q, long[] r, long now) {
        long today = Fmt.startOfDay(now);
        if (r[0] == today) return "today";
        if (r[0] == Fmt.addDays(today, -1) && r[1] == today) return "yesterday";
        if (r[0] == 0) return "so far";
        if (q.contains(" this week ")) return "this week";
        if (q.contains(" last week ") || q.contains(" previous week ")) return "last week";
        if (q.contains(" this year ")) return "this year";
        if (q.contains(" last year ")) return "last year";
        Matcher m = Pattern.compile("\\b(?:last|past|previous)\\s+([0-9]+)\\s+(days?|weeks?|months?)\\b").matcher(q);
        if (m.find()) return "in the last " + m.group(1) + " " + m.group(2);
        if (r[0] == Fmt.startOfMonth(now)) return "this month";
        if (r[0] == Fmt.addMonths(Fmt.startOfMonth(now), -1) && r[1] == Fmt.startOfMonth(now)) return "last month";
        return "in " + Fmt.month(r[0]);
    }
}
