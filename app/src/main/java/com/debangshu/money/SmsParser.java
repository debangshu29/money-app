package com.debangshu.money;

import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads Indian bank / card / wallet SMS and pulls out what happened:
 * amount, money in or out, account, who was paid, balance, reference number.
 * Also spots card bills ("Total amount due … due date …") and balance-only messages.
 *
 * Plain Java (no Android), so it is tested on a computer with real-style messages.
 */
public final class SmsParser {

    private SmsParser() {}

    public static final class Result {
        public boolean txn;            // a real money movement
        public boolean credit;
        public long amount = -1;       // paise
        public String bank = "";
        public String last4 = "";
        public boolean card;           // credit card account
        public boolean wallet;
        public String account = "";    // display key, e.g. "HDFC ••1234"
        public String merchant = "";
        public String vpa = "";
        public String ref = "";
        public long balance = -1;
        public long limit = -1;
        public boolean atm, refund, cardPayment, selfTransfer;
        // card bill
        public boolean bill;
        public long due = -1, minDue = -1, dueDate = -1;
        // only tells the balance (no money moved)
        public boolean balanceOnly;
    }

    // ------------------------------------------------------------------ banks

    /** sender id part, words in the message, name shown in the app */
    private static final String[][] BANKS = {
            {"SBI", "state bank|\\bsbi\\b|yono", "SBI"},
            {"HDFC", "hdfc", "HDFC"},
            {"ICICI", "icici", "ICICI"},
            {"AXIS", "axis bank|\\baxis\\b", "Axis"},
            {"KOTAK", "kotak", "Kotak"},
            {"PNB", "\\bpnb\\b|punjab national", "PNB"},
            {"BOB", "\\bbob\\b|bank of baroda", "BOB"},
            {"CANBNK", "canara", "Canara"},
            {"UNION", "union bank", "Union Bank"},
            {"IDFC", "idfc", "IDFC First"},
            {"YESB", "yes bank", "Yes Bank"},
            {"INDUS", "indusind", "IndusInd"},
            {"IDBI", "\\bidbi\\b", "IDBI"},
            {"BOI", "bank of india|\\bboi\\b", "BOI"},
            {"INDBNK", "indian bank", "Indian Bank"},
            {"UCO", "\\buco\\b", "UCO"},
            {"CENTBK", "central bank", "Central Bank"},
            {"BANDHN", "bandhan", "Bandhan"},
            {"AUBANK", "\\bau bank|au small", "AU Bank"},
            {"FEDBNK", "federal bank", "Federal"},
            {"RBL", "\\brbl\\b", "RBL"},
            {"SCB", "standard chartered|\\bsc bank", "SC Bank"},
            {"CITI", "\\bciti", "Citi"},
            {"HSBC", "hsbc", "HSBC"},
            {"PAYTM", "paytm", "Paytm"},
            {"AIRBNK", "airtel payments", "Airtel Bank"},
            {"IPB", "india post|ippb", "IPPB"},
            {"KVB", "karur vysya", "KVB"},
            {"SIB", "south indian bank", "SIB"},
            {"CUB", "city union", "CUB"},
            {"DBS", "\\bdbs\\b", "DBS"},
            {"EQUTAS", "equitas", "Equitas"},
            {"UJJIVN", "ujjivan", "Ujjivan"},
            {"JUPITR", "jupiter", "Jupiter"},
            {"FIMONY", "\\bfi money|federal bank", "Fi"},
            {"AMZPAY", "amazon pay", "Amazon Pay"},
            {"MOBIKW", "mobikwik", "MobiKwik"},
            {"ONECRD", "onecard|one card", "OneCard"},
            {"SLICE", "\\bslice\\b", "Slice"},
    };

    /** "AX-HDFCBK-S" → "HDFCBK" */
    public static String cleanSender(String s) {
        if (s == null) return "";
        s = s.trim().toUpperCase(Locale.ROOT);
        s = s.replaceFirst("^[A-Z]{2}-", "");
        s = s.replaceFirst("-[STPG]$", "");
        return s;
    }

    static String bankOf(String sender, String lo) {
        String s = cleanSender(sender);
        for (String[] b : BANKS) if (!s.isEmpty() && s.contains(b[0])) return b[2];
        for (String[] b : BANKS) if (Pattern.compile(b[1]).matcher(lo).find()) return b[2];
        return "";
    }

