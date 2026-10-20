package com.antarmakan.app.ui.merchant;

import android.content.Intent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.antarmakan.app.ui.common.BaseActivity;
import com.antarmakan.app.util.Async;
import com.antarmakan.app.util.Images;
import com.antarmakan.app.util.Ui;

/** Pembantu formulir: label + input, dan pilih foto. */
public abstract class BaseFormActivity extends BaseActivity {

    private static final int REQ_PHOTO = 701;

    private String pendingPhoto;   // base64 foto baru yang belum diunggah
    private boolean removePhoto;
    private ImageView preview;

    protected double pinLat, pinLng;   // titik lokasi yang dipilih di peta
    private TextView pinInfo;

    protected EditText field(LinearLayout parent, String label, String value, EditText input) {
        parent.addView(Ui.label(this, label));
        if (value != null) input.setText(value);
        parent.addView(input);
        return input;
    }

    /** Bagian foto: pratinjau + tombol pilih/hapus. */
    protected void photoSection(LinearLayout parent, String label, String imageId, String emoji) {
        parent.addView(Ui.label(this, label));
        FrameLayout frame = Ui.banner(this, imageId, emoji, 180);
        preview = Ui.photoImage(frame);
        parent.addView(frame);
        LinearLayout row = Ui.hbox(this);
        TextView pick = Ui.smallButton(this, "📷 Pilih Foto", Ui.PRIMARY, true);
        pick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("image/*");
                try {
                    startActivityForResult(Intent.createChooser(i, "Pilih foto"), REQ_PHOTO);
                } catch (Exception e) {
                    Ui.toast(BaseFormActivity.this, "Tidak ada aplikasi galeri");
                }
            }
        });
        TextView del = Ui.smallButton(this, "Hapus Foto", Ui.RED, false);
        del.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pendingPhoto = null;
                removePhoto = true;
                preview.setImageDrawable(null);
                preview.setVisibility(View.GONE);
            }
        });
        row.addView(pick);
        row.addView(del);
        parent.addView(row);
        parent.addView(Ui.muted(this, "Foto otomatis diperkecil agar hemat kuota."));
    }

    /** Bagian titik lokasi: info + tombol buka peta. */
    protected void pinSection(LinearLayout parent, String label, double lat, double lng) {
        pinLat = lat;
        pinLng = lng;
        parent.addView(Ui.label(this, label));
        LinearLayout row = Ui.hbox(this);
        pinInfo = Ui.text(this, "", 14, Ui.TEXT, true);
        row.addView(pinInfo, Ui.weight(1));
        TextView pick = Ui.smallButton(this, "🗺️ Atur di peta", Ui.PRIMARY, true);
        pick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                com.antarmakan.app.ui.common.MapPickerActivity.open(BaseFormActivity.this,
                        "Pilih Titik Lokasi", pinLat, pinLng);
            }
        });
        row.addView(pick);
        parent.addView(row);
        showPin();
    }

    private void showPin() {
        if (pinInfo == null) return;
        boolean has = com.antarmakan.app.util.Geo.has(pinLat, pinLng);
        pinInfo.setText(has ? "✅ Titik sudah diatur (Kec. "
                + com.antarmakan.app.util.Geo.nearestKecamatan(pinLat, pinLng) + ")" : "⚠️ Belum diatur");
        pinInfo.setTextColor(has ? Ui.PRIMARY_DARK : Ui.RED);
    }

    /** Dipanggil setelah titik dipilih di peta. */
    protected void onPinPicked(double lat, double lng) {
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req == com.antarmakan.app.ui.common.MapPickerActivity.REQ && result == RESULT_OK && data != null) {
            pinLat = data.getDoubleExtra("lat", 0);
            pinLng = data.getDoubleExtra("lng", 0);
            showPin();
            onPinPicked(pinLat, pinLng);
            return;
        }
        if (req != REQ_PHOTO || result != RESULT_OK || data == null || data.getData() == null) return;
        final android.net.Uri uri = data.getData();
        action("Memproses foto...", new Async.Task<String>() {
            @Override
            public String run() throws Exception {
                return Images.encode(BaseFormActivity.this, uri);
            }
        }, new Async.Callback<String>() {
            @Override
            public void onResult(String b64) {
                pendingPhoto = b64;
                removePhoto = false;
                if (preview != null) {
                    preview.setTag(null);
                    preview.setImageBitmap(Images.decode(b64));
                    preview.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    /**
     * Panggil di background sebelum menyimpan. Mengembalikan imageId yang dipakai
     * (foto baru diunggah). Hapus foto lama dengan repo.deleteImage setelah simpan berhasil.
     */
    protected String applyPhoto(String oldId) throws Exception {
        if (pendingPhoto != null) return repo.uploadImage(pendingPhoto);
        if (removePhoto) return "";
        return oldId;
    }
}
