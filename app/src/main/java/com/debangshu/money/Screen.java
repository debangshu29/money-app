package com.debangshu.money;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/** A simple full screen: title bar + scrolling column, rebuilt on every resume (and locked like the rest). */
abstract class Screen extends Activity {

    protected LinearLayout box;

    abstract String title();

    abstract void build();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.bars(this);
        LinearLayout root = Ui.vbox(this);
        root.addView(Screens.bar(this, title()));
        ScrollView sv = new ScrollView(this);
        box = Ui.vbox(this);
        box.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 32));
        sv.addView(box);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Lock.needed(this)) startActivity(new Intent(this, LockActivity.class));
        rebuild();
    }

    void rebuild() {
        box.removeAllViews();
        build();
    }

    void add(android.view.View v) { box.addView(v, Ui.margins(Ui.matchWrap(), this, 0, 0, 0, 12)); }

    EditText field(String hint, boolean money) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTypeface(Ui.face(this, money ? Ui.BOLD : Ui.REGULAR));
        if (money) {
            e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            e.setTextSize(20);
        } else e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        return e;
    }

    LinearLayout form() {
        LinearLayout b = Ui.vbox(this);
        int p = Ui.dp(this, 24);
        b.setPadding(p, 0, p, 0);
        return b;
    }

    static String id() { return Long.toString(System.currentTimeMillis(), 36) + Integer.toString((int) (Math.random() * 1296), 36); }
}
