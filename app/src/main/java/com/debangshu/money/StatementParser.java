package com.debangshu.money;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a bank statement (text from a PDF, or the cells of a CSV / Excel file) into rows: date, amount, in / out, balance,
 * and what the narration says (name, UPI ID, reference, your note). Plain Java, tested on a computer. Works for any bank
 * whose statement has a date, an amount and a running balance per row.
 */
public final class StatementParser {

    private StatementParser() {}

    public static final class Row {
        public long date;              // day (local midnight) + minutes if the statement shows a time
        public long amount;            // paise
        public boolean credit;
        public long balance = -1;
        public String narration = "";
        public String name = "", vpa = "", ref = "", remark = "";
        public boolean directionKnown;
    }

    public static final class Result {
        public List<Row> rows = new ArrayList<>();
        public String bank = "", last4 = "";
        public long from, to;
        public long opening = -1;
    }

    private static final int CI = Pattern.CASE_INSENSITIVE;
    private static final String MON = "jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec";
    /** Optional serial no. / transaction id before the date: "15 16/07/2023 …", "S83182271 28/09/2021 …". */
    private static final String LEAD = "(?:[0-9]{1,5}\\s+|[A-Z]{1,3}[0-9]{5,12}\\s+)?";
    private static final Pattern DATE = Pattern.compile(
            "^\\s*" + LEAD + "(?:([0-3]?[0-9])[/.\\-]([01]?[0-9])[/.\\-]((?:19|20)?[0-9]{2})|([0-3]?[0-9])[\\s\\-/]?(" + MON + ")[a-z]*[\\s\\-/,]*((?:19|20)?[0-9]{2})"
            + "|((?:19|20)[0-9]{2})-([01][0-9])-([0-3][0-9]))\\b", CI);
    private static final Pattern TIME = Pattern.compile("\\b([0-2]?[0-9]):([0-5][0-9])(?::[0-5][0-9])?\\s*(am|pm)?\\b", CI);
    /** Money in statements: 1,234.56 / 1234.56 / 1,23,456.00, optionally followed by Cr / Dr. */
    private static final Pattern AMT = Pattern.compile("(?<![0-9.,A-Za-z])([+-]?\\s?(?:[0-9]{1,3}(?:,[0-9]{2,3})+|[0-9]+)\\.[0-9]{2})(?![0-9])\\s*\\(?\\s*(cr|dr)?\\.?\\s*\\)?", CI);
    private static final Pattern OPENING = Pattern.compile("opening\\s*balance[^0-9]{0,30}([0-9][0-9,]*\\.[0-9]{2})", CI);
    private static final Pattern ACC = Pattern.compile("(?:a/c|account)\\s*(?:no\\.?|number|#)?\\s*[:\\-]?\\s*[x*•]*\\s*([0-9x*•]{4,20})", CI);
    private static final Pattern CREDIT_WORDS = Pattern.compile("\\b(?:cr|credit|credited|deposit|salary|refund|reversal|interest|/cr/|neft cr|imps cr|received)\\b", CI);
    private static final Pattern SKIP_LINE = Pattern.compile("opening balance|closing balance|^\\s*total|brought forward|carried forward|carried over|\\bb/f\\b|\\bc/f\\b|statement summary", CI);
    /** Column headings repeated on every page, page footers, bank addresses: ignored (they don't end a row). */
    private static final Pattern JUNK = Pattern.compile(
            "page\\s*(?:no\\.?\\s*)?[0-9]+|www\\.|https?://|phone ?(?:number|banking)|registered office|corporate office|customer care|toll free|"
            + "beware|disclaimer|computer generated|system generated|generated (?:on|by)|does not require|deposit insurance|"
            + "\\bs\\.? ?no\\.?\\b.*\\bdate\\b|\\bdate\\b.*(?:remarks|particulars|narration|description|details).*(?:balance|amount)|"
            + "(?:withdrawal|debit).*(?:deposit|credit).*balance|^\\s*(?:dr|cr|chq|cheque|value date|tran(?:saction)? ?id)\\s*$", CI);
    private static final Pattern DATE_TIME_ONLY = Pattern.compile("^\\s*[0-3]?[0-9][/.\\-][01]?[0-9][/.\\-](?:19|20)?[0-9]{2}\\s+[0-2]?[0-9]:[0-5][0-9](?::[0-5][0-9])?\\s*$");

