package com.antarmakan.app.ui.common;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.antarmakan.app.Config;
import com.antarmakan.app.model.Order;
import com.antarmakan.app.model.OrderItem;
import com.antarmakan.app.model.Restaurant;
import com.antarmakan.app.model.User;
import com.antarmakan.app.ui.customer.RestaurantActivity;
import com.antarmakan.app.util.Fmt;
import com.antarmakan.app.util.Geo;
import com.antarmakan.app.util.Ui;

/** Potongan tampilan yang dipakai di banyak layar. */
public final class Views {
    private Views() {}

    public static LinearLayout restoCard(final Activity a, final Restaurant r, boolean fav) {
        LinearLayout card = Ui.card(a);
        LinearLayout row = Ui.hbox(a);
        row.addView(Ui.photo(a, r.imageId, r.emoji, 72));
        LinearLayout info = Ui.vbox(a);
        LinearLayout top = Ui.hbox(a);
        TextView name = Ui.text(a, r.name, 16, Ui.TEXT, true);
        top.addView(name, Ui.weight(1));
        if (fav) top.addView(Ui.text(a, "❤️", 14, Ui.RED, false));
        info.addView(top);
        info.addView(Ui.muted(a, r.category + " • Kec. " + r.kecamatan));
        String rating = r.ratingCount > 0 ? "⭐ " + Fmt.rating(r.rating()) + " (" + r.ratingCount + ")" : "⭐ Baru";
        info.addView(Ui.text(a, rating + "  •  " + Fmt.km(r.distanceKm) + "  •  "
                + Geo.etaMinutes(r.distanceKm) + " mnt", 13, Ui.TEXT, false));
        LinearLayout bottom = Ui.hbox(a);
        bottom.setPadding(0, Ui.dp(a, 4), 0, 0);
        bottom.addView(Ui.text(a, (r.selfDelivery ? "🛵 Diantar penjual • " : "🛵 Ongkir ")
                + Fmt.rp(r.deliveryFee), 12, Ui.PRIMARY_DARK, true), Ui.weight(1));
        if (!r.active) {
            bottom.addView(Ui.badge(a, "MENUNGGU PERSETUJUAN", Ui.ACCENT));
        } else if (!r.isOpenNow()) {
            bottom.addView(Ui.badge(a, "TUTUP • buka " + r.openHour, Ui.RED));
        } else {
            bottom.addView(Ui.badge(a, "BUKA", Ui.PRIMARY));
        }
        info.addView(bottom, Ui.matchWrap());
        row.addView(info, Ui.weight(1));
        card.addView(row);
        if (!r.isOpenNow()) card.setAlpha(0.65f);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(a, RestaurantActivity.class);
                i.putExtra("id", r.id);
                a.startActivity(i);
            }
        });
        return card;
    }

    /** Kartu ringkas pesanan. perspective = peran yang melihat. */
    public static LinearLayout orderCard(final Activity a, final Order o, String perspective) {
        LinearLayout card = Ui.card(a);
        LinearLayout top = Ui.hbox(a);
        String head;
        if (User.ROLE_MERCHANT.equals(perspective)) head = "👤 " + o.customerName;
        else head = o.restoEmoji + " " + o.restoName;
        top.addView(Ui.text(a, head, 15, Ui.TEXT, true), Ui.weight(1));
        top.addView(Ui.badge(a, Order.statusShort(o.status), Ui.statusColor(o.status)));
        card.addView(top);
        card.addView(Ui.muted(a, o.code + " • " + Fmt.date(o.createdAt)));
        StringBuilder sb = new StringBuilder();
        for (OrderItem it : o.items) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(it.qty).append("x ").append(it.name);
        }
        TextView items = Ui.body(a, sb.toString());
        items.setMaxLines(2);
        items.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 4));
        card.addView(items);
        if (User.ROLE_DRIVER.equals(perspective)) {
            card.addView(Ui.muted(a, "📍 Ambil: " + o.restoName + ", Kec. " + o.restoKecamatan));
            card.addView(Ui.muted(a, "🏠 Antar: " + o.address + ", Kec. " + o.kecamatan));
        }
        LinearLayout bottom = Ui.hbox(a);
        bottom.addView(Ui.text(a, Order.statusLabel(o.status), 12, Ui.statusColor(o.status), false),
                Ui.weight(1));
        String amount;
        if (User.ROLE_DRIVER.equals(perspective)) {
            amount = "Ongkir " + Fmt.rp(o.deliveryFee) + " • " + Fmt.km(o.distanceKm);
        } else if (User.ROLE_MERCHANT.equals(perspective)) {
            amount = Fmt.rp(o.subtotal);
        } else {
            amount = Fmt.rp(o.total);
        }
        bottom.addView(Ui.text(a, amount, 14, Ui.TEXT, true));
        card.addView(bottom, Ui.matchWrap());
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openOrder(a, o.id);
            }
        });
        return card;
    }

    public static void openOrder(Activity a, String orderId) {
        Intent i = new Intent(a, OrderDetailActivity.class);
        i.putExtra("id", orderId);
        a.startActivity(i);
    }

    /** Header hijau berisi sapaan */
    public static LinearLayout header(Activity a, String title, String subtitle) {
        LinearLayout h = Ui.vbox(a);
        h.setBackgroundColor(Ui.PRIMARY);
        h.setPadding(Ui.dp(a, 16), Ui.dp(a, 14), Ui.dp(a, 16), Ui.dp(a, 16));
        h.addView(Ui.text(a, title, 20, Color.WHITE, true));
        if (subtitle != null) h.addView(Ui.text(a, subtitle, 13, Color.parseColor("#E8FFEA"), false));
        return h;
    }

    /** Kotak statistik kecil */
    public static LinearLayout stat(Activity a, String label, String value) {
        LinearLayout box = Ui.vbox(a);
        box.setBackground(Ui.rounded(Color.WHITE, Ui.dp(a, 12)));
        box.setPadding(Ui.dp(a, 12), Ui.dp(a, 10), Ui.dp(a, 12), Ui.dp(a, 10));
        box.addView(Ui.text(a, value, 17, Ui.TEXT, true));
        box.addView(Ui.muted(a, label));
        LinearLayout.LayoutParams p = Ui.weight(1);
        p.setMargins(Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4));
        box.setLayoutParams(p);
        return box;
    }

    public static LinearLayout statRow(Activity a, View... stats) {
        LinearLayout row = Ui.hbox(a);
        row.setPadding(Ui.dp(a, 8), 0, Ui.dp(a, 8), 0);
        for (View v : stats) row.addView(v);
        return row;
    }

    /** Baris menu yang bisa diklik (ikon + teks + panah) */
    public static LinearLayout menuRow(Activity a, String icon, String label, View.OnClickListener l) {
        LinearLayout row = Ui.hbox(a);
        row.setPadding(Ui.dp(a, 4), Ui.dp(a, 12), Ui.dp(a, 4), Ui.dp(a, 12));
        TextView ic = Ui.text(a, icon, 18, Ui.TEXT, false);
        ic.setPadding(0, 0, Ui.dp(a, 14), 0);
        row.addView(ic);
        row.addView(Ui.body(a, label), Ui.weight(1));
        row.addView(Ui.text(a, "›", 20, Ui.MUTED, false));
        row.setOnClickListener(l);
        return row;
    }

    /** Bagian akun (profil, saldo, bantuan, keluar) dipakai semua peran. */
    public static void accountSection(final BaseActivity a, final User u, LinearLayout page) {
        LinearLayout prof = Ui.card(a);
        LinearLayout row = Ui.hbox(a);
        String icon = u.isDriver() ? "🛵" : u.isMerchant() ? "🏪" : u.isAdmin() ? "🛠️" : "🙋";
        row.addView(Ui.emojiIcon(a, icon, 56));
        LinearLayout info = Ui.vbox(a);
        info.addView(Ui.title(a, u.name));
        info.addView(Ui.muted(a, u.phone + " • " + User.roleLabel(u.role)));
        if (u.isDriver() && u.vehicle.length() > 0) info.addView(Ui.muted(a, u.vehicle));
        info.addView(Ui.muted(a, "📍 " + u.address + ", Kec. " + u.kecamatan));
        row.addView(info, Ui.weight(1));
        prof.addView(row);
        page.addView(prof);

        LinearLayout wallet = Ui.card(a);
        LinearLayout wrow = Ui.hbox(a);
        LinearLayout winfo = Ui.vbox(a);
        winfo.addView(Ui.muted(a, "Saldo " + Config.NAMA_APLIKASI));
        winfo.addView(Ui.text(a, Fmt.rp(u.balance), 22, u.balance < 0 ? Ui.RED : Ui.PRIMARY_DARK, true));
        wrow.addView(winfo, Ui.weight(1));
        TextView open = Ui.smallButton(a, "Kelola Saldo", Ui.PRIMARY, true);
        wrow.addView(open);
        wallet.addView(wrow);
        if (u.balance < 0) {
            wallet.addView(Ui.text(a, "Saldo minus karena komisi pesanan tunai. Silakan top up.",
                    12, Ui.RED, false));
        }
        View.OnClickListener toWallet = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                a.startActivity(new Intent(a, WalletActivity.class));
            }
        };
        open.setOnClickListener(toWallet);
        wallet.setOnClickListener(toWallet);
        page.addView(wallet);

        LinearLayout menu = Ui.card(a);
        menu.addView(menuRow(a, "✏️", "Ubah Profil & Alamat", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                a.startActivity(new Intent(a, ProfileActivity.class));
            }
        }));
        menu.addView(menuRow(a, "💬", "Bantuan (WhatsApp CS)", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Ui.whatsapp(a, Config.CS_PHONE, "Halo CS " + Config.NAMA_APLIKASI + ", saya " + u.name
                        + " (" + u.phone + ") butuh bantuan.");
            }
        }));
        menu.addView(menuRow(a, "ℹ️", "Tentang Aplikasi", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new android.app.AlertDialog.Builder(a)
                        .setTitle(Config.NAMA_APLIKASI)
                        .setMessage("Aplikasi pesan-antar makanan khusus " + Config.NAMA_KABUPATEN
                                + ".\n\nMelayani " + Config.KECAMATAN.length + " kecamatan.\nVersi 1.1")
                        .setPositiveButton("OK", null)
                        .show();
            }
        }));
        if (!u.isAdmin()) {
            menu.addView(menuRow(a, "🗑️", "Hapus Akun", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Ui.prompt(a, "Hapus akun permanen?",
                            "Ketik HAPUS untuk konfirmasi", "", android.text.InputType.TYPE_CLASS_TEXT
                                    | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, new Ui.TextCallback() {
                                @Override
                                public void onText(String text) {
                                    if (!"HAPUS".equalsIgnoreCase(text.trim())) {
                                        Ui.toast(a, "Akun tidak dihapus");
                                        return;
                                    }
                                    a.action("Menghapus akun...", new com.antarmakan.app.util.Async.Task<Boolean>() {
                                        @Override
                                        public Boolean run() throws Exception {
                                            a.repo.deleteAccount();
                                            return true;
                                        }
                                    }, new com.antarmakan.app.util.Async.Callback<Boolean>() {
                                        @Override
                                        public void onResult(Boolean r) {
                                            com.antarmakan.app.notif.NotifCenter.stopService(a);
                                            Ui.toast(a, "Akun Anda sudah dihapus");
                                            Nav.login(a);
                                        }
                                    });
                                }
                            });
                }
            }));
        }
        menu.addView(menuRow(a, "🚪", "Keluar", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Ui.confirm(a, "Keluar", "Keluar dari akun ini?", "Keluar", new Runnable() {
                    @Override
                    public void run() {
                        final String uid = u.id;
                        final boolean driver = u.isDriver();
                        com.antarmakan.app.util.Async.run(a, new com.antarmakan.app.util.Async.Task<Boolean>() {
                            @Override
                            public Boolean run() throws Exception {
                                if (driver) a.repo.setDriverOnline(uid, false);
                                return true;
                            }
                        }, new com.antarmakan.app.util.Async.Callback<Boolean>() {
                            @Override
                            public void onResult(Boolean r) {
                                com.antarmakan.app.notif.NotifCenter.stopService(a);
                                a.repo.logout();
                                Nav.login(a);
                            }
                        }, new com.antarmakan.app.util.Async.ErrorCallback() {
                            @Override
                            public void onError(Exception e) {
                                com.antarmakan.app.notif.NotifCenter.stopService(a);
                                a.repo.logout();
                                Nav.login(a);
                            }
                        });
                    }
                });
            }
        }));
        page.addView(menu);
    }

    /** Teks di tengah untuk keadaan kosong */
    public static TextView center(Activity a, String s) {
        TextView t = Ui.empty(a, s);
        t.setGravity(Gravity.CENTER);
        return t;
    }
}
