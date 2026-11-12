package com.debangshu.money;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Monthly report card: a clean PDF (summary, score, budget, categories, places, biggest, every payment). */
public final class Report {

    private Report() {}

    private static final int W = 595, H = 842, M = 40;

    public static void make(Activity a, long month) {
        Ui.toast(a, "Making the report…");
        new Thread(() -> {
            try {
                byte[] pdf = build(a, month);
                java.util.Calendar k = Fmt.cal(month);
                String name = String.format(Locale.ROOT, "Money-report-%04d-%02d.pdf", k.get(java.util.Calendar.YEAR), k.get(java.util.Calendar.MONTH) + 1);
                Uri u = save(a, "Reports", name, "application/pdf", pdf);
                a.runOnUiThread(() -> {
                    if (u == null) { Ui.toast(a, "Saved to Documents/Money/Reports/" + name); return; }
                    new Ui.Popup(a).setTitle("Report ready 📄")
                            .setMessage("Saved to Documents/Money/Reports/" + name)
                            .setPositiveButton("Open", (d, w) -> {
                                try { a.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(u, "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)); }
                                catch (Exception e) { Ui.toast(a, "No PDF viewer found"); }
                            })
                            .setNeutralButton("Share", (d, w) -> {
                                Intent i = new Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, u)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                a.startActivity(Intent.createChooser(i, "Share report"));
                            })
                            .setNegativeButton("Close", null).show();
                });
            } catch (Exception e) {
                a.runOnUiThread(() -> Ui.toast(a, "Couldn't make the report: " + e.getMessage()));
            }
        }).start();
    }

    /** Saves into Documents/Money/&lt;folder&gt; (Android 10+), or the app's own folder on older phones (returns null then). */
    static Uri save(Activity a, String folder, String name, String mime, byte[] data) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/Money/" + folder + "/");
            Uri u = a.getContentResolver().insert(MediaStore.Files.getContentUri("external"), v);
            if (u == null) throw new Exception("no storage");
            try (OutputStream out = a.getContentResolver().openOutputStream(u)) { if (out != null) out.write(data); }
            return u;
        }
        File dir = new File(a.getExternalFilesDir(null), folder);
        if (!dir.isDirectory()) dir.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(dir, name))) { out.write(data); }
        return null;
    }

    // ------------------------------------------------------------------ drawing

    private static Paint p(Activity a, int weight, float size, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Ui.face(a, weight));
        p.setTextSize(size);
        p.setColor(color);
        return p;
    }

    private static String rs(long paise) { return Fmt.plain(paise); }

    static byte[] build(Activity a, long month) throws Exception {
        boolean hide = Fmt.hide;
        Fmt.hide = false;
        try { return draw(a, month); } finally { Fmt.hide = hide; }
    }

    private static byte[] draw(Activity a, long month) throws Exception {
        Store s = Store.get(a);
        List<Txn> all = s.txns();
        long end = Fmt.addMonths(month, 1);
        long in = Ledger.income(all, month, end), out = Ledger.spent(all, month, end);
        LinkedHashMap<String, Long> cats = Ledger.byCategory(all, month, end);
        LinkedHashMap<String, Long> shops = Ledger.byMerchant(all, month, end, null);
        Plan plan = s.plan();
        boolean within = plan != null && plan.start < end && plan.end > month && plan.spent(all) <= plan.amount;
        int score = Smart.score(in, out, within);

        PdfDocument doc = new PdfDocument();
        int pageNo = 1;
        PdfDocument.Page page = doc.startPage(new PdfDocument.PageInfo.Builder(W, H, pageNo).create());
        Canvas c = page.getCanvas();
        int green = 0xFF0E9F6E, ink = 0xFF111418, grey = 0xFF5B6270, line = 0xFFE3E7EC;

        // header band
        Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
        band.setShader(new android.graphics.LinearGradient(0, 0, W, 120, 0xFF0E9F6E, 0xFF0B6F86, android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, 118, band);
        c.drawText("Money · monthly report", M, 48, p(a, Ui.SEMIBOLD, 13, 0xD9FFFFFF));
        c.drawText(Fmt.month(month), M, 86, p(a, Ui.EXTRABOLD, 30, 0xFFFFFFFF));
        c.drawText("Made " + Fmt.date(System.currentTimeMillis()), W - M - 120, 48, p(a, Ui.REGULAR, 11, 0xD9FFFFFF));

        // summary row
        float y = 160;
        String[][] stats = {{"Money in", rs(in)}, {"Money out", rs(out)}, {in - out >= 0 ? "Saved" : "Overspent", rs(Math.abs(in - out))}};
        for (int i = 0; i < 3; i++) {
            float x = M + i * 172;
            c.drawText(stats[i][0], x, y, p(a, Ui.SEMIBOLD, 11, grey));
            c.drawText(stats[i][1], x, y + 24, p(a, Ui.EXTRABOLD, 19, i == 1 ? ink : green));
        }
        y += 56;
        // score pill
        Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
        pill.setColor(0xFFDDF5EC);
        c.drawRoundRect(new RectF(M, y, W - M, y + 44), 22, 22, pill);
        c.drawText("Savings score  " + score + "/100 · " + Smart.scoreWord(score), M + 18, y + 28, p(a, Ui.BOLD, 14, green));
        y += 70;
        if (plan != null && plan.start < end && plan.end > month) {
            long ps = plan.spent(all);
            c.drawText("Budget " + plan.range() + ": " + rs(ps) + " of " + rs(plan.amount) + (ps <= plan.amount ? " — within budget ✓" : " — over by " + rs(ps - plan.amount)),
                    M, y, p(a, Ui.SEMIBOLD, 12, ink));
            y += 28;
        }

        // categories with bars
        c.drawText("Where it went", M, y, p(a, Ui.EXTRABOLD, 15, ink));
        y += 20;
        long top = 1;
        for (long v : cats.values()) top = Math.max(top, v);
        int n = 0;
        Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (Map.Entry<String, Long> e : cats.entrySet()) {
            if (n++ >= 10) break;
            c.drawText(e.getKey(), M, y + 12, p(a, Ui.SEMIBOLD, 11.5f, ink));
            String v = rs(e.getValue()) + "  (" + Math.round(e.getValue() * 100.0 / Math.max(1, out)) + "%)";
            Paint vp = p(a, Ui.BOLD, 11.5f, ink);
            c.drawText(v, W - M - vp.measureText(v), y + 12, vp);
            bar.setColor(line);
            c.drawRoundRect(new RectF(M + 150, y + 4, W - M - 120, y + 12), 4, 4, bar);
            bar.setColor(Cats.color(e.getKey()));
            c.drawRoundRect(new RectF(M + 150, y + 4, M + 150 + (W - 2 * M - 270) * e.getValue() / (float) top, y + 12), 4, 4, bar);
            y += 22;
        }
        y += 14;
        // top places
        c.drawText("Top places", M, y, p(a, Ui.EXTRABOLD, 15, ink));
        y += 20;
        n = 0;
        for (Map.Entry<String, Long> e : shops.entrySet()) {
            if (n++ >= 8) break;
            c.drawText(e.getKey(), M, y + 12, p(a, Ui.REGULAR, 11.5f, ink));
            Paint vp = p(a, Ui.BOLD, 11.5f, ink);
            String v = rs(e.getValue());
            c.drawText(v, W - M - vp.measureText(v), y + 12, vp);
            y += 20;
        }
        y += 14;
        // biggest payments
        List<Txn> big = new ArrayList<>();
        for (Txn t : all) if (t.isSpend() && t.time >= month && t.time < end) big.add(t);
        java.util.Collections.sort(big, (x1, x2) -> Long.compare(x2.amount, x1.amount));
        if (y < H - 140) {
            c.drawText("Biggest payments", M, y, p(a, Ui.EXTRABOLD, 15, ink));
            y += 20;
            for (int i = 0; i < Math.min(5, big.size()) && y < H - 50; i++) {
                Txn t = big.get(i);
                c.drawText(Fmt.date(t.time) + "  ·  " + (t.merchant.isEmpty() ? "Payment" : t.merchant), M, y + 12, p(a, Ui.REGULAR, 11.5f, ink));
                Paint vp = p(a, Ui.BOLD, 11.5f, ink);
                c.drawText(rs(t.amount), W - M - vp.measureText(rs(t.amount)), y + 12, vp);
                y += 20;
            }
        }
        footer(a, c, pageNo);
        doc.finishPage(page);

        // every payment
        List<Txn> list = new ArrayList<>();
        for (Txn t : all) if (t.visible() && t.kind != Txn.IGNORED && t.time >= month && t.time < end) list.add(t);
        java.util.Collections.sort(list, (x1, x2) -> Long.compare(x1.time, x2.time));
        int i = 0;
        while (i < list.size()) {
            pageNo++;
            page = doc.startPage(new PdfDocument.PageInfo.Builder(W, H, pageNo).create());
            c = page.getCanvas();
            c.drawText("Every payment · " + Fmt.month(month), M, 56, p(a, Ui.EXTRABOLD, 16, ink));
            y = 86;
            Paint hp = p(a, Ui.BOLD, 10, grey);
            c.drawText("DATE", M, y, hp);
            c.drawText("NAME", M + 70, y, hp);
            c.drawText("CATEGORY", M + 300, y, hp);
            c.drawText("AMOUNT", W - M - 60, y, hp);
            y += 14;
            Paint lp = new Paint();
            lp.setColor(line);
            while (i < list.size() && y < H - 50) {
                Txn t = list.get(i++);
                c.drawLine(M, y, W - M, y, lp);
                y += 16;
                Paint tp = p(a, Ui.REGULAR, 10.5f, t.kind == Txn.TRANSFER ? grey : ink);
                c.drawText(Fmt.cal(t.time).get(java.util.Calendar.DAY_OF_MONTH) + " " + Fmt.monthShort(t.time) + " " + Fmt.time(t.time), M, y, tp);
                String nm = t.merchant.isEmpty() ? (t.credit ? "Money in" : "Payment") : t.merchant;
                if (nm.length() > 34) nm = nm.substring(0, 33) + "…";
                c.drawText(nm, M + 70, y, tp);
                String cat = t.category.length() > 18 ? t.category.substring(0, 17) + "…" : t.category;
                c.drawText(cat, M + 300, y, tp);
                String amt = (t.credit ? "+" : "−") + rs(t.amount);
                Paint ap = p(a, Ui.BOLD, 10.5f, t.credit ? green : tp.getColor());
                c.drawText(amt, W - M - ap.measureText(amt), y, ap);
                y += 6;
            }
            footer(a, c, pageNo);
            doc.finishPage(page);
        }
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        doc.writeTo(bo);
        doc.close();
        return bo.toByteArray();
    }

    private static void footer(Activity a, Canvas c, int page) {
        c.drawText("Made by Money on this phone · page " + page, M, H - 24, p(a, Ui.REGULAR, 9, 0xFF9AA1AD));
    }
}
