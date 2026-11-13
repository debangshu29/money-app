package com.debangshu.money;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything the app knows, kept ONLY on this phone (app-private storage, one JSON file).
 * Nothing is ever uploaded.
 */
public final class Store {

    private static Store inst;

    public static synchronized Store get(Context c) {
        if (inst == null) inst = new Store(c.getApplicationContext());
        return inst;
    }

    public static final class Bill {
        public String account = "";
        public long due = -1, minDue = -1, dueDate = -1, seen;
        public boolean paid;
        public String raw = "";
    }

    private final Context ctx;
    private final SharedPreferences prefs;
    private final File file;
    private final List<Txn> txns = new ArrayList<>();
    private final Map<String, Account> accounts = new LinkedHashMap<>();
    private final Map<String, String> catRules = new HashMap<>();     // merchant key → category
    private final Map<String, String> nameRules = new HashMap<>();    // merchant key → your name for it
    private final Map<String, Long> budgets = new LinkedHashMap<>();  // category ("" = whole month) → paise
    private final List<Bill> bills = new ArrayList<>();
    private Plan plan;                                                // the budget running now (or null)
    private final Map<String, String> aliases = new HashMap<>();      // debit card key → its bank account key
    private final java.util.Set<String> deletedAccounts = new java.util.TreeSet<>();   // accounts you deleted (don't come back)
    private JSONObject extra = new JSONObject();   // people, goals, challenges, assets… (each feature keeps its own list)
    private final List<String[]> unread = new ArrayList<>();          // bank messages that couldn't be read: time, sender, body
    private final List<Plan> pastPlans = new ArrayList<>();
    private int batch = 0;
    private boolean dirty = false;
    private final List<Runnable> listeners = new ArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());

    // settings
    public static final String S_SETUP = "setup_done", S_LOCK = "lock", S_HIDE = "hide_amounts", S_SECURE = "secure_screen",
            S_DAILY = "daily_summary", S_UNUSUAL = "alert_unusual", S_BUDGET = "alert_budget", S_BILLS = "alert_bills",
            S_EACH = "notify_each", S_AUTO_BACKUP = "auto_backup", S_ATM_SPEND = "atm_is_spend", S_AUTO_UPDATE = "auto_update",
            S_GAPS = "find_gaps", S_READ_APPS = "read_apps", S_READ_SMS_NOTIF = "read_sms_notif",
            S_LOCATION = "save_location", S_PLACE_NAMES = "place_names", S_ASK_NAME = "ask_name",
            S_FRAUD = "fraud_watch", S_REMIND = "remind_renewals",
            S_READ_MAIL = "read_mail", S_BAL_CHECK = "balance_check";

    private Store(Context c) {
        ctx = c;
        prefs = c.getSharedPreferences("money", Context.MODE_PRIVATE);
        file = new File(c.getFilesDir(), "data.json");
        load();
    }

    public SharedPreferences prefs() { return prefs; }

    public boolean flag(String key) {
        boolean def = !(key.equals(S_LOCK) || key.equals(S_SECURE) || key.equals(S_EACH) || key.equals(S_SETUP) || key.equals(S_HIDE)
                || key.equals(S_LOCATION));
        return prefs.getBoolean(key, def);
    }

    public void setFlag(String key, boolean v) { prefs.edit().putBoolean(key, v).apply(); }

    // ------------------------------------------------------------------ listeners (screens refresh themselves)

    public void listen(Runnable r) { synchronized (listeners) { listeners.add(r); } }

    public void unlisten(Runnable r) { synchronized (listeners) { listeners.remove(r); } }

    private void fire() {
        List<Runnable> copy;
        synchronized (listeners) { copy = new ArrayList<>(listeners); }
        main.post(() -> { for (Runnable r : copy) r.run(); });
    }

    // ------------------------------------------------------------------ transactions

    public synchronized List<Txn> txns() { return new ArrayList<>(txns); }

    /** Visible transactions, newest first. */
    public synchronized List<Txn> visible() {
        List<Txn> out = new ArrayList<>();
        for (Txn t : txns) if (t.visible() && t.kind != Txn.IGNORED) out.add(t);
        Collections.sort(out, (a, b) -> Long.compare(b.time, a.time));
        return out;
    }

    public synchronized Txn byId(String id) {
        for (Txn t : txns) if (t.id.equals(id)) return t;
        return null;
    }

    /** Adds, or merges into the same payment seen before. Returns the saved one; {@code isNew[0]} tells which. */
    public synchronized Txn addOrMerge(Txn n, boolean[] isNew) {
        if (n.id == null) n.id = UUID.randomUUID().toString();
        Txn twin = n.kind == Txn.SNAPSHOT ? null : Ledger.findTwin(txns, n);
        if (n.kind == Txn.SNAPSHOT) {
            for (Txn e : txns) if (e.kind == Txn.SNAPSHOT && e.srcKey.equals(n.srcKey) && Math.abs(e.time - n.time) < 6L * 3600000) { twin = e; break; }
            if (twin != null) { if (isNew != null) isNew[0] = false; return twin; }
        }
        if (twin != null) {
            Ledger.merge(twin, n);
            if (twin.category.isEmpty()) twin.category = categoryFor(twin);
            String nm = nameRules.get(Cats.key(twin));
            if (nm != null && !twin.userMerchant) twin.merchant = nm;
            if (isNew != null) isNew[0] = false;
            changed();
            return twin;
        }
        String nm = nameRules.get(Cats.key(n));
        if (nm != null) n.merchant = nm;
        if (n.category.isEmpty()) n.category = categoryFor(n);
        txns.add(n);
        if (isNew != null) isNew[0] = true;
        changed();
        return n;
    }

    public String categoryFor(Txn t) {
        if (t.kind == Txn.NORMAL && (t.sources & Txn.SRC_GAP) == 0) {
            String k = Cats.key(t);
            String r = k.isEmpty() ? null : catRules.get(k);
            if (r == null && !t.merchant.isEmpty()) r = catRules.get("name:" + t.merchant.toLowerCase(java.util.Locale.ROOT)
                    .replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim());
            if (r != null) return r;
        }
        return Cats.guess(t);
    }

    public synchronized void update(Txn t) {
        changed();
    }

    public synchronized void delete(Txn t) {
        txns.remove(t);
        changed();
    }

    /** "Always put Swiggy in Food": changes every past one too (except ones you picked by hand). */
    public synchronized int learnCategory(Txn t, String cat, boolean all) {
        if ((t.sources & Txn.SRC_GAP) != 0) all = false;   // an "Unrecorded" amount says nothing about other payments
        t.category = cat;
        t.userCategory = true;
        int n = 1;
        if (all) {
            String k = Cats.key(t);
            String nk = "name:" + t.merchant.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
            if (!k.isEmpty()) catRules.put(k, cat);
            if (!t.merchant.isEmpty()) catRules.put(nk, cat);
            for (Txn x : txns) {
                if (x == t || x.userCategory || x.kind != Txn.NORMAL || x.credit != t.credit) continue;
                if ((!k.isEmpty() && k.equals(Cats.key(x))) || (!t.merchant.isEmpty() && t.merchant.equalsIgnoreCase(x.merchant))) {
                    x.category = cat;
                    n++;
                }
            }
        }
        changed();
        return n;
    }

    /** Your name for this payment's UPI ID / shop, if you gave one before. */
    public synchronized String nameFor(Txn t) {
        String k = Cats.key(t);
        return k.isEmpty() ? null : nameRules.get(k);
    }

    /** Scan & Pay: remember a UPI ID's name and category (also fixes earlier payments to it). */
    public synchronized void learnVpa(String vpa, String name, String cat) {
        if (vpa == null || vpa.isEmpty()) return;
        String k = "vpa:" + vpa.toLowerCase(java.util.Locale.ROOT);
        if (name != null && !name.isEmpty()) nameRules.put(k, name);
        if (cat != null && !cat.isEmpty() && !Cats.OTHER.equals(cat)) catRules.put(k, cat);
        for (Txn x : txns) {
            if (!vpa.equalsIgnoreCase(x.vpa) || x.credit) continue;
            if (!x.userMerchant && name != null && !name.isEmpty()) x.merchant = name;
            if (!x.userCategory && x.kind == Txn.NORMAL && cat != null && !cat.isEmpty() && !Cats.OTHER.equals(cat)) x.category = cat;
        }
        changed();
    }

    public synchronized int learnName(Txn t, String name, boolean all) {
        if ((t.sources & Txn.SRC_GAP) != 0 || (t.vpa.isEmpty() && t.unnamed())) all = false;
        String old = t.merchant;
        String k = Cats.key(t);
        t.merchant = name;
        t.userMerchant = true;
        int n = 1;
        if (all) {
            if (!k.isEmpty()) nameRules.put(k, name);
            for (Txn x : txns) {
                if (x == t || x.userMerchant) continue;
                if ((!k.isEmpty() && k.equals(Cats.key(x))) || (!old.isEmpty() && old.equals(x.merchant))) { x.merchant = name; n++; }
            }
        }
        changed();
        return n;
    }

    /** Replaces the "Unrecorded" entries of one account (keeps what you wrote on them). */
    public synchronized void setGaps(String account, List<Txn> gaps) {
        Map<String, Txn> old = new HashMap<>();
        for (Iterator<Txn> it = txns.iterator(); it.hasNext(); ) {
            Txn t = it.next();
            if ((t.sources & Txn.SRC_GAP) != 0 && account.equals(t.account)) { old.put(t.id, t); it.remove(); }
        }
        if (flag(S_GAPS) && !deletedAccounts.contains(account)) {
            for (Txn g : gaps) {
                Txn o = old.get(g.id);
                if (o != null && o.amount == g.amount && o.credit == g.credit) { txns.add(o); continue; }
                if (o != null) { g.note = o.note; if (o.userCategory) { g.category = o.category; g.userCategory = true; } }
                txns.add(g);
            }
        }
        changed();
    }

    /** Removes "Unrecorded" entries you haven't touched (they're worked out again right after). */
    public synchronized void dropUntouchedGaps() {
        for (Iterator<Txn> it = txns.iterator(); it.hasNext(); ) {
            Txn t = it.next();
            if ((t.sources & Txn.SRC_GAP) != 0 && !t.userCategory && !t.userMerchant && t.note.isEmpty() && t.review) it.remove();
        }
        changed();
    }

    // ------------------------------------------------------------------ accounts

    public synchronized List<Account> accounts() {
        List<Account> l = new ArrayList<>(accounts.values());
        Collections.sort(l, (a, b) -> Long.compare(b.lastUsed, a.lastUsed));
        return l;
    }

    public synchronized Account account(String key) { return accounts.get(key); }

    public synchronized void touchAccount(Txn t, boolean card, boolean wallet) {
        if (t.account == null || t.account.isEmpty() || deletedAccounts.contains(t.account)) return;
        Account a = accounts.get(t.account);
        if (a == null) {
            a = new Account();
            a.key = t.account;
            a.card = card;
            a.wallet = wallet;
            accounts.put(t.account, a);
        }
        if (card) a.card = true;
        a.lastUsed = Math.max(a.lastUsed, t.time);
        if (t.balance >= 0 && t.time >= a.balanceTime) { a.balance = t.balance; a.balanceTime = t.time; }
        if (t.limit >= 0 && t.time >= a.limitTime) { a.limit = t.limit; a.limitTime = t.time; }
        changed();
    }

    public synchronized void renameAccount(Account a, String name) { a.name = name; changed(); }

    public synchronized void setAccountHidden(Account a, boolean h) { a.hidden = h; changed(); }

    public synchronized boolean isDeleted(String key) { return deletedAccounts.contains(key); }

    // ------------------------------------------------------------------ lists for People, Goals, Challenges, Net worth…

    /** A copy of a feature's list (empty if none). */
    public synchronized JSONArray list(String key) {
        JSONArray a = extra.optJSONArray(key);
        try { return a == null ? new JSONArray() : new JSONArray(a.toString()); } catch (Exception e) { return new JSONArray(); }
    }

    public synchronized void putList(String key, JSONArray a) {
        try { extra.put(key, a); } catch (Exception ignored) { }
        changed();
    }

    public synchronized JSONObject obj(String key) {
        JSONObject o = extra.optJSONObject(key);
        try { return o == null ? new JSONObject() : new JSONObject(o.toString()); } catch (Exception e) { return new JSONObject(); }
    }

    public synchronized void putObj(String key, JSONObject o) {
        try { extra.put(key, o); } catch (Exception ignored) { }
        changed();
    }

    public synchronized List<String> deletedAccounts() { return new ArrayList<>(deletedAccounts); }

    /**
     * Deletes an account (it won't come back from new messages). With {@code payments}, its payments are
     * hidden too (kept invisibly, so the same SMS read again doesn't add them back).
     */
    public synchronized void deleteAccount(String key, boolean payments) {
        accounts.remove(key);
        deletedAccounts.add(key);
        for (Iterator<Txn> it = txns.iterator(); it.hasNext(); ) {
            Txn t = it.next();
            if (!key.equals(t.account)) continue;
            if ((t.sources & Txn.SRC_GAP) != 0) { it.remove(); continue; }
            if (payments) { t.kind = Txn.IGNORED; t.review = false; }
        }
        changed();
    }

    /** Brings a deleted account back (and its hidden payments). */
    public synchronized void restoreAccount(String key) {
        deletedAccounts.remove(key);
        for (Txn t : txns) {
            if (!key.equals(t.account)) continue;
            if (t.kind == Txn.IGNORED && t.note.isEmpty()) t.kind = Txn.NORMAL;
        }
        rebuildAccount(key, false, false);
        changed();
    }

    /** "These two are the same account": moves every payment of {@code from} to {@code to}, and future ones too. */
    public synchronized void mergeAccount(String from, String to) {
        if (from.equals(to)) return;
        Account af = accounts.remove(from);
        Account at = accounts.get(to);
        boolean card = at != null && at.card;
        for (Iterator<Txn> it = txns.iterator(); it.hasNext(); ) {
            Txn t = it.next();
            if (!from.equals(t.account)) continue;
            if ((t.sources & Txn.SRC_GAP) != 0) { it.remove(); continue; }
            t.account = to;
        }
        aliases.put(from, to);
        for (Map.Entry<String, String> e : aliases.entrySet()) if (from.equals(e.getValue())) e.setValue(to);
        rebuildAccount(to, card, af != null && af.wallet);
        changed();
    }

    /** Works an account's balance / limit out again from its payments. */
    private void rebuildAccount(String key, boolean card, boolean wallet) {
        Account old = accounts.get(key);
        Account a = new Account();
        a.key = key;
        a.card = card || (old != null && old.card);
        a.wallet = wallet || (old != null && old.wallet);
        if (old != null) { a.name = old.name; a.hidden = old.hidden; }
        boolean any = false;
        for (Txn t : txns) {
            if (!key.equals(t.account) || t.kind == Txn.IGNORED || (t.sources & Txn.SRC_GAP) != 0) continue;
            any = true;
            a.lastUsed = Math.max(a.lastUsed, t.time);
            if (t.balance >= 0 && t.time >= a.balanceTime) { a.balance = t.balance; a.balanceTime = t.time; }
            if (t.limit >= 0 && t.time >= a.limitTime) { a.limit = t.limit; a.limitTime = t.time; }
        }
        if (any || old != null) accounts.put(key, a);
    }

    /** "My balance is ₹X now" (weekly check): a balance point like an SMS's — anything not explained shows as Unrecorded. */
    public synchronized void checkBalance(String key, long paise) {
        Txn t = new Txn();
        t.id = UUID.randomUUID().toString();
        t.time = System.currentTimeMillis();
        t.kind = Txn.SNAPSHOT;
        t.sources = Txn.SRC_NOTE;
        t.account = key;
        t.srcKey = "check:" + t.time;
        t.raw = "Balance checked by you";
        t.balance = paise;
        txns.add(t);
        Account a = accounts.get(key);
        if (a != null) { a.balance = paise; a.balanceTime = t.time; }
        changed();
    }

    /** "The balance is wrong": saves the right balance as of now (the missed-SMS check starts again from here). */
    public synchronized void correctBalance(String key, long paise, boolean limit) {
        Txn t = new Txn();
        t.id = UUID.randomUUID().toString();
        t.time = System.currentTimeMillis();
        t.kind = Txn.SNAPSHOT;
        t.sources = Txn.SRC_MANUAL;
        t.account = key;
        t.srcKey = "manual:" + t.time;
        t.raw = "Balance corrected by you";
        if (limit) t.limit = paise; else t.balance = paise;
        txns.add(t);
        Account a = accounts.get(key);
        if (a != null) {
            if (limit) { a.limit = paise; a.limitTime = t.time; } else { a.balance = paise; a.balanceTime = t.time; }
        }
        changed();
    }

    // ------------------------------------------------------------------ bills, budgets

    public synchronized void addBill(Bill b) {
        for (Bill o : bills) {
            if (o.account.equals(b.account) && Math.abs(o.dueDate - b.dueDate) < 3L * 86400000) {
                o.due = b.due; o.minDue = b.minDue; o.raw = b.raw; changed(); return;
            }
        }
        bills.add(b);
        changed();
    }

    public synchronized List<Bill> bills() { return new ArrayList<>(bills); }

    public synchronized void setBillPaid(Bill b, boolean paid) { b.paid = paid; changed(); }

    public synchronized Map<String, Long> budgets() { return new LinkedHashMap<>(budgets); }

    public synchronized void setBudget(String cat, long paise) {
        if (paise <= 0) budgets.remove(cat); else budgets.put(cat, paise);
        changed();
    }

    // ------------------------------------------------------------------ debit card ↔ account, unread messages

    public synchronized String alias(String key) { return aliases.get(key); }

    public synchronized void setAlias(String cardKey, String accountKey) { aliases.put(cardKey, accountKey); changed(); }

    public synchronized void addUnread(long time, String sender, String body) {
        for (String[] u : unread) if (u[2].equals(body)) return;
        unread.add(new String[]{String.valueOf(time), sender, body});
        while (unread.size() > 200) unread.remove(0);
        changed();
    }

    public synchronized List<String[]> unread() {
        List<String[]> l = new ArrayList<>(unread);
        Collections.reverse(l);
        return l;
    }

    public synchronized void removeUnread(String body) {
        for (Iterator<String[]> it = unread.iterator(); it.hasNext(); ) if (it.next()[2].equals(body)) it.remove();
        changed();
    }

    public synchronized void clearUnread() { unread.clear(); changed(); }

    // ------------------------------------------------------------------ the budget you started

    public synchronized Plan plan() { return plan; }

    public synchronized List<Plan> pastPlans() {
        List<Plan> l = new ArrayList<>(pastPlans);
        Collections.sort(l, (a, b) -> Long.compare(b.start, a.start));
        return l;
    }

    public synchronized void setPlan(Plan p) {
        if (p != null && (p.id == null || p.id.isEmpty())) p.id = UUID.randomUUID().toString();
        plan = p;
        changed();
    }

    /** Ends the running budget (keeps it in the history). */
    public synchronized void closePlan(long when) {
        if (plan == null) return;
        plan.closedAt = when;
        if (when < plan.end) plan.end = Math.max(plan.start + 86400000L, Fmt.addDays(Fmt.startOfDay(when), 1));
        pastPlans.add(plan);
        while (pastPlans.size() > 36) pastPlans.remove(0);
        plan = null;
        changed();
    }

    public synchronized void deletePastPlan(Plan p) { pastPlans.remove(p); changed(); }

    // ------------------------------------------------------------------ saving

    /** Many changes at once (importing old messages): saves once at the end. */
    public synchronized void begin() { batch++; }

    public synchronized void end() {
        batch = Math.max(0, batch - 1);
        if (batch == 0 && dirty) save();
    }

    public synchronized boolean inBatch() { return batch > 0; }

    private void changed() {
        dirty = true;
        if (batch == 0) save();
    }

    private synchronized void save() {
        try {
            byte[] data = toJson().toString().getBytes(StandardCharsets.UTF_8);
            File tmp = new File(file.getPath() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(data);
                out.getFD().sync();
            }
            if (!tmp.renameTo(file)) {
                try (FileOutputStream out = new FileOutputStream(file)) { out.write(data); }
                tmp.delete();
            }
            dirty = false;
        } catch (Exception ignored) { }
        fire();
        Widget.refresh(ctx);
    }

    private void load() {
        if (!file.exists()) return;
        try (FileInputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[65536];
            int n;
            while ((n = in.read(b)) > 0) buf.write(b, 0, n);
            fromJson(new JSONObject(buf.toString("UTF-8")));
        } catch (Exception ignored) { }
    }

    public synchronized JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("version", 1);
        JSONArray ta = new JSONArray();
        for (Txn t : txns) ta.put(txnJson(t));
        o.put("txns", ta);
        JSONArray aa = new JSONArray();
        for (Account a : accounts.values()) {
            JSONObject j = new JSONObject();
            j.put("key", a.key).put("name", a.name).put("card", a.card).put("wallet", a.wallet).put("bal", a.balance)
             .put("balT", a.balanceTime).put("lim", a.limit).put("limT", a.limitTime).put("used", a.lastUsed).put("hidden", a.hidden);
            aa.put(j);
        }
        o.put("accounts", aa);
        o.put("catRules", new JSONObject(catRules));
        o.put("nameRules", new JSONObject(nameRules));
        JSONObject bj = new JSONObject();
        for (Map.Entry<String, Long> e : budgets.entrySet()) bj.put(e.getKey(), e.getValue());
        o.put("budgets", bj);
        JSONArray bl = new JSONArray();
        for (Bill b : bills) {
            bl.put(new JSONObject().put("acc", b.account).put("due", b.due).put("min", b.minDue).put("date", b.dueDate)
                    .put("seen", b.seen).put("paid", b.paid).put("raw", b.raw));
        }
        o.put("bills", bl);
        if (plan != null) o.put("plan", planJson(plan));
        JSONArray pp = new JSONArray();
        for (Plan p : pastPlans) pp.put(planJson(p));
        o.put("pastPlans", pp);
        o.put("aliases", new JSONObject(aliases));
        JSONArray del = new JSONArray();
        for (String d : deletedAccounts) del.put(d);
        o.put("deletedAccounts", del);
        o.put("extra", extra);
        JSONArray un = new JSONArray();
        for (String[] u : unread) un.put(new JSONArray().put(u[0]).put(u[1]).put(u[2]));
        o.put("unread", un);
        return o;
    }

    public synchronized void fromJson(JSONObject o) throws Exception {
        txns.clear(); accounts.clear(); catRules.clear(); nameRules.clear(); budgets.clear(); bills.clear();
        plan = null; pastPlans.clear(); aliases.clear(); unread.clear(); deletedAccounts.clear();
        readMap(o.optJSONObject("aliases"), aliases);
        extra = o.optJSONObject("extra") == null ? new JSONObject() : o.optJSONObject("extra");
        JSONArray del = o.optJSONArray("deletedAccounts");
        if (del != null) for (int i = 0; i < del.length(); i++) deletedAccounts.add(del.optString(i));
        JSONArray un = o.optJSONArray("unread");
        if (un != null) for (int i = 0; i < un.length(); i++) {
            JSONArray u = un.optJSONArray(i);
            if (u != null && u.length() == 3) unread.add(new String[]{u.optString(0), u.optString(1), u.optString(2)});
        }
        JSONObject pj = o.optJSONObject("plan");
        if (pj != null) plan = planFrom(pj);
        JSONArray pp = o.optJSONArray("pastPlans");
        if (pp != null) for (int i = 0; i < pp.length(); i++) pastPlans.add(planFrom(pp.getJSONObject(i)));
        JSONArray ta = o.optJSONArray("txns");
        if (ta != null) for (int i = 0; i < ta.length(); i++) txns.add(txnFrom(ta.getJSONObject(i)));
        JSONArray aa = o.optJSONArray("accounts");
        if (aa != null) for (int i = 0; i < aa.length(); i++) {
            JSONObject j = aa.getJSONObject(i);
            Account a = new Account();
            a.key = j.optString("key"); a.name = j.optString("name"); a.card = j.optBoolean("card"); a.wallet = j.optBoolean("wallet");
            a.balance = j.optLong("bal", -1); a.balanceTime = j.optLong("balT"); a.limit = j.optLong("lim", -1);
            a.limitTime = j.optLong("limT"); a.lastUsed = j.optLong("used"); a.hidden = j.optBoolean("hidden");
            accounts.put(a.key, a);
        }
        readMap(o.optJSONObject("catRules"), catRules);
        readMap(o.optJSONObject("nameRules"), nameRules);
        JSONObject bj = o.optJSONObject("budgets");
        if (bj != null) for (Iterator<String> it = bj.keys(); it.hasNext(); ) { String k = it.next(); budgets.put(k, bj.getLong(k)); }
        JSONArray bl = o.optJSONArray("bills");
        if (bl != null) for (int i = 0; i < bl.length(); i++) {
            JSONObject j = bl.getJSONObject(i);
            Bill b = new Bill();
            b.account = j.optString("acc"); b.due = j.optLong("due", -1); b.minDue = j.optLong("min", -1);
            b.dueDate = j.optLong("date", -1); b.seen = j.optLong("seen"); b.paid = j.optBoolean("paid"); b.raw = j.optString("raw");
            bills.add(b);
        }
        // 1.0 kept a "whole month" budget among the category limits: turn it into a running budget
        Long old = budgets.remove("");
        if (old != null && old > 0 && plan == null) {
            Plan p = new Plan();
            p.id = UUID.randomUUID().toString();
            p.amount = old;
            p.start = Fmt.startOfMonth(System.currentTimeMillis());
            p.end = Plan.endFor(p.start, Plan.MONTH, 0);
            plan = p;
        }
    }

    /** Restore: replaces everything with a backup. */
    public synchronized void replaceAll(JSONObject o) throws Exception {
        fromJson(o);
        changed();
    }

    public synchronized void wipe() {
        txns.clear(); accounts.clear(); catRules.clear(); nameRules.clear(); budgets.clear(); bills.clear();
        plan = null; pastPlans.clear(); aliases.clear(); unread.clear(); deletedAccounts.clear();
        extra = new JSONObject();
        changed();
    }

    private static void readMap(JSONObject j, Map<String, String> m) {
        if (j == null) return;
        for (Iterator<String> it = j.keys(); it.hasNext(); ) { String k = it.next(); m.put(k, j.optString(k)); }
    }

    static JSONObject planJson(Plan p) throws Exception {
        JSONArray sk = new JSONArray();
        for (String c : p.skip) sk.put(c);
        return new JSONObject().put("id", p.id).put("amt", p.amount).put("start", p.start).put("end", p.end)
                .put("len", p.length).put("repeat", p.repeat).put("skip", sk).put("closed", p.closedAt);
    }

    static Plan planFrom(JSONObject j) {
        Plan p = new Plan();
        p.id = j.optString("id");
        p.amount = j.optLong("amt");
        p.start = j.optLong("start");
        p.end = j.optLong("end");
        p.length = j.optInt("len");
        p.repeat = j.optBoolean("repeat", true);
        p.closedAt = j.optLong("closed");
        JSONArray sk = j.optJSONArray("skip");
        if (sk != null) for (int i = 0; i < sk.length(); i++) p.skip.add(sk.optString(i));
        return p;
    }

    static JSONObject txnJson(Txn t) throws Exception {
        JSONObject j = new JSONObject();
        j.put("id", t.id).put("t", t.time).put("a", t.amount).put("cr", t.credit).put("acc", t.account).put("m", t.merchant)
         .put("vpa", t.vpa).put("cat", t.category).put("k", t.kind).put("src", t.sources).put("bal", t.balance).put("lim", t.limit)
         .put("ref", t.ref).put("raw", t.raw).put("snd", t.sender).put("note", t.note).put("key", t.srcKey)
         .put("rev", t.review).put("uc", t.userCategory).put("um", t.userMerchant);
        if (t.gapFrom > 0) j.put("gf", t.gapFrom);
        if (t.hasLoc()) j.put("lat", t.lat).put("lng", t.lng).put("acc", (double) t.locAcc);
        if (!t.place.isEmpty()) j.put("place", t.place);
        if (!t.photo.isEmpty()) j.put("photo", t.photo);
        return j;
    }

    static Txn txnFrom(JSONObject j) {
        Txn t = new Txn();
        t.id = j.optString("id"); t.time = j.optLong("t"); t.amount = j.optLong("a"); t.credit = j.optBoolean("cr");
        t.account = j.optString("acc"); t.merchant = j.optString("m"); t.vpa = j.optString("vpa"); t.category = j.optString("cat");
        t.kind = j.optInt("k"); t.sources = j.optInt("src"); t.balance = j.optLong("bal", -1); t.limit = j.optLong("lim", -1);
        t.ref = j.optString("ref"); t.raw = j.optString("raw"); t.sender = j.optString("snd"); t.note = j.optString("note");
        t.srcKey = j.optString("key"); t.review = j.optBoolean("rev"); t.userCategory = j.optBoolean("uc"); t.userMerchant = j.optBoolean("um");
        t.gapFrom = j.optLong("gf");
        if (j.has("acc")) { t.lat = j.optDouble("lat"); t.lng = j.optDouble("lng"); t.locAcc = (float) j.optDouble("acc", -1); }
        t.place = j.optString("place");
        t.photo = j.optString("photo");
        return t;
    }
}
