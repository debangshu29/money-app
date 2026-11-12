package com.debangshu.money;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the bank / telecom / insurer messages that are not payments but still matter (plain Java):
 * failed payments, renewals & expiries, FASTag balance, EMIs & loans, reward points.
 */
public final class Notices {

    private Notices() {}

    private static final int CI = Pattern.CASE_INSENSITIVE;
    private static final Pattern PROMO = Pattern.compile(
            "pre-?approved|eligible for|apply now|limited period|exclusive offer|get up ?to|\\bwin\\b|congratulations|click (?:here|on)|"
            + "no cost emi|lowest emi|avail (?:now|the)|offer valid|% off|discount|t&c|bit\\.ly|t\\.me/|instant loan|top-?up loan", CI);
    private static final Pattern OTP = Pattern.compile("\\botp\\b|one[ -]time[ -]pass|verification code", CI);

    // ------------------------------------------------------------------ failed payments

    public static final class Failed {
        public long amount = -1;
        public String account = "", ref = "", merchant = "";
        public boolean debited;     // the message itself says the money left
        public boolean merchantPay; // paid to a shop / card / ATM: T+5 instead of T+1
    }

    private static final Pattern FAILED = Pattern.compile(
            "\\bfailed\\b|\\bfailure\\b|unsuccessful|not (?:been )?successful|could not be (?:processed|completed)|not processed|"
            + "\\bdeclined\\b|timed? ?out|pending (?:at|with) (?:the )?(?:bank|beneficiary)|cash not dispensed|not dispensed", CI);
    private static final Pattern MONEY_LEFT = Pattern.compile(
            "\\b(?:debited|deducted)\\b|will be (?:auto[- ]?)?(?:refunded|reversed|credited back)|(?:refund|reversal) (?:will|shall) be|"
            + "reversed (?:within|in|by)|cash not dispensed|not dispensed", CI);
    private static final Pattern NO_MONEY = Pattern.compile(
            "insufficient|incorrect (?:upi )?pin|wrong pin|invalid pin|exceeded|limit (?:reached|exceeded)|not debited|no amount (?:has been|was) debited", CI);

    /** "Txn of Rs 500 debited from A/c XX12 failed, will be reversed" → the amount to watch. Null when nothing left the account. */
    public static Failed failed(String sender, String body) {
        if (body == null) return null;
        String text = body.replace(' ', ' ');
        String lo = text.toLowerCase(Locale.ROOT);
        if (!FAILED.matcher(lo).find() || OTP.matcher(lo).find() || PROMO.matcher(lo).find()) return null;
        if (NO_MONEY.matcher(lo).find()) return null;
        if (lo.contains("refund") && lo.contains("credited") && !lo.contains("will be credited")) return null;   // that's the refund itself
        Failed f = new Failed();
        f.amount = SmsParser.txnAmount(text, lo);
        if (f.amount <= 0) return null;
        String cond = lo.replaceAll("\\b(?:if|in case)\\b[^.;]{0,100}", " ");   // "if debited, it will be refunded" → not proof
        f.debited = MONEY_LEFT.matcher(cond).find();
        SmsParser.Result r = new SmsParser.Result();
        r.bank = SmsParser.bankOf(SmsParser.cleanSender(sender), lo);
        SmsParser.account(r, text, lo, false);
        f.account = r.account;
        Matcher m = Pattern.compile("(?:ref(?:erence)?|rrn|utr|txn id)\\s*(?:no\\.?|number|id)?\\s*[:.\\-]?\\s*([0-9]{8,14})", CI).matcher(text);
        if (m.find()) f.ref = m.group(1);
        f.merchantPay = lo.contains("card") || lo.contains("atm") || lo.contains("pos") || lo.contains("merchant") || lo.contains("dispensed");
        m = Pattern.compile("\\b(?:to|at)\\s+([A-Za-z][A-Za-z0-9 .&'-]{2,30}?)(?=\\s+(?:on|has|is|was|failed|of|for|ref|via)\\b|[.,;]|$)", CI).matcher(text);
        if (m.find()) f.merchant = SmsParser.cleanName(m.group(1));
        return f;
    }

    // ------------------------------------------------------------------ renewals & expiries

