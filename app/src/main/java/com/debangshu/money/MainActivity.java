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

    public static final int HOME = 0, ACTIVITY = 1, INSIGHTS = 2, ASK = 3;
    private static final String[] TABS = {"Home", "Activity", "Insights", "Ask"};
    private static final int[] ICONS = {R.drawable.ic_home, R.drawable.ic_list, R.drawable.ic_pie, R.drawable.ic_chat};

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
    final AskPage ask = new AskPage(this);

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

    private void handle(Intent i) {
        if (i == null) return;
        String id = i.getStringExtra("txn");
        if (id != null) {
            Txn t = Store.get(this).byId(id);
            if (t != null) TxnSheet.show(this, t);
        }
        if (i.getBooleanExtra("showUpdate", false)) Updater.maybePrompt(this, true);
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
        Updater.maybePrompt(this, false);
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
            default: break;   // Ask keeps its conversation
        }
    }

    void show(int t) {
        tab = t;
        content.removeAllViews();
        View v = t == HOME ? home.view() : t == ACTIVITY ? activity.view() : t == INSIGHTS ? insights.view() : ask.view();
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

    private View navBar() {
        LinearLayout bar = Ui.hbox(this);
        bar.setBackgroundColor(Ui.col(this, R.color.surface));
        bar.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 8));
        for (int i = 0; i < 4; i++) {
            LinearLayout t = Ui.vbox(this);
            t.setGravity(Gravity.CENTER);
            ImageView ic = new ImageView(this);
            ic.setImageResource(ICONS[i]);
            int ph = Ui.dp(this, 18), pv = Ui.dp(this, 5);
            ic.setPadding(ph, pv, ph, pv);
            t.addView(ic, Ui.lp(Ui.dp(this, 62), Ui.dp(this, 32)));
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
        return bar;
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
