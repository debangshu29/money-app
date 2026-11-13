package com.debangshu.money;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Compares a bank statement with what Money recorded for one account (plain Java, tested on a computer):
 * each statement row is matched to a payment Money has (same amount, in / out, within a day); rows Money never saw are
 * new payments; payments Money has that the statement doesn't are listed for you to check.
 */
public final class Reconcile {

    private Reconcile() {}

    private static final long DAY = 86400000L;

    public static final class Plan {
        public final List<StatementParser.Row> matchedRows = new ArrayList<>();
        public final List<Txn> matched = new ArrayList<>();     // same order as matchedRows
        public final List<Txn> added = new ArrayList<>();       // new payments to add
        public final List<Txn> notInStatement = new ArrayList<>();
        public long from, to;
    }

    public static Plan plan(List<Txn> all, String account, StatementParser.Result st, String bankName) {
        Plan p = new Plan();
        if (st.rows.isEmpty()) return p;
        p.from = Fmt.startOfDay(st.from);
        p.to = Fmt.startOfDay(st.to) + DAY;
        List<Txn> cand = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (Txn t : all) {
            if (t.kind == Txn.SNAPSHOT || t.kind == Txn.IGNORED || (t.sources & Txn.SRC_GAP) != 0) continue;
            if ("Cash".equals(t.account)) continue;   // the cash wallet
            if (t.time < p.from - DAY || t.time >= p.to + DAY) continue;
            boolean mine = account.equals(t.account) || (t.account.isEmpty() && (t.sources & (Txn.SRC_APP | Txn.SRC_NOTE | Txn.SRC_MANUAL)) != 0);
            if (mine) cand.add(t);
            if (!t.srcKey.isEmpty()) keys.add(t.srcKey);
        }
        Set<Txn> used = new HashSet<>();
        Txn[] byRow = new Txn[st.rows.size()];
        // pass 1: same reference number; pass 2: same amount + direction, nearest day
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < st.rows.size(); i++) {
                if (byRow[i] != null) continue;
                StatementParser.Row r = st.rows.get(i);
                Txn best = null;
                long bestD = Long.MAX_VALUE;
                for (Txn t : cand) {
                    if (used.contains(t) || t.amount != r.amount || t.credit != r.credit) continue;
                    if (pass == 0) { if (r.ref.isEmpty() || !r.ref.equals(t.ref)) continue; }
                    long d = Math.abs(Fmt.startOfDay(t.time) - Fmt.startOfDay(r.date));
                    if (d > DAY) continue;
                    if (pass == 1 && !t.ref.isEmpty() && !r.ref.isEmpty() && !t.ref.equals(r.ref)) continue;
                    if (d < bestD) { bestD = d; best = t; }
                }
                if (best != null) { used.add(best); byRow[i] = best; }
            }
        }
        // rows Money didn't have → new payments, placed in the statement's order between their neighbours
        long lastTime = -1;
        for (int i = 0; i < st.rows.size(); i++) {
            StatementParser.Row r = st.rows.get(i);
            if (byRow[i] != null) {
                p.matchedRows.add(r);
                p.matched.add(byRow[i]);
                lastTime = byRow[i].time;
                continue;
            }
            String key = "stmt:" + Integer.toHexString((account + "|" + r.date + "|" + r.amount + "|" + r.credit + "|" + r.balance + "|" + r.narration).hashCode());
            if (keys.contains(key)) continue;   // imported before
            long next = -1;
            for (int k = i + 1; k < st.rows.size(); k++) if (byRow[k] != null) { next = byRow[k].time; break; }
            long day = Fmt.startOfDay(r.date);
            long time;
            if (r.date != day) time = r.date;                       // the statement gave a time
            else {
                long lo = lastTime >= day && lastTime < day + DAY ? lastTime : day + 9 * 3600000L;
                long hi = next >= day && next < day + DAY ? next : day + 21 * 3600000L;
                time = hi > lo ? lo + Math.max(1000, (hi - lo) / 2) : lo + 60000;
            }
            lastTime = time;
            Txn t = new Txn();
            t.time = time;
            t.amount = r.amount;
            t.credit = r.credit;
            t.account = account;
            t.balance = r.balance;
            t.merchant = r.name;
            t.vpa = r.vpa;
            t.ref = r.ref;
            t.note = r.remark.isEmpty() ? "" : r.remark;
            t.raw = r.narration;
            t.sender = "Bank statement" + (bankName == null || bankName.isEmpty() ? "" : " · " + bankName);
            t.srcKey = key;
            t.sources = Txn.SRC_STMT;
            p.added.add(t);
        }
        for (Txn t : cand) {
            if (used.contains(t) || !account.equals(t.account)) continue;
            if (t.time < p.from || t.time >= p.to) continue;
            p.notInStatement.add(t);
        }
        return p;
    }

    /** What the statement knows that Money didn't (UPI ID, reference, name, your note). Returns true if anything changed. */
    public static boolean fill(Txn t, StatementParser.Row r, String account) {
        boolean ch = false;
        if (t.account.isEmpty()) { t.account = account; ch = true; }
        if (t.vpa.isEmpty() && !r.vpa.isEmpty()) { t.vpa = r.vpa; ch = true; }
        if (t.ref.isEmpty() && !r.ref.isEmpty()) { t.ref = r.ref; ch = true; }
        if (!t.userMerchant && t.unnamed() && !r.name.isEmpty()) { t.merchant = r.name; ch = true; }
        if (t.note.isEmpty() && !r.remark.isEmpty()) { t.note = r.remark; ch = true; }
        if (t.balance < 0 && r.balance >= 0) { t.balance = r.balance; ch = true; }
        if ((t.sources & Txn.SRC_STMT) == 0) { t.sources |= Txn.SRC_STMT; ch = true; }
        return ch;
    }
}