    public static final String RECHARGE = "recharge", INSURANCE = "insurance", PUC = "puc", FASTAG = "fastag",
            OTHER = "other", RETURN = "return", WARRANTY = "warranty";

    public static final class Renewal {
        public String kind = OTHER, name = "";
        public long until = -1;      // expiry / due date
        public long balance = -1;    // FASTag balance (paise)
        public boolean low;          // FASTag balance is low
    }

    private static final Pattern TELCO = Pattern.compile("\\b(jio|airtel|vodafone|\\bvi\\b|vi\\s|bsnl|mtnl)\\b", CI);
    private static final Pattern EXPIRY = Pattern.compile(
            "expir|validity|valid (?:till|until|upto|up to)|will end|plan ends|pack ends|ends on|due for renewal|renewal (?:due|date)|renew (?:by|before|on)|lapse", CI);
    private static final Pattern INSURE = Pattern.compile("\\bpolicy\\b|insurance|premium", CI);
    private static final String[][] INSURERS = {
            {"\\blic\\b|life insurance corporation", "LIC"}, {"hdfc ergo", "HDFC Ergo"}, {"hdfc life", "HDFC Life"}, {"icici lombard", "ICICI Lombard"},
            {"icici pru", "ICICI Pru Life"}, {"star health", "Star Health"}, {"care health|religare", "Care Health"}, {"niva|bupa", "Niva Bupa"},
            {"bajaj allianz", "Bajaj Allianz"}, {"tata aig", "Tata AIG"}, {"acko", "Acko"}, {"go digit|\\bdigit\\b", "Digit"},
            {"sbi life", "SBI Life"}, {"sbi general", "SBI General"}, {"max life|axis max", "Max Life"}, {"new india", "New India"},
            {"united india", "United India"}, {"oriental insurance", "Oriental"}, {"national insurance", "National Insurance"},
            {"aditya birla", "Aditya Birla"}, {"kotak life", "Kotak Life"}, {"policybazaar", "PolicyBazaar"},
    };
    private static final Pattern VEHICLE = Pattern.compile("\\b([A-Z]{2}[ -]?[0-9]{1,2}[ -]?[A-Z]{0,3}[ -]?[0-9]{4})\\b");
    private static final Pattern MOBILE = Pattern.compile("(?:number|no\\.?|mobile|for)\\s*[:\\-]?\\s*(?:\\+?91)?[x*]*([0-9]{4,10})\\b", CI);

