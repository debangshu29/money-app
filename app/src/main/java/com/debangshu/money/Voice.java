package com.debangshu.money;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Understands what you say for a cash spend: "200 cash for chai", "spent two hundred and fifty on auto", "chai 20 and samosa 30". */
public final class Voice {

    private Voice() {}

    public static final class Item {
        public long paise;
        public String what = "", category = "";
        /** said it was UPI / Google Pay / bank — not cash */
        public boolean upi;
        /** "from Federal" / "Union Bank ending 1234" / "in cash" — which money it came from */
        public String bank = "", last4 = "";
        public boolean cash;
    }

    /** Spoken bank names → the names Money uses for accounts. */
    private static final String[][] BANKS = {
            {"state bank(?: of india)?|sbi", "SBI"}, {"union(?: bank)?(?: of india)?", "Union Bank"}, {"federal(?: bank)?|jupiter", "Federal"},
            {"kotak(?: mahindra)?(?: bank)?|kotak 811", "Kotak"}, {"indusind(?: bank)?|indus", "IndusInd"}, {"hdfc(?: bank)?", "HDFC"},
            {"icici(?: bank)?", "ICICI"}, {"axis(?: bank)?", "Axis"}, {"pnb|punjab national(?: bank)?", "PNB"}, {"canara(?: bank)?", "Canara"},
            {"bank of baroda|\\bbob\\b", "BOB"}, {"idfc(?: first)?(?: bank)?", "IDFC First"}, {"yes bank", "Yes Bank"}, {"au(?: small finance)? bank", "AU Bank"},
            {"paytm(?: payments)? bank", "Paytm"}, {"airtel(?: payments)? bank", "Airtel Bank"}, {"bank of india", "BOI"}, {"indian bank", "Indian Bank"},
            {"uco(?: bank)?", "UCO"}, {"central bank(?: of india)?", "Central Bank"}, {"bandhan(?: bank)?", "Bandhan"}, {"idbi(?: bank)?", "IDBI"},
            {"rbl(?: bank)?", "RBL"}, {"bank of maharashtra", "Bank of Maharashtra"}, {"indian overseas(?: bank)?|\\biob\\b", "IOB"},
    };
    private static final Pattern DIGITS = Pattern.compile("\\b(?:account|a/c|ac|ending(?: with| in)?|last(?: four)?(?: digits?)?)\\s*([0-9]{4})\\b");

    private static final Pattern UPI = Pattern.compile(
            "\\b(?:upi|gpay|g pay|google ?pay|phone ?pe|phonepay|paytm|bhim|cred|super ?money|online|bank|transfer(?:red)?|net ?banking|by card|debit card|scan(?:ned)?)\\b");

