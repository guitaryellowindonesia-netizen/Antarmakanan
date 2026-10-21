package com.antarmakan.app.ui.merchant;

import android.content.Intent;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.antarmakan.app.model.MenuItem;
import com.antarmakan.app.model.Order;
import com.antarmakan.app.model.Restaurant;
import com.antarmakan.app.model.User;
import com.antarmakan.app.ui.common.ReviewsActivity;
import com.antarmakan.app.ui.common.TabbedActivity;
import com.antarmakan.app.ui.common.Views;
import com.antarmakan.app.util.Async;
import com.antarmakan.app.util.Fmt;
import com.antarmakan.app.util.Ui;

import java.util.Calendar;
import java.util.List;

/** Halaman Mitra Resto: Pesanan, Menu, Toko, Akun. */
public class MerchantMainActivity extends TabbedActivity {

    private static final int TAB_ORDERS = 0, TAB_MENU = 1, TAB_STORE = 2, TAB_ACCOUNT = 3;

    @Override
    protected String[] tabLabels() {
        return new String[]{"Pesanan", "Menu", "Toko", "Akun"};
    }

    @Override
    protected String[] tabIcons() {
        return new String[]{"🧾", "🍽️", "🏪", "👤"};
    }

    @Override
    protected long pollFor(int tab) {
        return tab == TAB_ORDERS ? 6000 : 0;
    }

    @Override
    protected void renderTab(int tab) {
        if (tab == TAB_ORDERS) loadOrders();
        else if (tab == TAB_MENU) loadMenu();
        else if (tab == TAB_STORE) loadStore();
        else loadAccount();
    }

    private static class Data {
        User me;
        Restaurant resto;
        List<Order> orders;
        List<MenuItem> menus;
    }