    // ------------------------------------------------------------------ patterns

    private static final int CI = Pattern.CASE_INSENSITIVE;
    private static final String NUM = "([0-9][0-9,]*(?:\\.[0-9]{1,2})?)";
    private static final Pattern MONEY = Pattern.compile("(?:\\brs\\b\\.?|\\binr\\b|₹|\\brupees\\b|\\brs(?=[0-9.:]))\\s*[:.]?\\s*" + NUM, CI);
    private static final Pattern VERB_AMT = Pattern.compile(
            "\\b(?:debited|credited|debit|credit)\\s+(?:by|with|for|of)\\s+(?:(?:rs\\.?|inr|₹)\\s*)?" + NUM, CI);
    private static final Pattern AMT_AFTER = Pattern.compile("^(?:ance)?\\s*(?:\\.|is|:|-|of|amt|amount)*\\s*(?:(?:rs\\.?|inr|₹)\\s*)?[:.]?\\s*(-?\\s?[0-9][0-9,]*(?:\\.[0-9]{1,2})?)", CI);

    private static final Pattern ACC = Pattern.compile(
            "(?:\\ba/?c\\b|\\bacct\\b|\\baccount\\b|\\bac\\b|a/c(?=[x*0-9]))\\.?\\s*(?:no\\.?|number|num)?\\s*[:\\-]?\\s*(?:ending\\s*(?:with|in)?\\s*)?[x*.\\s]*([0-9]{3,6})\\b", CI);
    private static final Pattern CARD = Pattern.compile(
            "\\bcard\\s*(?:no\\.?|number)?\\s*(?:ending|ends)?\\s*(?:with|in)?\\s*[:\\-]?\\s*[x*.\\s]*([0-9]{4})\\b", CI);
    private static final Pattern VPA = Pattern.compile("([a-zA-Z0-9][a-zA-Z0-9._-]{1,60}@[a-zA-Z][a-zA-Z0-9]{1,20})(?![.a-zA-Z0-9])");
    private static final Pattern[] REF = {
            Pattern.compile("UPI/(?:P2[MAP]|DR|CR)/([0-9]{9,16})", CI),
            Pattern.compile("\\b(?:upi\\s*ref(?:erence)?|ref(?:erence)?|refno|utr|rrn|txn\\s*id|upi)\\s*(?:no\\.?|number|id)?\\s*[:.#-]?\\s*([0-9]{9,16})\\b", CI),
    };

    private static final Pattern OTP = Pattern.compile("\\botp\\b|one[ -]time[ -]pass|verification code|\\bcode is\\b|security code", CI);
    private static final Pattern OTP_VALUE = Pattern.compile("(?:\\botp\\b|code|password)\\D{0,25}\\b[0-9]{4,8}\\b|\\b[0-9]{4,8}\\b\\s*(?:is|as)\\s*(?:your|the)?\\s*(?:otp|one|code|verification)", CI);
    private static final Pattern REQUEST = Pattern.compile(
            "has requested|requested (?:money|payment|rs|inr|₹)|collect request|is requesting|payment request|request (?:of|for) (?:rs|inr|₹)|approve (?:the )?request", CI);
    private static final Pattern FUTURE = Pattern.compile(
            "will be (?:auto[- ]?)?debited|would be debited|to be debited|will be deducted|is scheduled|scheduled (?:for|on)|upcoming|"
            + "\\bdue (?:on|by|date)\\b|\\bis due\\b|reminder|e-?mandate|mandate (?:has been )?(?:created|registered|set)|si (?:has been )?registered|"
            + "standing instruction|autopay (?:has been )?(?:set|registered|enabled)|kindly pay|please pay|pay now|bill (?:is )?generated", CI);
    private static final Pattern FAILED = Pattern.compile(
            "\\bfailed\\b|\\bdeclined\\b|unsuccessful|could not be|not processed|insufficient|\\brejected\\b|\\bcancelled\\b", CI);
    private static final Pattern REVERSAL = Pattern.compile("\\breversed\\b|\\brefunded\\b|\\breversal\\b|\\brefund\\b", CI);
    private static final Pattern PROMO = Pattern.compile(
            "pre-?approved|eligible for|apply now|limited period|exclusive offer|get up ?to|\\bupto\\b|\\bwin\\b|congratulations|"
            + "instant loan|loan (?:of|upto|up to)|limit (?:has been )?(?:increased|enhanced)|click (?:here|on)|bit\\.ly|t\\.me/|"
            + "avail (?:now|the)|offer valid|% off|discount|lowest emi|no cost emi|t&c apply", CI);

