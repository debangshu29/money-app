package com.debangshu.money;

import android.app.Activity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

/** Shared bits for the inner screens (back arrow + title). */
final class Screens {
    private Screens() {}

    static View bar(Activity a, String title) {
        LinearLayout bar = Ui.hbox(a);
        bar.setPadding(Ui.dp(a, 8), Ui.dp(a, 10), Ui.dp(a, 16), Ui.dp(a, 4));
        ImageView back = Ui.icon(a, R.drawable.ic_back, Ui.col(a, R.color.text), 22);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> a.onBackPressed());
        bar.addView(back);
        bar.addView(Ui.text(a, title, 24, Ui.EXTRABOLD));
        return bar;
    }
}