    public static Renewal renewal(String sender, String body) {
        if (body == null) return null;
        String text = body.replace(' ', ' ');
        String lo = text.toLowerCase(Locale.ROOT);
        if (OTP.matcher(lo).find()) return null;
        String snd = sender == null ? "" : sender.toLowerCase(Locale.ROOT);
        Renewal r = new Renewal();

        // FASTag balance
        if (lo.contains("fastag") || lo.contains("fas tag") || lo.contains("netc")) {
            long bal = SmsParser.amountAfter(lo, new String[]{"available balance", "avl bal", "avl. bal", "balance is", "balance:", "bal:", "balance of", "balance"});
            if (bal < 0 && !lo.contains("low balance") && !lo.contains("insufficient")) return null;
            r.kind = FASTAG;
            Matcher v = VEHICLE.matcher(text);
            r.name = v.find() ? "FASTag " + v.group(1).replaceAll("[ -]", "") : "FASTag";
            r.balance = bal;
            r.low = lo.contains("low balance") || lo.contains("insufficient") || lo.contains("blacklist") || (bal >= 0 && bal < 20000);
            return r;
        }
        if (PROMO.matcher(lo).find() && !lo.contains("policy no") && !lo.contains("expir")) return null;

        // PUC
        if (Pattern.compile("\\bpuc\\b|pollution under control|pollution certificate", CI).matcher(lo).find() && EXPIRY.matcher(lo).find()) {
            r.kind = PUC;
            Matcher v = VEHICLE.matcher(text);
            r.name = "PUC" + (v.find() ? " " + v.group(1).replaceAll("[ -]", "") : "");
            r.until = date(text, lo);
            return r.until > 0 ? r : null;
        }
        // mobile / DTH / broadband plan
        Matcher tm = TELCO.matcher(snd + " " + lo);
        boolean telco = tm.find();
        if (telco && EXPIRY.matcher(lo).find() && !INSURE.matcher(lo).find()) {
            r.kind = RECHARGE;
            String op = tm.group(1).trim().toLowerCase(Locale.ROOT);
            String opName = op.startsWith("jio") ? "Jio" : op.startsWith("airtel") ? "Airtel" : op.startsWith("bsnl") ? "BSNL" : op.startsWith("mtnl") ? "MTNL" : "Vi";
            Matcher mm = MOBILE.matcher(text);
            String num = mm.find() ? mm.group(1) : "";
            if (num.length() > 4) num = num.substring(num.length() - 4);
            String what = lo.contains("dth") || lo.contains("tv") ? " DTH" : lo.contains("broadband") || lo.contains("fiber") || lo.contains("airfiber") ? " broadband" : " plan";
            r.name = opName + what + (num.isEmpty() ? "" : " ••" + num);
            r.until = date(text, lo);
            if (r.until <= 0) {
                Matcher d = Pattern.compile("(?:in|within)\\s+([0-9]{1,2})\\s+days?", CI).matcher(lo);
                if (d.find()) r.until = Fmt.startOfDay(System.currentTimeMillis()) + Integer.parseInt(d.group(1)) * 86400000L;
                else if (lo.contains("expires today") || lo.contains("expiring today")) r.until = Fmt.startOfDay(System.currentTimeMillis());
                else if (lo.contains("tomorrow")) r.until = Fmt.startOfDay(System.currentTimeMillis()) + 86400000L;
            }
            return r.until > 0 ? r : null;
        }
        // insurance
        if (INSURE.matcher(lo).find() && (EXPIRY.matcher(lo).find() || Pattern.compile("premium[^.]{0,40}\\bdue\\b|due date", CI).matcher(lo).find())) {
            r.kind = INSURANCE;
            String who = "";
            for (String[] i : INSURERS) if (Pattern.compile(i[0], CI).matcher(lo + " " + snd).find()) { who = i[1]; break; }
            String type = lo.contains("health") || lo.contains("medi") ? "health" : lo.contains("motor") || lo.contains("vehicle") || lo.contains("car ") || lo.contains("bike") || lo.contains("two wheeler") ? "vehicle"
                    : lo.contains("life") || lo.contains("term") ? "life" : "";
            Matcher p = Pattern.compile("policy\\s*(?:no\\.?|number|#)?\\s*[:\\-]?\\s*[A-Za-z0-9/\\-]*?([0-9]{4})\\b", CI).matcher(text);
            String pn = p.find() ? " ••" + p.group(1) : "";
            r.name = (who.isEmpty() ? "" : who + " ") + (type.isEmpty() ? "insurance" : type + " insurance") + pn;
            r.name = Character.toUpperCase(r.name.charAt(0)) + r.name.substring(1);
            r.until = date(text, lo);
            return r.until > 0 ? r : null;
        }
        return null;
    }

    private static final Pattern DATE_KEY = Pattern.compile(
            "expir(?:es|y|ing|ed)?(?: date)?(?: on)?|valid(?:ity)? (?:till|until|upto|up to|ends?)(?: on)?|validity(?: date)?|ends on|due (?:on|date|for renewal on)|"
            + "renew(?:al)?(?: date)?(?: is)?(?: due)?(?: on| by| before)?|lapse[sd]? on|due by", CI);

    static long date(String text, String lo) {
        Matcher k = DATE_KEY.matcher(lo);
        while (k.find()) {
            long d = SmsParser.findDate(text.substring(k.end(), Math.min(text.length(), k.end() + 30)));
            if (d > 0) return d;
        }
        return SmsParser.findDate(text);
    }

    // ------------------------------------------------------------------ EMIs & loans

    public static final class Emi {
        public long amount = -1, outstanding = -1, due = -1;
        public String lender = "", loan = "";   // loan = last digits of the loan account
        public boolean paid;
        public String name() { return (lender.isEmpty() ? "Loan" : lender + " loan") + (loan.isEmpty() ? "" : " ••" + loan); }
    }

