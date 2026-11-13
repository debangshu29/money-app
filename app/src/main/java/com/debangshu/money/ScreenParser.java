package com.debangshu.money;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a UPI app's "Payment successful" screen (the text Android's accessibility gives us, one line per element)
 * into a payment. Plain Java, tested on a computer. Anything that isn't clearly a finished payment → null.
 */
public final class ScreenParser {

    private ScreenParser() {}

    private static final int CI = Pattern.CASE_INSENSITIVE;
    private static final Pattern SUCCESS = Pattern.compile(
            "payment successful|paid successfully|transaction successful|sent successfully|money sent|payment completed|"
            + "transfer successful|payment done|successfully paid|successfully sent|^completed$|^successful$|^success$|^paid$|"
            + "payment success|received successfully|^received$|you paid|you sent", CI | Pattern.MULTILINE);
    private static final Pattern NOT = Pattern.compile(
            "upi pin|enter pin|enter your pin|\\bfailed\\b|declined|pending|processing|in progress|request(?:ed)?\\b|collect|cancelled|"
            + "scheduled|autopay|mandate|insufficient|try again|expired|split", CI);
    private static final Pattern AMT_LINE = Pattern.compile("^\\s*(?:₹|rs\\.?|inr)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*$", CI);
    private static final Pattern AMT_ANY = Pattern.compile("(?:₹|\\brs\\.?|\\binr)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)", CI);
    private static final Pattern VPA = Pattern.compile("\\b([A-Za-z0-9._-]{2,}@[A-Za-z][A-Za-z0-9]{1,30})\\b");
    private static final Pattern REF = Pattern.compile(
            "(?:upi\\s*(?:transaction|txn|trans\\.?)?\\s*(?:id|ref(?:erence)?(?:\\s*no\\.?|\\s*number)?)|upi\\s*ref|utr(?:\\s*no\\.?)?|"
            + "bank\\s*ref(?:erence)?(?:\\s*no\\.?)?|rrn)\\s*[:#.]?\\s*\\n?\\s*([0-9]{10,14})", CI);
    private static final Pattern LAST4 = Pattern.compile("(?:x{2,}|•{1,}|\\*{2,}|\\.{2,}|ending\\s*(?:with\\s*)?|a/c\\s*(?:no\\.?)?\\s*|\\b(?:bank|india|baroda|bk)\\s*[-–:]?\\s*)\\s*([0-9]{4})\\b", CI);
    private static final Pattern TO_LABEL = Pattern.compile("^(?:paid to|sent to|to|payee|paying|you paid|you sent)\\s*:?$", CI);
    private static final Pattern FROM_LABEL = Pattern.compile("^(?:received from|from|sender|paid by)\\s*:?$", CI);
    private static final Pattern TO_INLINE = Pattern.compile("^(?:paid to|sent to|paid successfully to|payment to|to)\\s*:?\\s+(.{2,50})$", CI);
    private static final Pattern FROM_INLINE = Pattern.compile("^(?:received from|from)\\s*:?\\s+(.{2,50})$", CI);