    // ------------------------------------------------------------------ text (PDF)

    public static Result fromText(String text) {
        Result res = new Result();
        if (text == null) return res;
        String[] lines = text.replace('\u00a0', ' ').split("\\r?\\n");
        StringBuilder head = new StringBuilder();
        for (String l : lines) { if (DATE.matcher(l.trim()).find() && !l.toLowerCase(Locale.ROOT).contains("statement")) break; head.append(l).append('\n'); if (head.length() > 4000) break; }
        String hl = head.toString().toLowerCase(Locale.ROOT);
        res.bank = hl.contains("jupiter") || hl.contains("federal bank") ? "Federal" : SmsParser.bankOf("", hl);
        Matcher a = ACC.matcher(text);
        while (a.find()) {
            String d = a.group(1).replaceAll("[^0-9]", "");
            if (d.length() >= 4) { res.last4 = d.substring(d.length() - 4); break; }
        }
        Matcher op = OPENING.matcher(text);
        if (op.find()) res.opening = SmsParser.paise(op.group(1));

        List<String> block = null;
        List<List<String>> blocks = new ArrayList<>();
        for (String raw : lines) {
            String l = raw.trim();
            if (l.isEmpty() || JUNK.matcher(l).find()) continue;
            // "4/01/2024 17:27:27" inside a wrapped narration (Canara) is a time stamp, not a new row
            boolean stamp = DATE_TIME_ONLY.matcher(l).find() && block != null && !hasAmount(block);
            if (!stamp && DATE.matcher(l).find()) {
                block = new ArrayList<>();
                blocks.add(block);
                block.add(l);
            } else if (block != null && block.size() < 10 && !SKIP_LINE.matcher(l).find()) {
                // a wrapped narration has no amounts; a line with amounts after the row's own amounts is something else (a summary)
                if (hasAmount(block) && AMT.matcher(l).find()) { block = null; continue; }
                block.add(l);
            }
        }
        for (List<String> b : blocks) {
            Row r = rowFrom(b);
            if (r != null) res.rows.add(r);
        }
        finish(res);
        return res;
    }

    private static boolean hasAmount(List<String> b) {
        for (String x : b) if (AMT.matcher(x).find()) return true;
        return false;
    }