    private static final String[][] LENDERS = {
            {"bajaj fin", "Bajaj Finance"}, {"hdb fin", "HDB Financial"}, {"tata capital", "Tata Capital"}, {"home credit", "Home Credit"},
            {"muthoot", "Muthoot"}, {"manappuram", "Manappuram"}, {"lic housing|lic hfl", "LIC Housing"}, {"pnb housing", "PNB Housing"},
            {"aditya birla fin", "Aditya Birla Finance"}, {"l&t fin|ltfs", "L&T Finance"}, {"mahindra fin", "Mahindra Finance"},
            {"shriram", "Shriram Finance"}, {"kreditbee", "KreditBee"}, {"moneyview|money view", "MoneyView"}, {"navi\\b", "Navi"},
            {"fibe|earlysalary", "Fibe"}, {"idfc", "IDFC First"}, {"cholamandalam|chola", "Chola"}, {"poonawalla", "Poonawalla"},
    };

    public static Emi emi(String sender, String body) {
        if (body == null) return null;
        String text = body.replace(' ', ' ');
        String lo = text.toLowerCase(Locale.ROOT);
        if (OTP.matcher(lo).find() || PROMO.matcher(lo).find()) return null;
        boolean emiWord = Pattern.compile("\\bemi\\b|\\binstal?lment\\b|loan (?:a/c|ac|account|no)", CI).matcher(lo).find();
        if (!emiWord || !(lo.contains("loan") || lo.contains("emi"))) return null;
        if (lo.contains("credit card") && !lo.contains("loan")) return null;   // card EMI conversion offers etc.
        Emi e = new Emi();
        e.amount = SmsParser.amountAfter(lo, new String[]{"emi of", "emi amount of", "emi amount", "emi amt", "instalment of", "installment of", "emi"});
        if (e.amount <= 0) e.amount = SmsParser.txnAmount(text, lo);
        e.outstanding = SmsParser.amountAfter(lo, new String[]{"principal outstanding", "outstanding principal", "outstanding amount", "outstanding balance",
                "total outstanding", "loan outstanding", "outstanding is", "outstanding of", "outstanding", "o/s"});
        if (e.outstanding == e.amount) e.outstanding = -1;
        Matcher d = Pattern.compile("(?:due (?:on|date)|presented on|will be (?:debited|presented|deducted) on|payable on|on or before)", CI).matcher(lo);
        if (d.find()) e.due = SmsParser.findDate(text.substring(d.end(), Math.min(text.length(), d.end() + 30)));
        String cond = lo.replaceAll("will be \\w+|to be \\w+|due on|please (?:ensure|maintain|keep)", " ");
        e.paid = Pattern.compile("\\b(?:debited|deducted|paid|received|realised|realized|collected|successful(?:ly)?)\\b", CI).matcher(cond).find()
                && !lo.contains("bounced") && !lo.contains("dishonour") && !lo.contains("unpaid") && !lo.contains("returned");
        Matcher l = Pattern.compile("loan\\s*(?:a/c|ac|account|acct)?\\s*(?:no\\.?|number)?\\s*[:\\-]?\\s*(?:ending(?: with)?\\s*)?((?:[0-9]*[x*•]+)?[0-9]{3,})", CI).matcher(text);
        if (l.find()) { String n = l.group(1).replaceAll(".*[^0-9]", ""); e.loan = n.length() > 4 ? n.substring(n.length() - 4) : n; }
        for (String[] x : LENDERS) if (Pattern.compile(x[0], CI).matcher(lo + " " + (sender == null ? "" : sender.toLowerCase(Locale.ROOT))).find()) { e.lender = x[1]; break; }
        if (e.lender.isEmpty()) {
            String b = SmsParser.bankOf(SmsParser.cleanSender(sender), lo);
            e.lender = b;
        }
        if (e.amount <= 0 && e.outstanding <= 0) return null;
        return e;
    }

    // loan maths (monthly reducing balance) -------------------------------------------------------

    /** {months, total interest} to repay {@code p} at {@code ratePct} a year with monthly EMI {@code emi}; months = -1 if it never ends. */
    public static long[] repay(long p, double ratePct, long emi) {
        if (p <= 0) return new long[]{0, 0};
        if (emi <= 0) return new long[]{-1, -1};
        double i = ratePct / 1200.0, bal = p, interest = 0;
        int n = 0;
        while (bal > 0.5 && n < 720) {
            double in = bal * i;
            if (emi <= in && i > 0) return new long[]{-1, -1};
            interest += in;
            bal = bal + in - emi;
            n++;
        }
        return new long[]{n >= 720 ? -1 : n, Math.round(interest)};
    }

