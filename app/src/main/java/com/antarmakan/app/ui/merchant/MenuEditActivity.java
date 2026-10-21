package com.antarmakan.app.ui.merchant;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.antarmakan.app.model.MenuItem;
import com.antarmakan.app.util.Async;
import com.antarmakan.app.util.Fmt;
import com.antarmakan.app.util.Ui;

/** Form tambah/ubah menu. */
public class MenuEditActivity extends BaseFormActivity {

    private static final String[] CATEGORIES = {"Makanan", "Minuman", "Lauk", "Paket", "Cemilan"};
    private MenuItem m;
    private LinearLayout optionsBox;

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

        // varian & topping
        c.addView(Ui.divider(this));
        c.addView(Ui.title(this, "Pilihan varian & topping"));
        c.addView(Ui.muted(this, "Contoh: Level pedas (wajib pilih satu), Topping (boleh banyak, berbayar)."));
        optionsBox = Ui.vbox(this);
        c.addView(optionsBox);
        renderOptions();
        TextView addOpt = Ui.smallButton(this, "+ Tambah grup pilihan", Ui.PRIMARY, false);
        addOpt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                optionDialog(null);
            }
        });
        c.addView(addOpt);
        c.addView(Ui.divider(this));

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

    private void renderOptions() {
        optionsBox.removeAllViews();
        for (final com.antarmakan.app.model.MenuOption g : m.options) {
            LinearLayout row = Ui.hbox(this);
            row.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
            LinearLayout info = Ui.vbox(this);
            info.addView(Ui.text(this, g.name, 14, Ui.TEXT, true));
            StringBuilder sb = new StringBuilder(g.typeLabel()).append(": ");
            for (int i = 0; i < g.choices.size(); i++) {
                com.antarmakan.app.model.MenuOption.Choice ch = g.choices.get(i);
                if (i > 0) sb.append(", ");
                sb.append(ch.name);
                if (ch.price > 0) sb.append(" +").append(com.antarmakan.app.util.Fmt.rp(ch.price));
            }
            info.addView(Ui.muted(this, sb.toString()));
            row.addView(info, Ui.weight(1));
            TextView ed = Ui.smallButton(this, "Ubah", Ui.BLUE, false);
            ed.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    optionDialog(g);
                }
            });
            TextView del = Ui.smallButton(this, "Hapus", Ui.RED, false);
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    m.options.remove(g);
                    renderOptions();
                }
            });
            row.addView(ed);
            row.addView(del);
            optionsBox.addView(row, Ui.matchWrap());
        }
        if (m.options.isEmpty()) optionsBox.addView(Ui.muted(this, "Belum ada pilihan."));
    }

    private void optionDialog(final com.antarmakan.app.model.MenuOption existing) {
        LinearLayout box = Ui.vbox(this);
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        box.addView(Ui.label(this, "Nama grup"));
        final EditText name = Ui.edit(this, "Contoh: Level pedas / Topping");
        box.addView(name);
        box.addView(Ui.label(this, "Jenis"));
        final String[] types = {com.antarmakan.app.model.MenuOption.WAJIB_SATU,
                com.antarmakan.app.model.MenuOption.OPSIONAL_SATU, com.antarmakan.app.model.MenuOption.BANYAK};
        final Spinner type = Ui.spinner(this, new String[]{"Wajib pilih satu", "Boleh pilih satu", "Boleh pilih banyak"}, 0);
        box.addView(type);
        box.addView(Ui.label(this, "Pilihan (satu per baris, harga tambahan pakai =)"));
        final EditText choices = Ui.multiline(this, "Tidak pedas\nSedang\nPedas\nTelur=3000");
        choices.setMinLines(4);
        box.addView(choices);
        if (existing != null) {
            name.setText(existing.name);
            type.setSelection(Ui.indexOf(types, existing.type));
            choices.setText(existing.choicesText());
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle(existing == null ? "Tambah grup pilihan" : "Ubah grup pilihan")
                .setView(box)
                .setPositiveButton("Simpan", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        com.antarmakan.app.model.MenuOption g = existing != null ? existing
                                : new com.antarmakan.app.model.MenuOption();
                        g.name = name.getText().toString().trim().replace(";", ",").replace("=", "-");
                        g.type = types[type.getSelectedItemPosition()];
                        g.choices = com.antarmakan.app.model.MenuOption.parseChoices(
                                choices.getText().toString().replace(";", ","));
                        if (g.name.length() == 0 || g.choices.isEmpty()) {
                            Ui.toast(MenuEditActivity.this, "Isi nama grup & minimal satu pilihan");
                            return;
                        }
                        if (existing == null) m.options.add(g);
                        renderOptions();
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }
}
