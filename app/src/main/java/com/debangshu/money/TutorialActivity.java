package com.debangshu.money;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The welcome tour: what Money does, one idea per page, swipe or tap Next.
 * Shown the first time (then the permission checklist), and any time from Settings → Tutorials.
 */
public class TutorialActivity extends Activity {

    static final String SEEN = "seen_tour";

    /** emoji (line icon), colour, title, text */
    private static final Object[][] PAGES = {
            {"👛", 0xFF0B6F86, "Every rupee, by itself",
                    "Money reads your bank SMS and payment-app notifications and fills everything in — amount, who you paid, which account, the balance."},
            {"⚡", 0xFFF59E0B, "Pay as usual",
                    "Google Pay, PhonePe, cards, ATM, bank e-mails — all caught the moment they happen. Small payments with no SMS? It asks “Paid anyone?”."},
            {"🎤", 0xFFEC4899, "Cash? Add it or say it",
                    "Tap Add, pick where the money came from, done. Or just say “50 for Lays, paid from SBI”."},
            {"🏦", 0xFF0B6F86, "All your accounts",
                    "Each account with its number and live balance. Two accounts at the same bank stay separate. Delete an entry and the balance goes back."},
            {"📊", 0xFF22C55E, "Know what's safe to spend",
                    "Set a monthly budget and Home shows how much you can still spend today — after the bills that are still to come."},
            {"🎯", 0xFF0E9F6E, "Grow your money",
                    "Set a goal, tap the account the money is in, and Money tells you if you're ahead, on track or behind — with what-if sliders to get there sooner."},
            {"🔔", 0xFF3B82F6, "Never miss a date",
                    "Card bills, renewals, EMIs, return windows and failed payments that haven't come back — Money reminds you only when it matters."},
            {"💬", 0xFF0EA5E9, "Ask anything",
                    "“How much on food in October?” “What did I pay Ramesh?” Get the answer in a second."},
            {"🔒", 0xFF6366F1, "Private by design",
                    "Everything stays on this phone. No account, no cloud, no ads. App lock and password-protected backups."},
    };

    private int page;
    private boolean first;
    private LinearLayout content, dots;
    private TextView next, skip;

    static void start(Context c, boolean first) {
        c.startActivity(new Intent(c, TutorialActivity.class).putExtra("first", first)
                .addFlags(first ? Intent.FLAG_ACTIVITY_NEW_TASK : 0));
    }

