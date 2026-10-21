package com.antarmakan.app.ui.driver;

import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;

import com.antarmakan.app.Config;
import com.antarmakan.app.model.Order;
import com.antarmakan.app.model.User;
import com.antarmakan.app.ui.common.TabbedActivity;
import com.antarmakan.app.ui.common.Views;
import com.antarmakan.app.util.Async;
import com.antarmakan.app.util.Fmt;
import com.antarmakan.app.util.Ui;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Halaman Driver: Order, Riwayat, Akun. */
public class DriverMainActivity extends TabbedActivity {

    private static final int TAB_JOBS = 0, TAB_HISTORY = 1, TAB_ACCOUNT = 2;

    @Override
    protected String[] tabLabels() {
        return new String[]{"Order", "Riwayat", "Akun"};
    }

    @Override
    protected String[] tabIcons() {
        return new String[]{"🛵", "🧾", "👤"};
    }

    @Override
    protected long pollFor(int tab) {
        return tab == TAB_JOBS ? 6000 : 0;
    }

    @Override
    protected void renderTab(int tab) {
        if (tab == TAB_JOBS) loadJobs();
        else if (tab == TAB_HISTORY) loadHistory();
        else loadAccount();
    }

    private static class Data {
        User me;
        List<Order> mine = new ArrayList<Order>();
        List<Order> jobs = new ArrayList<Order>();
    }

    private void loadJobs() {
        load(new Async.Task<Data>() {
            @Override
            public Data run() throws Exception {
                Data d = new Data();
                d.me = repo.me();
                d.mine = repo.ordersOfDriver(d.me.id);
                android.location.Location l = com.antarmakan.app.util.Loc.lastKnown(DriverMainActivity.this);
                double lat = l != null ? l.getLatitude() : d.me.lat, lng = l != null ? l.getLongitude() : d.me.lng;
                if (d.me.online && l != null) repo.updateMyLocation(lat, lng);
                if (d.me.online && d.me.active) d.jobs = repo.availableJobs(lat, lng);
                return d;
            }
        }, new Async.Callback<Data>() {
            @Override
            public void onResult(Data d) {
                renderJobs(d);
            }
        });
    }

    private void renderJobs(final Data d) {
        LinearLayout page = Ui.vbox(this);
        page.addView(Views.header(this, "Halo, " + d.me.name,
                d.me.vehicle + (d.me.ratingCount > 0 ? "  •  ⭐ " + Fmt.rating(d.me.rating()) : "")));

        if (!d.me.active) {
            LinearLayout c = Ui.card(this);
            c.setBackground(Ui.rounded(android.graphics.Color.parseColor("#FFF3E0"), Ui.dp(this, 12)));
            c.addView(Ui.text(this, "⏳ Akun menunggu persetujuan admin", 14, Ui.ACCENT, true));
            c.addView(Ui.muted(this, "Hubungi admin " + Config.NAMA_APLIKASI + " untuk verifikasi data driver."));
            page.addView(c);
        }

        // saklar online
        LinearLayout sw = Ui.card(this);
        LinearLayout row = Ui.hbox(this);
        LinearLayout info = Ui.vbox(this);
        info.addView(Ui.title(this, d.me.online ? "🟢 Kamu ONLINE" : "⚫ Kamu OFFLINE"));
        info.addView(Ui.muted(this, d.me.online ? "Order baru akan muncul di bawah" : "Aktifkan untuk menerima order"));
        row.addView(info, Ui.weight(1));
        Switch s = new Switch(this);
        s.setChecked(d.me.online);
        s.setEnabled(d.me.active);
        s.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, final boolean on) {
                action("Menyimpan...", new Async.Task<Boolean>() {
                    @Override
                    public Boolean run() throws Exception {
                        repo.setDriverOnline(d.me.id, on);
                        return true;
                    }
                }, new Async.Callback<Boolean>() {
                    @Override
                    public void onResult(Boolean r) {
                        if (on) com.antarmakan.app.notif.NotifCenter.ensureService(DriverMainActivity.this);
                        loadJobs();
                    }
                });
            }
        });
        row.addView(s);
        sw.addView(row);
        page.addView(sw);

        // pendapatan hari ini
        long start = startOfToday();
        int count = 0;
        long earn = 0;
        Order active = null;
        for (Order o : d.mine) {
            if (o.isActive()) active = o;
            if (o.status == Order.SELESAI && o.createdAt >= start) {
                count++;
                earn += o.deliveryFee - o.deliveryFee * com.antarmakan.app.util.AppSettings.KOMISI_PERSEN_DRIVER / 100;
            }
        }
        page.addView(Views.statRow(this,
                Views.stat(this, "Order hari ini", String.valueOf(count)),
                Views.stat(this, "Pendapatan hari ini", Fmt.rp(earn)),
                Views.stat(this, "Saldo", Fmt.rp(d.me.balance))));

        if (active != null) {
            com.antarmakan.app.notif.TrackingService.start(this);
            page.addView(Ui.section(this, "🚩 Order yang sedang kamu kerjakan"));
            page.addView(Views.orderCard(this, active, User.ROLE_DRIVER));
        } else if (d.me.online) {
            page.addView(Ui.section(this, "📦 Order tersedia (" + d.jobs.size() + ")"));
            page.addView(Ui.muted(this, "      Resto dalam radius " + Fmt.km(com.antarmakan.app.util.AppSettings.RADIUS_DRIVER_KM)
                    + " dari posisimu"));
            if (d.jobs.isEmpty()) {
                page.addView(Ui.empty(this, "Belum ada order. Tunggu sebentar ya... ⏳"));
            }
            for (Order o : d.jobs) page.addView(Views.orderCard(this, o, User.ROLE_DRIVER));
        }
        show(TAB_JOBS, page);
    }

    private static long startOfToday() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private void loadHistory() {
        load(new Async.Task<List<Order>>() {
            @Override
            public List<Order> run() throws Exception {
                return repo.ordersOfDriver(repo.me().id);
            }
        }, new Async.Callback<List<Order>>() {
            @Override
            public void onResult(List<Order> list) {
                LinearLayout page = Ui.vbox(DriverMainActivity.this);
                long total = 0;
                int done = 0;
                for (Order o : list) {
                    if (o.status == Order.SELESAI) {
                        done++;
                        total += o.deliveryFee - o.deliveryFee * com.antarmakan.app.util.AppSettings.KOMISI_PERSEN_DRIVER / 100;
                    }
                }
                page.addView(Views.header(DriverMainActivity.this, "Riwayat Order",
                        done + " order selesai • pendapatan " + Fmt.rp(total)));
                if (list.isEmpty()) page.addView(Ui.empty(DriverMainActivity.this, "Belum ada riwayat"));
                for (Order o : list) page.addView(Views.orderCard(DriverMainActivity.this, o, User.ROLE_DRIVER));
                show(TAB_HISTORY, page);
            }
        });
    }

    private void loadAccount() {
        load(new Async.Task<User>() {
            @Override
            public User run() throws Exception {
                return repo.me();
            }
        }, new Async.Callback<User>() {
            @Override
            public void onResult(User u) {
                LinearLayout page = Ui.vbox(DriverMainActivity.this);
                page.addView(Views.header(DriverMainActivity.this, "Akun Driver",
                        u.ratingCount > 0 ? "⭐ " + Fmt.rating(u.rating()) + " dari " + u.ratingCount + " ulasan" : null));
                Views.accountSection(DriverMainActivity.this, u, page);
                show(TAB_ACCOUNT, page);
            }
        });
    }
}