    /** Tampilan jika mitra belum punya toko */
    private void noStore(int tab) {
        LinearLayout page = Ui.vbox(this);
        page.addView(Views.header(this, "Belum ada toko", null));
        page.addView(Ui.empty(this, "Akun mitra ini belum memiliki toko."));
        LinearLayout box = Ui.vbox(this);
        box.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        android.widget.Button b = Ui.primary(this, "Buat Toko");
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MerchantMainActivity.this, RestoEditActivity.class));
            }
        });
        box.addView(b);
        page.addView(box);
        show(tab, page);
    }

    private LinearLayout pendingBanner(Restaurant r) {
        LinearLayout c = Ui.card(this);
        c.setBackground(Ui.rounded(android.graphics.Color.parseColor("#FFF3E0"), Ui.dp(this, 12)));
        c.addView(Ui.text(this, "⏳ Toko menunggu persetujuan admin", 14, Ui.ACCENT, true));
        c.addView(Ui.muted(this, "Sambil menunggu, lengkapi menu toko Anda."));
        return c;
    }

    // ------------------------------------------------------------------ pesanan

    private void loadOrders() {
        load(new Async.Task<Data>() {
            @Override
            public Data run() throws Exception {
                Data d = new Data();
                d.me = repo.me();
                d.resto = repo.restoOfOwner(d.me.id);
                if (d.resto != null) d.orders = repo.ordersOfResto(d.resto.id);
                return d;
            }
        }, new Async.Callback<Data>() {
            @Override
            public void onResult(Data d) {
                if (d.resto == null) {
                    noStore(TAB_ORDERS);
                    return;
                }
                LinearLayout page = Ui.vbox(MerchantMainActivity.this);
                page.addView(Views.header(MerchantMainActivity.this, d.resto.emoji + " " + d.resto.name,
                        d.resto.isOpenNow() ? "🟢 Toko buka" : "🔴 Toko tutup"));
                if (!d.resto.active) page.addView(pendingBanner(d.resto));

                int pending = 0;
                LinearLayout fresh = Ui.vbox(MerchantMainActivity.this);
                LinearLayout process = Ui.vbox(MerchantMainActivity.this);
                LinearLayout history = Ui.vbox(MerchantMainActivity.this);
                int hist = 0;
                for (Order o : d.orders) {
                    if (o.status == Order.MENUNGGU) {
                        fresh.addView(Views.orderCard(MerchantMainActivity.this, o, User.ROLE_MERCHANT));
                        pending++;
                    } else if (o.isActive()) {
                        process.addView(Views.orderCard(MerchantMainActivity.this, o, User.ROLE_MERCHANT));
                    } else if (hist++ < 30) {
                        history.addView(Views.orderCard(MerchantMainActivity.this, o, User.ROLE_MERCHANT));
                    }
                }
                page.addView(Ui.section(MerchantMainActivity.this, "🔔 Pesanan baru (" + pending + ")"));
                if (pending == 0) page.addView(Ui.muted(MerchantMainActivity.this, "      Belum ada pesanan baru"));
                page.addView(fresh);
                page.addView(Ui.section(MerchantMainActivity.this, "👨‍🍳 Sedang diproses"));
                if (process.getChildCount() == 0) {
                    page.addView(Ui.muted(MerchantMainActivity.this, "      Tidak ada"));
                }
                page.addView(process);
                if (history.getChildCount() > 0) {
                    page.addView(Ui.section(MerchantMainActivity.this, "Riwayat"));
                    page.addView(history);
                }
                show(TAB_ORDERS, page);
            }
        });
    }

    // ------------------------------------------------------------------ menu

    private void loadMenu() {
        load(new Async.Task<Data>() {
            @Override
            public Data run() throws Exception {
                Data d = new Data();
                d.me = repo.me();
                d.resto = repo.restoOfOwner(d.me.id);
                if (d.resto != null) d.menus = repo.menus(d.resto.id);
                return d;
            }
        }, new Async.Callback<Data>() {
            @Override
            public void onResult(final Data d) {
                if (d.resto == null) {
                    noStore(TAB_MENU);
                    return;
                }
                LinearLayout page = Ui.vbox(MerchantMainActivity.this);
                page.addView(Views.header(MerchantMainActivity.this, "Menu Toko", d.menus.size() + " menu"));
                LinearLayout box = Ui.vbox(MerchantMainActivity.this);
                box.setPadding(Ui.dp(MerchantMainActivity.this, 12), 0, Ui.dp(MerchantMainActivity.this, 12), 0);
                android.widget.Button add = Ui.primary(MerchantMainActivity.this, "+ Tambah Menu");
                add.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openMenuEditor(d.resto.id, null);
                    }
                });
                box.addView(add);
                page.addView(box);
                for (final MenuItem m : d.menus) page.addView(menuCard(d.resto.id, m));
                if (d.menus.isEmpty()) page.addView(Ui.empty(MerchantMainActivity.this, "Belum ada menu"));
                show(TAB_MENU, page);
            }
        });
    }

    private View menuCard(final String restoId, final MenuItem m) {
        LinearLayout card = Ui.card(this);
        LinearLayout row = Ui.hbox(this);
        row.addView(Ui.photo(this, m.imageId, m.emoji, 56));
        LinearLayout info = Ui.vbox(this);
        info.addView(Ui.text(this, (m.recommended ? "⭐ " : "") + m.name, 15, Ui.TEXT, true));
        info.addView(Ui.muted(this, m.category + " • " + Fmt.rp(m.price)));
        row.addView(info, Ui.weight(1));
        final TextView toggle = Ui.smallButton(this, m.available ? "Tersedia" : "Habis",
                m.available ? Ui.PRIMARY : Ui.RED, false);
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                m.available = !m.available;
                action("Menyimpan...", new Async.Task<Boolean>() {
                    @Override
                    public Boolean run() throws Exception {
                        repo.saveMenu(m);
                        return true;
                    }
                }, new Async.Callback<Boolean>() {
                    @Override
                    public void onResult(Boolean r) {
                        loadMenu();
                    }
                });
            }
        });
        row.addView(toggle);
        card.addView(row);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openMenuEditor(restoId, m.id);
            }
        });
        return card;
    }

    private void openMenuEditor(String restoId, String menuId) {
        Intent i = new Intent(this, MenuEditActivity.class);
        i.putExtra("restoId", restoId);
        if (menuId != null) i.putExtra("menuId", menuId);
        startActivity(i);
    }

    // ------------------------------------------------------------------ toko

    private void loadStore() {
        load(new Async.Task<Data>() {
            @Override
            public Data run() throws Exception {
                Data d = new Data();
                d.me = repo.me();
                d.resto = repo.restoOfOwner(d.me.id);
                if (d.resto != null) d.orders = repo.ordersOfResto(d.resto.id);
                return d;
            }
        }, new Async.Callback<Data>() {
            @Override
            public void onResult(final Data d) {
                if (d.resto == null) {
                    noStore(TAB_STORE);
                    return;
                }
                final Restaurant r = d.resto;
                LinearLayout page = Ui.vbox(MerchantMainActivity.this);
                page.addView(Views.header(MerchantMainActivity.this, r.emoji + " " + r.name,
                        "Kec. " + r.kecamatan + " • " + r.openHour + "-" + r.closeHour));
                if (!r.active) page.addView(pendingBanner(r));
                if (!com.antarmakan.app.util.Geo.has(r.lat, r.lng)) {
                    LinearLayout w = Ui.card(MerchantMainActivity.this);
                    w.setBackground(Ui.rounded(android.graphics.Color.parseColor("#FFF3E0"),
                            Ui.dp(MerchantMainActivity.this, 12)));
                    w.addView(Ui.text(MerchantMainActivity.this, "📍 Titik lokasi resto belum diatur", 14, Ui.ACCENT, true));
                    w.addView(Ui.muted(MerchantMainActivity.this,
                            "Atur di \"Ubah info toko\" agar ongkir akurat & driver mudah menemukan resto."));
                    page.addView(w);
                }

                LinearLayout sw = Ui.card(MerchantMainActivity.this);
                LinearLayout row = Ui.hbox(MerchantMainActivity.this);
                LinearLayout info = Ui.vbox(MerchantMainActivity.this);
                info.addView(Ui.title(MerchantMainActivity.this, r.isOpen ? "Toko BUKA" : "Toko TUTUP"));
                info.addView(Ui.muted(MerchantMainActivity.this,
                        r.isOpen && r.isHolidayToday() ? "Hari ini jadwal LIBUR, pelanggan melihat toko libur"
                        : r.isOpen && !r.isOpenNow() ? "Di luar jam buka, pelanggan melihat toko tutup"
                                : "Matikan jika ingin tutup sementara"));
                row.addView(info, Ui.weight(1));
                Switch s = new Switch(MerchantMainActivity.this);
                s.setChecked(r.isOpen);
                s.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, final boolean on) {
                        action("Menyimpan...", new Async.Task<Boolean>() {
                            @Override
                            public Boolean run() throws Exception {
                                repo.setRestoOpen(r.id, on);
                                return true;
                            }
                        }, new Async.Callback<Boolean>() {
                            @Override
                            public void onResult(Boolean x) {
                                if (on) com.antarmakan.app.notif.NotifCenter.ensureService(MerchantMainActivity.this);
                                loadStore();
                            }
                        });
                    }
                });
                row.addView(s);
                sw.addView(row);

                // antar sendiri
                sw.addView(Ui.divider(MerchantMainActivity.this));
                LinearLayout row2 = Ui.hbox(MerchantMainActivity.this);
                LinearLayout info2 = Ui.vbox(MerchantMainActivity.this);
                info2.addView(Ui.title(MerchantMainActivity.this, "🛵 Antar sendiri"));
                info2.addView(Ui.muted(MerchantMainActivity.this, r.selfDelivery
                        ? "Pesanan Anda antar sendiri, ongkir 100% untuk Anda"
                        : "Nyalakan jika ingin mengantar sendiri (tanpa driver)"));
                row2.addView(info2, Ui.weight(1));
                Switch s2 = new Switch(MerchantMainActivity.this);
                s2.setChecked(r.selfDelivery);
                s2.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, final boolean on) {
                        action("Menyimpan...", new Async.Task<Boolean>() {
                            @Override
                            public Boolean run() throws Exception {
                                repo.setRestoSelfDelivery(r.id, on);
                                return true;
                            }
                        }, new Async.Callback<Boolean>() {
                            @Override
                            public void onResult(Boolean x) {
                                loadStore();
                            }
                        });
                    }
                });
                row2.addView(s2);
                sw.addView(row2);
                page.addView(sw);

                // laporan
                long startToday = startOfToday();
                int todayCount = 0, doneCount = 0;
                long todayRevenue = 0, totalRevenue = 0;
                for (Order o : d.orders) {
                    if (o.status != Order.SELESAI) continue;
                    doneCount++;
                    totalRevenue += o.subtotal;
                    if (o.createdAt >= startToday) {
                        todayCount++;
                        todayRevenue += o.subtotal;
                    }
                }
                page.addView(Ui.section(MerchantMainActivity.this, "📊 Laporan Penjualan"));
                page.addView(Views.statRow(MerchantMainActivity.this,
                        Views.stat(MerchantMainActivity.this, "Pesanan hari ini", String.valueOf(todayCount)),
                        Views.stat(MerchantMainActivity.this, "Omzet hari ini", Fmt.rp(todayRevenue))));
                page.addView(Views.statRow(MerchantMainActivity.this,
                        Views.stat(MerchantMainActivity.this, "Total pesanan selesai", String.valueOf(doneCount)),
                        Views.stat(MerchantMainActivity.this, "Total omzet", Fmt.rp(totalRevenue))));
                page.addView(Views.statRow(MerchantMainActivity.this,
                        Views.stat(MerchantMainActivity.this, "Rating", r.ratingCount > 0
                                ? "⭐ " + Fmt.rating(r.rating()) : "-"),
                        Views.stat(MerchantMainActivity.this, "Saldo", Fmt.rp(d.me.balance))));

                LinearLayout menu = Ui.card(MerchantMainActivity.this);
                menu.addView(Views.menuRow(MerchantMainActivity.this, "✏️", "Ubah info toko", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent i = new Intent(MerchantMainActivity.this, RestoEditActivity.class);
                        i.putExtra("restoId", r.id);
                        startActivity(i);
                    }
                }));
                menu.addView(Views.menuRow(MerchantMainActivity.this, "⭐", "Ulasan pelanggan", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent i = new Intent(MerchantMainActivity.this, ReviewsActivity.class);
                        i.putExtra("restoId", r.id);
                        i.putExtra("name", r.name);
                        startActivity(i);
                    }
                }));
                page.addView(menu);
                show(TAB_STORE, page);
            }
        });
    }

    static long startOfToday() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    // ------------------------------------------------------------------ akun

    private void loadAccount() {
        load(new Async.Task<User>() {
            @Override
            public User run() throws Exception {
                return repo.me();
            }
        }, new Async.Callback<User>() {
            @Override
            public void onResult(User u) {
                LinearLayout page = Ui.vbox(MerchantMainActivity.this);
                page.addView(Views.header(MerchantMainActivity.this, "Akun Mitra", null));
                Views.accountSection(MerchantMainActivity.this, u, page);
                show(TAB_ACCOUNT, page);
            }
        });
    }
}
