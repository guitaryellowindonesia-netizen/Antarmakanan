package com.antarmakan.app.ui.customer;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.antarmakan.app.model.MenuItem;
import com.antarmakan.app.model.Restaurant;
import com.antarmakan.app.model.User;
import com.antarmakan.app.ui.common.BaseActivity;
import com.antarmakan.app.ui.common.ReviewsActivity;
import com.antarmakan.app.util.Async;
import com.antarmakan.app.util.Cart;
import com.antarmakan.app.util.Fmt;
import com.antarmakan.app.util.Geo;
import com.antarmakan.app.util.Ui;

import java.util.List;

/** Detail resto + daftar menu + tambah ke keranjang. */
public class RestaurantActivity extends BaseActivity {

    private String restoId;
    private LinearLayout content;
    private FrameLayout cartBar;
    private Restaurant resto;
    private List<MenuItem> menus;
    private User me;
    private boolean fav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        restoId = getIntent().getStringExtra("id");
        enableBack("Resto");

        LinearLayout root = Ui.vbox(this);
        root.setBackgroundColor(Ui.BG);
        ScrollView sv = new ScrollView(this);
        content = Ui.vbox(this);
        content.setPadding(0, 0, 0, Ui.dp(this, 16));
        content.addView(Ui.empty(this, "Memuat..."));
        sv.addView(content);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        cartBar = new FrameLayout(this);
        root.addView(cartBar, Ui.matchWrap());
        setContentView(root);
    }

    private static class Data {
        User me;
        Restaurant r;
        List<MenuItem> menus;
        boolean fav;
    }

    @Override
    protected void refresh() {
        load(new Async.Task<Data>() {
            @Override
            public Data run() throws Exception {
                Data d = new Data();
                d.me = repo.me();
                android.location.Location l = com.antarmakan.app.util.Loc.lastKnown(RestaurantActivity.this);
                double lat = l != null ? l.getLatitude() : d.me.lat;
                double lng = l != null ? l.getLongitude() : d.me.lng;
                d.r = repo.resto(restoId, d.me.kecamatan, lat, lng);
                d.menus = repo.menus(restoId);
                d.fav = repo.favoriteIds(d.me.id).contains(restoId);
                return d;
            }
        }, new Async.Callback<Data>() {
            @Override
            public void onResult(Data d) {
                me = d.me;
                resto = d.r;
                menus = d.menus;
                fav = d.fav;
                render();
            }
        });
    }

    private void render() {
        setTitle(resto.name);
        content.removeAllViews();

        LinearLayout head = Ui.vbox(this);
        head.setBackgroundColor(Ui.PRIMARY);
        head.setGravity(Gravity.CENTER_HORIZONTAL);
        head.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 40));
        TextView em = Ui.text(this, resto.emoji, 48, Color.WHITE, false);
        em.setGravity(Gravity.CENTER);
        head.addView(em);
        if (resto.imageId.length() > 0) {
            android.widget.FrameLayout cover = Ui.banner(this, resto.imageId, resto.emoji, 200);
            cover.setBackgroundColor(Ui.PRIMARY_LIGHT);
            content.addView(cover);
        } else {
            content.addView(head);
        }

        LinearLayout info = Ui.card(this);
        LinearLayout.LayoutParams lp = Ui.margins(this, 12, -32, 12, 6);
        info.setLayoutParams(lp);
        LinearLayout top = Ui.hbox(this);
        top.addView(Ui.text(this, resto.name, 19, Ui.TEXT, true), Ui.weight(1));
        final TextView favBtn = Ui.text(this, fav ? "❤️" : "🤍", 24, Ui.RED, false);
        favBtn.setPadding(Ui.dp(this, 8), 0, 0, 0);
        favBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleFav(favBtn);
            }
        });
        top.addView(favBtn);
        info.addView(top);
        if (resto.description.length() > 0) info.addView(Ui.muted(this, resto.description));
        info.addView(Ui.muted(this, "📍 " + resto.address + ", Kec. " + resto.kecamatan));
        info.addView(Ui.muted(this, "🕘 Buka " + resto.openHour + " - " + resto.closeHour));
        info.addView(Ui.divider(this));
        LinearLayout stats = Ui.hbox(this);
        TextView rating = Ui.text(this, resto.ratingCount > 0
                ? "⭐ " + Fmt.rating(resto.rating()) + " (" + resto.ratingCount + " ulasan) ›"
                : "⭐ Belum ada ulasan", 13, Ui.TEXT, true);
        rating.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(RestaurantActivity.this, ReviewsActivity.class);
                i.putExtra("restoId", resto.id);
                i.putExtra("name", resto.name);
                startActivity(i);
            }
        });
        stats.addView(rating, Ui.weight(1));
        stats.addView(Ui.text(this, Fmt.km(resto.distanceKm) + " • " + Geo.etaMinutes(resto.distanceKm)
                + " mnt", 13, Ui.TEXT, false));
        info.addView(stats);
        info.addView(Ui.text(this, "🛵 Ongkir ke Kec. " + me.kecamatan + ": " + Fmt.rp(resto.deliveryFee),
                13, Ui.PRIMARY_DARK, true));
        if (resto.selfDelivery) info.addView(Ui.muted(this, "Pesanan diantar langsung oleh penjual"));
        if (!resto.isOpenNow() || !resto.inRange()) {
            String why = resto.isHolidayToday() ? "Resto LIBUR hari ini. Kamu belum bisa memesan."
                    : !resto.inRange() ? "Lokasimu di luar jangkauan antar resto ini (maks "
                    + Fmt.km(resto.effectiveMaxKm()) + ")."
                    : "Resto sedang TUTUP. Kamu belum bisa memesan.";
            TextView closed = Ui.text(this, why, 13, Ui.RED, true);
            closed.setPadding(0, Ui.dp(this, 6), 0, 0);
            info.addView(closed);
        }
        content.addView(info);

        String lastCat = null;
        boolean recShown = false;
        for (MenuItem m : menus) {
            if (m.recommended && !recShown) {
                content.addView(Ui.section(this, "⭐ Rekomendasi"));
                recShown = true;
                lastCat = "__rec";
            } else if (!m.recommended && !m.category.equals(lastCat)) {
                content.addView(Ui.section(this, m.category));
                lastCat = m.category;
            }
            content.addView(menuRow(m));
        }
        if (menus.isEmpty()) content.addView(Ui.empty(this, "Resto belum menambahkan menu"));
        updateCartBar();
    }

    private View menuRow(final MenuItem m) {
        LinearLayout card = Ui.card(this);
        LinearLayout row = Ui.hbox(this);
        row.addView(Ui.photo(this, m.imageId, m.emoji, 72));
        LinearLayout info = Ui.vbox(this);
        info.addView(Ui.text(this, m.name, 15, Ui.TEXT, true));
        if (m.description.length() > 0) info.addView(Ui.muted(this, m.description));
        info.addView(Ui.text(this, Fmt.rp(m.price), 14, Ui.TEXT, true));
        if (m.hasOptions()) info.addView(Ui.text(this, "Ada pilihan varian", 12, Ui.PRIMARY_DARK, false));
        row.addView(info, Ui.weight(1));

        final Cart cart = Cart.get(this);
        int qty = resto.id.equals(cart.getRestoId()) ? cart.qtyOf(m.id) : 0;
        boolean canOrder = m.available && resto.isOpenNow() && resto.inRange();
        if (!m.available) {
            row.addView(Ui.badge(this, "HABIS", Ui.MUTED));
            card.setAlpha(0.6f);
        } else if (qty == 0) {
            TextView add = Ui.smallButton(this, "Tambah", Ui.PRIMARY, false);
            add.setEnabled(canOrder);
            add.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    addToCart(m);
                }
            });
            row.addView(add);
        } else {
            LinearLayout q = Ui.hbox(this);
            TextView minus = Ui.smallButton(this, "−", Ui.PRIMARY, false);
            minus.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cart.removeOne(m.id);
                    render();
                }
            });
            TextView n = Ui.text(this, String.valueOf(qty), 15, Ui.TEXT, true);
            n.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
            TextView plus = Ui.smallButton(this, "+", Ui.PRIMARY, true);
            plus.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    addToCart(m);
                }
            });
            q.addView(minus);
            q.addView(n);
            q.addView(plus);
            row.addView(q);
        }
        card.addView(row);
        return card;
    }

    private void addToCart(final MenuItem m) {
        if (!resto.isOpenNow()) {
            Ui.toast(this, resto.isHolidayToday() ? "Resto libur hari ini" : "Resto sedang tutup");
            return;
        }
        if (!resto.inRange()) {
            Ui.toast(this, "Lokasimu di luar jangkauan antar resto ini");
            return;
        }
        final Cart cart = Cart.get(this);
        if (cart.isFromOtherResto(resto.id)) {
            Ui.confirm(this, "Ganti resto?",
                    "Keranjangmu berisi menu dari " + cart.getRestoName()
                            + ". Kosongkan keranjang dan pesan dari resto ini?",
                    "Ya, ganti", new Runnable() {
                        @Override
                        public void run() {
                            cart.clear();
                            addWithOptions(m);
                        }
                    });
            return;
        }
        addWithOptions(m);
    }

    /** Tambah ke keranjang; menu bervarian menampilkan pilihan dulu. */
    private void addWithOptions(final MenuItem m) {
        if (!m.hasOptions()) {
            Cart.get(this).add(resto.id, resto.name, m);
            render();
            return;
        }
        LinearLayout box = Ui.vbox(this);
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        final java.util.List<Object> controls = new java.util.ArrayList<Object>();
        for (com.antarmakan.app.model.MenuOption g : m.options) {
            box.addView(Ui.text(this, g.name, 15, Ui.TEXT, true));
            box.addView(Ui.muted(this, g.typeLabel()));
            if (com.antarmakan.app.model.MenuOption.BANYAK.equals(g.type)) {
                java.util.List<android.widget.CheckBox> boxes = new java.util.ArrayList<android.widget.CheckBox>();
                for (com.antarmakan.app.model.MenuOption.Choice c : g.choices) {
                    android.widget.CheckBox cb = new android.widget.CheckBox(this);
                    cb.setText(c.name + (c.price > 0 ? "  (+" + Fmt.rp(c.price) + ")" : ""));
                    box.addView(cb);
                    boxes.add(cb);
                }
                controls.add(boxes);
            } else {
                android.widget.RadioGroup rg = new android.widget.RadioGroup(this);
                for (int i = 0; i < g.choices.size(); i++) {
                    com.antarmakan.app.model.MenuOption.Choice c = g.choices.get(i);
                    android.widget.RadioButton rb = new android.widget.RadioButton(this);
                    rb.setId(1000 + i);
                    rb.setText(c.name + (c.price > 0 ? "  (+" + Fmt.rp(c.price) + ")" : ""));
                    rg.addView(rb);
                }
                box.addView(rg);
                controls.add(rg);
            }
            box.addView(Ui.spacer(this, 8));
        }
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(box);
        final android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(this)
                .setTitle(m.name + " • " + Fmt.rp(m.price))
                .setView(sv)
                .setPositiveButton("Tambah ke keranjang", null)
                .setNegativeButton("Batal", null)
                .create();
        dlg.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
            @Override
            @SuppressWarnings("unchecked")
            public void onShow(android.content.DialogInterface d) {
                dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        StringBuilder sel = new StringBuilder();
                        for (int gi = 0; gi < m.options.size(); gi++) {
                            com.antarmakan.app.model.MenuOption g = m.options.get(gi);
                            Object ctl = controls.get(gi);
                            if (ctl instanceof android.widget.RadioGroup) {
                                int id = ((android.widget.RadioGroup) ctl).getCheckedRadioButtonId();
                                if (id >= 1000) {
                                    if (sel.length() > 0) sel.append(';');
                                    sel.append(g.name).append('=').append(g.choices.get(id - 1000).name);
                                }
                            } else {
                                java.util.List<android.widget.CheckBox> boxes = (java.util.List<android.widget.CheckBox>) ctl;
                                for (int ci = 0; ci < boxes.size(); ci++) {
                                    if (boxes.get(ci).isChecked()) {
                                        if (sel.length() > 0) sel.append(';');
                                        sel.append(g.name).append('=').append(g.choices.get(ci).name);
                                    }
                                }
                            }
                        }
                        long extra;
                        try {
                            extra = com.antarmakan.app.model.MenuOption.extraPrice(m.options, sel.toString());
                        } catch (Exception e) {
                            Ui.toast(RestaurantActivity.this, e.getMessage());
                            return;
                        }
                        Cart.get(RestaurantActivity.this).add(resto.id, resto.name, m, sel.toString(), extra);
                        dlg.dismiss();
                        render();
                    }
                });
            }
        });
        dlg.show();
    }

    private void toggleFav(final TextView btn) {
        load(new Async.Task<Boolean>() {
            @Override
            public Boolean run() throws Exception {
                return repo.toggleFavorite(me.id, resto.id);
            }
        }, new Async.Callback<Boolean>() {
            @Override
            public void onResult(Boolean f) {
                fav = f;
                btn.setText(f ? "❤️" : "🤍");
                Ui.toast(RestaurantActivity.this, f ? "Disimpan ke favorit" : "Dihapus dari favorit");
            }
        });
    }

    private void updateCartBar() {
        cartBar.removeAllViews();
        Cart cart = Cart.get(this);
        if (cart.isEmpty()) return;
        LinearLayout bar = Ui.hbox(this);
        bar.setBackground(Ui.rounded(Ui.PRIMARY, Ui.dp(this, 12)));
        bar.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        LinearLayout info = Ui.vbox(this);
        info.addView(Ui.text(this, cart.count() + " item • " + Fmt.rp(cart.subtotal()), 15, Color.WHITE, true));
        info.addView(Ui.text(this, cart.getRestoName(), 12, Color.WHITE, false));
        bar.addView(info, Ui.weight(1));
        bar.addView(Ui.text(this, "Checkout ›", 15, Color.WHITE, true));
        bar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(RestaurantActivity.this, CartActivity.class));
            }
        });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 8));
        cartBar.addView(bar, lp);
    }
}
