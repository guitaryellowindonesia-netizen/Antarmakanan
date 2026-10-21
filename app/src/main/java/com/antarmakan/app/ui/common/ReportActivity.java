package com.antarmakan.app.ui.common;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.antarmakan.app.model.Order;
import com.antarmakan.app.model.OrderItem;
import com.antarmakan.app.model.Restaurant;
import com.antarmakan.app.model.User;
import com.antarmakan.app.util.Async;
import com.antarmakan.app.util.Fmt;
import com.antarmakan.app.util.Ui;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Laporan & grafik penjualan. Admin: semua resto. Mitra: restonya sendiri. */
public class ReportActivity extends BaseActivity {

    private LinearLayout page;
    private int days = 7;
    private User me;
    private Restaurant resto;
    private List<Order> orders = new ArrayList<Order>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enableBack("Laporan Penjualan");
        page = scrollPage();
        page.addView(Ui.empty(this, "Memuat..."));
        load(new Async.Task<Boolean>() {
            @Override
            public Boolean run() throws Exception {
                me = repo.me();
                if (me.isAdmin()) {
                    orders = repo.allOrders();
                } else {
                    resto = repo.restoOfOwner(me.id);
                    if (resto != null) orders = repo.ordersOfResto(resto.id);
                }
                return true;
            }
        }, new Async.Callback<Boolean>() {
            @Override
            public void onResult(Boolean r) {
                render();
            }
        });
    }

    private long startOf(int daysAgo) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.add(Calendar.DAY_OF_MONTH, -daysAgo);
        return c.getTimeInMillis();
    }

    /** Nilai penjualan: admin = total transaksi, mitra = harga makanan. */
    private long value(Order o) {
        return me.isAdmin() ? o.total : o.subtotal;
    }

    private void render() {
        page.removeAllViews();
        if (!me.isAdmin() && resto == null) {
            page.addView(Ui.empty(this, "Belum punya toko"));
            return;
        }
        // pilihan periode
        LinearLayout chips = Ui.hbox(this);
        int[] opts = {7, 14, 30};
        for (final int d : opts) {
            TextView chip = Ui.chip(this, d + " hari", d == days);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    days = d;
                    render();
                }
            });
            chips.addView(chip);
        }
        page.addView(Ui.hscroll(this, chips));

        long from = startOf(days - 1);
        String[] labels = new String[days];
        long[] perDayValue = new long[days];
        long[] perDayCount = new long[days];
        SimpleDateFormat dm = new SimpleDateFormat("d/M", Locale.US);
        for (int i = 0; i < days; i++) labels[i] = dm.format(new java.util.Date(startOf(days - 1 - i)));

        int done = 0, cancelled = 0;
        long total = 0, fees = 0;
        Map<String, Long> menuQty = new HashMap<String, Long>();
        Map<String, Long> restoValue = new HashMap<String, Long>();
        List<Order> period = new ArrayList<Order>();
        for (Order o : orders) {
            if (o.createdAt < from) continue;
            period.add(o);
            if (o.status == Order.DIBATALKAN) {
                cancelled++;
                continue;
            }
            if (o.status != Order.SELESAI) continue;
            done++;
            total += value(o);
            fees += o.serviceFee;
            int idx = (int) ((o.createdAt - from) / 86400000L);
            if (idx >= 0 && idx < days) {
                perDayValue[idx] += value(o);
                perDayCount[idx]++;
            }
            for (OrderItem it : o.items) {
                Long q = menuQty.get(it.name);
                menuQty.put(it.name, (q == null ? 0 : q) + it.qty);
            }
            Long rv = restoValue.get(o.restoName);
            restoValue.put(o.restoName, (rv == null ? 0 : rv) + o.total);
        }

        page.addView(Views.statRow(this,
                Views.stat(this, "Pesanan selesai", String.valueOf(done)),
                Views.stat(this, me.isAdmin() ? "Nilai transaksi" : "Omzet", Fmt.rp(total))));
        page.addView(Views.statRow(this,
                Views.stat(this, "Rata-rata / pesanan", Fmt.rp(done == 0 ? 0 : total / done)),
                Views.stat(this, "Dibatalkan", String.valueOf(cancelled))));

        LinearLayout chart = Ui.card(this);
        chart.addView(Ui.title(this, me.isAdmin() ? "Nilai transaksi per hari" : "Omzet per hari"));
        BarChartView bc = new BarChartView(this);
        bc.setData(labels, perDayValue);
        chart.addView(bc, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 180)));
        page.addView(chart);

        LinearLayout chart2 = Ui.card(this);
        chart2.addView(Ui.title(this, "Jumlah pesanan per hari"));
        BarChartView bc2 = new BarChartView(this);
        bc2.setData(labels, perDayCount);
        chart2.addView(bc2, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 150)));
        page.addView(chart2);

        page.addView(topList("🍽️ Menu terlaris", menuQty, " porsi"));
        if (me.isAdmin()) page.addView(topList("🏪 Resto teratas", restoValue, null));

        LinearLayout ex = Ui.vbox(this);
        ex.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        final List<Order> forExport = period;
        android.widget.Button export = Ui.outlineButton(this, "📥 Ekspor ke Excel (CSV)", Ui.PRIMARY);
        export.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportCsv(forExport);
            }
        });
        ex.addView(export);
        page.addView(ex);
    }

    private View topList(String title, Map<String, Long> data, String suffix) {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.title(this, title));
        List<Map.Entry<String, Long>> list = new ArrayList<Map.Entry<String, Long>>(data.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<String, Long>>() {
            @Override
            public int compare(Map.Entry<String, Long> a, Map.Entry<String, Long> b) {
                return Long.compare(b.getValue(), a.getValue());
            }
        });
        if (list.isEmpty()) c.addView(Ui.muted(this, "Belum ada data"));
        for (int i = 0; i < Math.min(5, list.size()); i++) {
            Map.Entry<String, Long> e = list.get(i);
            Ui.keyValue(this, c, (i + 1) + ". " + e.getKey(),
                    suffix != null ? e.getValue() + suffix : Fmt.rp(e.getValue()), false);
        }
        return c;
    }

    private static String csv(String s) {
        return "\"" + (s == null ? "" : s.replace("\"", "\"\"")) + "\"";
    }

    private void exportCsv(List<Order> list) {
        StringBuilder sb = new StringBuilder();
        sb.append("Tanggal,Kode,Status,Resto,Pelanggan,Kecamatan,Harga Makanan,Ongkir,Biaya Layanan,Diskon,Total,Pembayaran,Item\n");
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        for (Order o : list) {
            StringBuilder items = new StringBuilder();
            for (OrderItem it : o.items) {
                if (items.length() > 0) items.append("; ");
                items.append(it.qty).append("x ").append(it.name);
            }
            sb.append(csv(f.format(new java.util.Date(o.createdAt)))).append(',')
                    .append(csv(o.code)).append(',')
                    .append(csv(Order.statusShort(o.status))).append(',')
                    .append(csv(o.restoName)).append(',')
                    .append(csv(o.customerName)).append(',')
                    .append(csv(o.kecamatan)).append(',')
                    .append(o.subtotal).append(',').append(o.deliveryFee).append(',')
                    .append(o.serviceFee).append(',').append(o.discount).append(',')
                    .append(o.total).append(',')
                    .append(csv(o.paymentMethod)).append(',')
                    .append(csv(items.toString())).append('\n');
        }
        String name = "laporan_antarmakan_" + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US)
                .format(new java.util.Date()) + ".csv";
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                v.put(MediaStore.Downloads.MIME_TYPE, "text/csv");
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                OutputStream os = getContentResolver().openOutputStream(uri);
                os.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}); // agar Excel membaca UTF-8
                os.write(sb.toString().getBytes("UTF-8"));
                os.close();
                Ui.toast(this, "Tersimpan di folder Download: " + name);
                return;
            } catch (Exception e) {
                // lanjut bagikan sebagai teks
            }
        }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, name);
        share.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(share, "Bagikan laporan"));
    }
}