    static boolean seen(Context c) { return Store.get(c).prefs().getBoolean(SEEN, false); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.bars(this);
        first = getIntent().getBooleanExtra("first", false);
        if (b != null) page = b.getInt("page", 0);

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Ui.col(this, R.color.bg));
        LinearLayout root = Ui.vbox(this);
        int p = Ui.dp(this, 28);
        root.setPadding(p, Ui.dp(this, 20), p, Ui.dp(this, 28));
        frame.addView(root, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = Ui.hbox(this);
        top.addView(Ui.text(this, "Money", 15, Ui.col(this, R.color.text2), Ui.BOLD), Ui.weight1());
        skip = Ui.text(this, "Skip", 15, Ui.col(this, R.color.accent), Ui.BOLD);
        skip.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8));
        skip.setOnClickListener(v -> done());
        top.addView(skip);
        root.addView(top);

        content = Ui.vbox(this);
        content.setGravity(Gravity.CENTER);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        dots = Ui.hbox(this);
        dots.setGravity(Gravity.CENTER);
        root.addView(dots, Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 24));

        next = Ui.button(this, "Next", true);
        next.setGravity(Gravity.CENTER);
        next.setOnClickListener(v -> go(page + 1));
        root.addView(next, Ui.matchWrap());

        GestureDetector gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || Math.abs(vx) < Math.abs(vy) || Math.abs(e2.getX() - e1.getX()) < Ui.dp(TutorialActivity.this, 40)) return false;
                if (vx < 0) go(page + 1); else if (page > 0) go(page - 1);
                return true;
            }
        });
        content.setOnTouchListener((v, e) -> gd.onTouchEvent(e));
        setContentView(frame);
        show(0);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("page", page);
    }

    @Override
    public void onBackPressed() {
        if (page > 0) go(page - 1);
        else done();
    }

    private void go(int to) {
        if (to >= PAGES.length) { done(); return; }
        int dir = to > page ? 1 : -1;
        page = to;
        content.animate().alpha(0f).translationX(-dir * Ui.dp(this, 24)).setDuration(120).withEndAction(() -> {
            show(dir);
            content.setTranslationX(dir * Ui.dp(this, 24));
            content.animate().alpha(1f).translationX(0).setDuration(180).start();
        }).start();
    }

    private void show(int dir) {
        Object[] pg = PAGES[page];
        content.removeAllViews();
        View icon = Ui.iconBadge(this, (String) pg[0], (Integer) pg[1], 112);
        content.addView(icon);
        TextView t = Ui.text(this, (String) pg[2], 26, Ui.EXTRABOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(this, 32), 0, Ui.dp(this, 12));
        content.addView(t, Ui.matchWrap());
        TextView d = Ui.text(this, (String) pg[3], 16, Ui.col(this, R.color.text2), Ui.REGULAR);
        d.setGravity(Gravity.CENTER);
        d.setLineSpacing(Ui.dp(this, 3), 1f);
        content.addView(d, Ui.matchWrap());

        dots.removeAllViews();
        for (int i = 0; i < PAGES.length; i++) {
            View dot = new View(this);
            boolean on = i == page;
            dot.setBackground(Ui.round(on ? Ui.col(this, R.color.accent) : Ui.col(this, R.color.surface2), Ui.dp(this, 4)));
            dots.addView(dot, Ui.margins(Ui.lp(Ui.dp(this, on ? 22 : 8), Ui.dp(this, 8)), this, 4, 0, 4, 0));
        }
        boolean last = page == PAGES.length - 1;
        next.setText(last ? (first ? "Let's set it up" : "Done") : "Next");
        skip.setVisibility(last ? View.INVISIBLE : View.VISIBLE);
    }

    private void done() {
        Store.get(this).prefs().edit().putBoolean(SEEN, true).apply();
        if (first && !Store.get(this).flag(Store.S_SETUP)) startActivity(new Intent(this, SetupActivity.class));
        finish();
    }

    // ------------------------------------------------------------------ Settings → Tutorials: short how-tos

    /** emoji, colour, title, one line, steps, which screen "Show me" opens (or null) */
    private static Object[][] guides() {
        return new Object[][]{
                {"📥", 0xFF0E9F6E, "How payments are caught", "SMS, apps, e-mails, “Paid anyone?”",
                        "• Bank SMS: every debit, credit, card spend and ATM withdrawal — with the balance.\n"
                                + "• Payment apps: Google Pay, PhonePe, Paytm… notifications are read the moment they arrive.\n"
                                + "• Bank e-mails: alerts that come only by mail are read from the Gmail notification.\n"
                                + "• Small UPI payments with no SMS: if a UPI app was open and nothing came, Money asks “Paid anyone?” — reply with “50 Ramesh”.\n"
                                + "• Same payment from SMS and app? It's joined — never counted twice.", "capture"},
                {"➕", 0xFF8B5CF6, "Adding cash or a payment yourself", "Add, Paid from, voice",
                        "• Home → Add. Type the amount and what it was for.\n"
                                + "• Under “Paid from”, tap Cash or the bank account (each shows its number and balance).\n"
                                + "• That balance changes right away. If the bank's SMS comes later, it joins your entry.\n"
                                + "• Or tap Say it: “50 for Lays, paid from Union Bank”, “10 for chai in cash”.\n"
                                + "• Wrong account? Open the payment and tap the right account.", "add"},
                {"🏦", 0xFF0B6F86, "Accounts & balances", "Live balance, two accounts at one bank",
                        "• Accounts appear by themselves after the first bank SMS.\n"
                                + "• The balance is the bank's last one plus everything since — including what you added.\n"
                                + "• Two accounts at the same bank show their last digits (Federal ••1234 / ••5678).\n"
                                + "• Tap an account to rename it, hide it from the total, or set its balance.\n"
                                + "• Deleting a payment puts the money back in that account.", "accounts"},
                {"📊", 0xFF22C55E, "Budget & safe to spend", "One number for today",
                        "• Plan → Budget: set how much you want to spend this month.\n"
                                + "• Home shows what's safe to spend today — bills still to come are kept aside.\n"
                                + "• The bar goes green → yellow → red as the month is used up.", "budget"},
                {"🔔", 0xFF3B82F6, "Bills, renewals & reminders", "Card bills, recharges, insurance, returns",
                        "• Card bills and due dates come from your bank's messages.\n"
                                + "• Renewals (recharge, insurance, FASTag…) and return windows are picked up from SMS too.\n"
                                + "• You get one reminder before the date — nothing more.", "reminders"},
                {"↩️", 0xFFF59E0B, "Failed payments & refunds", "When money is cut but not paid",
                        "• If a payment fails but money left your account, Money watches for it to come back.\n"
                                + "• By RBI rules, a failed UPI payment must be returned by the next working day.\n"
                                + "• If it's late, Money tells you what to do and whom to contact.", "refunds"},
                {"📄", 0xFF0E9F6E, "Check with a bank statement", "Find anything that was missed",
                        "• Download your monthly statement (PDF, CSV or Excel) from the bank app.\n"
                                + "• Plan → Statement, pick the file (enter its password if it has one).\n"
                                + "• Money adds whatever it missed and shows what didn't match.", "statement"},
                {"🔒", 0xFF6366F1, "Privacy, lock & backups", "Nothing leaves your phone",
                        "• Nothing is uploaded — the internet is used only to check for app updates.\n"
                                + "• Settings → Privacy: app lock with fingerprint, hide amounts.\n"
                                + "• Settings → Backup: set a password, then backups are saved on the phone, locked.", "privacy"},
        };
    }

    /** The Tutorials page inside Settings. */
    static void fill(Activity a, LinearLayout box) {
        LinearLayout tour = Ui.card(a);
        tour.setOrientation(LinearLayout.HORIZONTAL);
        tour.setGravity(Gravity.CENTER_VERTICAL);
        tour.setBackground(Ui.rippleRound(a, Ui.col(a, R.color.accent_soft), 22));
        tour.addView(Ui.iconBadge(a, "🎓", 0xFF0B6F86, 48));
        LinearLayout col = Ui.vbox(a);
        col.setPadding(Ui.dp(a, 14), 0, Ui.dp(a, 8), 0);
        col.addView(Ui.text(a, "Take the tour", 16, Ui.BOLD));
        col.addView(Ui.text(a, "What Money does, in 9 short pages", 13, Ui.col(a, R.color.text2), Ui.REGULAR));
        tour.addView(col, Ui.weight1());
        tour.addView(Ui.chevron(a));
        tour.setOnClickListener(v -> start(a, false));
        box.addView(tour, Ui.margins(Ui.matchWrap(), a, 0, 4, 0, 4));

        box.addView(Ui.label(a, "How to"));
        LinearLayout c = Ui.card(a);
        c.setPadding(Ui.dp(a, 10), Ui.dp(a, 4), Ui.dp(a, 10), Ui.dp(a, 4));
        for (Object[] g : guides()) {
            c.addView(Ui.navRow(a, (String) g[0], (Integer) g[1], (String) g[2], (String) g[3], v -> guide(a, g)));
        }
        box.addView(c);
    }

    private static void guide(Activity a, Object[] g) {
        LinearLayout b = Ui.vbox(a);
        int p = Ui.dp(a, 22);
        b.setPadding(p, Ui.dp(a, 4), p, Ui.dp(a, 8));
        TextView t = Ui.text(a, (String) g[4], 15, Ui.col(a, R.color.text), Ui.REGULAR);
        t.setLineSpacing(Ui.dp(a, 6), 1f);
        b.addView(t);
        ScrollView sv = new ScrollView(a);
        sv.addView(b);
        Ui.Popup pop = new Ui.Popup(a);
        pop.setTitle((String) g[2]);
        pop.setView(sv).setPositiveButton("Show me", (d, w) -> open(a, (String) g[5])).setNegativeButton("Close", null);
        pop.sheet().show();
    }

    private static void open(Activity a, String where) {
        switch (where) {
            case "capture": a.startActivity(new Intent(a, SettingsActivity.class).putExtra("page", "capture")); break;
            case "privacy": a.startActivity(new Intent(a, SettingsActivity.class).putExtra("page", "privacy")); break;
            case "add": TxnSheet.addManual(a); break;
            case "accounts": a.startActivity(new Intent(a, AccountsActivity.class)); break;
            case "budget": a.startActivity(new Intent(a, BudgetActivity.class)); break;
            case "reminders": a.startActivity(new Intent(a, Reminders.class)); break;
            case "refunds": a.startActivity(new Intent(a, Refunds.class)); break;
            case "statement": a.startActivity(new Intent(a, StatementActivity.class)); break;
            default: break;
        }
    }
}