    private static final Pattern DEBIT_WORD = Pattern.compile(
            "\\b(?:debited|debit|spent|sent|paid|withdrawn|withdrawal|purchase|purchased|used for|used at|txn of|transaction of|"
            + "dr\\b\\.?|deducted|charged|transferred to|trf to|transfer to|payment of|has been used|txn\\b|withdraw)", CI);
    private static final Pattern CREDIT_WORD = Pattern.compile(
            "\\b(?:credited|credit\\b|received|deposited|cr\\b\\.?|refund(?:ed)?|reversed|reversal|cashback|added to)", CI);

    private static final Pattern BILL = Pattern.compile("total (?:amt|amount) due|total due|amount due|amt due|statement", CI);
    private static final Pattern BILL_DUE = Pattern.compile("due date|due by|due on|pay by|payment due", CI);

    // ------------------------------------------------------------------ main

    /** Returns null when the message is not about money moving (OTP, offer, request, failed…). */
    public static Result parse(String sender, String body) {
        if (body == null) return null;
        String snd = cleanSender(sender);
        if (sender != null) {
            String raw = sender.trim();
            if (raw.toUpperCase(Locale.ROOT).endsWith("-P")) return null;                  // promotional
            if (raw.replaceAll("[+\\s-]", "").matches("[0-9]{8,}")) return null;          // a person's number (or a fake)
        }
        String text = body.replace('\u00a0', ' ').replace("\r", "");
        String lo = text.toLowerCase(Locale.ROOT);

        if (OTP.matcher(lo).find() && OTP_VALUE.matcher(lo).find()) return null;
        if (REQUEST.matcher(lo).find()) return null;

        Result r = new Result();
        r.bank = bankOf(snd, lo);

        // card bill: "Total Amt Due Rs 12,345 … Payment Due Date 25-11-2026"
        if (BILL.matcher(lo).find() && BILL_DUE.matcher(lo).find() && lo.contains("card")) {
            r.bill = true;
            r.due = amountAfter(lo, new String[]{"total amount due", "total amt due", "total due", "amount due", "amt due"});
            r.minDue = amountAfter(lo, new String[]{"minimum amount due", "min amount due", "min amt due", "minimum due", "min due", "mad"});
            r.dueDate = dueDate(text);
            account(r, text, lo, true);
            return r.due > 0 || r.dueDate > 0 ? r : null;
        }

        boolean reversal = REVERSAL.matcher(lo).find();
        if (FUTURE.matcher(lo).find()) {
            String rest = FUTURE.matcher(lo).replaceAll(" ").replaceAll("will be \\w+|would be \\w+|to be \\w+", " ");
            if (!rest.matches("(?s).*\\b(debited|credited|spent|withdrawn|sent|received)\\b.*")) return null;
        }
        if (FAILED.matcher(lo).find() && !reversal) return null;

        // "credit card" / "debit card" are not directions
        String dir = lo.replace("credit card", "cc card").replace("debit card", "dc card")
                .replace("credit limit", "cc limit").replace("debit/credit", "card").replace("credit/debit", "card");

        Matcher dm = DEBIT_WORD.matcher(dir), cm = CREDIT_WORD.matcher(dir);
        int di = -1, ci = cm.find() ? cm.start() : -1;
        while (dm.find()) {
            if (ci >= 0 && dm.group().toLowerCase(Locale.ROOT).startsWith("payment of")) continue;   // "payment of ₹X received"
            di = dm.start();
            break;
        }

        balanceAndLimit(r, lo);

        long amt = txnAmount(text, lo);
        boolean promo = PROMO.matcher(lo).find();

        if (amt <= 0 || (di < 0 && ci < 0)) {
            // nothing moved, but the message may tell the balance
            if (r.balance >= 0 && !promo && (lo.contains("a/c") || lo.contains("account") || lo.contains("acct"))) {
                r.balanceOnly = true;
                account(r, text, lo, false);
                return r;
            }
            return null;
        }
        boolean hasAcc = ACC.matcher(text).find() || CARD.matcher(text).find() || lo.contains("wallet") || lo.contains("upi");
        if (promo && !hasAcc) return null;
        if (!hasAcc && r.bank.isEmpty()) return null;

        r.txn = true;
        r.amount = amt;
        if (di >= 0 && ci >= 0) r.credit = ci < di;
        else r.credit = ci >= 0;
        // "Dr. from A/C … and Cr. to x@ybl" style: the first word wins (handled above)

        if (reversal && r.credit) r.refund = true;
        if (lo.contains("cashback")) r.refund = r.credit;

        r.atm = lo.contains("atm") && (lo.contains("withdraw") || lo.contains("cash"));
        r.wallet = lo.contains("wallet") || lo.contains("paytm balance") || lo.contains("amazon pay balance");

        boolean payingCard = !r.credit && Pattern.compile("credit card (?:bill )?payment|cc payment|card (?:bill )?payment|cred club|"
                + "towards (?:your )?[a-z ]*credit card|billdesk[^.]*card|cc bill", CI).matcher(lo).find();
        account(r, text, payingCard ? lo.replace("credit card", "card") : lo, false);
        if (payingCard && !r.card) r.cardPayment = true;

        if (r.card && r.credit && (lo.contains("payment") || lo.contains("received towards") || lo.contains("thank you for"))) {
            r.cardPayment = true;
        }
        if (Pattern.compile("self transfer|to self\\b|own account|between your accounts", CI).matcher(lo).find()) r.selfTransfer = true;

        Matcher vm = VPA.matcher(text);
        while (vm.find()) {
            String v = vm.group(1);
            if (v.toLowerCase(Locale.ROOT).matches(".*@(gmail|yahoo|outlook|hotmail|rediff)")) continue;
            r.vpa = v;
            break;
        }
        for (Pattern p : REF) {
            Matcher m = p.matcher(text);
            if (m.find()) { r.ref = m.group(1); break; }
        }
        if (r.atm) r.merchant = "ATM cash";
        else r.merchant = merchant(text, r.credit);
        if (r.merchant.isEmpty() && !r.vpa.isEmpty()) r.merchant = nameFromVpa(r.vpa);
        return r;
    }

