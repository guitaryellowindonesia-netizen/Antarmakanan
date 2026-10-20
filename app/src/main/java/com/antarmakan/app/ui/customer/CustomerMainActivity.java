package com.antarmakan.app.ui.customer;

import android.content.Intent;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.antarmakan.app.Config;
import com.antarmakan.app.model.MenuItem;
import com.antarmakan.app.model.Order;
import com.antarmakan.app.model.Promo;
import com.antarmakan.app.model.Restaurant;
import com.antarmakan.app.model.User;
import com.antarmakan.app.ui.common.TabbedActivity;
import com.antarmakan.app.ui.common.Views;
import com.antarmakan.app.util.Async;
import com.antarmakan.app.util.Cart;
import com.antarmakan.app.util.Fmt;
import com.antarmakan.app.util.Ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Halaman utama pelanggan: Beranda, Pesanan, Favorit, Akun. */
public class CustomerMainActivity extends TabbedActivity {

    private static final int TAB_HOME = 0, TAB_ORDERS = 1, TAB_FAV = 2, TAB_ACCOUNT = 3;

    // data beranda
    private User me;
    private List<Restaurant> restos = new ArrayList<Restaurant>();
    private Map<String, String> menuText = new HashMap<String, String>();
    private Set<String> favs;
    private String query = "";
    private String category = "Semua";
    private LinearLayout restoList;

    @Override
    protected String[] tabLabels() {
        return new String[]{"Beranda", "Pesanan", "Favorit", "Akun"};
    }

    @Override
    protected String[] tabIcons() {
        return new String[]{"🏠", "🧾", "❤️", "👤"};
    }

    @Override
    protected long pollFor(int tab) {
        return tab == TAB_ORDERS ? 8000 : 0;
    }

    @Override
    protected void renderTab(int tab) {
        updateCartBar();
        if (tab == TAB_HOME) loadHome();
        else if (tab == TAB_ORDERS) loadOrders();
        else if (tab == TAB_FAV) loadFavorites();
        else loadAccount();
    }

    // ------------------------------------------------------------------ beranda

    private static class HomeData {
        User me;
        List<Restaurant> restos;
        List<MenuItem> menus;
        List<Promo> promos;
        List<Order> active = new ArrayList<Order>();
        Set<String> favs;
    }

    private void loadHome() {
        load(new Async.Task<HomeData>() {
            @Override
            public HomeData run() throws Exception {
                HomeData d = new HomeData();
                d.me = repo.me();
                double[] p = here(d.me);
                d.restos = repo.restaurants(d.me.kecamatan, p[0], p[1], false);
                d.menus = repo.allMenus();
                d.promos = repo.promos(true);
                d.favs = repo.favoriteIds(d.me.id);
                for (Order o : repo.ordersOfCustomer(d.me.id)) if (o.isActive()) d.active.add(o);
                return d;
            }
        }, new Async.Callback<HomeData>() {
            @Override
            public void onResult(HomeData d) {
                renderHome(d);
            }
        });
    }