    private static Row rowFrom(List<String> b) {
        String first = b.get(0);
        if (SKIP_LINE.matcher(first).find()) return null;
        Matcher dm = DATE.matcher(first);
        if (!dm.find()) return null;
        long day = day(dm);
        if (day <= 0) return null;
        // amounts: from the whole block, but the narration may hold numbers too — amounts have exactly 2 decimals
        StringBuilder all = new StringBuilder();
        for (String s : b) all.append(s).append(' ');
        String text = all.toString();
        String rest = text.substring(dm.end());
        // drop a second (value) date
        Matcher d2 = DATE.matcher(rest.trim());
        if (d2.find()) rest = rest.trim().substring(d2.end());
        List<long[]> amts = new ArrayList<>();   // {paise, crdr(0 none,1 cr,2 dr), start}
        Matcher am = AMT.matcher(rest);
        while (am.find()) {
            String num = am.group(1).replace(" ", "");
            long v = SmsParser.paise(num.replace("-", "").replace("+", ""));
            String cd = am.group(2);
            long flag = cd != null ? (cd.equalsIgnoreCase("cr") ? 1 : 2) : num.startsWith("+") ? 1 : num.startsWith("-") ? 2 : 0;
            amts.add(new long[]{v, flag, am.start(), am.end()});
        }
        if (amts.isEmpty()) return null;
        Row r = new Row();
        r.date = day;
        Matcher tm = TIME.matcher(rest);
        if (tm.find()) {
            int h = Integer.parseInt(tm.group(1)), mi = Integer.parseInt(tm.group(2));
            if (tm.group(3) != null) { if (tm.group(3).equalsIgnoreCase("pm") && h < 12) h += 12; if (tm.group(3).equalsIgnoreCase("am") && h == 12) h = 0; }
            if (h < 24) r.date = day + (h * 60L + mi) * 60000;
        }
        // last amount = running balance (when there are 2+); amount = the non-zero one before it
        if (amts.size() >= 2) {
            long[] bal = amts.get(amts.size() - 1);
            r.balance = bal[0];
            long[] pick = null;
            for (int i = amts.size() - 2; i >= 0; i--) if (amts.get(i)[0] > 0) { pick = amts.get(i); break; }
            if (pick == null) return null;
            r.amount = pick[0];
            if (pick[1] != 0) { r.credit = pick[1] == 1; r.directionKnown = true; }
            // three numbers "withdrawal deposit balance" with a 0.00 in one column → the column tells the direction
            if (amts.size() >= 3 && !r.directionKnown) {
                long[] w = amts.get(amts.size() - 3), dpt = amts.get(amts.size() - 2);
                if (w[0] == 0 && dpt[0] > 0) { r.credit = true; r.directionKnown = true; }
                else if (dpt[0] == 0 && w[0] > 0) { r.credit = false; r.directionKnown = true; }
            }
            r.narration = trimTail(clean(rest.substring(0, (int) amts.get(0)[2])));
            if (r.narration.replaceAll("[^A-Za-z]", "").length() < 3)
                r.narration = trimTail(clean(rest.substring((int) amts.get(amts.size() - 1)[3])));
        } else {
            long[] only = amts.get(0);
            r.amount = only[0];
            if (only[1] != 0) { r.credit = only[1] == 1; r.directionKnown = true; }
            r.narration = trimTail(clean(rest.substring(0, (int) only[2])));
            if (r.narration.replaceAll("[^A-Za-z]", "").length() < 3) r.narration = trimTail(clean(rest.substring((int) only[3])));
        }
        if (r.amount <= 0) return null;
        r.narration = r.narration.replaceFirst("^[A-Z]{1,3}[0-9]{5,12}\\s*", "").trim();
        if (!r.directionKnown) r.credit = credit(r.narration);
        narration(r);
        return r;
    }

    private static final Pattern CREDIT_HINT = Pattern.compile(
            "upiab|upi ?in\\b|upiin/|upi/cr/|/cr/|\\bcr/|by transfer|transfer from|neft ?cr|imps ?cr|impsab|imps-in|credit interest|int\\.? ?pd|"
            + "interest paid|salary|refund|reversal|reversed|cash dep|deposit|received|cashback", CI);
    private static final Pattern DEBIT_HINT = Pattern.compile(
            "upiar|upiout|upi/dr/|/dr/|\\bdr/|to transfer|transfer to|neft ?dr|imps ?dr|impsar|atm|\\bpos\\b|ecom|wdl|withdrawal|charges|\\bemi\\b|ach ?d|nach", CI);

    static boolean credit(String narration) {
        Matcher c = CREDIT_HINT.matcher(narration), d = DEBIT_HINT.matcher(narration);
        int ci = c.find() ? c.start() : -1, di = d.find() ? d.start() : -1;
        return ci >= 0 && (di < 0 || ci < di);
    }

    // ------------------------------------------------------------------ CSV / Excel cells

