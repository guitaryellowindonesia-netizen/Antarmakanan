package com.antarmakan.app.ui.admin;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.antarmakan.app.model.Order;
import com.antarmakan.app.model.Promo;
import com.antarmakan.app.model.Restaurant;
import com.antarmakan.app.model.TopUp;
import com.antarmakan.app.model.User;
import com.antarmakan.app.ui.common.TabbedActivity;
import com.antarmakan.app.ui.common.Views;
import com.antarmakan.app.ui.merchant.RestoEditActivity;
import com.antarmakan.app.util.Async;
import com.antarmakan.app.util.Fmt;
import com.antarmakan.app.util.Ui;

import java.util.Calendar;
import java.util.List;

/** Panel Admin: Dasbor, Resto, Pengguna, Pesanan, Lainnya (promo, saldo, akun). */
public class AdminMainActivity extends TabbedActivity {

    private static final int TAB_DASH = 0, TAB_RESTO = 1, TAB_USERS = 2, TAB_ORDERS = 3, TAB_MORE = 4;
    private String userFilter = "semua";

    @Override
    protected String[] tabLabels() {
        return new String[]{"Dasbor", "Resto", "Pengguna", "Pesanan", "Lainnya"};
    }

    @Override
    protected String[] tabIcons() {
        return new String[]{"📊", "🏪", "👥", "🧾", "⚙️"};
    }

    @Override
    protected long pollFor(int tab) {
        return tab == TAB_ORDERS || tab == TAB_DASH ? 10000 : 0;
    }

    @Override
    protected void renderTab(int tab) {
        if (tab == TAB_DASH) loadDashboard();
        else if (tab == TAB_RESTO) loadRestos();
        else if (tab == TAB_USERS) loadUsers();
        else if (tab == TAB_ORDERS) loadOrders();
        else loadMore();
    }

    private void reload() {
        renderTab(current);
    }

    /** Jalankan aksi admin lalu muat ulang tab. */
    private void exec(final Async.Task<Boolean> task, final String ok) {
        action("Memproses...", task, new Async.Callback<Boolean>() {
            @Override
            public void onResult(Boolean r) {
                if (ok != null) Ui.toast(AdminMainActivity.this, ok);
                reload();
            }
        });
    }

    // ------------------------------------------------------------------ dasbor

    private static class Dash {
        List<Order> orders;
        List<User> users;
        List<Restaurant> restos;
        List<TopUp> topups;
    }

    private void loadDashboard() {
        load(new Async.Task<Dash>() {
            @Override
            public Dash run() throws Exception {
                Dash d = new Dash();
                d.orders = repo.allOrders();
                d.users = repo.users();
                d.restos = repo.restaurants("", true);
                d.topups = repo.topUps(null);
                return d;
            }
        }, new Async.Callback<Dash>() {
            @Override
            public void onResult(Dash d) {
                renderDashboard(d);
            }
        });
    }

    private void renderDashboard(Dash d) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        long today = c.getTimeInMillis();

        int todayOrders = 0, active = 0, done = 0;
        long gmv = 0, gmvToday = 0, revenue = 0;
        for (Order o : d.orders) {
            if (o.createdAt >= today) todayOrders++;
            if (o.isActive()) active++;
            if (o.status == Order.SELESAI) {
                done++;
                gmv += o.total;
                if (o.createdAt >= today) gmvToday += o.total;
                revenue += o.serviceFee - o.discount
                        + o.subtotal * com.antarmakan.app.Config.KOMISI_PERSEN_RESTO / 100
                        + o.deliveryFee * com.antarmakan.app.Config.KOMISI_PERSEN_DRIVER / 100;
            }
        }
        int customers = 0, drivers = 0, driversOnline = 0, pendingDrivers = 0, merchants = 0;
        for (User u : d.users) {
            if (u.isCustomer()) customers++;
            if (u.isMerchant()) merchants++;
            if (u.isDriver()) {
                drivers++;
                if (u.online && u.active) driversOnline++;
                if (!u.active) pendingDrivers++;
            }
        }
        int pendingRestos = 0;
        for (Restaurant r : d.restos) if (!r.active) pendingRestos++;
        int pendingTopups = 0;
        for (TopUp t : d.topups) if (t.status == TopUp.PENDING) pendingTopups++;

        LinearLayout page = Ui.vbox(this);
        page.addView(Views.header(this, "Dasbor Admin", com.antarmakan.app.Config.NAMA_APLIKASI + " • "
                + com.antarmakan.app.Config.NAMA_KABUPATEN));

