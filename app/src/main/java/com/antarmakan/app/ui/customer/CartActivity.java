package com.antarmakan.app.ui.customer;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import com.antarmakan.app.Config;
import com.antarmakan.app.data.Repo;
import com.antarmakan.app.model.CartItem;
import com.antarmakan.app.model.Order;
import com.antarmakan.app.model.Promo;
import com.antarmakan.app.model.Restaurant;
import com.antarmakan.app.model.User;
import com.antarmakan.app.ui.common.BaseActivity;
import com.antarmakan.app.ui.common.Views;
import com.antarmakan.app.util.Async;
import com.antarmakan.app.util.Cart;
import com.antarmakan.app.util.Fmt;
import com.antarmakan.app.util.Geo;
import com.antarmakan.app.util.Ui;

import java.util.ArrayList;
import java.util.List;

/** Keranjang & checkout. */
public class CartActivity extends BaseActivity {

    private User me;
    private Restaurant resto;
    private List<Promo> promos = new ArrayList<Promo>();

    private LinearLayout itemsBox, summaryBox, promoChips;
    private Spinner kec;
    private double pinLat, pinLng;     // titik antar (GPS)
    private TextView pinInfo;
    private EditText address, note, promoCode;
    private RadioGroup payGroup;
    private TextView promoMsg;
    private String appliedPromo = "";
    private Repo.Quote quote;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enableBack("Keranjang");
        final LinearLayout page = scrollPage();
        page.addView(Ui.empty(this, "Memuat..."));