    public static SmsParser.Result parse(String pkg, List<String> lines, long now) {
        if (lines == null || lines.isEmpty()) return null;
        List<String> ls = new ArrayList<>();
        for (String l : lines) { String t = l == null ? "" : l.replace(' ', ' ').trim(); if (!t.isEmpty()) ls.add(t); }
        StringBuilder sb = new StringBuilder();
        for (String l : ls) sb.append(sb.length() == 0 ? "" : "\n").append(l);
        String all = sb.toString();
        String lo = all.toLowerCase(Locale.ROOT);
        if (!SUCCESS.matcher(all).find() || NOT.matcher(lo).find()) return null;

        // the amount: a line that is only an amount (the big one), else the first amount mentioned
        long amt = -1;
        Set<Long> amounts = new LinkedHashSet<>();
        for (String l : ls) {
            Matcher m = AMT_LINE.matcher(l);
            if (m.find()) { long v = SmsParser.paise(m.group(1)); if (v > 0) { if (amt < 0) amt = v; amounts.add(v); } }
        }
        Matcher am = AMT_ANY.matcher(all);
        while (am.find()) { long v = SmsParser.paise(am.group(1)); if (v > 0) { amounts.add(v); if (amt < 0) amt = v; } }
        if (amt <= 0) return null;
        Matcher rf = REF.matcher(all);
        String ref = rf.find() ? rf.group(1) : "";
        if (amounts.size() > 3 && ref.isEmpty()) return null;   // a list / history screen, not one payment

        boolean credit = Pattern.compile("received from|^from$|paid you|sent you|credited to|received successfully|^received$", CI | Pattern.MULTILINE).matcher(all).find()
                && !Pattern.compile("paid to|sent to|^to$|debited from|you paid|you sent", CI | Pattern.MULTILINE).matcher(all).find();

        // the other side's name
        String who = "";
        for (int i = 0; i < ls.size(); i++) {
            String l = ls.get(i);
            Matcher in = (credit ? FROM_INLINE : TO_INLINE).matcher(l);
            if (in.find() && !looksLikeNoise(in.group(1))) { who = in.group(1); break; }
            if ((credit ? FROM_LABEL : TO_LABEL).matcher(l).find()) {
                for (int k = i + 1; k < Math.min(ls.size(), i + 4); k++) {
                    if (!looksLikeNoise(ls.get(k))) { who = ls.get(k); break; }
                }
                if (!who.isEmpty()) break;
            }
        }
        // the VPA nearest after the name (not the bank account line)
        String vpa = "";
        int nameAt = who.isEmpty() ? -1 : ls.indexOf(who);
        for (int i = Math.max(0, nameAt); i < ls.size(); i++) {
            Matcher v = VPA.matcher(ls.get(i));
            if (v.find() && !ls.get(i).toLowerCase(Locale.ROOT).contains("debited") && !ls.get(i).toLowerCase(Locale.ROOT).contains("from")) { vpa = v.group(1); break; }
        }
        if (who.isEmpty() && vpa.isEmpty() && ref.isEmpty()) return null;   // not sure what this is

        SmsParser.Result r = new SmsParser.Result();
        r.txn = true;
        r.amount = amt;
        r.credit = credit;
        r.merchant = who.isEmpty() ? (vpa.isEmpty() ? "" : SmsParser.nameFromVpa(vpa)) : SmsParser.cleanName(who.replaceAll("(?i)^banking name\\s*:?\\s*", ""));
        r.vpa = vpa;
        r.ref = ref;
        // which of your accounts: "HDFC Bank ••1234" / "Debited from XXXX1234"
        String bank = SmsParser.bankOf("", lo);
        Matcher l4 = LAST4.matcher(all);
        if (!bank.isEmpty() && l4.find()) { r.bank = bank; r.last4 = l4.group(1); r.account = bank + " ••" + r.last4; }
        long when = dateOn(all, now);
        if (when < 0) return null;   // an old payment opened from history (over 60 days) — leave it
        r.time = when;
        return r;
    }

    private static boolean looksLikeNoise(String s) {
        String l = s.toLowerCase(Locale.ROOT);
        return AMT_LINE.matcher(s).find() || VPA.matcher(s).matches() || l.matches(".*(successful|completed|transaction id|upi|ref|share|done|bank|debited|credited|split|•|xx).*")
                || l.length() < 2 || l.matches("[0-9 :,/.-]+");
    }

    private static final String[] MON = {"jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"};
    private static final Pattern D1 = Pattern.compile("\\b([0-3]?[0-9])\\s+(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*,?\\s+(20[0-9]{2})(?:,?\\s*(?:at\\s*)?([0-2]?[0-9]):([0-5][0-9])\\s*(am|pm)?)?", CI);
    private static final Pattern D2 = Pattern.compile("\\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\s+([0-3]?[0-9]),?\\s+(20[0-9]{2})(?:,?\\s*(?:at\\s*)?([0-2]?[0-9]):([0-5][0-9])\\s*(am|pm)?)?", CI);

    /** The date / time printed on the screen (a payment opened from history) — or now. */
    static long dateOn(String all, long now) {
        int d = -1, mo = -1, y = -1, h = -1, mi = 0;
        String ap = null;
        Matcher m = D1.matcher(all);
        if (m.find()) { d = Integer.parseInt(m.group(1)); mo = mon(m.group(2)); y = Integer.parseInt(m.group(3)); if (m.group(4) != null) { h = Integer.parseInt(m.group(4)); mi = Integer.parseInt(m.group(5)); ap = m.group(6); } }
        else {
            m = D2.matcher(all);
            if (m.find()) { mo = mon(m.group(1)); d = Integer.parseInt(m.group(2)); y = Integer.parseInt(m.group(3)); if (m.group(4) != null) { h = Integer.parseInt(m.group(4)); mi = Integer.parseInt(m.group(5)); ap = m.group(6); } }
        }
        if (d < 1 || mo < 0) return now;
        if (ap != null) { if (ap.equalsIgnoreCase("pm") && h < 12) h += 12; if (ap.equalsIgnoreCase("am") && h == 12) h = 0; }
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        c.set(y, mo, d, h < 0 ? c.get(Calendar.HOUR_OF_DAY) : h, h < 0 ? c.get(Calendar.MINUTE) : mi, 0);
        c.set(Calendar.MILLISECOND, 0);
        long t = c.getTimeInMillis();
        if (t > now + 3600000L || t < now - 60L * 86400000) return t > now ? now : -1;   // too old → -1 (ignore)
        // the same day with no time → now
        return t;
    }

    private static int mon(String s) {
        String l = s.toLowerCase(Locale.ROOT).substring(0, 3);
        for (int i = 0; i < 12; i++) if (MON[i].equals(l)) return i;
        return -1;
    }
}