    // ------------------------------------------------------------------ amount / balance

    /** The amount that moved: the first ₹ amount that isn't a balance / limit / due. */
    static long txnAmount(String text, String lo) {
        Matcher m = MONEY.matcher(text);
        while (m.find()) {
            int s = m.start();
            String before = lo.substring(Math.max(0, s - 28), s);
            if (before.matches("(?s).*(bal|balance|limit|lmt|due|outstanding|o/s|min\\.?|minimum)\\s*[a-z.:\\-]*\\s*$")) continue;
            if (before.matches("(?s).*(bal|limit|lmt)[^0-9]{0,12}$")) continue;
            long v = paise(m.group(1));
            if (v > 0) return v;
        }
        Matcher v = VERB_AMT.matcher(text);
        if (v.find()) return paise(v.group(1));
        return -1;
    }

    static void balanceAndLimit(Result r, String lo) {
        // balance: "Avl Bal Rs 1,234.50", "AvlBal:Rs5,432.10", "Bal INR 3,456.78", "Available balance is Rs. 100"
        int i = 0;
        while ((i = lo.indexOf("bal", i)) >= 0) {
            boolean word = i == 0 || !Character.isLetter(lo.charAt(i - 1)) || lo.startsWith("avl", Math.max(0, i - 3))
                    || lo.startsWith("ail", Math.max(0, i - 3));
            String tail = lo.substring(i + 3, Math.min(lo.length(), i + 40));
            if (word && !tail.startsWith("l")) {   // not "ball" etc.
                Matcher m = AMT_AFTER.matcher(tail);
                if (m.find()) {
                    long v = paise(m.group(1).replace(" ", ""));
                    if (v >= 0 || m.group(1).trim().startsWith("-")) { r.balance = v; break; }
                }
            }
            i += 3;
        }
        if (r.balance < 0) {
            Matcher m = Pattern.compile("bal(?:ance)?\\b[^₹]{0,40}?\\b(?:is|:|of)\\s*(?:rs\\.?|inr|₹)\\s*[:.]?\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)", CI).matcher(lo);
            if (m.find()) r.balance = paise(m.group(1));
        }
        // card: "Avl Limit: INR 47,501.00", "Available Credit Limit Rs 50,000"
        String[] keys = {"avl limit", "avl lmt", "available limit", "available credit limit", "avl. limit", "avl cr lmt", "avl credit limit", "limit available"};
        for (String k : keys) {
            int j = lo.indexOf(k);
            if (j < 0) continue;
            Matcher m = AMT_AFTER.matcher(lo.substring(j + k.length(), Math.min(lo.length(), j + k.length() + 40)));
            if (m.find()) { r.limit = paise(m.group(1).replace(" ", "")); break; }
        }
    }

