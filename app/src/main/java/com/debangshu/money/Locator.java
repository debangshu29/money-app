package com.debangshu.money;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

/**
 * Notes where the phone is when a payment is caught (one quick check, then the GPS is off again),
 * and — if you allow it — turns it into an area name like "near Garia Station Road".
 * The coordinates stay on the phone; only the area-name lookup asks Google (Settings switch).
 */
public final class Locator {

    private Locator() {}

    public static boolean allowed(Context c) {
        return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean backgroundAllowed(Context c) {
        if (Build.VERSION.SDK_INT < 29) return allowed(c);
        return c.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public static final int REQ_FG = 61, REQ_BG = 62;

    /** Step 1: "while using the app". Step 2 (Android 10+): "Allow all the time" — needed because payments are caught in the background. */
    public static void ask(Activity a) {
        if (!allowed(a)) {
            a.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_FG);
        } else if (!backgroundAllowed(a)) {
            new Ui.Popup(a).setTitle("Allow all the time")
                    .setMessage("Payments are caught in the background (when the SMS arrives), so Money needs location “Allow all the time”.\n\n"
                            + "On the next screen choose “Allow all the time”. Money checks the location once per payment — nothing else.")
                    .setPositiveButton("Continue", (d, w) -> {
                        if (Build.VERSION.SDK_INT >= 29) a.requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, REQ_BG);
                    })
                    .setNegativeButton("Not now", null).show();
        }
    }

    /** Tags a just-caught payment with the phone's location (in the background; saves when done). */
    public static void tag(Context ctx, Txn t) {
        Context c = ctx.getApplicationContext();
        Store s = Store.get(c);
        if (!s.flag(Store.S_LOCATION) || !allowed(c) || t.hasLoc()) return;
        if (Math.abs(System.currentTimeMillis() - t.time) > 15L * 60000) return;   // only fresh payments: old ones are elsewhere
        LocationManager lm = c.getSystemService(LocationManager.class);
        if (lm == null) return;
        final boolean[] done = {false};
        Handler h = new Handler(Looper.getMainLooper());
        Runnable fallback = () -> {
            if (done[0]) return;
            done[0] = true;
            apply(c, t, lastKnown(lm));
        };
        h.postDelayed(fallback, 25000);
        try {
            String provider = pick(lm);
            if (provider == null) { h.removeCallbacks(fallback); fallback.run(); return; }
            if (Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(provider, new CancellationSignal(), Executors.newSingleThreadExecutor(), loc -> {
                    if (done[0]) return;
                    done[0] = true;
                    h.removeCallbacks(fallback);
                    apply(c, t, loc != null ? loc : lastKnown(lm));
                });
            } else {
                h.post(() -> {
                    try {
                        oneShot(lm, provider, loc -> {
                            if (done[0]) return;
                            done[0] = true;
                            h.removeCallbacks(fallback);
                            apply(c, t, loc);
                        });
                    } catch (SecurityException ignored) { }
                });
            }
        } catch (SecurityException e) {
            h.removeCallbacks(fallback);
        }
    }

    interface OnLoc { void got(Location l); }

    @SuppressWarnings("deprecation")
    private static void oneShot(LocationManager lm, String provider, OnLoc cb) throws SecurityException {
        lm.requestSingleUpdate(provider, new LocationListener() {
            @Override public void onLocationChanged(Location l) { cb.got(l); }
            @Override public void onStatusChanged(String p, int s, Bundle b) { }
            @Override public void onProviderEnabled(String p) { }
            @Override public void onProviderDisabled(String p) { }
        }, Looper.getMainLooper());
    }

    private static String pick(LocationManager lm) {
        try {
            if (Build.VERSION.SDK_INT >= 31 && lm.isProviderEnabled(LocationManager.FUSED_PROVIDER)) return LocationManager.FUSED_PROVIDER;
        } catch (Exception ignored) { }
        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) return LocationManager.NETWORK_PROVIDER;
        if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) return LocationManager.GPS_PROVIDER;
        return null;
    }

    /** The newest location the phone already knows, if it's from the last 10 minutes. */
    private static Location lastKnown(LocationManager lm) {
        Location best = null;
        try {
            for (String p : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
        } catch (SecurityException ignored) { }
        if (best != null && System.currentTimeMillis() - best.getTime() > 10L * 60000) return null;
        return best;
    }

    private static void apply(Context c, Txn t, Location l) {
        if (l == null) return;
        new Thread(() -> {
            Store s = Store.get(c);
            Txn live = s.byId(t.id);
            Txn x = live != null ? live : t;
            x.lat = l.getLatitude();
            x.lng = l.getLongitude();
            x.locAcc = l.hasAccuracy() ? l.getAccuracy() : 50;
            if (s.flag(Store.S_PLACE_NAMES)) x.place = placeName(c, x.lat, x.lng);
            s.update(x);
            Alerts.locationReady(c, x);
        }).start();
    }

    /** "near Garia Station Road, Garia" (asks the phone's geocoder, which uses Google). */
    @SuppressWarnings("deprecation")
    public static String placeName(Context c, double lat, double lng) {
        if (!Geocoder.isPresent()) return "";
        try {
            List<Address> list = new Geocoder(c, Locale.getDefault()).getFromLocation(lat, lng, 1);
            if (list == null || list.isEmpty()) return "";
            Address a = list.get(0);
            String road = a.getThoroughfare(), area = a.getSubLocality(), town = a.getLocality(), feature = a.getFeatureName();
            StringBuilder b = new StringBuilder("near ");
            if (feature != null && !feature.matches("[0-9A-Z+\\-/ ]{1,12}") && !feature.equals(road)) b.append(feature).append(", ");
            if (road != null) b.append(road);
            else if (area != null) b.append(area);
            else if (town != null) b.append(town);
            else return "";
            if (area != null && road != null && !road.contains(area)) b.append(", ").append(area);
            else if (town != null && (area == null || road == null)) { if (!b.toString().contains(town)) b.append(", ").append(town); }
            return b.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** Opens the spot in Google Maps (or any maps app). */
    public static void openMap(Activity a, Txn t) {
        String label = Uri.encode((t.merchant.isEmpty() ? "Payment" : t.merchant) + " · " + Fmt.plain(t.amount));
        Uri u = Uri.parse("geo:" + t.lat + "," + t.lng + "?q=" + t.lat + "," + t.lng + "(" + label + ")");
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, u));
        } catch (Exception e) {
            try {
                a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=" + t.lat + "," + t.lng)));
            } catch (Exception ignored) { Ui.toast(a, "No maps app found"); }
        }
    }

    /** Metres between two points. */
    public static double distance(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371000, dLat = Math.toRadians(lat2 - lat1), dLng = Math.toRadians(lng2 - lng1);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * R * Math.asin(Math.min(1, Math.sqrt(h)));
    }
}
