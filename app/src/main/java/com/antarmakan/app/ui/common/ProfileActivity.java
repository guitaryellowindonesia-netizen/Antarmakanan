package com.antarmakan.app.ui.common;

import android.os.Bundle;
import android.widget.TextView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;

import com.antarmakan.app.Config;
import com.antarmakan.app.model.User;
import com.antarmakan.app.util.Async;
import com.antarmakan.app.util.Ui;

/** Ubah profil: nama, kecamatan, alamat, kendaraan (driver). */
public class ProfileActivity extends com.antarmakan.app.ui.merchant.BaseFormActivity {

    private User me;
    private boolean phoneLogin;
    private String recoveryEmail = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enableBack("Ubah Profil");
        final LinearLayout page = scrollPage();
        page.addView(Ui.empty(this, "Memuat..."));
        load(new Async.Task<User>() {
            @Override
            public User run() throws Exception {
                User u = repo.me();
                phoneLogin = repo.isPhoneLogin(u);
                recoveryEmail = phoneLogin ? repo.recoveryEmail(u) : "";
                return u;
            }
        }, new Async.Callback<User>() {
            @Override
            public void onResult(User u) {
                me = u;
                build(page);
            }
        });
    }

    private void build(LinearLayout page) {
        page.removeAllViews();
        if (phoneLogin) {
            LinearLayout ec = formCard(page);
            ec.addView(Ui.title(this, "🔐 Email Pemulihan"));
            ec.addView(Ui.muted(this, "Dipakai bila Anda lupa kata sandi (link reset dikirim ke email ini)."));
            ec.addView(Ui.text(this, recoveryEmail.length() > 0 ? "✅ " + recoveryEmail : "⚠️ Belum diatur",
                    14, recoveryEmail.length() > 0 ? Ui.PRIMARY_DARK : Ui.RED, true));
            TextView set = Ui.smallButton(this, recoveryEmail.length() > 0 ? "Ganti email" : "Atur email",
                    Ui.PRIMARY, true);
            set.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Ui.prompt(ProfileActivity.this, "Email pemulihan", "nama@gmail.com", recoveryEmail,
                            android.text.InputType.TYPE_CLASS_TEXT
                                    | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                            new Ui.TextCallback() {
                                @Override
                                public void onText(final String email) {
                                    action("Menyimpan...", new Async.Task<Boolean>() {
                                        @Override
                                        public Boolean run() throws Exception {
                                            repo.setRecoveryEmail(email);
                                            return true;
                                        }
                                    }, new Async.Callback<Boolean>() {
                                        @Override
                                        public void onResult(Boolean r) {
                                            Ui.toast(ProfileActivity.this, "Email pemulihan disimpan");
                                            recreate();
                                        }
                                    });
                                }
                            });
                }
            });
            ec.addView(set);
        }
        LinearLayout card = formCard(page);
        card.addView(Ui.label(this, "Nomor HP (tidak bisa diubah)"));
        card.addView(Ui.body(this, me.phone));
        card.addView(Ui.spacer(this, 8));
        card.addView(Ui.label(this, "Nama"));
        final EditText name = Ui.edit(this, "Nama");
        name.setText(me.name);
        card.addView(name);
        card.addView(Ui.label(this, "Kecamatan"));
        final Spinner kec = Ui.spinner(this, Config.namaKecamatan(), Config.indexKecamatan(me.kecamatan));
        card.addView(kec);
        card.addView(Ui.label(this, "Alamat lengkap"));
        final EditText addr = Ui.multiline(this, "Alamat");
        addr.setText(me.address);
        card.addView(addr);
        final EditText veh = Ui.edit(this, "Kendaraan & plat nomor");
        if (me.isDriver()) {
            card.addView(Ui.label(this, "Kendaraan & plat nomor"));
            veh.setText(me.vehicle);
            card.addView(veh);
        }
        if (me.isCustomer()) pinSection(card, "Titik lokasi rumah", me.lat, me.lng);
        Button save = Ui.primary(this, "Simpan");
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                me.name = name.getText().toString().trim();
                me.kecamatan = (String) kec.getSelectedItem();
                if (me.isCustomer()) {
                    me.lat = pinLat;
                    me.lng = pinLng;
                }
                me.address = addr.getText().toString().trim();
                if (me.isDriver()) me.vehicle = veh.getText().toString().trim();
                action("Menyimpan...", new Async.Task<Boolean>() {
                    @Override
                    public Boolean run() throws Exception {
                        repo.saveProfile(me);
                        return true;
                    }
                }, new Async.Callback<Boolean>() {
                    @Override
                    public void onResult(Boolean r) {
                        Ui.toast(ProfileActivity.this, "Profil disimpan");
                        finish();
                    }
                });
            }
        });
        card.addView(save);
    }
}
