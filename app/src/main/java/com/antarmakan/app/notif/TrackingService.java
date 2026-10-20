package com.antarmakan.app.notif;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.antarmakan.app.data.Backend;
import com.antarmakan.app.data.Repo;
import com.antarmakan.app.model.Order;
import com.antarmakan.app.util.Async;
import com.antarmakan.app.util.Loc;

import java.util.ArrayList;
import java.util.List;

/**
 * Mengirim posisi pengantar (driver / penjual antar sendiri) tiap 15 detik
 * selama ada pesanan yang sedang ditangani. Berhenti sendiri bila tidak ada.
 */
public class TrackingService extends Service {

    private static final long SEND_INTERVAL = 15000;
    private static final long RECHECK_ORDERS = 60000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private LocationManager lm;
    private Location latest;
    private List<String> orderIds = new ArrayList<String>();
    private long lastOrderCheck = 0;
    private boolean running;

    /** Mulai bila izin lokasi ada (aman dipanggil berulang). */
    public static void start(Context c) {
        if (!Loc.hasPermission(c)) return;
        try {
            Intent i = new Intent(c.getApplicationContext(), TrackingService.class);
            if (Build.VERSION.SDK_INT >= 26) c.getApplicationContext().startForegroundService(i);
            else c.getApplicationContext().startService(i);
        } catch (Exception ignored) {
        }
    }

    public static void stop(Context c) {
        c.getApplicationContext().stopService(new Intent(c, TrackingService.class));
    }

    private final LocationListener listener = new LocationListener() {
        @Override
        public void onLocationChanged(Location l) {
            if (latest == null || !l.hasAccuracy() || !latest.hasAccuracy()
                    || l.getAccuracy() <= latest.getAccuracy() + 20
                    || l.getTime() - latest.getTime() > 20000) {
                latest = l;
            }
        }

        public void onStatusChanged(String p, int s, Bundle b) {}
        public void onProviderEnabled(String p) {}
        public void onProviderDisabled(String p) {}
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            final boolean recheck = System.currentTimeMillis() - lastOrderCheck > RECHECK_ORDERS;
            final Location loc = latest;
            Async.run(null, new Async.Task<List<String>>() {
                @Override
                public List<String> run() throws Exception {
                    Repo repo = Backend.repo(TrackingService.this);
                    List<String> ids = orderIds;
                    if (recheck) {
                        ids = new ArrayList<String>();
                        for (Order o : repo.deliveriesInProgress(repo.me())) ids.add(o.id);
                    }
                    if (loc != null) {
                        for (String id : ids) repo.updateTracking(id, loc.getLatitude(), loc.getLongitude());
                    }
                    return ids;
                }
            }, new Async.Callback<List<String>>() {
                @Override
                public void onResult(List<String> ids) {
                    if (recheck) lastOrderCheck = System.currentTimeMillis();
                    orderIds = ids;
                    if (ids.isEmpty()) {
                        stopSelf();
                        return;
                    }
                    handler.postDelayed(tick, SEND_INTERVAL);
                }
            }, new Async.ErrorCallback() {
                @Override
                public void onError(Exception e) {
                    handler.postDelayed(tick, SEND_INTERVAL);
                }
            });
        }
    };

    @Override
    @SuppressWarnings("MissingPermission")
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1002, NotifCenter.serviceNotification(this, "📍 Membagikan lokasi pengantaran"));
        if (!running) {
            running = true;
            lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            latest = Loc.lastKnown(this);
            for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                try {
                    lm.requestLocationUpdates(p, 5000, 5, listener, Looper.getMainLooper());
                } catch (Exception ignored) {
                }
            }
            lastOrderCheck = 0;
            handler.post(tick);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        try {
            if (lm != null) lm.removeUpdates(listener);
        } catch (Exception ignored) {
        }
        running = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