    /** Outstanding from months left (EMI and rate known). */
    public static long outstanding(long emi, double ratePct, int months) {
        if (months <= 0) return 0;
        double i = ratePct / 1200.0;
        if (i == 0) return emi * months;
        return Math.round(emi * (1 - Math.pow(1 + i, -months)) / i);
    }

    /** {months saved, interest saved} for a one-time prepayment {@code x} (EMI unchanged, tenure shrinks). */
    public static long[] prepay(long p, double ratePct, long emi, long x) {
        long[] a = repay(p, ratePct, emi), b = repay(Math.max(0, p - x), ratePct, emi);
        if (a[0] < 0 || b[0] < 0) return new long[]{0, 0};
        return new long[]{a[0] - b[0], a[1] - b[1]};
    }

    /** Balance after {@code k} EMIs. */
    public static long after(long p, double ratePct, long emi, int k) {
        double i = ratePct / 1200.0, bal = p;
        for (int n = 0; n < k && bal > 0; n++) bal = bal + bal * i - emi;
        return Math.max(0, Math.round(bal));
    }

    // ------------------------------------------------------------------ reward points

    public static final class Points {
        public String account = "";
        public long balance = -1, earned = -1;
    }

    private static final Pattern PTS_WORD = Pattern.compile("reward points?|rewards? pts|\\bpoints?\\b|neu ?coins?|edge (?:reward|miles)|\\bmiles\\b|cred coins", CI);
    private static final Pattern PTS_BAL = Pattern.compile(
            "(?:reward points?|points?|neu ?coins?|miles|rewards?)\\s*(?:balance|available|total|bal\\.?)\\s*(?:is|of|:|-)?\\s*(?:now\\s*)?([0-9][0-9,]*)"
            + "|(?:balance|available|total)\\s*(?:reward\\s*)?(?:points?|neu ?coins?|miles)\\s*(?:is|of|:|-)?\\s*([0-9][0-9,]*)"
            + "|([0-9][0-9,]*)\\s*(?:reward\\s*)?(?:points?|neu ?coins?|miles)\\s*(?:balance|available|in your account)", CI);
    private static final Pattern PTS_EARN = Pattern.compile(
            "(?:earned|credited with|received|won|added)\\s*([0-9][0-9,]*)\\s*(?:reward\\s*)?(?:points?|neu ?coins?|miles)"
            + "|([0-9][0-9,]*)\\s*(?:reward\\s*)?(?:points?|neu ?coins?|miles)\\s*(?:have been|has been|are|were)?\\s*(?:credited|earned|added)", CI);

    public static Points points(String sender, String body) {
        if (body == null) return null;
        String text = body.replace(' ', ' ');
        String lo = text.toLowerCase(Locale.ROOT);
        if (!PTS_WORD.matcher(lo).find() || OTP.matcher(lo).find()) return null;
        if (Pattern.compile("redeem now|expir(?:e|ing) soon|win|offer|bonus points on|get [0-9]+x|[0-9]+x (?:reward )?points", CI).matcher(lo).find()
                && !Pattern.compile("balance|available", CI).matcher(lo).find()) return null;
        Points p = new Points();
        Matcher m = PTS_BAL.matcher(lo);
        if (m.find()) p.balance = num(m.group(1) != null ? m.group(1) : m.group(2) != null ? m.group(2) : m.group(3));
        m = PTS_EARN.matcher(lo);
        if (m.find()) p.earned = num(m.group(1) != null ? m.group(1) : m.group(2));
        if (p.balance < 0 && p.earned < 0) return null;
        SmsParser.Result r = new SmsParser.Result();
        r.bank = SmsParser.bankOf(SmsParser.cleanSender(sender), lo);
        SmsParser.account(r, text, lo, true);
        p.account = r.account.isEmpty() ? (r.bank.isEmpty() ? "" : r.bank + " card") : r.account;
        if (p.account.isEmpty()) return null;
        return p;
    }

    private static long num(String s) {
        try { return Long.parseLong(s.replace(",", "")); } catch (Exception e) { return -1; }
    }
}