    /** Rows of cells (first rows may be headings / bank details). */
    public static Result fromTable(List<List<String>> table, String headerText) {
        Result res = new Result();
        String lo = headerText == null ? "" : headerText.toLowerCase(Locale.ROOT);
        res.bank = lo.contains("jupiter") || lo.contains("federal bank") ? "Federal" : SmsParser.bankOf("", lo);
        Matcher a = ACC.matcher(headerText == null ? "" : headerText);
        if (a.find()) { String d = a.group(1).replaceAll("[^0-9]", ""); if (d.length() >= 4) res.last4 = d.substring(d.length() - 4); }
        int hdr = -1, cDate = -1, cNar = -1, cW = -1, cD = -1, cBal = -1, cAmt = -1, cType = -1;
        for (int i = 0; i < Math.min(table.size(), 40) && hdr < 0; i++) {
            List<String> row = table.get(i);
            int fd = -1, fn = -1, fw = -1, fdp = -1, fb = -1, fa = -1, ft = -1;
            for (int j = 0; j < row.size(); j++) {
                String c = row.get(j).toLowerCase(Locale.ROOT).trim();
                if (fd < 0 && c.matches(".*\\b(txn |transaction |tran |value )?date\\b.*") && !c.contains("value")) fd = j;
                else if (fn < 0 && c.matches(".*(narration|description|particulars|details|remarks|transaction remarks).*")) fn = j;
                else if (fw < 0 && c.matches(".*(withdrawal|debit|\\bdr\\b|paid out|money out).*") && !c.contains("credit")) fw = j;
                else if (fdp < 0 && c.matches(".*(deposit|credit|\\bcr\\b|paid in|money in).*")) fdp = j;
                else if (fb < 0 && c.matches(".*balance.*")) fb = j;
                else if (fa < 0 && c.matches(".*\\bamount\\b.*")) fa = j;
                else if (ft < 0 && c.matches(".*(type|dr/cr|cr/dr).*")) ft = j;
            }
            if (fd < 0 && !row.isEmpty() && row.get(0).toLowerCase(Locale.ROOT).contains("date")) fd = 0;
            if (fd >= 0 && (fw >= 0 || fdp >= 0 || fa >= 0)) { hdr = i; cDate = fd; cNar = fn; cW = fw; cD = fdp; cBal = fb; cAmt = fa; cType = ft; }
        }
        if (hdr < 0) {
            // no header we understand: read each row like a line of text
            StringBuilder sb = new StringBuilder();
            for (List<String> row : table) { for (String c : row) sb.append(c).append("  "); sb.append('\n'); }
            Result r = fromText(sb.toString());
            if (r.bank.isEmpty()) r.bank = res.bank;
            if (r.last4.isEmpty()) r.last4 = res.last4;
            return r;
        }
        for (int i = hdr + 1; i < table.size(); i++) {
            List<String> row = table.get(i);
            String ds = cell(row, cDate).trim();
            if (ds.matches("[0-9]{5}(?:\\.[0-9]+)?")) {           // Excel keeps dates as a day count from 1899-12-30
                java.util.Calendar x = java.util.Calendar.getInstance();
                x.clear();
                x.set(1899, 11, 30);
                x.add(java.util.Calendar.DAY_OF_MONTH, (int) Double.parseDouble(ds));
                ds = String.format(Locale.ROOT, "%02d/%02d/%04d", x.get(java.util.Calendar.DAY_OF_MONTH), x.get(java.util.Calendar.MONTH) + 1, x.get(java.util.Calendar.YEAR));
            }
            Matcher dm = DATE.matcher(ds);
            if (!dm.find()) continue;
            long day = day(dm);
            if (day <= 0) continue;
            Row r = new Row();
            r.date = day;
            Matcher tm = TIME.matcher(ds.substring(dm.end()));
            if (tm.find()) {
                int h = Integer.parseInt(tm.group(1)), mi = Integer.parseInt(tm.group(2));
                if (tm.group(3) != null) { if (tm.group(3).equalsIgnoreCase("pm") && h < 12) h += 12; if (tm.group(3).equalsIgnoreCase("am") && h == 12) h = 0; }
                if (h < 24) r.date = day + (h * 60L + mi) * 60000;
            }
            long w = money(cell(row, cW)), dp = money(cell(row, cD));
            if (w > 0) { r.amount = w; r.credit = false; r.directionKnown = true; }
            else if (dp > 0) { r.amount = dp; r.credit = true; r.directionKnown = true; }
            else if (cAmt >= 0) {
                String av = cell(row, cAmt);
                r.amount = money(av);
                String t = (cell(row, cType) + " " + av).toLowerCase(Locale.ROOT);
                if (t.contains("cr")) { r.credit = true; r.directionKnown = true; }
                else if (t.contains("dr") || av.trim().startsWith("-")) { r.credit = false; r.directionKnown = true; }
            }
            if (r.amount <= 0) continue;
            long b = money(cell(row, cBal));
            if (b >= 0 && !cell(row, cBal).trim().isEmpty()) r.balance = b;
            r.narration = clean(cell(row, cNar));
            if (!r.directionKnown) r.credit = credit(r.narration);
            narration(r);
            res.rows.add(r);
        }
        finish(res);
        return res;
    }

