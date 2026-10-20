package com.antarmakan.app.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.util.Base64;
import android.util.LruCache;
import android.widget.ImageView;

import com.antarmakan.app.data.Backend;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Foto makanan/resto: dikecilkan & dikompres (JPEG ~30 KB), disimpan sebagai
 * teks base64 di database, lalu di-cache di HP agar tidak diunduh berulang.
 */
public final class Images {
    private Images() {}

    private static final int MAX_SIZE = 640;   // piksel sisi terpanjang
    private static final int QUALITY = 70;

    private static final LruCache<String, Bitmap> MEM = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap b) {
            return b.getByteCount();
        }
    };

    /** Baca foto dari galeri/kamera, kecilkan, kembalikan base64 JPEG. */
    public static String encode(Context c, Uri uri) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        InputStream in = c.getContentResolver().openInputStream(uri);
        BitmapFactory.decodeStream(in, null, bounds);
        in.close();
        if (bounds.outWidth <= 0) throw new com.antarmakan.app.data.AppException("File bukan gambar");

        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= MAX_SIZE || bounds.outHeight / (sample * 2) >= MAX_SIZE) {
            sample *= 2;
        }
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inSampleSize = sample;
        in = c.getContentResolver().openInputStream(uri);
        Bitmap bmp = BitmapFactory.decodeStream(in, null, opt);
        in.close();
        if (bmp == null) throw new com.antarmakan.app.data.AppException("Gagal membaca gambar");

        // putar sesuai orientasi foto kamera
        int rotate = rotation(c, uri);
        float scale = Math.min(1f, (float) MAX_SIZE / Math.max(bmp.getWidth(), bmp.getHeight()));
        if (rotate != 0 || scale < 1f) {
            Matrix m = new Matrix();
            m.postScale(scale, scale);
            m.postRotate(rotate);
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, QUALITY, out);
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    private static int rotation(Context c, Uri uri) {
        if (android.os.Build.VERSION.SDK_INT < 24) return 0;
        try {
            InputStream in = c.getContentResolver().openInputStream(uri);
            android.media.ExifInterface exif = new android.media.ExifInterface(in);
            int o = exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
            in.close();
            if (o == android.media.ExifInterface.ORIENTATION_ROTATE_90) return 90;
            if (o == android.media.ExifInterface.ORIENTATION_ROTATE_180) return 180;
            if (o == android.media.ExifInterface.ORIENTATION_ROTATE_270) return 270;
        } catch (Exception ignored) {
        }
        return 0;
    }

    public static Bitmap decode(String base64) {
        byte[] b = Base64.decode(base64, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(b, 0, b.length);
    }

    /** Tampilkan foto ke ImageView (dari cache, atau unduh dulu). */
    public static void load(final ImageView iv, final String imageId) {
        if (imageId == null || imageId.length() == 0) return;
        iv.setTag(imageId);
        Bitmap cached = MEM.get(imageId);
        if (cached != null) {
            show(iv, cached);
            return;
        }
        final Context ctx = iv.getContext().getApplicationContext();
        Async.run(new Async.Task<Bitmap>() {
            @Override
            public Bitmap run() throws Exception {
                File f = new File(new File(ctx.getCacheDir(), "img"), imageId + ".jpg");
                Bitmap b = null;
                if (f.exists()) b = BitmapFactory.decodeFile(f.getAbsolutePath());
                if (b == null) {
                    String data = Backend.repo(ctx).imageData(imageId);
                    if (data == null) return null;
                    byte[] bytes = Base64.decode(data, Base64.DEFAULT);
                    f.getParentFile().mkdirs();
                    FileOutputStream fo = new FileOutputStream(f);
                    fo.write(bytes);
                    fo.close();
                    b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                }
                return b;
            }
        }, new Async.Callback<Bitmap>() {
            @Override
            public void onResult(Bitmap b) {
                if (b == null) return;
                MEM.put(imageId, b);
                if (imageId.equals(iv.getTag())) show(iv, b);
            }
        });
    }

    private static void show(ImageView iv, Bitmap b) {
        iv.setImageBitmap(b);
        iv.setVisibility(android.view.View.VISIBLE);
    }
}