        final Cart cart = Cart.get(this);
        if (cart.isEmpty()) {
            page.removeAllViews();
            page.addView(Ui.empty(this, "Keranjang masih kosong 🛒"));
            return;
        }
        load(new Async.Task<Object[]>() {
            @Override
            public Object[] run() throws Exception {
                User u = repo.me();
                return new Object[]{u, repo.resto(cart.getRestoId(), u.kecamatan), repo.promos(true)};
            }
        }, new Async.Callback<Object[]>() {
            @Override
            @SuppressWarnings("unchecked")
            public void onResult(Object[] r) {
                me = (User) r[0];
                resto = (Restaurant) r[1];
                promos = (List<Promo>) r[2];
                build(page);
            }
        });
    }

    private void build(LinearLayout page) {
        page.removeAllViews();

        // ---- daftar menu
        LinearLayout items = Ui.card(this);
        items.addView(Ui.title(this, resto.emoji + " " + resto.name));
        items.addView(Ui.muted(this, "Kec. " + resto.kecamatan));
        itemsBox = Ui.vbox(this);
        items.addView(itemsBox);
        TextView addMore = Ui.text(this, "+ Tambah menu lain", 14, Ui.PRIMARY, true);
        addMore.setPadding(0, Ui.dp(this, 8), 0, 0);
        addMore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        items.addView(addMore);
        page.addView(items);
        renderItems();

        // ---- alamat
        LinearLayout addr = Ui.card(this);
        addr.addView(Ui.title(this, "📍 Alamat Pengiriman"));
        addr.addView(Ui.label(this, "Titik lokasi antar (wajib)"));
        LinearLayout pinRow = Ui.hbox(this);
        pinInfo = Ui.text(this, "Mencari lokasi Anda...", 14, Ui.TEXT, true);
        pinRow.addView(pinInfo, Ui.weight(1));
        TextView pick = Ui.smallButton(this, "🗺️ Atur di peta", Ui.PRIMARY, true);
        pick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                com.antarmakan.app.ui.common.MapPickerActivity.open(CartActivity.this,
                        "Titik Antar Pesanan", pinLat, pinLng);
            }
        });
        pinRow.addView(pick);
        addr.addView(pinRow);
        addr.addView(Ui.muted(this, "Pastikan pin tepat di rumah Anda agar ongkir akurat & pengantar tidak tersesat."));
        addr.addView(Ui.label(this, "Kecamatan"));
        kec = Ui.spinner(this, Config.namaKecamatan(), Config.indexKecamatan(me.kecamatan));
        kec.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                recalc();
            }

            @Override
            public void onNothingSelected(AdapterView<?> p) {
            }
        });
        addr.addView(kec);
        addr.addView(Ui.label(this, "Alamat lengkap"));
        address = Ui.multiline(this, "Nama jalan, RT/RW, desa, patokan rumah");
        address.setText(me.address);
        addr.addView(address);
        addr.addView(Ui.label(this, "Catatan untuk driver (opsional)"));
        note = Ui.edit(this, "Contoh: rumah pagar hijau");
        addr.addView(note);
        page.addView(addr);

        // ---- promo
        LinearLayout pc = Ui.card(this);
        pc.addView(Ui.title(this, "🎁 Promo"));
        LinearLayout prow = Ui.hbox(this);
        promoCode = Ui.edit(this, "Kode promo", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        prow.addView(promoCode, Ui.weight(1));
        TextView apply = Ui.smallButton(this, "Pakai", Ui.PRIMARY, true);
        apply.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appliedPromo = promoCode.getText().toString().trim();
                recalc();
            }
        });
        prow.addView(apply);
        pc.addView(prow);
        promoMsg = Ui.text(this, "", 13, Ui.PRIMARY_DARK, false);
        pc.addView(promoMsg);
        promoChips = Ui.hbox(this);
        for (final Promo p : promos) {
            TextView chip = Ui.chip(this, p.code, false);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    promoCode.setText(p.code);
                    appliedPromo = p.code;
                    recalc();
                }
            });
            promoChips.addView(chip);
        }
        if (!promos.isEmpty()) pc.addView(Ui.hscroll(this, promoChips));
        page.addView(pc);

        // ---- pembayaran
        LinearLayout pay = Ui.card(this);
        pay.addView(Ui.title(this, "💳 Pembayaran"));
        payGroup = new RadioGroup(this);
        RadioButton cash = new RadioButton(this);
        cash.setId(1);
        cash.setText("💵 Tunai (bayar ke driver)");
        RadioButton saldo = new RadioButton(this);
        saldo.setId(2);
        saldo.setText("👛 Saldo " + Config.NAMA_APLIKASI + " (" + Fmt.rp(me.balance) + ")");
        payGroup.addView(cash);
        payGroup.addView(saldo);
        payGroup.check(1);
        pay.addView(payGroup);
        page.addView(pay);

        // ---- ringkasan
        LinearLayout sum = Ui.card(this);
        sum.addView(Ui.title(this, "Ringkasan Pembayaran"));
        summaryBox = Ui.vbox(this);
        sum.addView(summaryBox);
        Button order = Ui.primary(this, "Pesan Sekarang");
        order.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                placeOrder();
            }
        });
        sum.addView(order);
        page.addView(sum);
        recalc();
        initPin();
    }

    private void renderItems() {
        itemsBox.removeAllViews();
        final Cart cart = Cart.get(this);
        for (final CartItem ci : cart.getItems()) {
            itemsBox.addView(Ui.divider(this));
            LinearLayout row = Ui.hbox(this);
            row.addView(Ui.text(this, ci.emoji, 22, Ui.TEXT, false));
            LinearLayout info = Ui.vbox(this);
            info.setPadding(Ui.dp(this, 10), 0, 0, 0);
            info.addView(Ui.text(this, ci.name, 14, Ui.TEXT, true));
            info.addView(Ui.text(this, Fmt.rp(ci.total()), 13, Ui.TEXT, false));
            TextView noteView = Ui.text(this, ci.note.length() > 0 ? "📝 " + ci.note : "📝 Tambah catatan",
                    12, ci.note.length() > 0 ? Ui.TEXT : Ui.PRIMARY, false);
            noteView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Ui.prompt(CartActivity.this, "Catatan " + ci.name, "Contoh: pedas level 3, tanpa bawang",
                            ci.note, InputType.TYPE_CLASS_TEXT, new Ui.TextCallback() {
                                @Override
                                public void onText(String text) {
                                    cart.setNote(ci.menuId, text);
                                    renderItems();
                                }
                            });
                }
            });
            info.addView(noteView);
            row.addView(info, Ui.weight(1));
            TextView minus = Ui.smallButton(this, "−", Ui.PRIMARY, false);
            minus.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cart.remove(ci.menuId);
                    if (cart.isEmpty()) {
                        Ui.toast(CartActivity.this, "Keranjang kosong");
                        finish();
                        return;
                    }
                    renderItems();
                    recalc();
                }
            });
            TextView qty = Ui.text(this, String.valueOf(ci.qty), 15, Ui.TEXT, true);
            qty.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
            TextView plus = Ui.smallButton(this, "+", Ui.PRIMARY, true);
            plus.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cart.increase(ci.menuId);
                    renderItems();
                    recalc();
                }
            });
            row.addView(minus);
            row.addView(qty);
            row.addView(plus);
            itemsBox.addView(row);
        }
    }

    /** Titik awal: lokasi rumah tersimpan, jika belum ada pakai GPS sekarang. */
    private void initPin() {
        if (Geo.has(me.lat, me.lng)) {
            setPin(me.lat, me.lng, false);
            return;
        }
        com.antarmakan.app.util.Loc.current(this, new com.antarmakan.app.util.Loc.Callback() {
            @Override
            public void onLocation(android.location.Location l) {
                if (l != null) {
                    setPin(l.getLatitude(), l.getLongitude(), false);
                } else {
                    pinInfo.setText("Belum dipilih");
                    pinInfo.setTextColor(Ui.RED);
                }
            }
        });
    }

    private void setPin(double lat, double lng, boolean saveAsHome) {
        pinLat = lat;
        pinLng = lng;
        String k = Geo.nearestKecamatan(lat, lng);
        pinInfo.setText("✅ Titik dipilih (Kec. " + k + ")");
        pinInfo.setTextColor(Ui.PRIMARY_DARK);
        int idx = Config.indexKecamatan(k);
        if (kec.getSelectedItemPosition() != idx) kec.setSelection(idx); // memicu recalc
        else recalc();
        if (saveAsHome) {
            me.lat = lat;
            me.lng = lng;
            me.kecamatan = k;
            Async.run(new Async.Task<Boolean>() {
                @Override
                public Boolean run() throws Exception {
                    repo.saveProfile(me);
                    return true;
                }
            }, null);
        }
    }

    @Override
    protected void onActivityResult(int req, int res, android.content.Intent data) {
        super.onActivityResult(req, res, data);
        if (req == com.antarmakan.app.ui.common.MapPickerActivity.REQ && res == RESULT_OK && data != null) {
            setPin(data.getDoubleExtra("lat", 0), data.getDoubleExtra("lng", 0), true);
        }
    }

    private String selectedKec() {
        return (String) kec.getSelectedItem();
    }

    private void recalc() {
        if (summaryBox == null) return;
        final long subtotal = Cart.get(this).subtotal();
        final String k = selectedKec();
        final String code = appliedPromo;
        final double lat = pinLat, lng = pinLng;
        load(new Async.Task<Repo.Quote>() {
            @Override
            public Repo.Quote run() throws Exception {
                return repo.quote(resto, subtotal, k, lat, lng, code);
            }
        }, new Async.Callback<Repo.Quote>() {
            @Override
            public void onResult(Repo.Quote q) {
                quote = q;
                renderSummary();
            }
        });
    }

    private void renderSummary() {
        summaryBox.removeAllViews();
        promoMsg.setText(quote.promoMessage);
        promoMsg.setTextColor(quote.promo != null ? Ui.PRIMARY_DARK : Ui.RED);
        Ui.keyValue(this, summaryBox, "Harga makanan", Fmt.rp(quote.subtotal), false);
        Ui.keyValue(this, summaryBox, "Ongkir (" + Fmt.km(quote.distanceKm) + ")", Fmt.rp(quote.deliveryFee), false);
        Ui.keyValue(this, summaryBox, "Biaya layanan", Fmt.rp(quote.serviceFee), false);
        if (quote.discount > 0) {
            TextView d = Ui.keyValue(this, summaryBox, "Diskon promo", "-" + Fmt.rp(quote.discount), false);
            d.setTextColor(Ui.PRIMARY_DARK);
        }
        summaryBox.addView(Ui.divider(this));
        Ui.keyValue(this, summaryBox, "Total bayar", Fmt.rp(quote.total), true);
        summaryBox.addView(Ui.muted(this, "Estimasi tiba ± " + Geo.etaMinutes(quote.distanceKm) + " menit"));
    }

    private void placeOrder() {
        if (quote == null) return;
        if (!Geo.has(pinLat, pinLng)) {
            Ui.toast(this, "Atur dulu titik lokasi antar di peta");
            return;
        }
        final double lat = pinLat, lng = pinLng;
        final String pay = payGroup.getCheckedRadioButtonId() == 2 ? Order.BAYAR_SALDO : Order.BAYAR_TUNAI;
        if (Order.BAYAR_SALDO.equals(pay) && me.balance < quote.total) {
            Ui.toast(this, "Saldo tidak cukup. Top up dulu di menu Akun");
            return;
        }
        final String addr = address.getText().toString();
        final String k = selectedKec();
        final String n = note.getText().toString();
        final String code = quote.promo != null ? quote.promo.code : "";
        Ui.confirm(this, "Konfirmasi Pesanan",
                "Total " + Fmt.rp(quote.total) + " dibayar " + (Order.BAYAR_SALDO.equals(pay) ? "pakai saldo" : "tunai")
                        + ".\nAntar ke: " + addr + ", Kec. " + k, "Pesan", new Runnable() {
                    @Override
                    public void run() {
                        action("Mengirim pesanan...", new Async.Task<Order>() {
                            @Override
                            public Order run() throws Exception {
                                return repo.placeOrder(me, resto.id, Cart.get(CartActivity.this),
                                        addr, k, lat, lng, n, pay, code);
                            }
                        }, new Async.Callback<Order>() {
                            @Override
                            public void onResult(Order o) {
                                Ui.toast(CartActivity.this, "Pesanan terkirim ke resto! 🎉");
                                com.antarmakan.app.notif.NotifCenter.ensureService(CartActivity.this);
                                Views.openOrder(CartActivity.this, o.id);
                                finish();
                            }
                        });
                    }
                });
    }
}