    private static final Map<String, Integer> NUM = new HashMap<>();
    static {
        String[] ones = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen",
                "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"};
        for (int i = 0; i < ones.length; i++) NUM.put(ones[i], i);
        String[] tens = {"twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"};
        for (int i = 0; i < tens.length; i++) NUM.put(tens[i], (i + 2) * 10);
        NUM.put("a", 1);   // "a hundred"
    }

    /** "two hundred and fifty" → "250" (words that aren't numbers stay as they are). */
    static String digits(String s) {
        String[] w = s.split("\\s+");
        StringBuilder out = new StringBuilder();
        long total = 0, cur = 0;
        boolean in = false;
        for (int k = 0; k < w.length; k++) {
            String x = w[k];
            boolean isNum = NUM.containsKey(x) && !(x.equals("a") && (k + 1 >= w.length || !w[k + 1].matches("hundred|thousand|lakh|lac")));
            boolean mult = x.matches("hundred|thousand|lakh|lakhs|lac|lacs");
            boolean andInside = x.equals("and") && in && k + 1 < w.length && NUM.containsKey(w[k + 1]) && !w[k + 1].equals("a");
            if (isNum) { cur += NUM.get(x); in = true; continue; }
            if (mult && in) {
                if (x.startsWith("hundred")) cur = Math.max(1, cur) * 100;
                else { long m = x.startsWith("thousand") ? 1000 : 100000; total += Math.max(1, cur) * m; cur = 0; }
                continue;
            }
            if (andInside) continue;
            if (in) { out.append(total + cur).append(' '); total = 0; cur = 0; in = false; }
            out.append(x).append(' ');
        }
        if (in) out.append(total + cur).append(' ');
        return out.toString().trim();
    }

    private static final Pattern AMOUNT = Pattern.compile("(?<![a-z])([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*(k|thousand|lakh|lac)?(?![0-9])");
    private static final String FILLER = "\\b(?:i|i've|have|has|just|spent|spend|paid|pay|gave|give|given|bought|buy|for|on|at|to|the|a|an|in|of|cash|by|rupees?|rs|inr|bucks|"
            + "today|now|some|my|ka|ke|ki|ko|me|mein|diya|diye|kharcha|kharch|liye|taka|takar|via|using|through|with|"
            + "wallet|upi|gpay|g pay|google ?pay|google|phone ?pe|phonepay|paytm|bhim|cred|super ?money|online|bank|transfer|transferred|net ?banking|card|debit|scan|scanned)\\b";

    /** Typed text like "50 Ramesh for test from federal ending 1234" → {bank, last4, text without the bank words}. Keeps the original case. */
    public static String[] hint(String typed) {
        String bank = "", last4 = "", s = typed == null ? "" : typed;
        Matcher dm = Pattern.compile("(?i)\\b(?:account|a/c|ac|ending(?: with| in)?|last(?: four)?(?: digits?)?)\\s*([0-9]{4})\\b").matcher(s);
        if (dm.find()) { last4 = dm.group(1); s = s.substring(0, dm.start()) + " " + s.substring(dm.end()); }
        for (String[] b : BANKS) {
            Matcher bm = Pattern.compile("(?i)\\b(?:from|via|using|through|by|with)?\\s*(?:my\\s+)?(?:" + b[0] + ")\\b(?:\\s+(?:bank|account|a/c))?").matcher(s);
            if (bm.find()) { bank = b[1]; s = s.substring(0, bm.start()) + " " + s.substring(bm.end()); break; }
        }
        return new String[]{bank, last4, s.replaceAll("\\s+", " ").trim()};
    }

    public static List<Item> parse(String said) {
        List<Item> out = new ArrayList<>();
        if (said == null) return out;
        String s = said.toLowerCase(Locale.ROOT).replace("₹", " rs ").replaceAll("[^a-z0-9.,'\\s]", " ").replaceAll("(?<=[0-9]),(?=[0-9]{3})", "")
                .replaceAll("(?<=[0-9])(rs|k)\\b", " $1").replaceAll("\\s+", " ").trim();
        s = digits(s);
        // which money: "from federal bank ending 1234", "in cash"
        String bank = "", last4 = "";
        Matcher dm = DIGITS.matcher(s);
        if (dm.find()) { last4 = dm.group(1); s = (s.substring(0, dm.start()) + " " + s.substring(dm.end())).trim(); }
        for (String[] b : BANKS) {
            Matcher bm = Pattern.compile("\\b(?:from|via|using|through|by|in|on|with)?\\s*(?:my\\s+)?(?:" + b[0] + ")\\b(?:\\s+(?:bank|account|a/c))?").matcher(s);
            if (bm.find()) { bank = b[1]; s = (s.substring(0, bm.start()) + " " + s.substring(bm.end())).replaceAll("\\s+", " ").trim(); break; }
        }
        boolean cash = Pattern.compile("\\b(?:in |by |with |from )?(?:my )?cash\\b|\\bwallet\\b").matcher(s).find();
        boolean upi = UPI.matcher(s).find() || !bank.isEmpty() || !last4.isEmpty();
        // split into items where both sides have an amount: "chai 20 and samosa 30", "20 chai, 30 auto"
        String[] parts = s.split("\\s*(?:,|\\band\\b|\\bplus\\b|\\bthen\\b|\\balso\\b)\\s*");
        List<String> chunks = new ArrayList<>();
        StringBuilder pending = new StringBuilder();
        for (String p : parts) {
            if (p.trim().isEmpty()) continue;
            boolean has = AMOUNT.matcher(p).find();
            if (!has) { pending.append(pending.length() > 0 ? " and " : "").append(p); continue; }
            if (pending.length() > 0) {
                // words before the first amount belong to it ("chai and biscuits 40" → one item)
                chunks.add(pending + " " + p);
                pending.setLength(0);
            } else chunks.add(p);
        }
        if (pending.length() > 0 && !chunks.isEmpty()) chunks.set(chunks.size() - 1, chunks.get(chunks.size() - 1) + " and " + pending);
        for (String c : chunks) {
            Matcher m = AMOUNT.matcher(c);
            if (!m.find()) continue;
            double v;
            try { v = Double.parseDouble(m.group(1).replace(",", "")); } catch (Exception e) { continue; }
            String mult = m.group(2);
            if ("k".equals(mult) || "thousand".equals(mult)) v *= 1000;
            else if ("lakh".equals(mult) || "lac".equals(mult)) v *= 100000;
            if (v <= 0 || v > 10000000) continue;
            Item it = new Item();
            it.paise = Math.round(v * 100);
            String what = (c.substring(0, m.start()) + " " + c.substring(m.end())).replaceAll(FILLER, " ").replaceAll("\\s+", " ").trim();
            what = what.replaceAll("^(and|with)\\s+|\\s+(and|with)$", "").trim();
            it.what = what.isEmpty() ? "Cash spend" : Character.toUpperCase(what.charAt(0)) + what.substring(1);
            it.category = category(what);
            it.upi = upi && !cash;
            it.bank = bank;
            it.last4 = last4;
            it.cash = cash;
            if (upi && Cats.CASH.equals(it.category)) it.category = "";   // let the name decide (person → People…)
            out.add(it);
        }
        return out;
    }

    private static final String[][] WORDS = {
            {Cats.FOOD, "chai|tea|coffee|snack|samosa|lunch|dinner|breakfast|food|biryani|momo|roll|puchka|phuchka|pani ?puri|juice|sweets?|mishti|cake|ice ?cream|canteen|restaurant|dhaba|thali|pizza|burger|maggi|paratha|dosa|idli|biscuits?|cold ?drink"},
            {Cats.GROCERY, "vegetables?|veggies|sabzi|sabji|milk|grocer(?:y|ies)|kirana|fruits?|eggs?|rice|atta|dal|oil|bread|fish|chicken|mutton|meat|onions?|potato(?:es)?|aloo"},
            {Cats.TRANSPORT, "auto|rickshaw|toto|e-?rickshaw|bus|metro|taxi|cab|train|ticket|parking|toll|ferry|uber|ola|rapido"},
            {Cats.FUEL, "petrol|diesel|fuel|cng"},
            {Cats.HEALTH, "medicines?|medical|pharmacy|doctor|tablets?|chemist"},
            {Cats.PERSONAL, "haircut|salon|parlour|parlor|barber|shave"},
            {Cats.GIFTS, "gift|donation|temple|tip|charity"},
            {Cats.SHOP, "clothes|shirt|shoes|stationery|pen|book|market|shopping"},
            {Cats.BILLS, "newspaper|laundry|dhobi|ironing|press|maid|gas cylinder|cylinder|water"},
            {Cats.RECHARGE, "recharge"},
            {Cats.FUN, "movie|cinema|game"},
    };

    static String category(String what) {
        String w = what.toLowerCase(Locale.ROOT);
        for (String[] r : WORDS) if (Pattern.compile("\\b(?:" + r[1] + ")\\b").matcher(w).find()) return r[0];
        return Cats.CASH;
    }
}
