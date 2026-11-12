package com.debangshu.money;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** "Ask your money" — opened from the Ask bar at the top of Insights. */
public class AskActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.bars(this);
        AskPage page = new AskPage(this);
        setContentView(page.view());
        String q = getIntent().getStringExtra("q");
        if (q != null && !q.trim().isEmpty()) page.ask(q);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Lock.needed(this)) startActivity(new Intent(this, LockActivity.class));
    }
}
