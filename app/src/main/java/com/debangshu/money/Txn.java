package com.debangshu.money;

/**
 * One money movement: a payment, a credit, an ATM withdrawal…
 * Amounts are kept in paise (₹1 = 100) so sums are always exact.
 */
public final class Txn {

    // what kind of movement it is (only NORMAL counts as spending / income)
    public static final int NORMAL = 0, TRANSFER = 1, REFUND = 2, IGNORED = 3, SNAPSHOT = 4;

    // where it was seen (several can be set when the same payment came from SMS and from an app)
    public static final int SRC_SMS = 1, SRC_APP = 2, SRC_GAP = 4, SRC_MANUAL = 8;

    public String id;
    public long time;
    public long amount;            // paise, always positive
    public boolean credit;         // true = money came in
    public String account = "";    // e.g. "HDFC ••1234" (empty when unknown)
    public String merchant = "";   // who was paid / who paid
    public String vpa = "";        // UPI id, when known
    public String category = "";
    public int kind = NORMAL;
    public int sources;
    public long balance = -1;      // account balance right after this (paise), -1 = unknown
    public long limit = -1;        // credit card: available limit after this, -1 = unknown
    public String ref = "";        // UPI / bank reference number
    public String raw = "";        // the original message
    public String sender = "";     // SMS sender (e.g. HDFCBK) or app name (e.g. Google Pay)
    public String note = "";       // your own note
    public String srcKey = "";     // stops the same message being counted twice
    public boolean review;         // shown under "Needs a look"
    public boolean userCategory;   // category picked by you (never changed automatically)
    public boolean userMerchant;   // name changed by you

    public boolean isSpend() { return kind == NORMAL && !credit; }
    public boolean isIncome() { return kind == NORMAL && credit; }
    public boolean visible() { return kind != SNAPSHOT; }

    /** + for money in, − for money out (paise). */
    public long signed() { return credit ? amount : -amount; }

    public Txn copy() {
        Txn t = new Txn();
        t.id = id; t.time = time; t.amount = amount; t.credit = credit; t.account = account; t.merchant = merchant;
        t.vpa = vpa; t.category = category; t.kind = kind; t.sources = sources; t.balance = balance; t.limit = limit;
        t.ref = ref; t.raw = raw; t.sender = sender; t.note = note; t.srcKey = srcKey; t.review = review;
        t.userCategory = userCategory; t.userMerchant = userMerchant;
        return t;
    }

    static String nz(String s) { return s == null ? "" : s; }
}