    private static String cell(List<String> row, int i) { return i >= 0 && i < row.size() && row.get(i) != null ? row.get(i) : ""; }

    private static long money(String s) {
        if (s == null) return -1;
        String t = s.replaceAll("(?i)cr|dr|inr|rs\\.?|₹", "").replace(",", "").replace("-", "").trim();
        if (!t.matches("[0-9]+(?:\\.[0-9]{1,2})?")) return -1;
        return SmsParser.paise(t);
    }

    // ------------------------------------------------------------------ shared

    /** Order oldest → newest, then use the running balance to settle in / out where the statement didn't say. */
    private static void finish(Result res) {
        List<Row> rs = res.rows;
        if (rs.size() >= 2 && rs.get(0).date > rs.get(rs.size() - 1).date) java.util.Collections.reverse(rs);
        long prev = res.opening;
        for (int i = 0; i < rs.size(); i++) {
            Row r = rs.get(i);
            if (r.balance >= 0 && prev >= 0) {
                if (prev - r.amount == r.balance) { r.credit = false; r.directionKnown = true; }
                else if (prev + r.amount == r.balance) { r.credit = true; r.directionKnown = true; }
            } else if (r.balance >= 0 && prev < 0 && i + 1 < rs.size()) {
                // first row: work backwards from the next one later; nothing to compare yet
            }
            if (r.balance >= 0) prev = r.balance;
        }
        // the very first row (no opening balance): its balance minus/plus amount should equal "before"; use the next row's agreement
        if (!rs.isEmpty() && res.opening < 0 && !rs.get(0).directionKnown) rs.get(0).credit = credit(rs.get(0).narration);
        if (!rs.isEmpty()) { res.from = rs.get(0).date; res.to = rs.get(rs.size() - 1).date; }
    }

