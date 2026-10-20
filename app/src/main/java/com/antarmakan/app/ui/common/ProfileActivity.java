package com.antarmakan.app.ui.common;

import android.os.Bundle;
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
public class ProfileActivity extends BaseActivity {

    private User me;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enableBack("Ubah Profil");
        final LinearLayout page = scrollPage();
        page.addView(Ui.empty(this, "Memuat..."));
        load(new Async.Task<User>() {
            @Override
            public User run() throws Exception {
                return repo.me();
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
        Button save = Ui.primary(this, "Simpan");
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                me.name = name.getText().toString().trim();
                me.kecamatan = (String) kec.getSelectedItem();
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
