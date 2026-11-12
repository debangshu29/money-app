package com.debangshu.money;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Locked screen: unlock with fingerprint / face / screen lock. */
public class LockActivity extends Activity {

    private static final int REQ = 77;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.bars(this);
        LinearLayout box = Ui.vbox(this);
        box.setGravity(Gravity.CENTER);
        int p = Ui.dp(this, 32);
        box.setPadding(p, p, p, p);
        android.widget.ImageView ic = Ui.icon(this, R.drawable.ic_lock, Ui.col(this, R.color.accent), 40);
        box.addView(ic, Ui.lp(Ui.dp(this, 72), Ui.dp(this, 72)));
        TextView t = Ui.text(this, "Money is locked", 22, Ui.EXTRABOLD);
        t.setGravity(Gravity.CENTER);
        box.addView(t);
        TextView s = Ui.sub(this, "Use your fingerprint, face or screen lock");
        s.setGravity(Gravity.CENTER);
        s.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 20));
        box.addView(s);
        TextView u = Ui.button(this, "Unlock", true);
        u.setOnClickListener(v -> ask());
        box.addView(u);
        setContentView(box);
        ask();
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    private void ok() {
        Lock.done();
        finish();
    }

    @SuppressWarnings("deprecation")
    private void ask() {
        if (!Lock.deviceSecure(this)) { ok(); return; }
        if (Build.VERSION.SDK_INT >= 29) {
            BiometricPrompt.Builder b = new BiometricPrompt.Builder(this)
                    .setTitle("Unlock Money")
                    .setSubtitle("Fingerprint, face or screen lock");
            if (Build.VERSION.SDK_INT >= 30) {
                b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
            } else {
                b.setDeviceCredentialAllowed(true);
            }
            try {
                b.build().authenticate(new CancellationSignal(), getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                    @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) { ok(); }
                });
                return;
            } catch (Exception ignored) { }
        }
        KeyguardManager km = getSystemService(KeyguardManager.class);
        Intent i = km == null ? null : km.createConfirmDeviceCredentialIntent("Unlock Money", "Use your screen lock");
        if (i != null) startActivityForResult(i, REQ);
        else ok();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ && res == RESULT_OK) ok();
    }
}
