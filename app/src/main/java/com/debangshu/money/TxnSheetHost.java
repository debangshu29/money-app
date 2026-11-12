package com.debangshu.money;

import android.app.Activity;
import android.content.Intent;

/** Opens the app on the typed "Cash spent" form (when voice isn't available). */
final class TxnSheetHost {
    private TxnSheetHost() {}

    static void typeInstead(Activity a) {
        a.startActivity(new Intent(a, MainActivity.class).putExtra("addCash", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        a.finish();
    }
}
