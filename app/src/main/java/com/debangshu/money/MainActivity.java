package com.debangshu.money;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Four tabs: Home · Activity · Insights · Ask. */
public class MainActivity extends Activity {

    public static final int HOME = 0, ACTIVITY = 1, INSIGHTS = 2, PLAN = 3;
    private static final String[] TABS = {"Home", "Activity", "Insights", "Plan"};
    private static final int[] ICONS = {R.drawable.ic_home, R.drawable.ic_list, R.drawable.ic_pie, R.drawable.ic_grid};

    int tab = HOME;
    private FrameLayout content;
    private final LinearLayout[] tabViews = new LinearLayout[4];
    private final Runnable onChange = this::refreshSoon;
    private boolean pending;

    // what the Activity tab shows (set from Insights: "show me Food in October")
    String filterCategory = null;
    long filterFrom = 0, filterTo = 0;
    String filterLabel = null;

    final HomePage home = new HomePage(this);
    final ActivityPage activity = new ActivityPage(this);
    final InsightsPage insights = new InsightsPage(this);
    final PlanPage plan = new PlanPage(this);

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.bars(this);
        if (!Store.get(this).flag(Store.S_SETUP)) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }
        LinearLayout root = Ui.vbox(this);
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(navBar());
        setContentView(root);
        if (b != null) tab = b.getInt("tab", HOME);
        show(tab);
        Store.get(this).listen(onChange);
        handle(getIntent());
        Updater.cleanUpAfterUpdate(this);
        Updater.showWhatsNewOnce(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", tab);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        handle(i);
    }

    /** Pay & note / cash spend asked for from the widget or a quick-settings tile — done once the app is unlocked. */
    private String pendingAction;

    private void handle(Intent i) {
        if (i == null) return;
        String id = i.getStringExtra("txn");
        if (id != null) {
            Txn t = Store.get(this).byId(id);
            if (t != null) TxnSheet.show(this, t);
        }
        if (i.getBooleanExtra("showUpdate", false)) Updater.maybePrompt(this, true);
        if (i.getBooleanExtra("scan", false) || i.getBooleanExtra("pay", false)) { pendingAction = "pay"; i.removeExtra("scan"); i.removeExtra("pay"); }
        if (i.getStringExtra("paidOn") != null) {
            pendingAction = "paid:" + i.getLongExtra("paidAt", 0) + ":" + i.getStringExtra("paidOn");
            i.removeExtra("paidOn");
        }
        if (i.getStringExtra("balCheck") != null) { pendingAction = "bal:" + i.getStringExtra("balCheck"); i.removeExtra("balCheck"); }
        if (i.getBooleanExtra("addCash", false)) { pendingAction = "cash"; i.removeExtra("addCash"); }
        if (i.hasExtra("filterLabel")) {
            activity.setQuick(i.getIntExtra("filterQuick", 0));
            showFiltered(i.getStringExtra("filterCat"), i.getLongExtra("filterFrom", 0), i.getLongExtra("filterTo", 0),
                    i.getStringExtra("filterLabel"));
            i.removeExtra("filterLabel");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Lock.needed(this)) { startActivity(new Intent(this, LockActivity.class)); }
        Ui.bars(this);
        Alerts.rollPlan(this);
        refresh();
        // a quick catch-up in case an SMS slipped past while the app was asleep
        new Thread(() -> { try { Engine.catchUp(getApplicationContext()); } catch (Exception ignored) { } }).start();
        PayListener.refresh(getApplicationContext());
        Updater.maybePrompt(this, false);
        if (!Lock.needed(this)) PayNote.onReturn(this);
        new Thread(() -> { try { UsageWatch.tick(getApplicationContext()); } catch (Exception ignored) { } }).start();
        if (pendingAction != null && !Lock.needed(this)) {
            String p = pendingAction;
            pendingAction = null;
            if (p.equals("pay")) PayNote.sheet(this);
            else if (p.startsWith("paid:")) {
                String[] q = p.split(":", 3);
                android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
                if (nm != null) nm.cancel(31000 + (int) ((Long.parseLong(q[1]) / 1000) & 0x3FF));
                PayNote.ask(this, null, q[2], Long.parseLong(q[1]));
            }
            else if (p.startsWith("bal:")) BalanceCheck.ask(this, p.substring(4));
            else TxnSheet.addManual(this);
        }
    }

    @Override
    protected void onDestroy() {
        Store.get(this).unlisten(onChange);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (tab != HOME) { show(HOME); return; }
        super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        Updater.onCleanupResult(this, req, res, data);
        Photos.onResult(this, req, res, data);
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req == Locator.REQ_FG && Locator.allowed(this)) Locator.ask(this);
    }

    private void refreshSoon() {
        if (pending || content == null) return;
        pending = true;
        content.postDelayed(() -> { pending = false; refresh(); }, 250);
    }

    void refresh() {
        if (content == null) return;
        switch (tab) {
            case HOME: home.refresh(); break;
            case ACTIVITY: activity.refresh(); break;
            case INSIGHTS: insights.refresh(); break;
            default: plan.refresh(); break;
        }
    }

    void show(int t) {
        tab = t;
        content.removeAllViews();
        View v = t == HOME ? home.view() : t == ACTIVITY ? activity.view() : t == INSIGHTS ? insights.view() : plan.view();
        if (v.getParent() != null) ((ViewGroup) v.getParent()).removeView(v);
        content.addView(v);
        for (int i = 0; i < 4; i++) {
            boolean on = i == t;
            LinearLayout tv = tabViews[i];
            if (tv == null) continue;
            ImageView ic = (ImageView) tv.getChildAt(0);
            TextView l = (TextView) tv.getChildAt(1);
            ic.setColorFilter(on ? Ui.col(this, R.color.accent) : Ui.col(this, R.color.text2));
            ic.setBackground(on ? Ui.round(Ui.col(this, R.color.accent_soft), Ui.dp(this, 16)) : null);
            l.setTextColor(on ? Ui.col(this, R.color.text) : Ui.col(this, R.color.text2));
            l.setTypeface(Ui.face(this, on ? Ui.BOLD : Ui.SEMIBOLD));
        }
        refresh();
    }

    /** Open the Activity tab showing one category (and month) — from Insights. */
    void showFiltered(String category, long from, long to, String label) {
        filterCategory = category;
        filterFrom = from;
        filterTo = to;
        filterLabel = label;
        show(ACTIVITY);
    }

    /** Bottom bar: Home · Activity · ( Pay & note ) · Insights · Plan — the round button pops out above the bar. */
    private View navBar() {
        FrameLayout wrap = new FrameLayout(this);
        wrap.setClipChildren(false);
        wrap.setClipToPadding(false);
        int lift = Ui.dp(this, 24);

        LinearLayout bar = Ui.hbox(this);
        bar.setBackground(Ui.round(Ui.col(this, R.color.surface), 0));
        bar.setElevation(Ui.dp(this, 8));
        bar.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 8));
        for (int i = 0; i < 4; i++) {
            if (i == 2) bar.addView(fabSlot(), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.1f));
            LinearLayout t = Ui.vbox(this);
            t.setGravity(Gravity.CENTER);
            ImageView ic = new ImageView(this);
            ic.setImageResource(ICONS[i]);
            int ph = Ui.dp(this, 16), pv = Ui.dp(this, 5);
            ic.setPadding(ph, pv, ph, pv);
            t.addView(ic, Ui.lp(Ui.dp(this, 56), Ui.dp(this, 32)));
            TextView l = Ui.text(this, TABS[i], 12, Ui.SEMIBOLD);
            l.setGravity(Gravity.CENTER);
            l.setPadding(0, Ui.dp(this, 3), 0, 0);
            t.addView(l);
            final int idx = i;
            t.setOnClickListener(v -> {
                if (idx == ACTIVITY && tab != ACTIVITY) { filterCategory = null; filterLabel = null; filterFrom = 0; filterTo = 0; }
                show(idx);
            });
            tabViews[i] = t;
            bar.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = lift;
        wrap.addView(bar, blp);

        // the round button: a ring in the page colour around a green gradient circle, raised above the bar
        int size = Ui.dp(this, 66);
        FrameLayout fab = new FrameLayout(this);
        android.graphics.drawable.GradientDrawable ring = new android.graphics.drawable.GradientDrawable();
        ring.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        ring.setColor(Ui.col(this, R.color.bg));
        fab.setBackground(ring);
        fab.setElevation(Ui.dp(this, 14));
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR, new int[]{0xFF34D399, 0xFF0E9F6E, 0xFF0B6F86});
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        ImageView core = new ImageView(this);
        core.setImageResource(R.drawable.ic_pay);
        core.setColorFilter(0xFFFFFFFF);
        core.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int ip = Ui.dp(this, 15);
        core.setPadding(ip, ip, ip, ip);
        core.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x40FFFFFF), g, null));
        int pad = Ui.dp(this, 5);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(size - 2 * pad, size - 2 * pad, Gravity.CENTER);
        fab.addView(core, clp);
        core.setContentDescription("Pay & note");
        core.setOnClickListener(v -> PayNote.sheet(this));
        core.setOnLongClickListener(v -> { TxnSheet.addManual(this); return true; });
        core.setOnTouchListener((v, e) -> {
            if (e.getAction() == android.view.MotionEvent.ACTION_DOWN) fab.animate().scaleX(0.9f).scaleY(0.9f).setDuration(90).start();
            else if (e.getAction() == android.view.MotionEvent.ACTION_UP || e.getAction() == android.view.MotionEvent.ACTION_CANCEL)
                fab.animate().scaleX(1f).scaleY(1f).setDuration(160).setInterpolator(new android.view.animation.OvershootInterpolator(3f)).start();
            return false;
        });
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(size, size, Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        flp.topMargin = Ui.dp(this, 2);
        wrap.addView(fab, flp);
        return wrap;
    }

    /** The middle slot of the bar: room for the round button, with its label underneath. */
    private View fabSlot() {
        LinearLayout t = Ui.vbox(this);
        t.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        View space = new View(this);
        t.addView(space, Ui.lp(1, Ui.dp(this, 32)));
        TextView l = Ui.text(this, "Pay & note", 12, Ui.col(this, R.color.accent), Ui.BOLD);
        l.setGravity(Gravity.CENTER);
        l.setPadding(0, Ui.dp(this, 3), 0, 0);
        t.addView(l);
        t.setOnClickListener(v -> PayNote.sheet(this));
        return t;
    }

    /** Top bar used by every tab: big title, then the eye (hide amounts) and settings. */
    View topBar(String title, View extra) {
        LinearLayout bar = Ui.hbox(this);
        bar.setPadding(Ui.dp(this, 20), Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 6));
        bar.addView(Ui.text(this, title, 26, Ui.EXTRABOLD), Ui.weight1());
        if (extra != null) bar.addView(extra);
        ImageView set = Ui.icon(this, R.drawable.ic_settings, Ui.col(this, R.color.text2), 22);
        set.setContentDescription("Settings");
        set.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        bar.addView(set);
        return bar;
    }
}