    static long amountAfter(String lo, String[] keys) {
        for (String k : keys) {
            int j = lo.indexOf(k);
            if (j < 0) continue;
            if (k.equals("mad") && (j > 0 && Character.isLetter(lo.charAt(j - 1)))) continue;
            Matcher m = AMT_AFTER.matcher(lo.substring(j + k.length(), Math.min(lo.length(), j + k.length() + 40)));
            if (m.find()) return paise(m.group(1).replace(" ", ""));
        }
        return -1;
    }

    static long paise(String s) {
        if (s == null) return -1;
        s = s.replace(",", "").trim();
        boolean neg = s.startsWith("-");
        if (neg) s = s.substring(1).trim();
        if (s.isEmpty()) return -1;
        try {
            int dot = s.indexOf('.');
            long rupees = Long.parseLong(dot < 0 ? s : (dot == 0 ? "0" : s.substring(0, dot)));
            long p = 0;
            if (dot >= 0) {
                String f = s.substring(dot + 1);
                if (f.length() == 1) f += "0";
                if (f.length() > 2) f = f.substring(0, 2);
                if (!f.isEmpty()) p = Long.parseLong(f);
            }
            long v = rupees * 100 + p;
            return neg ? -v : v;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ------------------------------------------------------------------ account

    static void account(Result r, String text, String lo, boolean bill) {
        boolean creditCard = lo.contains("credit card") || lo.contains("cc card") || r.limit >= 0
                || (bill && lo.contains("card"));
        Matcher a = ACC.matcher(text), c = CARD.matcher(text);
        String acc = a.find() ? a.group(1) : "", card = c.find() ? c.group(1) : "";
        if (creditCard && !card.isEmpty()) { r.card = true; r.last4 = last4(card); }
        else if (creditCard && !acc.isEmpty()) { r.card = true; r.last4 = last4(acc); }
        else if (!acc.isEmpty()) r.last4 = last4(acc);
        else if (!card.isEmpty()) r.last4 = last4(card);
        if (creditCard) r.card = true;

        String bank = r.bank.isEmpty() ? "" : r.bank;
        if (r.wallet && r.last4.isEmpty()) {
            r.account = (bank.isEmpty() ? "Wallet" : bank + " wallet");
        } else if (r.card) {
            r.account = (bank.isEmpty() ? "Card" : bank + " card") + (r.last4.isEmpty() ? "" : " ••" + r.last4);
        } else {
            r.account = bank.isEmpty() ? (r.last4.isEmpty() ? "" : "A/c ••" + r.last4) : bank + (r.last4.isEmpty() ? "" : " ••" + r.last4);
        }
    }

    private static String last4(String d) { return d.length() > 4 ? d.substring(d.length() - 4) : d; }

    // ------------------------------------------------------------------ merchant

    private static final Pattern[] DEBIT_WHO = {
            Pattern.compile("\\btrf to\\s+(.+?)\\s+(?:ref|refno|on)\\b", CI),
            Pattern.compile("UPI/(?:P2[MAP]|DR)/[0-9]+/([^\\n/]+)", CI),
            Pattern.compile("(?:^|\\n)\\s*to\\s*:?\\s+([^\\n]+?)\\s*(?:\\n|$)", CI),
            Pattern.compile("\\bto\\s+vpa\\s+(\\S+)", CI),
            Pattern.compile("\\b(?:cr\\.?|credited)\\s+to\\s+([a-zA-Z0-9._-]+@[a-zA-Z0-9]+)", CI),
            Pattern.compile(";\\s*([^;.\\n]+?)\\s+credited", CI),
            Pattern.compile("\\b(?:paid|sent|transferred|payment of)\\s+(?:(?:rs\\.?|inr|₹)\\s*[0-9,.]+\\s+)?to\\s+(.+?)(?:\\s+on\\s|\\s+from\\s|\\s+via\\s|\\s+using\\s|\\s+ref|\\s+upi|[.,;]\\s|\\.$|\\n|$)", CI),
            Pattern.compile("\\bto\\s+([a-zA-Z0-9._-]+@[a-zA-Z0-9]+)", CI),
            Pattern.compile("\\b(?:at|@)\\s+([A-Za-z][^\\n]*?)(?:\\s+on\\s|\\s+by\\s|\\s+via\\s|\\s+for\\s|\\s*\\.\\s|\\s+avl|\\s+bal|\\s+txn|\\s+ref|,|\\.$|\\n|$)", CI),
            Pattern.compile("\\binfo[:\\s-]+\\s*(.+?)(?:\\.\\s|\\s+avl|\\s+bal|\\n|$)", CI),
            Pattern.compile("\\btowards\\s+(.+?)(?:\\.\\s|\\s+total|\\s+avl|\\s+bal|\\n|$)", CI),
            Pattern.compile("\\bto\\s+([A-Za-z][A-Za-z0-9 .&'-]{1,40}?)(?:\\s+on\\s|\\s+ref|\\s+upi|[.,]\\s|\\.$|\\n|$)", CI),
            Pattern.compile("\\bfor\\s+([A-Z][A-Z0-9&'.-]+(?: [A-Z0-9&'.-]+){0,4})(?:\\.\\s|\\.$|\\s+avl|\\s+on\\s|,|\\n|$)"),
    };
    private static final Pattern[] CREDIT_WHO = {
            Pattern.compile("UPI/(?:P2[MAP]|CR)/[0-9]+/([^\\n/]+)", CI),
            Pattern.compile("\\bfrom\\s+vpa\\s+(\\S+)", CI),
            Pattern.compile("\\bfrom\\s+([a-zA-Z0-9._-]+@[a-zA-Z0-9]+)", CI),
            Pattern.compile("\\b(?:neft|imps|rtgs)[-/ ](?:[A-Z]*[0-9][A-Z0-9]*[-/ ])+([A-Za-z][A-Za-z .&'-]{2,40})", CI),
            Pattern.compile("\\bby\\s+(?:neft|imps|rtgs|upi)\\s+from\\s+([A-Za-z][A-Za-z0-9 .&'-]{1,40}?)(?:\\.|,|\\s+avl|\\s+on\\s|\\s+ref|\\n|$)", CI),
            Pattern.compile("\\bfrom\\s+([A-Za-z][A-Za-z0-9 .&'-]{1,40}?)(?:\\s*\\(|\\.\\s|\\.$|,|\\s+on\\s|\\s+upi|\\s+ref|\\s+avl|\\s+info|\\s+via|\\n|$)", CI),
            Pattern.compile("\\binfo[:\\s-]+\\s*(.+?)(?:\\.\\s|\\s+avl|\\s+bal|\\n|$)", CI),
    };

    static String merchant(String text, boolean credit) {
        for (Pattern p : credit ? CREDIT_WHO : DEBIT_WHO) {
            Matcher m = p.matcher(text);
            while (m.find()) {
                String c = cleanName(m.group(1));
                if (!c.isEmpty()) return c;
            }
        }
        return "";
    }

    private static final Pattern NOT_A_NAME = Pattern.compile(
            "^(?:your|ur|the|a/?c|ac|acct|account|card|bank|hdfc bank|sbi|icici bank|axis bank|kotak bank|beneficiary|mobile|"
            + "you|self|rs|inr|on|date|upi|imps|neft|rtgs|ref|call|sms|block|not you|dispute)\\b.*", CI);

    /** "ZOMATO LTD " → "Zomato Ltd", "swiggy@ybl" → "Swiggy", junk → "" */
    public static String cleanName(String s) {
        if (s == null) return "";
        s = s.replaceAll("[\\s\\u00a0]+", " ").trim();
        s = s.replaceAll("\\.\\s.*$", "");
        s = s.replaceAll("^(?:VPA|UPI|M/S\\.?|MS\\.?|MR\\.?|MRS\\.?)\\s+", "");
        s = s.replaceAll("(?i)\\s*(?:\\(.*|\\bref\\b.*|\\bupi\\b.*|\\bon\\s+[0-9].*|\\bnot you.*|\\bavl.*|\\bbal\\b.*)$", "");
        s = s.replaceAll("[.,;:\\-\\s]+$", "").trim();
        if (s.contains("@")) return nameFromVpa(s);
        s = s.replaceAll("(?i)\\.(?:com|in|co\\.in|net|org)$", "");
        if (s.length() < 2 || s.length() > 45) return "";
        if (NOT_A_NAME.matcher(s).matches()) return "";
        if (s.matches("[0-9 x*:/.-]+")) return "";
        if (s.matches("(?i).*\\b(?:a/c|acct|account)\\b.*")) return "";
        if (s.equals(s.toUpperCase(Locale.ROOT))) s = Fmt.title(s);
        return s;
    }

    /** UPI id → a readable name: "swiggy.stores@axb" → "Swiggy Stores", "9876543210@ybl" → "98765 43210" */
    public static String nameFromVpa(String vpa) {
        String local = vpa.contains("@") ? vpa.substring(0, vpa.indexOf('@')) : vpa;
        String l = local.toLowerCase(Locale.ROOT);
        if (l.matches("(?:paytmqr|q[0-9]|bharatpe|gpay-|phonepeqr|ombk|stq|mab|yespay|bqr|paytm-)[a-z0-9.-]*") || l.matches(".*[0-9]{8,}.*[a-z].*|.*[a-z].*[0-9]{8,}.*"))
            return "Shop (QR) " + (local.length() > 6 ? local.substring(local.length() - 4) : local);
        String digits = l.replaceAll("[^0-9]", "");
        if (digits.length() >= 10 && digits.length() == l.replaceAll("[.\\-_]", "").length()) {
            String d = digits.substring(digits.length() - 10);
            return d.substring(0, 5) + " " + d.substring(5);
        }
        local = local.replaceAll("[._-]+", " ").replaceAll("[0-9]+$", "").trim();
        if (local.isEmpty()) return vpa;
        return Fmt.title(local);
    }

    // ------------------------------------------------------------------ due date

    private static final Pattern D_NUM = Pattern.compile("\\b([0-3]?[0-9])[-/.]([01]?[0-9])[-/.]((?:20)?[0-9]{2})\\b");
    private static final Pattern D_MON = Pattern.compile("\\b([0-3]?[0-9])(?:st|nd|rd|th)?[-\\s/]?(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*[-\\s/,]*((?:20)?[0-9]{2})?\\b", CI);
    private static final Pattern D_ISO = Pattern.compile("\\b(20[0-9]{2})-([01][0-9])-([0-3][0-9])\\b");

    /** The date after "due date / due by / pay by", or -1. */
    static long dueDate(String text) {
        String lo = text.toLowerCase(Locale.ROOT);
        Matcher k = BILL_DUE.matcher(lo);
        String part = k.find() ? text.substring(k.end(), Math.min(text.length(), k.end() + 40)) : text;
        long d = findDate(part);
        return d > 0 ? d : findDate(text);
    }

    static long findDate(String s) {
        Matcher m = D_ISO.matcher(s);
        if (m.find()) return make(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
        m = D_NUM.matcher(s);
        if (m.find()) return make(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        m = D_MON.matcher(s);
        if (m.find()) {
            String mon = m.group(2).toLowerCase(Locale.ROOT);
            int mi = 0;
            for (int i = 0; i < 12; i++) if (Fmt.MON[i].toLowerCase(Locale.ROOT).equals(mon)) mi = i + 1;
            int y = m.group(3) == null ? -1 : Integer.parseInt(m.group(3));
            return make(Integer.parseInt(m.group(1)), mi, y);
        }
        return -1;
    }

    private static long make(int d, int mo, int y) {
        if (d < 1 || d > 31 || mo < 1 || mo > 12) return -1;
        Calendar now = Calendar.getInstance();
        if (y < 0) {
            y = now.get(Calendar.YEAR);
            Calendar t = Calendar.getInstance();
            t.set(y, mo - 1, d, 9, 0, 0);
            if (t.getTimeInMillis() < now.getTimeInMillis() - 60L * 86400000) y++;
        } else if (y < 100) y += 2000;
        Calendar c = Calendar.getInstance();
        c.set(y, mo - 1, d, 9, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
}
