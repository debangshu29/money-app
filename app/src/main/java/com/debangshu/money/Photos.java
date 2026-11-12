package com.debangshu.money;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Bill photos: kept inside the app (private), one per payment, shrunk to a sensible size. */
public final class Photos {

    private Photos() {}

    public static final int REQ_PICK = 71, REQ_CAMERA = 72;
    private static String pendingTxn;
    private static Uri pendingCameraUri;

    public static File file(Activity a, Txn t) {
        File dir = new File(a.getFilesDir(), "photos");
        if (!dir.isDirectory()) dir.mkdirs();
        return new File(dir, t.photo);
    }

    public static void choose(Activity a, Txn t) {
        new Ui.Popup(a).setTitle("Bill photo").setItems(new String[]{"📷  Take a photo", "🖼️  Choose from gallery"}, (d, w) -> {
            pendingTxn = t.id;
            if (w == 0) camera(a); else pick(a);
        }).show();
    }

    private static void pick(Activity a) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try { a.startActivityForResult(Intent.createChooser(i, "Choose the bill photo"), REQ_PICK); }
        catch (Exception e) { Ui.toast(a, "No gallery app found"); }
    }

    private static void camera(Activity a) {
        try {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Images.Media.DISPLAY_NAME, "money-bill-" + System.currentTimeMillis() + ".jpg");
            v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            if (Build.VERSION.SDK_INT >= 29) v.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Money");
            pendingCameraUri = a.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (pendingCameraUri == null) { pick(a); return; }
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri);
            a.startActivityForResult(i, REQ_CAMERA);
        } catch (Exception e) {
            pick(a);
        }
    }

    /** Call from onActivityResult. */
    public static void onResult(Activity a, int req, int res, Intent data) {
        if (req != REQ_PICK && req != REQ_CAMERA) return;
        Uri src = req == REQ_CAMERA ? pendingCameraUri : (data == null ? null : data.getData());
        String id = pendingTxn;
        pendingTxn = null;
        if (res != Activity.RESULT_OK || src == null || id == null) {
            if (req == REQ_CAMERA && pendingCameraUri != null) try { a.getContentResolver().delete(pendingCameraUri, null, null); } catch (Exception ignored) { }
            return;
        }
        Store s = Store.get(a);
        Txn t = s.byId(id);
        if (t == null) return;
        new Thread(() -> {
            try {
                Bitmap b = load(a, src, 1600);
                if (b == null) throw new Exception("unreadable");
                if (!t.photo.isEmpty()) file(a, t).delete();
                t.photo = t.id.replaceAll("[^A-Za-z0-9:-]", "_").replace(':', '_') + "-" + System.currentTimeMillis() + ".jpg";
                try (FileOutputStream out = new FileOutputStream(file(a, t))) { b.compress(Bitmap.CompressFormat.JPEG, 80, out); }
                s.update(t);
                if (req == REQ_CAMERA) try { a.getContentResolver().delete(src, null, null); } catch (Exception ignored) { }
                a.runOnUiThread(() -> { Ui.toast(a, "Bill photo saved"); TxnSheet.show(a, t); });
            } catch (Exception e) {
                a.runOnUiThread(() -> Ui.toast(a, "Couldn't save the photo"));
            }
        }).start();
    }

    private static Bitmap load(Activity a, Uri u, int max) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = a.getContentResolver().openInputStream(u)) { BitmapFactory.decodeStream(in, null, o); }
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / sample > max * 2) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        Bitmap b;
        try (InputStream in = a.getContentResolver().openInputStream(u)) { b = BitmapFactory.decodeStream(in, null, o2); }
        if (b == null) return null;
        int rot = 0;
        try (InputStream in = a.getContentResolver().openInputStream(u)) {
            if (in != null && Build.VERSION.SDK_INT >= 24) {
                int or = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                rot = or == ExifInterface.ORIENTATION_ROTATE_90 ? 90 : or == ExifInterface.ORIENTATION_ROTATE_180 ? 180 : or == ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
            }
        } catch (Exception ignored) { }
        float scale = Math.min(1f, max / (float) Math.max(b.getWidth(), b.getHeight()));
        if (scale < 1f || rot != 0) {
            Matrix m = new Matrix();
            m.postScale(scale, scale);
            m.postRotate(rot);
            b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
        }
        return b;
    }

    /** Thumbnail for the payment sheet; tap for full screen. */
    public static ImageView thumb(Activity a, Txn t) {
        ImageView iv = new ImageView(a);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setClipToOutline(true);
        iv.setBackground(Ui.round(Ui.col(a, R.color.surface2), Ui.dp(a, 14)));
        File f = file(a, t);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = 4;
        Bitmap b = f.exists() ? BitmapFactory.decodeFile(f.getPath(), o) : null;
        if (b != null) iv.setImageBitmap(b);
        iv.setOnClickListener(v -> full(a, t));
        return iv;
    }

    static void full(Activity a, Txn t) {
        File f = file(a, t);
        Bitmap b = f.exists() ? BitmapFactory.decodeFile(f.getPath()) : null;
        if (b == null) { Ui.toast(a, "Photo not found"); return; }
        ImageView iv = new ImageView(a);
        iv.setAdjustViewBounds(true);
        iv.setImageBitmap(b);
        new Ui.Popup(a).setView(iv)
                .setPositiveButton("Close", null)
                .setNeutralButton("Remove photo", (d, w) -> { f.delete(); t.photo = ""; Store.get(a).update(t); })
                .show();
    }
}
