package com.debangshu.money;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Tap a transaction: see everything, fix the name or category (and teach the app), add a note. */
public final class TxnSheet {

    private TxnSheet() {}

    public static void show(Activity a, Txn t) {
        Store s = Store.get(a);
        ScrollView sv = new ScrollView(a);
        LinearLayout box = Ui.vbox(a);
        int p = Ui.dp(a, 20);
        box.setPadding(p, 0, p, Ui.dp(a, 8));
        sv.addView(box);
        final AlertDialog[] dlg = {null};

        // ---- header: badge, amount, name
        LinearLayout head = Ui.hbox(a);
        head.addView(Ui.badge(a, Cats.emoji(t.category), Cats.color(t.category), 54));
        LinearLayout hc = Ui.vbox(a);
        hc.setPadding(Ui.dp(a, 14), 0, 0, 0);
        int amtColor = t.credit ? Ui.col(a, R.color.success) : Ui.col(a, R.color.text);
        hc.addView(Ui.text(a, (t.credit ? "+" : "−") + Fmt.rs(t.amount), 28, amtColor, Ui.EXTRABOLD));
        TextView name = Ui.text(a, (t.merchant.isEmpty() ? "Add a name" : t.merchant) + "  ✎", 16, Ui.SEMIBOLD);
        name.setOnClickListener(v -> rename(a, t, () -> { if (dlg[0] != null) dlg[0].dismiss(); show(a, t); }));
        hc.addView(name);
        if (t.isSpend()) {   // what it really costs: hours of work, and the goal it would have gone to
            String hrs = MoneyFacts.hoursFor(a, t.amount), gi = MoneyFacts.goalImpact(a, t.amount);
            String extra = hrs.isEmpty() ? gi : gi.isEmpty() ? "≈ " + hrs : "≈ " + hrs + "  ·  " + gi;
            if (!extra.isEmpty()) hc.addView(Ui.text(a, extra, 12.5f, Ui.col(a, R.color.text2), Ui.SEMIBOLD));
        }
        head.addView(hc, Ui.weight1());
        box.addView(head);

        // ---- category chip
        TextView cat = Ui.chip(a, Cats.emoji(t.category) + "  " + (t.category.isEmpty() ? Cats.OTHER : t.category) + "  ▾", false);
        cat.setBackground(Ui.rippleRound(a, Ui.col(a, R.color.surface2), 18));
        cat.setOnClickListener(v -> pickCategory(a, t, () -> { if (dlg[0] != null) dlg[0].dismiss(); show(a, t); }));
        LinearLayout cr = Ui.hbox(a);
        cr.addView(cat);
        box.addView(cr, Ui.margins(Ui.matchWrap(), a, 0, 14, 0, 4));

        // ---- needs a look
        if (t.review) {
            LinearLayout warn = Ui.card(a);
            warn.setBackground(Ui.round(Ui.col(a, R.color.warn_soft), Ui.dp(a, 18)));
            boolean gap = (t.sources & Txn.SRC_GAP) != 0;
            warn.addView(Ui.text(a, gap ? "⚠️ No message came for this" : "⚠️ Worth a look", 15, Ui.BOLD));
            TextView w = Ui.sub(a, gap ? "Your balance changed by this much without any SMS or app notification. "
                    + "It could be a cash deposit, a cheque, bank charges, or a payment whose SMS never arrived. "
                    + "Tap the name to say what it was, or mark it OK."
                    : "This looked unusual (much bigger than normal, or the same amount twice in a few minutes). "
                    + "If it was you, mark it OK.");
            w.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 10));
            warn.addView(w);
            TextView sort = Ui.button(a, gap ? "Find out what it was" : "Check it", true);
            sort.setOnClickListener(v -> { if (dlg[0] != null) dlg[0].dismiss(); Resolve.one(a, t); });
            TextView ok = Ui.button(a, "It's OK", false);
            ok.setOnClickListener(v -> { t.review = false; s.update(t); if (dlg[0] != null) dlg[0].dismiss(); });
            LinearLayout br = Ui.hbox(a);
            br.addView(sort, Ui.margins(Ui.lp(-2, -2), a, 0, 0, 8, 0));
            br.addView(ok);
            warn.addView(br);
            box.addView(warn, Ui.margins(Ui.matchWrap(), a, 0, 10, 0, 4));
        }

        // ---- details
        LinearLayout info = Ui.vbox(a);
        info.setPadding(0, Ui.dp(a, 10), 0, 0);
        line(a, info, "When", Fmt.date(t.time) + ", " + Fmt.time(t.time));
        if (!t.account.isEmpty()) {
            Account ac = s.account(t.account);
            line(a, info, t.credit ? "Into" : "From", ac == null ? t.account : ac.display());
        }
        if (!t.vpa.isEmpty()) line(a, info, "UPI ID", t.vpa);
        if (t.balance >= 0) line(a, info, "Balance after", Fmt.rs(t.balance));
        if (t.limit >= 0) line(a, info, "Card limit left", Fmt.rs(t.limit));
        if (!t.ref.isEmpty()) line(a, info, "Reference", t.ref);
        String src = (t.sources & Txn.SRC_GAP) != 0 ? "Found by the balance check" : (t.sources & Txn.SRC_MANUAL) != 0 ? "Added by you"
                : t.sender.isEmpty() ? "SMS" : t.sender;
        if ((t.sources & Txn.SRC_SMS) != 0 && (t.sources & Txn.SRC_APP) != 0) src += "  (SMS + app, counted once)";
        line(a, info, "Seen in", src);
        box.addView(info);

        // ---- where it happened
        if (t.hasLoc()) {
            LinearLayout wr = Ui.hbox(a);
            wr.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 2));
            TextView wk = Ui.text(a, "Where", 13.5f, Ui.col(a, R.color.text2), Ui.REGULAR);
            wr.addView(wk, Ui.lp(Ui.dp(a, 110), -2));
            TextView wv = Ui.text(a, "📍 " + (t.place.isEmpty() ? "Pin saved (±" + Math.round(t.locAcc) + " m)" : t.place), 13.5f, Ui.SEMIBOLD);
            wr.addView(wv, Ui.weight1());
            TextView map = Ui.chip(a, "Map", true);
            map.setOnClickListener(v -> Locator.openMap(a, t));
            wr.addView(map);
            box.addView(wr);
        }

        // ---- "probably …" from your habits
        if (t.unnamed() && !t.credit) {
            Habits.Guess g = Habits.guess(s.txns(), t);
            if (g != null) {
                LinearLayout gc = Ui.card(a);
                gc.setBackground(Ui.round(Ui.col(a, R.color.accent_soft), Ui.dp(a, 18)));
                gc.addView(Ui.text(a, "🤔 Probably " + g.name + "?", 15, Ui.BOLD));
                TextView gs = Ui.sub(a, "You paid " + g.name + " " + g.times + (g.times == 1 ? " time" : " times") + " before at this spot / amount / time.");
                gs.setPadding(0, Ui.dp(a, 3), 0, Ui.dp(a, 8));
                gc.addView(gs);
                TextView yes = Ui.button(a, "Yes, it was " + g.name, true);
                yes.setOnClickListener(v -> { Alerts.applyName(a, t, g.name, g.category); if (dlg[0] != null) dlg[0].dismiss(); show(a, t); });
                LinearLayout yr = Ui.hbox(a);
                yr.addView(yes);
                gc.addView(yr);
                box.addView(gc, Ui.margins(Ui.matchWrap(), a, 0, 10, 0, 4));
            }
        }

        // ---- split / lend / borrow
        if ((t.sources & Txn.SRC_GAP) == 0 && (t.kind == Txn.NORMAL || t.kind == Txn.TRANSFER) && a instanceof MainActivity) {
            TextView sp = Ui.chip(a, t.credit ? "🤝  Borrowed / paid back" : "👥  Split / lend", false);
            sp.setBackground(Ui.rippleRound(a, Ui.col(a, R.color.surface2), 18));
            sp.setOnClickListener(v -> { if (dlg[0] != null) dlg[0].dismiss(); People.fromPayment(a, t); });
            LinearLayout spr = Ui.hbox(a);
            spr.addView(sp);
            box.addView(spr, Ui.margins(Ui.matchWrap(), a, 0, 10, 0, 0));
        }

        // ---- failed payment? · return / warranty
        if ((t.sources & Txn.SRC_GAP) == 0 && !t.credit && (t.kind == Txn.NORMAL || t.kind == Txn.TRANSFER) && a instanceof MainActivity) {
            LinearLayout xr = Ui.hbox(a);
            boolean watched = false;
            for (org.json.JSONObject w : Refunds.all(a)) if (t.id.equals(w.optString("txn"))) watched = true;
            if (watched) {
                TextView rf = Ui.chip(a, "↩️  Refund tracking", false);
                rf.setBackground(Ui.rippleRound(a, Ui.col(a, R.color.surface2), 18));
                rf.setOnClickListener(v -> { if (dlg[0] != null) dlg[0].dismiss(); a.startActivity(new android.content.Intent(a, Refunds.class)); });
                xr.addView(rf, Ui.margins(Ui.lp(-2, -2), a, 0, 0, 8, 0));
            } else if (t.kind == Txn.NORMAL && System.currentTimeMillis() - t.time < 60L * 86400000) {
                TextView rf = Ui.chip(a, "❌  Payment failed?", false);
                rf.setBackground(Ui.rippleRound(a, Ui.col(a, R.color.surface2), 18));
                rf.setOnClickListener(v -> new Ui.Popup(a).setTitle("Payment failed but money left?")
                        .setMessage("Money will keep " + Fmt.plain(t.amount) + " out of your spending, watch for it to come back, and remind you if it doesn't.")
                        .setPositiveButton("Track the refund", (d, w) -> {
                            Refunds.watch(a, t, !Cats.PEOPLE.equals(t.category), t.ref, true);   // to a person: next day · shop / card: 5 days
                            if (dlg[0] != null) dlg[0].dismiss();
                            Ui.toast(a, "Watching for " + Fmt.plain(t.amount) + " to come back");
                        }).setNegativeButton("Cancel", null).show());
                xr.addView(rf, Ui.margins(Ui.lp(-2, -2), a, 0, 0, 8, 0));
            }
            if (t.kind == Txn.NORMAL) {
                org.json.JSONObject rem = Reminders.items(a);
                boolean has = rem.has(Notices.RETURN + ":" + t.id) || rem.has(Notices.WARRANTY + ":" + t.id);
                TextView rw = Ui.chip(a, has ? "⏰  Return / warranty ✓" : "⏰  Return / warranty", false);
                rw.setBackground(Ui.rippleRound(a, Ui.col(a, R.color.surface2), 18));
                rw.setOnClickListener(v -> { if (dlg[0] != null) dlg[0].dismiss(); Reminders.askReturn(a, t, null); });
                xr.addView(rw);
            }
            if (xr.getChildCount() > 0) {
                android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(a);
                hs.setHorizontalScrollBarEnabled(false);
                hs.addView(xr);
                box.addView(hs, Ui.margins(Ui.matchWrap(), a, 0, 10, 0, 0));
            }
        }

        // ---- bill photo
        if (!t.photo.isEmpty() && Photos.file(a, t).exists()) {
            android.widget.ImageView th = Photos.thumb(a, t);
            box.addView(th, Ui.margins(new LinearLayout.LayoutParams(-1, Ui.dp(a, 150)), a, 0, 10, 0, 0));
        } else if (a instanceof MainActivity) {
            TextView ph = Ui.chip(a, "📷  Add bill photo", false);
            ph.setBackground(Ui.rippleRound(a, Ui.col(a, R.color.surface2), 18));
            ph.setOnClickListener(v -> { if (dlg[0] != null) dlg[0].dismiss(); Photos.choose(a, t); });
            LinearLayout pr = Ui.hbox(a);
            pr.addView(ph);
            box.addView(pr, Ui.margins(Ui.matchWrap(), a, 0, 10, 0, 0));
        }

        // ---- note
        EditText note = new EditText(a);
        note.setHint(Lang.t("Add a note"));
        note.setText(t.note);
        note.setTypeface(Ui.face(a, Ui.REGULAR));
        note.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        note.setTextSize(15);
        box.addView(note, Ui.margins(Ui.matchWrap(), a, 0, 6, 0, 0));

        // ---- paid from (entries you added yourself): move it to the right account — its balance follows
        if ((t.sources & (Txn.SRC_SMS | Txn.SRC_APP | Txn.SRC_STMT | Txn.SRC_GAP)) == 0 && t.kind != Txn.SNAPSHOT && !t.credit) {
            TextView pf = Ui.text(a, "Paid from — tap to change", 13, Ui.col(a, R.color.text2), Ui.BOLD);
            String[] from = {t.account.isEmpty() ? Cash.ACCOUNT : t.account};
            box.addView(pf, Ui.margins(Ui.lp(-1, -2), a, 0, 14, 0, 8));
            box.addView(Noted.fromPicker(a, from, true, () -> {
                Noted.move(a, t, from[0]);
                Ui.toast(a, "Moved to " + Noted.fullLabel(a, from[0]) + " — balance updated");
            }));

        }

        // ---- switches
        if ((t.sources & Txn.SRC_GAP) == 0) {
            Switch transfer = sw(a, t.credit ? "Not income (money from my own account, a refund I'm tracking elsewhere…)"
                    : "Not spending (moved to my own account, card bill, investment…)", t.kind == Txn.TRANSFER);
            transfer.setOnCheckedChangeListener((b, on) -> { t.kind = on ? Txn.TRANSFER : Txn.NORMAL; s.update(t); });
            box.addView(transfer);
        }
        Switch hide = sw(a, "Hide this (not mine, or counted twice)", t.kind == Txn.IGNORED);
        hide.setOnCheckedChangeListener((b, on) -> { gone(a, t, on); t.kind = on ? Txn.IGNORED : Txn.NORMAL; s.update(t); });
        box.addView(hide);

        // ---- original message
        if (!t.raw.isEmpty()) {
            TextView ttl = Ui.text(a, "Original message", 13, Ui.col(a, R.color.text2), Ui.BOLD);
            ttl.setPadding(0, Ui.dp(a, 14), 0, Ui.dp(a, 4));
            box.addView(ttl);
            TextView raw = Ui.text(a, t.raw, 13, Ui.col(a, R.color.text2), Ui.REGULAR);
            raw.setTextIsSelectable(true);
            raw.setBackground(Ui.round(Ui.col(a, R.color.surface2), Ui.dp(a, 14)));
            raw.setPadding(Ui.dp(a, 12), Ui.dp(a, 10), Ui.dp(a, 12), Ui.dp(a, 10));
            box.addView(raw);
        }

        AlertDialog d = new Ui.Popup(a).sheet().setView(Ui.capHeight(sv, 0.72f))
                .setPositiveButton("Done", null)
                .setNeutralButton("Delete", (x, w) -> new Ui.Popup(a).setTitle("Delete this?")
                        .setMessage("It's removed from your totals and the balance goes back. (If the same SMS is read again later, it won't come back.)")
                        .setPositiveButton("Delete", (y, z) -> { gone(a, t, true); t.kind = Txn.IGNORED; t.review = false; s.update(t); })
                        .setNegativeButton("Cancel", null).show())
                .setOnDismissListener(x -> {
                    String n = note.getText().toString().trim();
                    if (!n.equals(t.note)) { t.note = n; s.update(t); }
                })
                .create();
        dlg[0] = d;
        d.show();
    }

    private static void line(Activity a, LinearLayout box, String k, String v) {
        LinearLayout r = Ui.hbox(a);
        r.setPadding(0, Ui.dp(a, 5), 0, Ui.dp(a, 5));
        r.setGravity(Gravity.TOP);
        TextView kt = Ui.text(a, k, 13.5f, Ui.col(a, R.color.text2), Ui.REGULAR);
        r.addView(kt, Ui.lp(Ui.dp(a, 110), -2));
        TextView vt = Ui.text(a, v, 13.5f, Ui.SEMIBOLD);
        vt.setTextIsSelectable(true);
        r.addView(vt, Ui.weight1());
        box.addView(r);
    }

    private static Switch sw(Activity a, String label, boolean on) {
        Switch s = new Ui.LSwitch(a);
        s.setText(label);
        s.setChecked(on);
        s.setTextSize(14);
        s.setTypeface(Ui.face(a, Ui.REGULAR));
        s.setTextColor(Ui.col(a, R.color.text));
        s.setPadding(0, Ui.dp(a, 10), 0, Ui.dp(a, 4));
        return s;
    }

    static int similar(Store s, Txn t) {
        int n = 0;
        String k = Cats.key(t);
        for (Txn x : s.txns()) {
            if (x == t || x.kind != Txn.NORMAL) continue;
            if ((!k.isEmpty() && k.equals(Cats.key(x))) || (!t.merchant.isEmpty() && t.merchant.equalsIgnoreCase(x.merchant))) n++;
        }
        return n;
    }

    public static void pickCategory(Activity a, Txn t, Runnable after) {
        String[] names = t.credit ? Cats.incomeNames() : Cats.spendNames();
        CharSequence[] items = new CharSequence[names.length];
        for (int i = 0; i < names.length; i++) items[i] = Cats.emoji(names[i]) + "   " + names[i];
        new Ui.Popup(a).setTitle("Category").setItems(items, (d, w) -> {
            String c = names[w];
            Store s = Store.get(a);
            int others = similar(s, t);
            if (others > 0 && !t.merchant.isEmpty() && (t.sources & Txn.SRC_GAP) == 0) {
                new Ui.Popup(a).setTitle("All " + t.merchant + " payments?")
                        .setMessage("Put " + others + " other " + (others == 1 ? "payment" : "payments") + " to " + t.merchant
                                + " in " + c + " too — and new ones automatically?")
                        .setPositiveButton("Yes, all", (x, y) -> { s.learnCategory(t, c, true); if (after != null) after.run(); })
                        .setNegativeButton("Only this one", (x, y) -> { s.learnCategory(t, c, false); if (after != null) after.run(); })
                        .show();
            } else {
                s.learnCategory(t, c, !t.merchant.isEmpty() && (t.sources & Txn.SRC_GAP) == 0);
                if (after != null) after.run();
            }
        }).show();
    }

    static void rename(Activity a, Txn t, Runnable after) {
        EditText e = new EditText(a);
        e.setText(t.merchant);
        e.setSelectAllOnFocus(true);
        e.setTypeface(Ui.face(a, Ui.REGULAR));
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        LinearLayout box = Ui.vbox(a);
        int p = Ui.dp(a, 24);
        box.setPadding(p, 0, p, 0);
        if (!t.vpa.isEmpty()) {
            TextView v = Ui.sub(a, "UPI ID: " + t.vpa + "\n🧠 Every future payment to this UPI ID (this shop's QR) will get this name and category by itself.");
            v.setPadding(0, 0, 0, Ui.dp(a, 6));
            box.addView(v);
        }
        box.addView(e);
        Store s = Store.get(a);
        int others = similar(s, t);
        new Ui.Popup(a).setTitle("Who is this?").setView(box)
                .setPositiveButton(others > 0 ? "Save for all " + (others + 1) : "Save", (d, w) -> {
                    String n = e.getText().toString().trim();
                    if (n.isEmpty()) return;
                    s.learnName(t, n, true);
                    if (!t.userCategory) {
                        String c = s.categoryFor(t);
                        if (!c.equals(t.category)) { t.category = c; s.update(t); }
                    }
                    if ((t.sources & Txn.SRC_GAP) != 0) { t.review = false; s.update(t); }
                    if (Cats.OTHER.equals(t.category) || Cats.UNRECORDED.equals(t.category)) pickCategory(a, t, after);
                    else if (after != null) after.run();
                })
                .setNegativeButton("Cancel", null).show();
    }

    /** For things no message will ever show (cash spent at a shop). */
    public static void addManual(Activity a) {
        LinearLayout box = Ui.vbox(a);
        int p = Ui.dp(a, 24);
        box.setPadding(p, 0, p, 0);
        EditText amt = new EditText(a);
        amt.setHint(Lang.t("Amount (₹)"));
        amt.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amt.setTextSize(22);
        amt.setTypeface(Ui.face(a, Ui.BOLD));
        EditText what = new EditText(a);
        what.setHint(Lang.t("For what / to whom (e.g. chips, Ramesh)"));
        what.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        what.setTypeface(Ui.face(a, Ui.REGULAR));
        box.addView(amt);
        box.addView(what);
        TextView fl = Ui.text(a, "Paid from", 13, Ui.col(a, R.color.text2), Ui.BOLD);
        fl.setPadding(0, Ui.dp(a, 14), 0, Ui.dp(a, 6));
        box.addView(fl);
        String[] from = {Noted.lastFrom(a)};
        if (!Cash.ACCOUNT.equals(from[0]) && !Noted.bankAccounts(a).contains(from[0])) from[0] = Cash.ACCOUNT;
        box.addView(Noted.fromPicker(a, from, true, null));
        TextView info = Ui.sub(a, "The balance in Money changes right away. If the bank's SMS comes later, it joins this — no double entry.");
        info.setPadding(0, Ui.dp(a, 10), 0, 0);
        box.addView(info);
        TextView mic = Ui.chip(a, "🎤  Say it instead — “50 for Lays, paid from Union Bank”", false);
        box.addView(mic, Ui.margins(Ui.lp(-2, -2), a, 0, 12, 0, 0));
        android.app.AlertDialog[] cd = new android.app.AlertDialog[1];
        mic.setOnClickListener(v -> { if (cd[0] != null) cd[0].dismiss(); VoiceActivity.start(a); });
        android.widget.ScrollView sv = new android.widget.ScrollView(a);
        sv.addView(box);
        Ui.Popup pop = new Ui.Popup(a);
        pop.setTitle("Add a payment").setView(sv)
                .setPositiveButton("Add", (d, w) -> {
                    long paise = SmsParser.paise(amt.getText().toString());
                    if (paise <= 0) { Ui.toast(a, "Enter an amount"); return; }
                    Txn t = Noted.save(a, from[0], paise, what.getText().toString().trim(), null, "");
                    Ui.toast(a, "Added ✓ " + Fmt.plain(paise) + " from " + Noted.fullLabel(a, from[0]));
                    if (Cats.OTHER.equals(t.category) || t.category.isEmpty()) pickCategory(a, t, null);
                })
                .setNegativeButton("Cancel", null);
        cd[0] = pop.show();
    }

    /** Deleting / hiding a cash spend puts the money back in the cash wallet (and un-hiding takes it out again). Bank balances follow by themselves. */
    static void gone(Context c, Txn t, boolean gone) {
        if (!Cash.ACCOUNT.equals(t.account) || t.credit || !Cash.on(c)) return;
        boolean was = t.kind == Txn.IGNORED;
        if (gone && !was) Cash.refund(c, t.amount);
        else if (!gone && was) Cash.refund(c, -t.amount);
    }

    static List<Txn> sortedNewest(List<Txn> l) {
        java.util.Collections.sort(l, (x, y) -> Long.compare(y.time, x.time));
        return l;
    }
}
