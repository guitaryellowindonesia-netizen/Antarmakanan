package com.antarmakan.app.ui.admin;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.antarmakan.app.ui.merchant.BaseFormActivity;
import com.antarmakan.app.util.AppSettings;
import com.antarmakan.app.util.Async;
import com.antarmakan.app.util.Fmt;
import com.antarmakan.app.util.Ui;

import org.json.JSONObject;

import java.util.Locale;

/** Admin: ubah tarif ongkir, komisi, biaya layanan, dll tanpa build ulang APK. */
public class SettingsActivity extends BaseFormActivity {

    private EditText dasar, jarakDasar, perKm, maks, layanan, kResto, kDriver, minTopup, info, cs,
            batasResto, batasDriver, radius, jarakMaks;
    private android.widget.CheckBox qris;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enableBack("Pengaturan Aplikasi");
        final LinearLayout page = scrollPage();
        page.addView(Ui.empty(this, "Memuat..."));
        load(new Async.Task<Boolean>() {
            @Override
            public Boolean run() throws Exception {
                repo.refreshSettings(true);
                return true;
            }
        }, new Async.Callback<Boolean>() {
            @Override
            public void onResult(Boolean r) {
                build(page);
            }
        });
    }

    private static String num(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.format(Locale.US, "%.1f", d);
    }

    private void build(LinearLayout page) {
        page.removeAllViews();
        LinearLayout c = formCard(page);
        c.addView(Ui.title(this, "🛵 Ongkos kirim"));
        dasar = field(c, "Ongkir dasar (Rp)", num(AppSettings.ONGKIR_DASAR), Ui.number(this, "5000"));
        jarakDasar = field(c, "Berlaku sampai jarak (km)", num(AppSettings.JARAK_DASAR_KM),
                Ui.edit(this, "2", android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL));
        perKm = field(c, "Tambahan per km berikutnya (Rp)", num(AppSettings.ONGKIR_PER_KM), Ui.number(this, "2000"));
        maks = field(c, "Ongkir maksimal (Rp)", num(AppSettings.ONGKIR_MAKS), Ui.number(this, "40000"));
        jarakMaks = field(c, "Jarak antar maksimal semua resto (km)", num(AppSettings.JARAK_ANTAR_MAKS_KM),
                Ui.number(this, "25"));

        LinearLayout c2 = formCard(page);
        c2.addView(Ui.title(this, "💰 Komisi & biaya"));
        layanan = field(c2, "Biaya layanan per pesanan (Rp)", num(AppSettings.BIAYA_LAYANAN), Ui.number(this, "2000"));
        kResto = field(c2, "Komisi dari resto (%)", num(AppSettings.KOMISI_PERSEN_RESTO), Ui.number(this, "5"));
        kDriver = field(c2, "Komisi dari ongkir driver (%)", num(AppSettings.KOMISI_PERSEN_DRIVER), Ui.number(this, "5"));
        minTopup = field(c2, "Minimal top up / tarik saldo (Rp)", num(AppSettings.MIN_TOPUP), Ui.number(this, "10000"));
        info = field(c2, "Info transfer untuk top up", AppSettings.INFO_TRANSFER, Ui.multiline(this, "Bank / e-wallet"));

        LinearLayout c3 = formCard(page);
        c3.addView(Ui.title(this, "⏱️ Operasional"));
        batasResto = field(c3, "Batal otomatis bila resto tidak merespons (menit)",
                num(AppSettings.BATAS_RESPON_RESTO_MENIT), Ui.number(this, "10"));
        batasDriver = field(c3, "Pelanggan boleh batal bila belum ada driver (menit)",
                num(AppSettings.BATAS_CARI_DRIVER_MENIT), Ui.number(this, "20"));
        radius = field(c3, "Driver hanya melihat order dari resto dalam radius (km)",
                num(AppSettings.RADIUS_DRIVER_KM), Ui.number(this, "10"));
        cs = field(c3, "Nomor WhatsApp CS (format 62...)", AppSettings.CS_PHONE, Ui.phone(this, "628..."));
        qris = new android.widget.CheckBox(this);
        qris.setText("📱 Aktifkan pembayaran QRIS (Midtrans) — nyalakan setelah server terpasang");
        qris.setChecked(AppSettings.QRIS_AKTIF);
        c3.addView(qris);

        Button save = Ui.primary(this, "Simpan Pengaturan");
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
        c3.addView(save);
        c3.addView(Ui.muted(this, "Perubahan berlaku untuk pesanan baru. Contoh ongkir 5 km: "
                + Fmt.rp(com.antarmakan.app.util.Geo.deliveryFee(5))));
    }

    private static double dbl(EditText e) {
        try {
            return Double.parseDouble(e.getText().toString().trim().replace(',', '.'));
        } catch (Exception ex) {
            return -1;
        }
    }

    private void save() {
        final JSONObject o = new JSONObject();
        try {
            o.put("ongkirDasar", (int) dbl(dasar));
            o.put("jarakDasarKm", dbl(jarakDasar));
            o.put("ongkirPerKm", (int) dbl(perKm));
            o.put("ongkirMaks", (int) dbl(maks));
            o.put("jarakAntarMaksKm", dbl(jarakMaks));
            o.put("biayaLayanan", (int) dbl(layanan));
            o.put("komisiResto", (int) dbl(kResto));
            o.put("komisiDriver", (int) dbl(kDriver));
            o.put("minTopup", (int) dbl(minTopup));
            o.put("infoTransfer", info.getText().toString().trim());
            o.put("batasRestoMenit", (int) dbl(batasResto));
            o.put("batasDriverMenit", (int) dbl(batasDriver));
            o.put("radiusDriverKm", dbl(radius));
            o.put("csPhone", Fmt.waNumber(cs.getText().toString()));
            o.put("qrisAktif", qris.isChecked());
        } catch (Exception e) {
            Ui.toast(this, "Isian tidak valid");
            return;
        }
        action("Menyimpan...", new Async.Task<Boolean>() {
            @Override
            public Boolean run() throws Exception {
                repo.saveSettings(o);
                return true;
            }
        }, new Async.Callback<Boolean>() {
            @Override
            public void onResult(Boolean r) {
                Ui.toast(SettingsActivity.this, "Pengaturan disimpan");
                finish();
            }
        });
    }
}