        if (pendingDrivers + pendingRestos + pendingTopups > 0) {
            LinearLayout w = Ui.card(this);
            w.setBackground(Ui.rounded(android.graphics.Color.parseColor("#FFF3E0"), Ui.dp(this, 12)));
            w.addView(Ui.text(this, "⚠️ Perlu tindakan", 15, Ui.ACCENT, true));
            if (pendingRestos > 0) w.addView(Ui.body(this, "• " + pendingRestos + " resto menunggu persetujuan (tab Resto)"));
            if (pendingDrivers > 0) w.addView(Ui.body(this, "• " + pendingDrivers + " driver menunggu persetujuan (tab Pengguna)"));
            if (pendingTopups > 0) w.addView(Ui.body(this, "• " + pendingTopups + " permintaan saldo (tab Lainnya)"));
            page.addView(w);
        }

        page.addView(Ui.section(this, "Hari ini"));
        page.addView(Views.statRow(this, Views.stat(this, "Pesanan", String.valueOf(todayOrders)),
                Views.stat(this, "Sedang berjalan", String.valueOf(active)),
                Views.stat(this, "Transaksi", Fmt.rp(gmvToday))));
        page.addView(Ui.section(this, "Total"));
        page.addView(Views.statRow(this, Views.stat(this, "Pesanan selesai", String.valueOf(done)),
                Views.stat(this, "Nilai transaksi", Fmt.rp(gmv))));
        page.addView(Views.statRow(this, Views.stat(this, "Pendapatan aplikasi", Fmt.rp(revenue)),
                Views.stat(this, "Resto", String.valueOf(d.restos.size()))));
        page.addView(Views.statRow(this, Views.stat(this, "Pelanggan", String.valueOf(customers)),
                Views.stat(this, "Mitra", String.valueOf(merchants)),
                Views.stat(this, "Driver online", driversOnline + "/" + drivers)));
        show(TAB_DASH, page);
    }

    // ------------------------------------------------------------------ resto

    private void loadRestos() {
        load(new Async.Task<List<Restaurant>>() {
            @Override
            public List<Restaurant> run() throws Exception {
                return repo.restaurants("", true);
            }
        }, new Async.Callback<List<Restaurant>>() {
            @Override
            public void onResult(List<Restaurant> list) {
                LinearLayout page = Ui.vbox(AdminMainActivity.this);
                page.addView(Views.header(AdminMainActivity.this, "Kelola Resto", list.size() + " resto"));
                LinearLayout box = Ui.vbox(AdminMainActivity.this);
                box.setPadding(Ui.dp(AdminMainActivity.this, 12), 0, Ui.dp(AdminMainActivity.this, 12), 0);
                android.widget.Button add = Ui.primary(AdminMainActivity.this, "+ Tambah Resto");
                add.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(new Intent(AdminMainActivity.this, RestoEditActivity.class));
                    }
                });
                box.addView(add);
                page.addView(box);
                for (Restaurant r : list) page.addView(restoAdminCard(r));
                show(TAB_RESTO, page);
            }
        });
    }

    private View restoAdminCard(final Restaurant r) {
        LinearLayout card = Ui.card(this);
        LinearLayout row = Ui.hbox(this);
        row.addView(Ui.emojiIcon(this, r.emoji, 44));
        LinearLayout info = Ui.vbox(this);
        info.addView(Ui.text(this, r.name, 15, Ui.TEXT, true));
        info.addView(Ui.muted(this, r.category + " • Kec. " + r.kecamatan + " • " + r.openHour + "-" + r.closeHour));
        info.addView(Ui.muted(this, (r.ratingCount > 0 ? "⭐ " + Fmt.rating(r.rating()) : "Belum ada rating")
                + (r.ownerId.length() == 0 ? " • tanpa akun mitra" : "")));
        row.addView(info, Ui.weight(1));
        row.addView(Ui.badge(this, r.active ? (r.isOpenNow() ? "BUKA" : "TUTUP") : "PENDING",
                r.active ? (r.isOpenNow() ? Ui.PRIMARY : Ui.MUTED) : Ui.ACCENT));
        card.addView(row);

        LinearLayout actions = Ui.hbox(this);
        TextView approve = Ui.smallButton(this, r.active ? "Nonaktifkan" : "✅ Setujui",
                r.active ? Ui.RED : Ui.PRIMARY, !r.active);
        approve.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exec(new Async.Task<Boolean>() {
                    @Override
                    public Boolean run() throws Exception {
                        repo.setRestoActive(r.id, !r.active);
                        return true;
                    }
                }, r.active ? "Resto dinonaktifkan" : "Resto disetujui");
            }
        });
        actions.addView(approve);
        TextView edit = Ui.smallButton(this, "Ubah", Ui.BLUE, false);
        edit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(AdminMainActivity.this, RestoEditActivity.class);
                i.putExtra("restoId", r.id);
                startActivity(i);
            }
        });
        actions.addView(edit);
        TextView menu = Ui.smallButton(this, "Menu", Ui.BLUE, false);
        menu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(AdminMainActivity.this, AdminMenuActivity.class);
                i.putExtra("restoId", r.id);
                i.putExtra("name", r.name);
                startActivity(i);
            }
        });
        actions.addView(menu);
        TextView del = Ui.smallButton(this, "Hapus", Ui.RED, false);
        del.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Ui.confirm(AdminMainActivity.this, "Hapus resto", "Hapus " + r.name + " beserta menunya?",
                        "Hapus", new Runnable() {
                            @Override
                            public void run() {
                                exec(new Async.Task<Boolean>() {
                                    @Override
                                    public Boolean run() throws Exception {
                                        repo.deleteResto(r.id);
                                        return true;
                                    }
                                }, "Resto dihapus");
                            }
                        });
            }
        });
        actions.addView(del);
        card.addView(Ui.hscroll(this, actions));
        return card;
    }

    // ------------------------------------------------------------------ pengguna

    private void loadUsers() {
        load(new Async.Task<List<User>>() {
            @Override
            public List<User> run() throws Exception {
                return repo.users();
            }
        }, new Async.Callback<List<User>>() {
            @Override
            public void onResult(List<User> list) {
                LinearLayout page = Ui.vbox(AdminMainActivity.this);
                page.addView(Views.header(AdminMainActivity.this, "Pengguna", list.size() + " akun"));
                final String[] filters = {"semua", User.ROLE_CUSTOMER, User.ROLE_MERCHANT, User.ROLE_DRIVER, User.ROLE_ADMIN};
                final String[] labels = {"Semua", "Pelanggan", "Mitra", "Driver", "Admin"};
                LinearLayout chips = Ui.hbox(AdminMainActivity.this);
                for (int i = 0; i < filters.length; i++) {
                    final String f = filters[i];
                    TextView chip = Ui.chip(AdminMainActivity.this, labels[i], f.equals(userFilter));
                    chip.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            userFilter = f;
                            loadUsers();
                        }
                    });
                    chips.addView(chip);
                }
                page.addView(Ui.hscroll(AdminMainActivity.this, chips));
                for (User u : list) {
                    if (!"semua".equals(userFilter) && !userFilter.equals(u.role)) continue;
                    page.addView(userCard(u));
                }
                show(TAB_USERS, page);
            }
        });
    }

    private View userCard(final User u) {
        LinearLayout card = Ui.card(this);
        LinearLayout row = Ui.hbox(this);
        LinearLayout info = Ui.vbox(this);
        info.addView(Ui.text(this, u.name, 15, Ui.TEXT, true));
        info.addView(Ui.muted(this, u.phone + " • " + User.roleLabel(u.role) + " • Kec. " + u.kecamatan));
        if (u.isDriver()) {
            info.addView(Ui.muted(this, u.vehicle + (u.online ? " • 🟢 online" : "")
                    + (u.ratingCount > 0 ? " • ⭐ " + Fmt.rating(u.rating()) : "")));
        }
        info.addView(Ui.muted(this, "Saldo " + Fmt.rp(u.balance)));
        row.addView(info, Ui.weight(1));
        if (!u.active) row.addView(Ui.badge(this, u.isDriver() ? "PENDING" : "NONAKTIF", Ui.ACCENT));
        card.addView(row);

        LinearLayout actions = Ui.hbox(this);
        if (!u.isAdmin()) {
            TextView t = Ui.smallButton(this, u.active ? "Nonaktifkan" : "✅ Aktifkan",
                    u.active ? Ui.RED : Ui.PRIMARY, !u.active);
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    exec(new Async.Task<Boolean>() {
                        @Override
                        public Boolean run() throws Exception {
                            repo.setUserActive(u.id, !u.active);
                            return true;
                        }
                    }, u.active ? "Akun dinonaktifkan" : "Akun diaktifkan");
                }
            });
            actions.addView(t);
        }
        TextView wa = Ui.smallButton(this, "WhatsApp", Ui.PRIMARY, false);
        wa.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Ui.whatsapp(AdminMainActivity.this, u.phone, "Halo " + u.name + ", ");
            }
        });
        actions.addView(wa);
        TextView bal = Ui.smallButton(this, "Atur Saldo", Ui.BLUE, false);
        bal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Ui.prompt(AdminMainActivity.this, "Koreksi saldo " + u.name,
                        "Contoh: 10000 atau -5000", "", android.text.InputType.TYPE_CLASS_NUMBER
                                | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED, new Ui.TextCallback() {
                            @Override
                            public void onText(String text) {
                                final long amount;
                                try {
                                    amount = Long.parseLong(text.trim());
                                } catch (Exception e) {
                                    Ui.toast(AdminMainActivity.this, "Angka tidak valid");
                                    return;
                                }
                                exec(new Async.Task<Boolean>() {
                                    @Override
                                    public Boolean run() throws Exception {
                                        repo.adjustBalance(u.id, amount, "Koreksi saldo oleh admin", true);
                                        return true;
                                    }
                                }, "Saldo diperbarui");
                            }
                        });
            }
        });
        actions.addView(bal);
        card.addView(actions);
        return card;
    }

    // ------------------------------------------------------------------ pesanan

    private void loadOrders() {
        load(new Async.Task<List<Order>>() {
            @Override
            public List<Order> run() throws Exception {
                return repo.allOrders();
            }
        }, new Async.Callback<List<Order>>() {
            @Override
            public void onResult(List<Order> list) {
                LinearLayout page = Ui.vbox(AdminMainActivity.this);
                page.addView(Views.header(AdminMainActivity.this, "Semua Pesanan", list.size() + " pesanan"));
                int n = 0;
                for (Order o : list) {
                    if (n++ >= 100) break;
                    page.addView(Views.orderCard(AdminMainActivity.this, o, User.ROLE_ADMIN));
                }
                if (list.isEmpty()) page.addView(Ui.empty(AdminMainActivity.this, "Belum ada pesanan"));
                show(TAB_ORDERS, page);
            }
        });
    }

    // ------------------------------------------------------------------ lainnya

    private void loadMore() {
        load(new Async.Task<Object[]>() {
            @Override
            public Object[] run() throws Exception {
                return new Object[]{repo.me(), repo.promos(false), repo.topUps(null)};
            }
        }, new Async.Callback<Object[]>() {
            @Override
            @SuppressWarnings("unchecked")
            public void onResult(Object[] r) {
                renderMore((User) r[0], (List<Promo>) r[1], (List<TopUp>) r[2]);
            }
        });
    }

    private void renderMore(User me, List<Promo> promos, List<TopUp> topups) {
        LinearLayout page = Ui.vbox(this);
        page.addView(Views.header(this, "Lainnya", null));

        // permintaan saldo
        page.addView(Ui.section(this, "💰 Permintaan Top Up / Penarikan"));
        int shown = 0;
        for (final TopUp t : topups) {
            if (shown++ >= 30) break;
            LinearLayout c = Ui.card(this);
            LinearLayout row = Ui.hbox(this);
            LinearLayout info = Ui.vbox(this);
            boolean top = TopUp.TYPE_TOPUP.equals(t.type);
            info.addView(Ui.text(this, (top ? "Top up " : "Tarik saldo ") + Fmt.rp(t.amount), 15, Ui.TEXT, true));
            info.addView(Ui.muted(this, t.userName + " • " + t.userPhone));
            info.addView(Ui.muted(this, (top ? "Pengirim: " : "Tujuan: ") + t.info));
            info.addView(Ui.muted(this, Fmt.date(t.createdAt)));
            row.addView(info, Ui.weight(1));
            row.addView(Ui.badge(this, t.statusLabel(), t.status == TopUp.PENDING ? Ui.ACCENT
                    : t.status == TopUp.APPROVED ? Ui.PRIMARY : Ui.RED));
            c.addView(row);
            if (t.status == TopUp.PENDING) {
                LinearLayout a = Ui.hbox(this);
                TextView ok = Ui.smallButton(this, top ? "✅ Dana diterima" : "✅ Sudah ditransfer", Ui.PRIMARY, true);
                ok.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        exec(new Async.Task<Boolean>() {
                            @Override
                            public Boolean run() throws Exception {
                                repo.decideTopUp(t.id, true);
                                return true;
                            }
                        }, "Disetujui");
                    }
                });
                TextView no = Ui.smallButton(this, "Tolak", Ui.RED, false);
                no.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        exec(new Async.Task<Boolean>() {
                            @Override
                            public Boolean run() throws Exception {
                                repo.decideTopUp(t.id, false);
                                return true;
                            }
                        }, "Ditolak");
                    }
                });
                a.addView(ok);
                a.addView(no);
                c.addView(a);
            }
            page.addView(c);
        }
        if (topups.isEmpty()) page.addView(Ui.muted(this, "      Belum ada permintaan"));

        // promo
        page.addView(Ui.section(this, "🎁 Kode Promo"));
        LinearLayout box = Ui.vbox(this);
        box.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        android.widget.Button add = Ui.outlineButton(this, "+ Tambah Promo", Ui.PRIMARY);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                promoDialog(new Promo());
            }
        });
        box.addView(add);
        page.addView(box);
        for (final Promo p : promos) {
            LinearLayout c = Ui.card(this);
            LinearLayout row = Ui.hbox(this);
            LinearLayout info = Ui.vbox(this);
            info.addView(Ui.text(this, p.code, 15, Ui.TEXT, true));
            info.addView(Ui.muted(this, p.typeLabel() + " • min " + Fmt.rp(p.minOrder)
                    + " • kuota " + (p.quota < 0 ? "∞" : String.valueOf(p.quota))));
            if (p.description.length() > 0) info.addView(Ui.muted(this, p.description));
            row.addView(info, Ui.weight(1));
            row.addView(Ui.badge(this, p.active ? "AKTIF" : "MATI", p.active ? Ui.PRIMARY : Ui.MUTED));
            c.addView(row);
            c.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    promoDialog(p);
                }
            });
            page.addView(c);
        }

        Views.accountSection(this, me, page);
        show(TAB_MORE, page);
    }

    private void promoDialog(final Promo p) {
        LinearLayout box = Ui.vbox(this);
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        box.addView(Ui.label(this, "Kode"));
        final EditText code = Ui.edit(this, "HEMAT10", android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        code.setText(p.code);
        box.addView(code);
        box.addView(Ui.label(this, "Deskripsi"));
        final EditText desc = Ui.edit(this, "Keterangan promo");
        desc.setText(p.description);
        box.addView(desc);
        box.addView(Ui.label(this, "Jenis"));
        final String[] types = {Promo.TYPE_POTONGAN, Promo.TYPE_PERSEN, Promo.TYPE_ONGKIR};
        final Spinner type = Ui.spinner(this, new String[]{"Potongan Rp", "Diskon %", "Gratis ongkir (Rp)"},
                Ui.indexOf(types, p.type));
        box.addView(type);
        box.addView(Ui.label(this, "Nilai (Rp atau %)"));
        final EditText value = Ui.number(this, "10");
        if (p.value > 0) value.setText(String.valueOf(p.value));
        box.addView(value);
        box.addView(Ui.label(this, "Maks potongan untuk % (0 = tanpa batas)"));
        final EditText max = Ui.number(this, "0");
        max.setText(String.valueOf(p.maxDiscount));
        box.addView(max);
        box.addView(Ui.label(this, "Minimal belanja (Rp)"));
        final EditText min = Ui.number(this, "0");
        min.setText(String.valueOf(p.minOrder));
        box.addView(min);
        box.addView(Ui.label(this, "Kuota (kosongkan = tanpa batas)"));
        final EditText quota = Ui.number(this, "100");
        if (p.quota >= 0) quota.setText(String.valueOf(p.quota));
        box.addView(quota);
        final CheckBox active = new CheckBox(this);
        active.setText("Aktif");
        active.setChecked(p.active);
        box.addView(active);
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(box);

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(p.id.length() == 0 ? "Tambah Promo" : "Ubah Promo")
                .setView(sv)
                .setPositiveButton("Simpan", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        p.code = code.getText().toString();
                        p.description = desc.getText().toString().trim();
                        p.type = types[type.getSelectedItemPosition()];
                        p.value = Fmt.parseLong(value.getText().toString());
                        p.maxDiscount = Fmt.parseLong(max.getText().toString());
                        p.minOrder = Fmt.parseLong(min.getText().toString());
                        String q = quota.getText().toString().trim();
                        p.quota = q.length() == 0 ? -1 : (int) Fmt.parseLong(q);
                        p.active = active.isChecked();
                        exec(new Async.Task<Boolean>() {
                            @Override
                            public Boolean run() throws Exception {
                                repo.savePromo(p);
                                return true;
                            }
                        }, "Promo disimpan");
                    }
                })
                .setNegativeButton("Batal", null);
        if (p.id.length() > 0) {
            b.setNeutralButton("Hapus", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    exec(new Async.Task<Boolean>() {
                        @Override
                        public Boolean run() throws Exception {
                            repo.deletePromo(p.id);
                            return true;
                        }
                    }, "Promo dihapus");
                }
            });
        }
        b.show();
    }
}
