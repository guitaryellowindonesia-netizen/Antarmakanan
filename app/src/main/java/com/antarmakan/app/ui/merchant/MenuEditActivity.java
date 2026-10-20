package com.antarmakan.app.ui.merchant;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;

import com.antarmakan.app.model.MenuItem;
import com.antarmakan.app.util.Async;
import com.antarmakan.app.util.Fmt;
import com.antarmakan.app.util.Ui;

/** Form tambah/ubah menu. */
public class MenuEditActivity extends BaseFormActivity {

    private static final String[] CATEGORIES = {"Makanan", "Minuman", "Lauk", "Paket", "Cemilan"};
    private MenuItem m;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String restoId = getIntent().getStringExtra("restoId");
        final String menuId = getIntent().getStringExtra("menuId");
        enableBack(menuId == null ? "Tambah Menu" : "Ubah Menu");
        final LinearLayout page = scrollPage();
        if (menuId == null) {
            m = new MenuItem();
            m.restoId = restoId;
            build(page);
        } else {
            page.addView(Ui.empty(this, "Memuat..."));
            load(new Async.Task<MenuItem>() {
                @Override
                public MenuItem run() throws Exception {
                    return repo.menu(menuId);
                }
            }, new Async.Callback<MenuItem>() {
                @Override
                public void onResult(MenuItem item) {
                    m = item;
                    build(page);
                }
            });
        }
    }

    private void build(LinearLayout page) {
        page.removeAllViews();
        LinearLayout c = formCard(page);
        final EditText name = field(c, "Nama menu", m.name, Ui.edit(this, "Contoh: Nasi Goreng Spesial"));
        final EditText desc = field(c, "Deskripsi", m.description, Ui.multiline(this, "Isi / keterangan menu"));
        final EditText price = field(c, "Harga (Rp)", m.price > 0 ? String.valueOf(m.price) : "",
                Ui.number(this, "15000"));
        c.addView(Ui.label(this, "Kategori"));
        final Spinner cat = Ui.spinner(this, CATEGORIES, Ui.indexOf(CATEGORIES, m.category));
        c.addView(cat);
        photoSection(c, "Foto menu", m.imageId, m.emoji);
        final EditText emoji = field(c, "Ikon/emoji (dipakai jika tanpa foto)", m.emoji, Ui.edit(this, "🍛"));
        final CheckBox avail = new CheckBox(this);
        avail.setText("Tersedia (bisa dipesan)");
        avail.setChecked(m.available);
        c.addView(avail);
        final CheckBox rec = new CheckBox(this);
        rec.setText("Tampilkan sebagai rekomendasi ⭐");
        rec.setChecked(m.recommended);
        c.addView(rec);

        Button save = Ui.primary(this, "Simpan Menu");
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                m.name = name.getText().toString().trim();
                m.description = desc.getText().toString().trim();
                m.price = Fmt.parseLong(price.getText().toString());
                m.category = (String) cat.getSelectedItem();
                String em = emoji.getText().toString().trim();
                m.emoji = em.length() > 0 ? em : "🍽️";
                m.available = avail.isChecked();
                m.recommended = rec.isChecked();
                action("Menyimpan...", new Async.Task<Boolean>() {
                    @Override
                    public Boolean run() throws Exception {
                        String old = m.imageId;
                        m.imageId = applyPhoto(old);
                        repo.saveMenu(m);
                        if (!old.equals(m.imageId)) repo.deleteImage(old);
                        return true;
                    }
                }, new Async.Callback<Boolean>() {
                    @Override
                    public void onResult(Boolean r) {
                        Ui.toast(MenuEditActivity.this, "Menu disimpan");
                        finish();
                    }
                });
            }
        });
        c.addView(save);

        if (m.id.length() > 0) {
            Button del = Ui.outlineButton(this, "Hapus Menu", Ui.RED);
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Ui.confirm(MenuEditActivity.this, "Hapus menu", "Hapus " + m.name + "?", "Hapus", new Runnable() {
                        @Override
                        public void run() {
                            action("Menghapus...", new Async.Task<Boolean>() {
                                @Override
                                public Boolean run() throws Exception {
                                    repo.deleteMenu(m.id);
                                    return true;
                                }
                            }, new Async.Callback<Boolean>() {
                                @Override
                                public void onResult(Boolean r) {
                                    finish();
                                }
                            });
                        }
                    });
                }
            });
            c.addView(del);
        }
    }
}