    private void renderHome(HomeData d) {
        if (current != TAB_HOME) return;
        me = d.me;
        restos = d.restos;
        favs = d.favs;
        menuText.clear();
        for (MenuItem m : d.menus) {
            String prev = menuText.get(m.restoId);
            menuText.put(m.restoId, (prev == null ? "" : prev) + " " + m.name.toLowerCase(Locale.US));
        }

        LinearLayout page = Ui.vbox(this);

        // header lokasi
        LinearLayout header = Views.header(this, "Halo, " + firstName(me.name) + " 👋",
                "📍 Antar ke Kec. " + me.kecamatan + " (ketuk untuk ganti)");
        header.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeKecamatan();
            }
        });
        final EditText search = Ui.edit(this, "🔍  Cari resto atau menu...");
        search.setText(query);
        search.setSingleLine(true);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable e) {
                query = e.toString().trim().toLowerCase(Locale.US);
                renderRestoList();
            }
        });
        header.addView(search);
        page.addView(header);

        // pesanan aktif
        for (final Order o : d.active) {
            LinearLayout bar = Ui.card(this);
            bar.setBackground(Ui.rounded(Ui.PRIMARY_LIGHT, Ui.dp(this, 12)));
            LinearLayout r = Ui.hbox(this);
            r.addView(Ui.text(this, "🛵", 26, Ui.TEXT, false));
            LinearLayout info = Ui.vbox(this);
            info.setPadding(Ui.dp(this, 10), 0, 0, 0);
            info.addView(Ui.text(this, Order.statusLabel(o.status), 14, Ui.PRIMARY_DARK, true));
            info.addView(Ui.muted(this, o.restoName + " • " + o.code));
            r.addView(info, Ui.weight(1));
            r.addView(Ui.text(this, "Lacak ›", 13, Ui.PRIMARY_DARK, true));
            bar.addView(r);
            bar.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Views.openOrder(CustomerMainActivity.this, o.id);
                }
            });
            page.addView(bar);
        }

        // promo
        if (!d.promos.isEmpty()) {
            page.addView(Ui.section(this, "🎁 Promo untukmu"));
            LinearLayout promoRow = Ui.hbox(this);
            for (final Promo p : d.promos) {
                LinearLayout pc = Ui.vbox(this);
                pc.setBackground(Ui.rounded(Ui.ACCENT, Ui.dp(this, 12)));
                pc.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
                pc.addView(Ui.text(this, p.code, 16, Color.WHITE, true));
                TextView desc = Ui.text(this, p.description.length() > 0 ? p.description : p.typeLabel(),
                        12, Color.WHITE, false);
                desc.setMaxWidth(Ui.dp(this, 200));
                pc.addView(desc);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 230),
                        LinearLayout.LayoutParams.MATCH_PARENT);
                lp.setMargins(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
                pc.setLayoutParams(lp);
                pc.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Ui.toast(CustomerMainActivity.this, "Pakai kode " + p.code + " saat checkout");
                    }
                });
                promoRow.addView(pc);
            }
            page.addView(Ui.hscroll(this, promoRow));
        }

        // kategori
        page.addView(Ui.section(this, "Kategori"));
        LinearLayout cats = Ui.hbox(this);
        for (int i = 0; i < Config.KATEGORI.length; i++) {
            final String c = Config.KATEGORI[i];
            TextView chip = Ui.chip(this, Config.KATEGORI_EMOJI[i] + " " + c, c.equals(category));
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    category = c;
                    renderHome(lastHome);
                }
            });
            cats.addView(chip);
        }
        page.addView(Ui.hscroll(this, cats));

        page.addView(Ui.section(this, "Resto di sekitarmu"));
        restoList = Ui.vbox(this);
        page.addView(restoList);
        lastHome = d;
        renderRestoList();
        show(TAB_HOME, page);
    }

    private HomeData lastHome;

    private void renderRestoList() {
        if (restoList == null) return;
        restoList.removeAllViews();
        int n = 0;
        for (Restaurant r : restos) {
            if (!"Semua".equals(category) && !category.equals(r.category)) continue;
            if (query.length() > 0) {
                String hay = (r.name + " " + r.category + " " + r.kecamatan).toLowerCase(Locale.US)
                        + (menuText.containsKey(r.id) ? menuText.get(r.id) : "");
                if (!hay.contains(query)) continue;
            }
            restoList.addView(Views.restoCard(this, r, favs != null && favs.contains(r.id)));
            n++;
        }
        if (n == 0) restoList.addView(Ui.empty(this, "Tidak ada resto yang cocok 🍽️"));
    }

    private void changeKecamatan() {
        if (me == null) return;
        Ui.choose(this, "Antar ke kecamatan", Config.namaKecamatan(), new Ui.ChoiceCallback() {
            @Override
            public void onChoice(int index) {
                final String k = Config.KECAMATAN[index][0];
                me.kecamatan = k;
                action("Menyimpan...", new Async.Task<Boolean>() {
                    @Override
                    public Boolean run() throws Exception {
                        repo.saveProfile(me);
                        return true;
                    }
                }, new Async.Callback<Boolean>() {
                    @Override
                    public void onResult(Boolean r) {
                        loadHome();
                    }
                });
            }
        });
    }

    /** Posisi pelanggan: GPS terakhir, cadangannya titik rumah tersimpan. */
    private double[] here(User u) {
        android.location.Location l = com.antarmakan.app.util.Loc.lastKnown(this);
        if (l != null) return new double[]{l.getLatitude(), l.getLongitude()};
        return new double[]{u.lat, u.lng};
    }

    private static String firstName(String n) {
        int i = n.indexOf(' ');
        return i > 0 ? n.substring(0, i) : n;
    }

    // ------------------------------------------------------------------ keranjang

    private void updateCartBar() {
        bottomExtra.removeAllViews();
        final Cart cart = Cart.get(this);
        if (cart.isEmpty() || current == TAB_ACCOUNT || current == TAB_ORDERS) return;
        LinearLayout bar = Ui.hbox(this);
        bar.setBackground(Ui.rounded(Ui.PRIMARY, Ui.dp(this, 12)));
        bar.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        LinearLayout info = Ui.vbox(this);
        info.addView(Ui.text(this, cart.count() + " item • " + Fmt.rp(cart.subtotal()), 15, Color.WHITE, true));
        info.addView(Ui.text(this, cart.getRestoName(), 12, Color.WHITE, false));
        bar.addView(info, Ui.weight(1));
        bar.addView(Ui.text(this, "Keranjang 🛒", 15, Color.WHITE, true));
        bar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(CustomerMainActivity.this, CartActivity.class));
            }
        });
        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        lp.setMargins(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        bottomExtra.addView(bar, lp);
    }

    // ------------------------------------------------------------------ pesanan

    private void loadOrders() {
        load(new Async.Task<List<Order>>() {
            @Override
            public List<Order> run() throws Exception {
                return repo.ordersOfCustomer(repo.me().id);
            }
        }, new Async.Callback<List<Order>>() {
            @Override
            public void onResult(List<Order> orders) {
                LinearLayout page = Ui.vbox(CustomerMainActivity.this);
                page.addView(Views.header(CustomerMainActivity.this, "Pesanan Saya", null));
                if (orders.isEmpty()) {
                    page.addView(Ui.empty(CustomerMainActivity.this,
                            "Belum ada pesanan.\nYuk pesan makanan favoritmu! 🍜"));
                }
                boolean historyLabel = false;
                for (Order o : orders) {
                    if (!o.isActive() && !historyLabel) {
                        page.addView(Ui.section(CustomerMainActivity.this, "Riwayat"));
                        historyLabel = true;
                    }
                    page.addView(Views.orderCard(CustomerMainActivity.this, o, User.ROLE_CUSTOMER));
                }
                show(TAB_ORDERS, page);
            }
        });
    }

    // ------------------------------------------------------------------ favorit

    private void loadFavorites() {
        load(new Async.Task<List<Restaurant>>() {
            @Override
            public List<Restaurant> run() throws Exception {
                User u = repo.me();
                Set<String> f = repo.favoriteIds(u.id);
                List<Restaurant> out = new ArrayList<Restaurant>();
                double[] p = here(u);
                for (Restaurant r : repo.restaurants(u.kecamatan, p[0], p[1], false)) {
                    if (f.contains(r.id)) out.add(r);
                }
                return out;
            }
        }, new Async.Callback<List<Restaurant>>() {
            @Override
            public void onResult(List<Restaurant> list) {
                LinearLayout page = Ui.vbox(CustomerMainActivity.this);
                page.addView(Views.header(CustomerMainActivity.this, "Resto Favorit", null));
                if (list.isEmpty()) {
                    page.addView(Ui.empty(CustomerMainActivity.this,
                            "Belum ada favorit.\nKetuk ❤️ di halaman resto untuk menyimpan."));
                }
                for (Restaurant r : list) page.addView(Views.restoCard(CustomerMainActivity.this, r, true));
                show(TAB_FAV, page);
            }
        });
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
                LinearLayout page = Ui.vbox(CustomerMainActivity.this);
                page.addView(Views.header(CustomerMainActivity.this, "Akun Saya", null));
                Views.accountSection(CustomerMainActivity.this, u, page);
                show(TAB_ACCOUNT, page);
            }
        });
    }
}
