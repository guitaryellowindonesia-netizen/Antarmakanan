package com.antarmakan.app.ui.customer;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.antarmakan.app.model.Order;
import com.antarmakan.app.ui.common.BaseActivity;
import com.antarmakan.app.ui.common.Views;
import com.antarmakan.app.util.Async;
import com.antarmakan.app.util.Fmt;
import com.antarmakan.app.util.Ui;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.json.JSONObject;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/** Tampilkan QRIS untuk dibayar; otomatis lanjut saat pembayaran terkonfirmasi. */
public class QrisActivity extends BaseActivity {

    private String orderId;
    private ImageView qrView;
    private TextView amountText, timerText, statusText;
    private Bitmap qrBitmap;
    private long expiresAt;
    private boolean done;

    public static void open(Activity a, String orderId) {
        Intent i = new Intent(a, QrisActivity.class);
        i.putExtra("orderId", orderId);
        a.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        orderId = getIntent().getStringExtra("orderId");
        enableBack("Bayar dengan QRIS");
        LinearLayout page = scrollPage();
        LinearLayout c = Ui.card(this);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView head = Ui.text(this, "Scan QRIS ini dengan aplikasi e-wallet / m-banking", 14, Ui.TEXT, true);
        head.setGravity(Gravity.CENTER);
        c.addView(head, Ui.matchWrap());
        amountText = Ui.text(this, "", 26, Ui.PRIMARY_DARK, true);
        amountText.setGravity(Gravity.CENTER);
        c.addView(amountText, Ui.matchWrap());
        qrView = new ImageView(this);
        qrView.setBackgroundColor(Color.WHITE);
        c.addView(qrView, new LinearLayout.LayoutParams(Ui.dp(this, 260), Ui.dp(this, 260)));
        timerText = Ui.text(this, "", 13, Ui.ACCENT, true);
        timerText.setGravity(Gravity.CENTER);
        c.addView(timerText, Ui.matchWrap());
        statusText = Ui.text(this, "Membuat QRIS...", 14, Ui.MUTED, false);
        statusText.setGravity(Gravity.CENTER);
        c.addView(statusText, Ui.matchWrap());
        page.addView(c);

        LinearLayout box = Ui.vbox(this);
        box.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        Button save = Ui.outlineButton(this, "💾 Simpan QR ke Galeri", Ui.PRIMARY);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveToGallery();
            }
        });
        box.addView(save);
        Button check = Ui.primary(this, "Saya sudah bayar, cek sekarang");
        check.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkNow();
            }
        });
        box.addView(check);
        Button later = Ui.outlineButton(this, "Lihat pesanan", Ui.BLUE);
        later.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Views.openOrder(QrisActivity.this, orderId);
                finish();
            }
        });
        box.addView(later);
        box.addView(Ui.muted(this, "Bayar dari HP yang sama? Simpan QR ke galeri, lalu di aplikasi e-wallet pilih "
                + "Scan → ikon galeri/foto → pilih gambar QR ini."));
        page.addView(box);

        createQr();
        setPolling(3000);
    }

    private void createQr() {
        load(new Async.Task<JSONObject>() {
            @Override
            public JSONObject run() throws Exception {
                return repo.createQris(orderId);
            }
        }, new Async.Callback<JSONObject>() {
            @Override
            public void onResult(JSONObject r) {
                if (r.optBoolean("paid")) {
                    onPaid();
                    return;
                }
                expiresAt = r.optLong("expiresAt");
                amountText.setText(Fmt.rp(r.optLong("amount")));
                qrBitmap = qr(r.optString("qrString"), Ui.dp(QrisActivity.this, 260));
                qrView.setImageBitmap(qrBitmap);
                statusText.setText("Menunggu pembayaran...");
            }
        });
    }

    private static Bitmap qr(String text, int size) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<EncodeHintType, Object>();
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints);
            Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) b.setPixel(x, y, m.get(x, y) ? Color.BLACK : Color.WHITE);
            }
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    /** Dipanggil tiap 3 detik: cek status lunas & hitung mundur. */
    @Override
    protected void refresh() {
        if (done) return;
        if (expiresAt > 0) {
            long left = (expiresAt - System.currentTimeMillis()) / 1000;
            if (left <= 0) {
                timerText.setText("QR kedaluwarsa. Membuat QR baru...");
                expiresAt = 0;
                createQr();
            } else {
                timerText.setText(String.format(java.util.Locale.US, "Berlaku %d:%02d lagi", left / 60, left % 60));
            }
        }
        load(new Async.Task<Order>() {
            @Override
            public Order run() throws Exception {
                return repo.order(orderId);
            }
        }, new Async.Callback<Order>() {
            @Override
            public void onResult(Order o) {
                if (o.paid) onPaid();
                else if (o.status == Order.DIBATALKAN) {
                    done = true;
                    statusText.setText("Pesanan dibatalkan.");
                }
            }
        });
    }

    private void checkNow() {
        action("Mengecek pembayaran...", new Async.Task<Boolean>() {
            @Override
            public Boolean run() throws Exception {
                return repo.checkQris(orderId);
            }
        }, new Async.Callback<Boolean>() {
            @Override
            public void onResult(Boolean paid) {
                if (paid) onPaid();
                else Ui.toast(QrisActivity.this, "Pembayaran belum diterima. Tunggu sebentar lalu cek lagi.");
            }
        });
    }

    private void onPaid() {
        if (done) return;
        done = true;
        Ui.toast(this, "Pembayaran berhasil! Pesanan diteruskan ke resto 🎉");
        Views.openOrder(this, orderId);
        finish();
    }

    private void saveToGallery() {
        if (qrBitmap == null) return;
        if (Build.VERSION.SDK_INT < 29) {
            Ui.toast(this, "Ambil screenshot layar ini, lalu scan dari galeri");
            return;
        }
        try {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Images.Media.DISPLAY_NAME, "QRIS_" + orderId + ".png");
            v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            OutputStream os = getContentResolver().openOutputStream(uri);
            qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
            os.close();
            Ui.toast(this, "QR tersimpan di galeri");
        } catch (Exception e) {
            Ui.toast(this, "Gagal menyimpan, ambil screenshot saja");
        }
    }
}
