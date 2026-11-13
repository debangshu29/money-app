package com.debangshu.money;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads payment notifications from UPI / wallet / bank apps (Google Pay, PhonePe, Paytm…),
 * and bank SMS shown by the Messages app (a backup in case the SMS itself was missed).
 * Plain Java, tested on a computer.
 */
public final class NotifParser {

    private NotifParser() {}

    /** package → name shown in the app */
    public static final String[][] PAY_APPS = {
            {"com.google.android.apps.nbu.paisa.user", "Google Pay"},
            {"com.phonepe.app", "PhonePe"},
            {"net.one97.paytm", "Paytm"},
            {"in.org.npci.upiapp", "BHIM"},
            {"com.dreamplug.androidapp", "CRED"},
            {"in.amazon.mShop.android.shopping", "Amazon Pay"},
            {"com.mobikwik_new", "MobiKwik"},
            {"com.naviapp", "Navi"},
            {"money.super.payments", "super.money"},
            {"com.whatsapp", "WhatsApp Pay"},
            {"com.sbi.lotusintouch", "YONO SBI"},
            {"com.sbi.upi", "BHIM SBI Pay"},
            {"com.snapwork.hdfc", "HDFC Bank"},
            {"com.hdfcbank.payzapp", "PayZapp"},
            {"com.csam.icici.bank.imobile", "iMobile"},
            {"com.axis.mobile", "Axis Mobile"},
            {"com.msf.kbank.mobile", "Kotak"},
            {"com.freecharge.android", "Freecharge"},
            {"com.jupiter.money", "Jupiter"},
            {"com.epifi.paisa", "Fi"},
            {"com.slice", "slice"},
            {"com.myairtelapp", "Airtel Thanks"},
            {"com.bharatpe.app", "BharatPe"},
            {"com.fampay.in", "FamPay"},
            {"com.pnb.onesmartbanking", "PNB ONE"},
            {"com.bankofbaroda.mconnect", "bob World"},
    };

    public static final String[] SMS_APPS = {
            "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms",
            "com.android.messaging", "com.miui.smsextra", "com.oneplus.mms", "com.coloros.mms", "com.vivo.message",
            "com.truecaller", "com.microsoft.android.smsorganizer", "com.motorola.messaging", "com.asus.message"};

    public static String appName(String pkg) {
        for (String[] a : PAY_APPS) if (a[0].equals(pkg)) return a[1];
        return null;
    }

    public static boolean isSmsApp(String pkg) {
        for (String s : SMS_APPS) if (s.equals(pkg)) return true;
        return false;
    }

    private static final int CI = Pattern.CASE_INSENSITIVE;
    private static final Pattern AMOUNT = Pattern.compile("(?:₹|\\brs\\.?|\\binr\\b)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)", CI);
    /** Never a finished payment. */
    private static final Pattern SKIP = Pattern.compile(
            "request|reminder|pending|processing|failed|declined|unsuccessful|"
            + "\\bdue\\b|pay now|pay ₹|bill (?:is )?due|bill generated|new bill|recharge now|expir|is waiting|collect|"
            + "\\blimit (?:increase|available|enhance)|kyc|update your|verify|\\botp\\b|split|remind|autopay (?:set|scheduled|upcoming)|will be", CI);
    /** Promotions — skipped unless the notification clearly says money moved ("₹1 paid to Ramesh · you won a reward"). */
    private static final Pattern PROMO = Pattern.compile(
            "offer|cashback|scratch|reward|won\\b|\\bwin\\b|\\brefer|invite|coupon|voucher|\\bloan\\b", CI);
    private static final Pattern STRONG = Pattern.compile(
            "(?:paid|sent|transferred)\\s+(?:(?:₹|rs\\.?)\\s*[0-9,.]+\\s+)?(?:successfully\\s+)?to\\s|"
            + "(?:₹|rs\\.?)\\s*[0-9,.]+\\s+(?:paid|sent)\\b|payment (?:of [^ ]+ )?(?:to [^|]+ )?(?:successful|done|completed|complete)|"
            + "(?:paid|sent) you|received (?:(?:₹|rs\\.?)\\s*[0-9,.]+ )?from|\\bdebited\\b|\\bcredited\\b", CI);
    /** Looks like it's about a payment (for the "couldn't read" list). */
    public static final Pattern MONEYISH = Pattern.compile(
            "\\bpaid\\b|\\bsent\\b|payment|\\bdebited\\b|\\bcredited\\b|\\breceived\\b|successful|transferred|\\bspent\\b", CI);
    private static final Pattern CREDIT = Pattern.compile(
            "\\breceived\\b|\\bcredited\\b|paid you|sent you|\\badded to\\b|money in|you got", CI);
    private static final Pattern DEBIT = Pattern.compile(
            "\\bpaid\\b|\\bsent\\b|\\bdebited\\b|payment (?:of [^ ]+ )?(?:to [^|]+? )?(?:is |was |has been )?(?:successful|done|completed|complete)|\\btransferred\\b|money out|\\bspent\\b", CI);

