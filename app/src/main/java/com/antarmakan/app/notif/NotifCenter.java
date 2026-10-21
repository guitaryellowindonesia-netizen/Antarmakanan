package com.antarmakan.app.notif;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.antarmakan.app.R;
import com.antarmakan.app.data.Backend;
import com.antarmakan.app.data.Repo;
import com.antarmakan.app.model.AppNotif;
import com.antarmakan.app.model.Order;
import com.antarmakan.app.model.Restaurant;
import com.antarmakan.app.model.User;
import com.antarmakan.app.ui.common.NotificationsActivity;
import com.antarmakan.app.ui.common.OrderDetailActivity;
import com.antarmakan.app.util.Async;
import com.antarmakan.app.util.Session;

import java.util.List;

/**
 * Pusat notifikasi: mengambil notifikasi baru dari server lalu menampilkannya
 * sebagai notifikasi Android. Dijalankan saat aplikasi dibuka (tiap 10 detik)
 * dan oleh NotifService di latar belakang (tiap 30 detik).
 */
public final class NotifCenter {
    private NotifCenter() {}

    public static final String CHANNEL_ORDER = "pesanan";
    public static final String CHANNEL_SERVICE = "layanan";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static int resumedCount = 0;
    private static boolean inAppRunning = false;

    // ------------------------------------------------------------ saluran

    public static void createChannels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel order = new NotificationChannel(CHANNEL_ORDER, "Pesanan & pesan",
                NotificationManager.IMPORTANCE_HIGH);
        order.setDescription("Status pesanan, order baru, chat, dan pengumuman");
        order.enableVibration(true);
        nm.createNotificationChannel(order);
        NotificationChannel svc = new NotificationChannel(CHANNEL_SERVICE, "Layanan latar belakang",
                NotificationManager.IMPORTANCE_MIN);
        svc.setDescription("Menjaga notifikasi tetap masuk saat aplikasi ditutup");
        nm.createNotificationChannel(svc);
    }

    public static boolean canNotify(Context c) {
        return Build.VERSION.SDK_INT < 33
                || c.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressWarnings("deprecation")
    private static Notification.Builder builder(Context c, String channel) {
        return Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, channel) : new Notification.Builder(c);
    }

    private static PendingIntent open(Context c, String orderId, int requestCode) {
        Intent i;
        if (orderId != null && orderId.length() > 0) {
            i = new Intent(c, OrderDetailActivity.class);
            i.putExtra("id", orderId);
        } else {
            i = new Intent(c, NotificationsActivity.class);
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(c, requestCode, i, flags);
    }

    @SuppressWarnings("deprecation")
    public static void show(Context c, AppNotif n) {
        if (!canNotify(c)) return;
        createChannels(c);
        int id = n.id.hashCode();
        Notification.Builder b = builder(c, CHANNEL_ORDER)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(n.title)
                .setContentText(n.body)
                .setStyle(new Notification.BigTextStyle().bigText(n.body))
                .setAutoCancel(true)
                .setContentIntent(open(c, n.orderId, id));
        if (Build.VERSION.SDK_INT < 26) {
            b.setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_ALL);
        }
        if (Build.VERSION.SDK_INT >= 21) b.setColor(0xFF00AA13);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(id, b.build());
    }

    @SuppressWarnings("deprecation")
    static Notification serviceNotification(Context c, String text) {
        createChannels(c);
        Notification.Builder b = builder(c, CHANNEL_SERVICE)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("AntarMakan aktif")
                .setContentText(text)
                .setOngoing(true)
                .setContentIntent(open(c, "", 1));
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_MIN);
        return b.build();
    }

    // ------------------------------------------------------------ cek server

    /** Ambil notifikasi baru & tampilkan. Blocking: panggil dari background. */
    public static synchronized void check(Context c) throws Exception {
        Context app = c.getApplicationContext();
        if (!Session.isLoggedIn(app)) return;
        String uid = Session.userId(app);
        String role = Session.role(app);
        SharedPreferences sp = app.getSharedPreferences("notif", Context.MODE_PRIVATE);
        Repo repo = Backend.repo(app);

        long last = sp.getLong("last_" + uid, -1);
        List<AppNotif> mine = repo.notificationsSince(uid, last);
        long newLast = Math.max(last, 0);
        for (AppNotif n : mine) {
            if (last >= 0) show(app, n);   // pertama kali: tandai saja, jangan banjiri
            newLast = Math.max(newLast, n.createdAt);
        }

        long lastB = sp.getLong("lastb_" + uid, -1);
        List<AppNotif> bc = repo.broadcastsSince(role, lastB);
        long newLastB = Math.max(lastB, 0);
        for (AppNotif n : bc) {
            if (lastB >= 0) show(app, n);
            newLastB = Math.max(newLastB, n.createdAt);
        }
        sp.edit().putLong("last_" + uid, newLast).putLong("lastb_" + uid, newLastB).apply();
    }

    // ------------------------------------------------------------ saat aplikasi dibuka

    private static final Runnable IN_APP = new Runnable() {
        @Override
        public void run() {
            if (resumedCount <= 0 || appContext == null) {
                inAppRunning = false;
                return;
            }
            Async.run(new Async.Task<Boolean>() {
                @Override
                public Boolean run() throws Exception {
                    check(appContext);
                    return true;
                }
            }, null);
            MAIN.postDelayed(this, 10000);
        }
    };
    private static Context appContext;

    /** Dipanggil BaseActivity.onResume */
    public static void onActivityResumed(Context c) {
        appContext = c.getApplicationContext();
        resumedCount++;
        if (!inAppRunning) {
            inAppRunning = true;
            MAIN.post(IN_APP);
        }
    }

    /** Dipanggil BaseActivity.onPause */
    public static void onActivityPaused() {
        resumedCount = Math.max(0, resumedCount - 1);
    }

    // ------------------------------------------------------------ latar belakang

    /**
     * Perlu berjalan di latar belakang?
     * Mitra: toko buka. Driver: sedang online. Pelanggan: ada pesanan aktif.
     */
    public static String backgroundReason(Context c) throws Exception {
        if (!Session.isLoggedIn(c)) return null;
        Repo repo = Backend.repo(c);
        User u = repo.me();
        if (u.isMerchant()) {
            Restaurant r = repo.restoOfOwner(u.id);
            if (r != null) repo.ordersOfResto(r.id); // sekalian batalkan pesanan yang kedaluwarsa
            return r != null && r.isOpen ? "Siap menerima pesanan" : null;
        }
        if (u.isDriver()) return u.online && u.active ? "Kamu online, menunggu order" : null;
        if (u.isCustomer()) {
            for (Order o : repo.ordersOfCustomer(u.id)) {
                if (o.isActive()) return "Memantau pesanan " + o.code;
            }
        }
        return null;
    }

    /** Nyalakan layanan latar belakang jika diperlukan (aman dipanggil berulang). */
    public static void ensureService(final Context c) {
        final Context app = c.getApplicationContext();
        Async.run(new Async.Task<String>() {
            @Override
            public String run() throws Exception {
                return backgroundReason(app);
            }
        }, new Async.Callback<String>() {
            @Override
            public void onResult(String reason) {
                if (reason == null) return;
                try {
                    Intent i = new Intent(app, NotifService.class);
                    if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(i);
                    else app.startService(i);
                } catch (Exception ignored) {
                    // Android bisa menolak bila aplikasi sedang di latar belakang
                }
            }
        });
    }

    public static void stopService(Context c) {
        c.getApplicationContext().stopService(new Intent(c, NotifService.class));
    }
}