    private static long day(Matcher m) {
        int d, mo, y;
        try {
            if (m.group(1) != null) { d = Integer.parseInt(m.group(1)); mo = Integer.parseInt(m.group(2)) - 1; y = Integer.parseInt(m.group(3)); }
            else if (m.group(4) != null) {
                d = Integer.parseInt(m.group(4));
                mo = ("janfebmaraprmayjunjulaugsepoctnovdec".indexOf(m.group(5).toLowerCase(Locale.ROOT).substring(0, 3))) / 3;
                y = Integer.parseInt(m.group(6));
            } else { y = Integer.parseInt(m.group(7)); mo = Integer.parseInt(m.group(8)) - 1; d = Integer.parseInt(m.group(9)); }
        } catch (Exception e) { return -1; }
        if (y < 100) y += 2000;
        if (mo < 0 || mo > 11 || d < 1 || d > 31 || y < 2000 || y > 2100) return -1;
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, mo, d, 0, 0, 0);
        return c.getTimeInMillis();
    }

    /** Drop reference numbers / value dates at the end of a narration. */
    private static String trimTail(String s) { return s.replaceAll("(\\s+[0-9][0-9/.\\-]{5,})+$", "").trim(); }

    private static String clean(String s) { return s.replaceAll("\\s+", " ").replaceAll("^[\\s|:,-]+|[\\s|:,-]+$", "").trim(); }

    private static final Pattern VPA = Pattern.compile("([A-Za-z0-9._]{2,}@[A-Za-z][A-Za-z0-9]{1,30})");
    private static final Pattern REF = Pattern.compile("(?<![0-9])([0-9]{12})(?![0-9])");
    private static final Pattern GENERIC = Pattern.compile(
            "^(?:(?:upi|imps|neft|rtgs|ach|nach)[a-z]{0,4}\\s*(?:cr|dr|in|out|p2a|p2m)?|dr|cr|tfr|clg|cash|wdl|int\\.? ?pd|pay to|paying|sent (?:from|using|via) .*|to transfer|by transfer|transfer to|transfer from|debit|credit|p2a|p2m|pay(?:ment)?|transfer|trf|to|by|from|via|na|n/a|null|sent using.*|payment from.*|paid via.*|upi intent|collect|"
            + "pos|ecom|ach|nach|mb|ib|inb|ft|sbin|hdfc|icic|utib|kkbk|punb|barb|cnrb|ubin|idib|ioba|bkid|yesb|indb|fdrl|"
            + "[a-z]{4}0[a-z0-9]{6}|[0-9 .-]+|bank|ok|yes|state bank of india|hdfc bank|icici bank|axis bank|kotak mahindra bank|paytm payments bank|"
            + "upi transaction|money transfer|fund transfer|transaction)$", CI);

    /** "UPI/DR/431712345678/RAMESH KUMAR/HDFC/ramesh@okaxis/chai" → name, UPI ID, reference, your note. */
    /** Bank codes that sit inside UPI narrations (from IFSC): SBIN, UTIB, YESB… */
    private static final Pattern BANKCODE = Pattern.compile(
            "(?i)(?:SBIN|HDFC|ICIC|UTIB|KKBK|PUNB|BARB|CNRB|UBIN|IDIB|IOBA|BKID|YESB|INDB|FDRL|IDFB|AUBL|PYTM|AIRP|UCBA|CBIN|MAHB|KLGB|IPOS|ESFB|"
            + "RATN|SIBL|KVBL|CIUB|DBSS|SCBL|CITI|HSBC|JAKA|PSIB|TMBL|DLXB|NTBL|KARB|BDBL|UJVN|FINO|JIOP|NSPB|ANDB|CORP|ORBC|SYNB|VIJB|ALLA|UTBI|ICICI|AXIS|PAYTM)"
            + "(?:0[A-Z0-9]{6})?");

    static void narration(Row r) {
        String n = r.narration;
        Matcher v = VPA.matcher(n);
        if (v.find()) r.vpa = v.group(1).toLowerCase(Locale.ROOT);
        Matcher rf = REF.matcher(n);
        if (rf.find()) r.ref = rf.group(1);
        String[] parts = n.split("\\s*[/|]\\s*|\\s+-\\s*|-(?=[A-Za-z])|(?<=[A-Za-z])-|\\s*:\\s*");
        List<String> words = new ArrayList<>();
        int vpaAt = -1;
        for (String p : parts) {
            String t = p.trim();
            if (t.contains("@")) { vpaAt = words.size(); continue; }
            t = t.replaceAll("(?i)\\b(?:tfr|clg|chq|cheque|upi|ref|no|txn|ecom|pos|cr|dr)\\b\\.?|\\b[0-9]+\\b|\\*+", " ").replaceAll("\\s+", " ").replaceAll("^[^A-Za-z]+|[^A-Za-z.)]+$", "").trim();
            if (t.matches("[a-z0-9._]+\\.[a-z0-9._]+")) continue;              // a cut-off UPI handle (rahul.hospital)
            if (t.length() < 2 || GENERIC.matcher(t).matches() || REF.matcher(t).find() || !t.matches(".*[A-Za-z]{2,}.*")) continue;
            if (t.replaceAll("[^0-9]", "").length() > 4) continue;               // codes: paytmqr2810050501011q852, ICICN12345678
            words.add(t);
        }
        for (String w : words) if (!BANKCODE.matcher(w).matches()) { r.name = SmsParser.cleanName(w); break; }   // not a bank code (SBIN, UTIB…)
        // your note: the last word after the UPI ID (HDFC "…-vpa-IFSC-ref-NOTE", SBI "/vpa/NOTE-"), never a bank code
        if (vpaAt >= 0 && vpaAt < words.size()) {
            String last = words.get(words.size() - 1);
            if (!BANKCODE.matcher(last).matches() && !last.equalsIgnoreCase(r.name) && last.length() <= 40) r.remark = last;
        } else if (vpaAt < 0 && words.size() >= 2) {
            String last = words.get(words.size() - 1);
            if (!BANKCODE.matcher(last).matches() && !last.matches("(?i)[A-Z]{4}0[A-Z0-9]{6}|.*\\bbank\\b.*") && !last.equalsIgnoreCase(r.name) && last.length() <= 40) r.remark = last;
        }
        if (r.name.isEmpty() && !r.vpa.isEmpty()) r.name = SmsParser.nameFromVpa(r.vpa);
    }
}
