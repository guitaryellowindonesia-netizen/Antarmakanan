package com.antarmakan.app.notif;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.antarmakan.app.util.Async;

/**
 * Layanan latar belakang agar notifikasi tetap masuk saat aplikasi ditutup.
 * Hanya berjalan bila perlu (toko buka / driver online / ada pesanan aktif),
 * lalu berhenti sendiri.
 */
public class NotifService extends Service {

    private static final long INTERVAL = 30000;          // cek notifikasi tiap 30 detik
    private static final long RECHECK_NEED = 5 * 60000;  // cek perlu/tidak tiap 5 menit

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastNeedCheck = 0;
    private boolean started = false;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            final boolean recheck = System.currentTimeMillis() - lastNeedCheck > RECHECK_NEED;
            Async.run(null, new Async.Task<String>() {
                @Override
                public String run() throws Exception {
                    NotifCenter.check(NotifService.this);
                    if (recheck) {
                        String reason = NotifCenter.backgroundReason(NotifService.this);
                        return reason == null ? "" : reason;
                    }
                    return null;
                }
            }, new Async.Callback<String>() {
                @Override
                public void onResult(String reason) {
                    if (reason != null) {
                        lastNeedCheck = System.currentTimeMillis();
                        if (reason.length() == 0) {
                            stopSelf();
                            return;
                        }
                        updateNotification(reason);
                    }
                    handler.postDelayed(tick, INTERVAL);
                }
            }, new Async.ErrorCallback() {
                @Override
                public void onError(Exception e) {
                    handler.postDelayed(tick, INTERVAL); // coba lagi (mis. sinyal hilang)
                }
            });
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1001, NotifCenter.serviceNotification(this, "Notifikasi pesanan aktif"));
        if (!started) {
            started = true;
            lastNeedCheck = 0;
            handler.post(tick);
        }
        return START_STICKY;
    }

    private void updateNotification(String text) {
        android.app.NotificationManager nm =
                (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(1001, NotifCenter.serviceNotification(this, text));
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        started = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