    private static final Pattern[] PAID_TO = {
            Pattern.compile("(?:paid|sent|transferred)\\s+(?:(?:₹|rs\\.?)\\s*[0-9,.]+\\s+)?(?:successfully\\s+)?to\\s+(.+?)(?:\\s+(?:using|from|via|on|successfully|with)\\b|[.!|\\n]|$)", CI),
            Pattern.compile("payment (?:of (?:₹|rs\\.?)\\s*[0-9,.]+ )?to\\s+(.+?)(?:\\s+(?:is|was|has|successful|using|from|via)\\b|[.!|\\n]|$)", CI),
            Pattern.compile("(?:₹|rs\\.?)\\s*[0-9,.]+\\s+(?:paid|sent)\\s+to\\s+(.+?)(?:\\s+(?:using|from|via|on)\\b|[.!|\\n]|$)", CI),
            Pattern.compile("(?:₹|rs\\.?)\\s*[0-9,.]+\\s+to\\s+(.+?)(?:\\s+(?:using|from|via|on|successfully|is|was)\\b|[.!|\\n]|$)", CI),
            // Google Pay style: title "₹1 paid" / "Payment successful", text "To Ramesh Kumar"
            Pattern.compile("(?:^|\\|\\s*)to\\s+([A-Za-z][^|.!\\n]{1,40}?)(?:\\s+(?:using|from|via|on|successfully)\\b|[.!|\\n]|$)", CI),
    };
    private static final Pattern[] FROM = {
            Pattern.compile("^(.+?)\\s+(?:paid|sent)\\s+you", CI),
            Pattern.compile("(?:received|got)\\s+(?:(?:₹|rs\\.?)\\s*[0-9,.]+\\s+)?from\\s+(.+?)(?:\\s+(?:in|on|to|via)\\b|[.!|\\n]|$)", CI),
            Pattern.compile("\\bfrom\\s+(.+?)(?:\\s+(?:in|on|to|via)\\b|[.!|\\n]|$)", CI),
    };

    /** A payment app's notification → a transaction, or null. */
    public static SmsParser.Result parseApp(String pkg, String title, String text) {
        String app = appName(pkg);
        if (app == null) return null;
        String t = (title == null ? "" : title.trim());
        String x = (text == null ? "" : text.trim());
        String all = (t + " | " + x).replace(' ', ' ');
        String lo = all.toLowerCase(Locale.ROOT);
        if (SKIP.matcher(lo).find()) return null;
        if (PROMO.matcher(lo).find() && !STRONG.matcher(lo).find()) return null;
        Matcher am = AMOUNT.matcher(all);
        if (!am.find()) return null;
        long amt = SmsParser.paise(am.group(1));
        if (amt <= 0) return null;
        Matcher dm = DEBIT.matcher(lo), cm = CREDIT.matcher(lo);
        int di = dm.find() ? dm.start() : -1, ci = cm.find() ? cm.start() : -1;
        if (di < 0 && ci < 0) return null;
        SmsParser.Result r = new SmsParser.Result();
        r.txn = true;
        r.amount = amt;
        r.credit = di < 0 || (ci >= 0 && ci < di);
        // "Ramesh paid you ₹200" — "paid" comes first but it's money in
        if (lo.contains("paid you") || lo.contains("sent you")) r.credit = true;
        String who = "";
        for (Pattern p : r.credit ? FROM : PAID_TO) {
            Matcher m = p.matcher(all);
            if (m.find()) { who = SmsParser.cleanName(m.group(1).replace("|", " ")); if (!who.isEmpty()) break; }
        }
        if (who.isEmpty() && !t.isEmpty() && !AMOUNT.matcher(t).find() && !DEBIT.matcher(t).find()
                && !CREDIT.matcher(t).find() && !t.equalsIgnoreCase(app)) {
            who = SmsParser.cleanName(t);
        }
        r.merchant = who;
        r.bank = SmsParser.bankOf("", lo);
        Matcher acc = Pattern.compile("(?:a/c|account|acct|bank|••|xx|\\*\\*)\\s*(?:no\\.?)?\\s*[x*•.]*\\s*([0-9]{4})\\b", CI).matcher(all);
        if (acc.find() && !r.bank.isEmpty()) { r.last4 = acc.group(1); r.account = r.bank + " ••" + r.last4; }
        Matcher ref = Pattern.compile("(?:upi ref|ref(?:erence)?|utr|transaction id)\\s*(?:no\\.?|id)?\\s*[:.#-]?\\s*([0-9]{9,16})", CI).matcher(all);
        if (ref.find()) r.ref = ref.group(1);
        return r;
    }

    /** The Messages app showing a bank SMS: read it like an SMS (sender = notification title). */
    public static SmsParser.Result parseSmsNotif(String title, String text) {
        if (title == null || text == null) return null;
        return SmsParser.parse(title, text);
    }
}
