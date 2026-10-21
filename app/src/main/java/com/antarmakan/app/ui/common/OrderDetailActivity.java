package com.antarmakan.app.ui.common;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;

import com.antarmakan.app.model.Order;
import com.antarmakan.app.model.OrderItem;
import com.antarmakan.app.model.User;
import com.antarmakan.app.util.Async;
import com.antarmakan.app.util.Fmt;
import com.antarmakan.app.util.Geo;
import com.antarmakan.app.util.MapUtil;
import com.antarmakan.app.util.Ui;

import org.json.JSONObject;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.ArrayList;
import java.util.List;

/** Detail & pelacakan pesanan. Tombol aksi menyesuaikan peran pengguna. */
public class OrderDetailActivity extends BaseActivity {

    private String orderId;
    private LinearLayout page;
    private Order order;
    private User me;

    // peta pelacakan
    private MapView map;
    private Marker courier;
    private TextView trackInfo;
    private JSONObject lastTrack;
    private boolean mapFitted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        orderId = getIntent().getStringExtra("id");
        enableBack("Detail Pesanan");
        page = scrollPage();
        page.addView(Ui.empty(this, "Memuat..."));
        setPolling(5000);
    }

    @Override
    protected void refresh() {
        load(new Async.Task<Object[]>() {
            @Override
            public Object[] run() throws Exception {
                Order o = repo.order(orderId);
                JSONObject t = o.isActive() ? repo.tracking(orderId) : null;
                return new Object[]{repo.me(), o, t};
            }
        }, new Async.Callback<Object[]>() {
            @Override
            public void onResult(Object[] r) {
                me = (User) r[0];
                Order o = (Order) r[1];
                lastTrack = (JSONObject) r[2];
                // hanya gambar ulang jika ada perubahan
                if (order == null || order.updatedAt != o.updatedAt || page.getChildCount() <= 1) {
                    order = o;
                    render();
                }
                updateTracking();
            }
        });
    }

    /** Pengantar sedang menangani pesanan ini (posisinya dibagikan). */
    private boolean courierActive() {
        if (!order.isActive()) return false;
        if (order.selfDelivery) return order.status == Order.DIANTAR;
        return order.hasDriver();
    }

    private boolean iAmCourier() {
        return (isDriver() && me.id.equals(order.driverId))
                || (isMerchant() && order.selfDelivery);
    }

    /** Kartu peta: resto 🏪, tujuan 🏠, pengantar 🛵. */
    private void addMapCard() {
        map = null;
        courier = null;
        trackInfo = null;
        mapFitted = false;
        if (!order.isActive() || !Geo.has(order.custLat, order.custLng)) return;
        LinearLayout card = Ui.card(this);
        card.addView(Ui.label(this, courierActive() ? "LACAK PENGANTAR" : "LOKASI PENGANTARAN"));
        map = MapUtil.create(this);
        card.addView(map, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 260)));
        MapUtil.marker(map, "🏠", "Tujuan: " + order.customerName, order.custLat, order.custLng);
        if (Geo.has(order.restoLat, order.restoLng)) {
            MapUtil.marker(map, "🏪", order.restoName, order.restoLat, order.restoLng);
        }
        map.getController().setZoom(15.0);
        map.getController().setCenter(new GeoPoint(order.custLat, order.custLng));
        trackInfo = Ui.text(this, courierActive() ? "Menunggu lokasi pengantar..." : "", 13, Ui.PRIMARY_DARK, true);
        trackInfo.setPadding(0, Ui.dp(this, 6), 0, 0);
        card.addView(trackInfo);
        page.addView(card);
    }

    private void updateTracking() {
        if (order == null || me == null) return;
        if (iAmCourier() && courierActive()) {
            com.antarmakan.app.notif.TrackingService.start(this);
        }
        if (map == null || !courierActive()) return;
        if (lastTrack == null || !lastTrack.has("lat")) {
            if (trackInfo != null) trackInfo.setText(iAmCourier()
                    ? "📡 Mengirim lokasi Anda..." : "Menunggu lokasi pengantar...");
            return;
        }
        double lat = lastTrack.optDouble("lat"), lng = lastTrack.optDouble("lng");
        if (courier == null) {
            courier = MapUtil.marker(map, "🛵", "Pengantar", lat, lng);
        } else {
            courier.setPosition(new GeoPoint(lat, lng));
        }
        if (!mapFitted) {
            mapFitted = true;
            fitMap(lat, lng);
        }
        map.invalidate();
        double km = Geo.straightKm(lat, lng, order.custLat, order.custLng) * 1.3;
        int min = Math.max(1, (int) Math.ceil(km / 25.0 * 60)); // ±25 km/jam
        long ago = Math.max(0, (System.currentTimeMillis() - lastTrack.optLong("at")) / 1000);
        String when = ago < 90 ? ago + " detik lalu" : (ago / 60) + " menit lalu";
        if (trackInfo != null) {
            trackInfo.setText(order.status == Order.DIANTAR
                    ? "🛵 ± " + Fmt.km(km) + " lagi • tiba ± " + min + " menit (update " + when + ")"
                    : "🛵 Pengantar menuju resto (update " + when + ")");
        }
    }

    private void fitMap(double lat, double lng) {
        List<GeoPoint> pts = new ArrayList<GeoPoint>();
        pts.add(new GeoPoint(order.custLat, order.custLng));
        pts.add(new GeoPoint(lat, lng));
        if (Geo.has(order.restoLat, order.restoLng)) pts.add(new GeoPoint(order.restoLat, order.restoLng));
        try {
            final BoundingBox box = BoundingBox.fromGeoPoints(pts).increaseByScale(1.4f);
            map.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        map.zoomToBoundingBox(box, false);
                    } catch (Exception ignored) {
                    }
                }
            });
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (map != null) map.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (map != null) map.onPause();
    }

    private boolean isCustomer() {
        return me.id.equals(order.customerId);
    }

    private boolean isMerchant() {
        return me.isMerchant() && me.id.equals(order.restoOwnerId);
    }

    private boolean isDriver() {
        return me.isDriver();
    }

    private void render() {
        page.removeAllViews();
        setTitle("Pesanan " + order.code);

        // ---- status
        LinearLayout st = Ui.card(this);
        st.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView big = Ui.text(this, statusEmoji(order.status), 46, Ui.TEXT, false);
        big.setGravity(Gravity.CENTER);
        st.addView(big, Ui.matchWrap());
        TextView label = Ui.text(this, Order.statusLabel(order.status), 18, Ui.statusColor(order.status), true);
        label.setGravity(Gravity.CENTER);
        st.addView(label, Ui.matchWrap());
        if (order.isActive()) {
            long eta = order.createdAt + Geo.etaMinutes(order.distanceKm) * 60000L;
            TextView e = Ui.muted(this, "Perkiraan tiba pukul " + Fmt.time(eta));
            e.setGravity(Gravity.CENTER);
            st.addView(e, Ui.matchWrap());
        }
        if (order.status == Order.DIBATALKAN && order.cancelReason.length() > 0) {
            TextView c = Ui.text(this, order.cancelReason, 13, Ui.RED, false);
            c.setGravity(Gravity.CENTER);
            st.addView(c, Ui.matchWrap());
        }
        if (order.status != Order.DIBATALKAN) {
            st.addView(Ui.divider(this));
            step(st, "Pesanan dibuat", true);
            step(st, "Diterima & dimasak resto", order.status >= Order.DIMASAK);
            if (order.selfDelivery) {
                step(st, "Diantar langsung oleh penjual", order.status >= Order.DIANTAR);
            } else {
                step(st, order.hasDriver() ? "Driver " + order.driverName + " ditugaskan" : "Mencari driver",
                        order.hasDriver());
            }
            step(st, "Makanan diambil & diantar", order.status >= Order.DIANTAR);
            step(st, "Pesanan sampai", order.status == Order.SELESAI);
        }
        page.addView(st);

        // ---- aksi
        LinearLayout actions = Ui.vbox(this);
        actions.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        addActions(actions);
        if (actions.getChildCount() > 0) page.addView(actions);

        // ---- peta
        addMapCard();

        // ---- diantar sendiri oleh penjual
        if (order.selfDelivery) {
            LinearLayout d = Ui.card(this);
            d.addView(Ui.label(this, "PENGANTARAN"));
            d.addView(Ui.text(this, "🛵 Diantar langsung oleh penjual", 15, Ui.TEXT, true));
            if (order.isActive() && isCustomer()) d.addView(contactRow(order.restoPhone, "resto"));
            page.addView(d);
        }

        // ---- driver
        if (order.hasDriver()) {
            LinearLayout d = Ui.card(this);
            d.addView(Ui.label(this, "DRIVER"));
            LinearLayout row = Ui.hbox(this);
            row.addView(Ui.emojiIcon(this, "🛵", 48));
            LinearLayout info = Ui.vbox(this);
            info.addView(Ui.text(this, order.driverName, 15, Ui.TEXT, true));
            info.addView(Ui.muted(this, order.driverVehicle));
            row.addView(info, Ui.weight(1));
            d.addView(row);
            if (order.isActive() && !isDriver()) d.addView(contactRow(order.driverPhone, "driver"));
            page.addView(d);
        }

        // ---- resto
        LinearLayout r = Ui.card(this);
        r.addView(Ui.label(this, "RESTO"));
        r.addView(Ui.text(this, order.restoEmoji + " " + order.restoName, 15, Ui.TEXT, true));
        r.addView(Ui.muted(this, "📍 " + order.restoAddress + ", Kec. " + order.restoKecamatan));
        if (order.isActive() && (isDriver() || me.isAdmin())) {
            LinearLayout b = Ui.hbox(this);
            TextView nav = Ui.smallButton(this, "🧭 Navigasi", Ui.BLUE, false);
            nav.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (Geo.has(order.restoLat, order.restoLng)) {
                        MapUtil.navigate(OrderDetailActivity.this, order.restoLat, order.restoLng);
                    } else {
                        Ui.maps(OrderDetailActivity.this, order.restoName + ", " + order.restoAddress,
                                order.restoKecamatan);
                    }
                }
            });
            b.addView(nav);
            TextView call = Ui.smallButton(this, "📞 Telepon", Ui.PRIMARY, false);
            call.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Ui.call(OrderDetailActivity.this, order.restoPhone);
                }
            });
            b.addView(call);
            r.addView(b);
        }
        page.addView(r);

        // ---- pengiriman
        LinearLayout c = Ui.card(this);
        c.addView(Ui.label(this, "ANTAR KE"));
        c.addView(Ui.text(this, order.customerName, 15, Ui.TEXT, true));
        c.addView(Ui.muted(this, "🏠 " + order.address + ", Kec. " + order.kecamatan));
        c.addView(Ui.muted(this, "Jarak ± " + Fmt.km(order.distanceKm)));
        if (order.note.length() > 0) c.addView(Ui.body(this, "📝 " + order.note));
        if (order.isActive() && !isCustomer()) {
            if ((isDriver() && order.status >= Order.DIJEMPUT)
                    || (isMerchant() && order.selfDelivery && order.status == Order.DIANTAR)) {
                TextView nav = Ui.smallButton(this, "🧭 Navigasi ke pelanggan", Ui.BLUE, false);
                nav.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (Geo.has(order.custLat, order.custLng)) {
                            MapUtil.navigate(OrderDetailActivity.this, order.custLat, order.custLng);
                        } else {
                            Ui.maps(OrderDetailActivity.this, order.address, order.kecamatan);
                        }
                    }
                });
                c.addView(nav);
            }
            c.addView(contactRow(order.customerPhone, "pelanggan"));
        }
        page.addView(c);

        // ---- item
        LinearLayout it = Ui.card(this);
        it.addView(Ui.label(this, "PESANAN (" + order.itemCount() + " item)"));
        for (OrderItem i : order.items) {
            Ui.keyValue(this, it, i.qty + "x  " + i.name, Fmt.rp(i.price * i.qty), false);
            if (i.options.length() > 0) {
                it.addView(Ui.text(this, "     • " + com.antarmakan.app.model.MenuOption.describe(i.options),
                        12, Ui.MUTED, false));
            }
            if (i.note.length() > 0) {
                TextView n = Ui.text(this, "     📝 " + i.note, 12, Ui.ACCENT, false);
                it.addView(n);
            }
        }
        it.addView(Ui.divider(this));
        Ui.keyValue(this, it, "Harga makanan", Fmt.rp(order.subtotal), false);
        Ui.keyValue(this, it, "Ongkir", Fmt.rp(order.deliveryFee), false);
        Ui.keyValue(this, it, "Biaya layanan", Fmt.rp(order.serviceFee), false);
        if (order.discount > 0) {
            Ui.keyValue(this, it, "Diskon (" + order.promoCode + ")", "-" + Fmt.rp(order.discount), false);
        }
        Ui.keyValue(this, it, "Total", Fmt.rp(order.total), true);
        Ui.keyValue(this, it, "Pembayaran",
                Order.BAYAR_SALDO.equals(order.paymentMethod) ? "Saldo (lunas)" : "Tunai ke driver", false);
        it.addView(Ui.muted(this, "Dipesan " + Fmt.date(order.createdAt)));
        page.addView(it);
    }

    private void step(LinearLayout parent, String text, boolean done) {
        LinearLayout row = Ui.hbox(this);
        row.setPadding(0, Ui.dp(this, 3), 0, Ui.dp(this, 3));
        TextView dot = Ui.text(this, done ? "✅" : "⚪", 14, Ui.TEXT, false);
        dot.setPadding(0, 0, Ui.dp(this, 10), 0);
        row.addView(dot);
        row.addView(Ui.text(this, text, 14, done ? Ui.TEXT : Ui.MUTED, done));
        parent.addView(row, Ui.matchWrap());
    }

    private View contactRow(final String phone, final String who) {
        LinearLayout b = Ui.hbox(this);
        TextView call = Ui.smallButton(this, "📞 Telepon", Ui.PRIMARY, false);
        call.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Ui.call(OrderDetailActivity.this, phone);
            }
        });
        TextView wa = Ui.smallButton(this, "WhatsApp", Ui.PRIMARY, false);
        wa.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Ui.whatsapp(OrderDetailActivity.this, phone, "Halo, terkait pesanan " + order.code + " ");
            }
        });
        TextView chat = Ui.smallButton(this, "💬 Chat", Ui.PRIMARY, true);
        chat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openChat();
            }
        });
        b.addView(call);
        b.addView(wa);
        b.addView(chat);
        return b;
    }

    private void openChat() {
        Intent i = new Intent(this, ChatActivity.class);
        i.putExtra("orderId", order.id);
        i.putExtra("title", "Chat " + order.code);
        startActivity(i);
    }

    private static String statusEmoji(int s) {
        switch (s) {
            case Order.MENUNGGU: return "⏳";
            case Order.DIMASAK: return "👨‍🍳";
            case Order.SIAP: return "🥡";
            case Order.DIJEMPUT: return "🛵";
            case Order.DIANTAR: return "🛵💨";
            case Order.SELESAI: return "🎉";
            default: return "❌";
        }
    }

    // ------------------------------------------------------------------ aksi

    private Button btn(LinearLayout parent, String label, int color, boolean filled) {
        Button b = filled ? Ui.button(this, label, color) : Ui.outlineButton(this, label, color);
        parent.addView(b);
        return b;
    }

    private void addActions(LinearLayout a) {
        final int s = order.status;

        if (isCustomer()) {
            if (s == Order.MENUNGGU) {
                btn(a, "Batalkan Pesanan", Ui.RED, false).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Ui.confirm(OrderDetailActivity.this, "Batalkan pesanan?",
                                "Pesanan yang belum diterima resto bisa dibatalkan.", "Ya, batalkan",
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        doAction(new Async.Task<Order>() {
                                            @Override
                                            public Order run() throws Exception {
                                                return repo.customerCancel(order.id);
                                            }
                                        }, "Pesanan dibatalkan");
                                    }
                                });
                    }
                });
            }
            if (com.antarmakan.app.data.Repo.canCancelNoDriver(order)) {
                btn(a, "Batalkan (belum ada driver)", Ui.RED, false).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Ui.confirm(OrderDetailActivity.this, "Batalkan pesanan?",
                                "Belum ada driver yang mengambil pesanan ini. Dana saldo akan dikembalikan.",
                                "Ya, batalkan", new Runnable() {
                                    @Override
                                    public void run() {
                                        doAction(new Async.Task<Order>() {
                                            @Override
                                            public Order run() throws Exception {
                                                return repo.customerCancelNoDriver(order.id);
                                            }
                                        }, "Pesanan dibatalkan");
                                    }
                                });
                    }
                });
            }
            if (order.isActive() && order.hasDriver()) {
                btn(a, "💬 Chat Driver", Ui.PRIMARY, false).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openChat();
                    }
                });
            }
            if (s == Order.SELESAI || s == Order.DIANTAR) {
                btn(a, "⚠️ Laporkan Masalah", Ui.ACCENT, false).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent i = new Intent(OrderDetailActivity.this,
                                com.antarmakan.app.ui.customer.ComplaintFormActivity.class);
                        i.putExtra("orderId", order.id);
                        startActivity(i);
                    }
                });
            }
            if (!order.isActive()) {
                btn(a, "🔁 Pesan Lagi", Ui.PRIMARY, true).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        action("Menyiapkan keranjang...", new Async.Task<Integer>() {
                            @Override
                            public Integer run() throws Exception {
                                return repo.reorder(order, com.antarmakan.app.util.Cart.get(OrderDetailActivity.this));
                            }
                        }, new Async.Callback<Integer>() {
                            @Override
                            public void onResult(Integer skipped) {
                                if (skipped > 0) {
                                    Ui.toast(OrderDetailActivity.this, skipped + " menu sedang tidak tersedia, dilewati");
                                }
                                startActivity(new Intent(OrderDetailActivity.this,
                                        com.antarmakan.app.ui.customer.CartActivity.class));
                            }
                        });
                    }
                });
            }
            if (s == Order.SELESAI && !order.rated) {
                btn(a, "⭐ Beri Ulasan", Ui.ACCENT, true).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        reviewDialog();
                    }
                });
            }
        }

        if (isMerchant()) {
            if (s == Order.MENUNGGU) {
                btn(a, "✅ Terima Pesanan", Ui.PRIMARY, true).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        doAction(new Async.Task<Order>() {
                            @Override
                            public Order run() throws Exception {
                                return repo.restoAccept(order.id);
                            }
                        }, "Pesanan diterima, silakan dimasak");
                    }
                });
                btn(a, "Tolak Pesanan", Ui.RED, false).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final String[] reasons = {"Menu habis", "Resto terlalu ramai", "Resto akan tutup", "Lainnya"};
                        Ui.choose(OrderDetailActivity.this, "Alasan menolak", reasons, new Ui.ChoiceCallback() {
                            @Override
                            public void onChoice(final int i) {
                                doAction(new Async.Task<Order>() {
                                    @Override
                                    public Order run() throws Exception {
                                        return repo.restoReject(order.id, reasons[i]);
                                    }
                                }, "Pesanan ditolak");
                            }
                        });
                    }
                });
            }
            if (order.selfDelivery && (s == Order.DIMASAK || s == Order.SIAP)) {
                btn(a, "🛵 Mulai Antar", Ui.PRIMARY, true).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        doAction(new Async.Task<Order>() {
                            @Override
                            public Order run() throws Exception {
                                return repo.restoStartDelivery(order.id);
                            }
                        }, "Hati-hati di jalan!");
                    }
                });
            }
            if (order.selfDelivery && s == Order.DIANTAR) {
                String cash = Order.BAYAR_TUNAI.equals(order.paymentMethod)
                        ? "\n\nTagih TUNAI ke pelanggan: " + Fmt.rp(order.total) : "\n\nSudah dibayar via saldo.";
                final String msg = "Pastikan pesanan sudah diterima pelanggan." + cash;
                btn(a, "🎉 Pesanan Sudah Sampai", Ui.PRIMARY, true).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Ui.confirm(OrderDetailActivity.this, "Selesaikan pesanan", msg, "Selesai", new Runnable() {
                            @Override
                            public void run() {
                                doAction(new Async.Task<Order>() {
                                    @Override
                                    public Order run() throws Exception {
                                        return repo.restoDelivered(order.id);
                                    }
                                }, "Pesanan selesai. Terima kasih!");
                            }
                        });
                    }
                });
            }
            if (!order.selfDelivery && s == Order.DIMASAK) {
                btn(a, "🥡 Makanan Siap Diambil", Ui.PRIMARY, true).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        doAction(new Async.Task<Order>() {
                            @Override
                            public Order run() throws Exception {
                                return repo.restoReady(order.id);
                            }
                        }, "Driver akan segera mengambil pesanan");
                    }
                });
            }
        }

        if (isDriver()) {
            boolean mine = me.id.equals(order.driverId);
            if (!order.hasDriver() && (s == Order.DIMASAK || s == Order.SIAP)) {
                btn(a, "🛵 Ambil Order Ini", Ui.PRIMARY, true).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        doAction(new Async.Task<Order>() {
                            @Override
                            public Order run() throws Exception {
                                return repo.driverTake(order.id, repo.me());
                            }
                        }, "Order diambil. Segera menuju resto!");
                    }
                });
            }
            if (mine && s == Order.DIMASAK) {
                TextView w = Ui.text(this, "Menunggu resto selesai memasak...", 14, Ui.ACCENT, true);
                w.setGravity(Gravity.CENTER);
                a.addView(w, Ui.matchWrap());
            }
            if (mine && s == Order.DIJEMPUT) {
                btn(a, "📦 Makanan Sudah Diambil", Ui.PRIMARY, true).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        doAction(new Async.Task<Order>() {
                            @Override
                            public Order run() throws Exception {
                                return repo.driverPickedUp(order.id, me.id);
                            }
                        }, "Selamat mengantar, hati-hati di jalan!");
                    }
                });
            }
            if (mine && s == Order.DIANTAR) {
                String cash = Order.BAYAR_TUNAI.equals(order.paymentMethod)
                        ? "\n\nTagih TUNAI ke pelanggan: " + Fmt.rp(order.total) : "\n\nSudah dibayar via saldo.";
                final String msg = "Pastikan pesanan sudah diterima pelanggan." + cash;
                btn(a, "🎉 Pesanan Sudah Sampai", Ui.PRIMARY, true).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Ui.confirm(OrderDetailActivity.this, "Selesaikan pesanan", msg, "Selesai", new Runnable() {
                            @Override
                            public void run() {
                                doAction(new Async.Task<Order>() {
                                    @Override
                                    public Order run() throws Exception {
                                        return repo.driverDelivered(order.id, me.id);
                                    }
                                }, "Pesanan selesai. Terima kasih!");
                            }
                        });
                    }
                });
            }
            if (mine && Order.BAYAR_TUNAI.equals(order.paymentMethod) && order.isActive()) {
                TextView t = Ui.text(this, "💵 Tagih tunai ke pelanggan: " + Fmt.rp(order.total)
                        + "\nBayar ke resto: " + Fmt.rp(order.subtotal), 14, Ui.ACCENT, true);
                t.setBackground(Ui.rounded(Color.parseColor("#FFF3E0"), Ui.dp(this, 10)));
                t.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
                a.addView(t, Ui.margins(this, 0, 6, 0, 6));
            }
        }

        if (me.isAdmin() && order.isActive()) {
            btn(a, "Batalkan (Admin)", Ui.RED, false).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Ui.prompt(OrderDetailActivity.this, "Alasan pembatalan", "Alasan", "",
                            InputType.TYPE_CLASS_TEXT, new Ui.TextCallback() {
                                @Override
                                public void onText(final String text) {
                                    doAction(new Async.Task<Order>() {
                                        @Override
                                        public Order run() throws Exception {
                                            return repo.adminCancel(order.id, text);
                                        }
                                    }, "Pesanan dibatalkan");
                                }
                            });
                }
            });
        }
    }

    private void doAction(Async.Task<Order> task, final String okMsg) {
        action("Memproses...", task, new Async.Callback<Order>() {
            @Override
            public void onResult(Order o) {
                Ui.toast(OrderDetailActivity.this, okMsg);
                order = o;
                render();
            }
        });
    }

    private void reviewDialog() {
        LinearLayout box = Ui.vbox(this);
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        box.addView(Ui.label(this, "Rasa & pelayanan resto"));
        final RatingBar rr = new RatingBar(this);
        rr.setNumStars(5);
        rr.setStepSize(1);
        rr.setRating(5);
        box.addView(rr, Ui.wrap());
        final RatingBar dr = new RatingBar(this);
        if (order.hasDriver()) {
            box.addView(Ui.label(this, "Driver " + order.driverName));
            dr.setNumStars(5);
            dr.setStepSize(1);
            dr.setRating(5);
            box.addView(dr, Ui.wrap());
        }
        final EditText comment = Ui.multiline(this, "Ceritakan pengalamanmu (opsional)");
        box.addView(comment);
        new AlertDialog.Builder(this)
                .setTitle("Beri Ulasan")
                .setView(box)
                .setPositiveButton("Kirim", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        final int r1 = Math.max(1, (int) rr.getRating());
                        final int r2 = order.hasDriver() ? Math.max(1, (int) dr.getRating()) : 0;
                        final String c = comment.getText().toString();
                        action("Mengirim ulasan...", new Async.Task<Boolean>() {
                            @Override
                            public Boolean run() throws Exception {
                                repo.addReview(order.id, r1, r2, c);
                                return true;
                            }
                        }, new Async.Callback<Boolean>() {
                            @Override
                            public void onResult(Boolean ok) {
                                Ui.toast(OrderDetailActivity.this, "Terima kasih atas ulasannya! ⭐");
                                order = null;
                                refresh();
                            }
                        });
                    }
                })
                .setNegativeButton("Nanti", null)
                .show();
    }
}
