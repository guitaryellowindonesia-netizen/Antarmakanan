package com.antarmakan.app.ui.merchant;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;

import com.antarmakan.app.Config;
import com.antarmakan.app.data.AppException;
import com.antarmakan.app.model.Restaurant;
import com.antarmakan.app.model.User;
import com.antarmakan.app.util.Async;
import com.antarmakan.app.util.Ui;

import java.util.Arrays;

/** Form tambah/ubah resto (dipakai Mitra & Admin). */
public class RestoEditActivity extends BaseFormActivity {

    private Restaurant r;
    private User me;
    private String ownerPhone = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String id = getIntent().getStringExtra("restoId");
        enableBack(id == null ? "Tambah Resto" : "Ubah Resto");
        final LinearLayout page = scrollPage();
        page.addView(Ui.empty(this, "Memuat..."));
        load(new Async.Task<Object[]>() {
            @Override
            public Object[] run() throws Exception {
                User u = repo.me();
                Restaurant res = id == null ? new Restaurant() : repo.resto(id, u.kecamatan);
                String phone = "";
                if (res.ownerId.length() > 0) {
                    User owner = repo.user(res.ownerId);
                    if (owner != null) phone = owner.phone;
                }
                return new Object[]{u, res, phone};
            }
        }, new Async.Callback<Object[]>() {
            @Override
            public void onResult(Object[] x) {
                me = (User) x[0];
                r = (Restaurant) x[1];
                ownerPhone = (String) x[2];
                if (r.kecamatan.length() == 0) r.kecamatan = me.kecamatan;
                build(page);
            }
        });
    }

    private void build(LinearLayout page) {
        page.removeAllViews();
        LinearLayout c = formCard(page);
        final EditText name = field(c, "Nama resto", r.name, Ui.edit(this, "Nama resto"));
        final EditText desc = field(c, "Deskripsi singkat", r.description, Ui.multiline(this, "Contoh: masakan rumahan"));
        c.addView(Ui.label(this, "Kategori"));
        final String[] cats = Arrays.copyOfRange(Config.KATEGORI, 1, Config.KATEGORI.length);
        final Spinner cat = Ui.spinner(this, cats, Ui.indexOf(cats, r.category));
        c.addView(cat);
        photoSection(c, "Foto resto (sampul)", r.imageId, r.emoji);
        final EditText emoji = field(c, "Ikon/emoji (kosongkan = otomatis)", r.emoji, Ui.edit(this, "🍛"));
        c.addView(Ui.label(this, "Kecamatan"));
        final Spinner kec = Ui.spinner(this, Config.namaKecamatan(), Config.indexKecamatan(r.kecamatan));
        c.addView(kec);
        final EditText addr = field(c, "Alamat resto", r.address, Ui.multiline(this, "Alamat lengkap"));
        final EditText phone = field(c, "No. telepon resto", r.phone.length() > 0 ? r.phone : me.phone,
                Ui.phone(this, "08xxx"));
        LinearLayout hours = Ui.hbox(this);
        LinearLayout o = Ui.vbox(this);
        final EditText open = field(o, "Jam buka", r.openHour, Ui.edit(this, "08:00"));
        LinearLayout cl = Ui.vbox(this);
        cl.setPadding(Ui.dp(this, 8), 0, 0, 0);
        final EditText close = field(cl, "Jam tutup", r.closeHour, Ui.edit(this, "21:00"));
        hours.addView(o, Ui.weight(1));
        hours.addView(cl, Ui.weight(1));
        c.addView(hours);

        final EditText owner = Ui.phone(this, "No HP akun mitra pemilik");
        final CheckBox active = new CheckBox(this);
        if (me.isAdmin()) {
            c.addView(Ui.divider(this));
            c.addView(Ui.label(this, "Pemilik (No HP akun Mitra Resto, opsional)"));
            owner.setText(ownerPhone);
            c.addView(owner);
            active.setText("Resto aktif / disetujui");
            active.setChecked(r.active);
            c.addView(active);
        }

        Button save = Ui.primary(this, "Simpan");
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                r.name = name.getText().toString().trim();
                r.description = desc.getText().toString().trim();
                r.category = (String) cat.getSelectedItem();
                String em = emoji.getText().toString().trim();
                r.emoji = em.length() > 0 ? em : Config.emojiKategori(r.category);
                r.kecamatan = (String) kec.getSelectedItem();
                r.address = addr.getText().toString().trim();
                r.phone = phone.getText().toString().trim();
                r.openHour = normHour(open.getText().toString(), "08:00");
                r.closeHour = normHour(close.getText().toString(), "21:00");
                final String ownerPh = owner.getText().toString().trim();
                if (me.isAdmin()) {
                    r.active = active.isChecked();
                } else if (r.id.length() == 0) {
                    r.ownerId = me.id;
                    r.active = false;
                }
                action("Menyimpan...", new Async.Task<Boolean>() {
                    @Override
                    public Boolean run() throws Exception {
                        if (me.isAdmin()) {
                            if (ownerPh.length() > 0) {
                                User u = repo.userByPhone(ownerPh);
                                if (u == null || !u.isMerchant()) {
                                    throw new AppException("No HP pemilik bukan akun Mitra Resto");
                                }
                                r.ownerId = u.id;
                            } else {
                                r.ownerId = "";
                            }
                        }
                        String old = r.imageId;
                        r.imageId = applyPhoto(old);
                        repo.saveResto(r);
                        if (!old.equals(r.imageId)) repo.deleteImage(old);
                        return true;
                    }
                }, new Async.Callback<Boolean>() {
                    @Override
                    public void onResult(Boolean x) {
                        Ui.toast(RestoEditActivity.this, "Resto disimpan");
                        finish();
                    }
                });
            }
        });
        c.addView(save);
    }

    private static String normHour(String s, String def) {
        s = s.trim().replace('.', ':');
        if (s.matches("\\d{1,2}:\\d{2}")) {
            if (s.length() == 4) s = "0" + s;
            return s;
        }
        if (s.matches("\\d{1,2}")) return (s.length() == 1 ? "0" + s : s) + ":00";
        return def;
    }
}
