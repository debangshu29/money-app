package com.debangshu.money;

/** A bank account, card or wallet, found automatically from the messages. */
public final class Account {
    public String key = "";        // as written on transactions, e.g. "HDFC ••1234"
    public String name = "";       // your own name for it (optional)
    public boolean card, wallet;
    public long balance = -1;      // paise, from the last message that showed it
    public long balanceTime;
    public long limit = -1;        // card: available limit
    public long limitTime;
    public long lastUsed;
    public boolean hidden;         // not counted in "total balance"

    public String display() { return name == null || name.isEmpty() ? key : name; }
}
