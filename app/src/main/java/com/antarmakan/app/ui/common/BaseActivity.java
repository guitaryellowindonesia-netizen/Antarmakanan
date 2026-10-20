package com.antarmakan.app.ui.common;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.antarmakan.app.data.Backend;
import com.antarmakan.app.data.Repo;
import com.antarmakan.app.util.Async;
import com.antarmakan.app.util.Ui;

/** Dasar semua layar: akses Repo, proses background, polling data. */
public abstract class BaseActivity extends Activity {

    protected Repo repo;
    protected final Handler handler = new Handler(Looper.getMainLooper());
    private long pollMs = 0;
    private ProgressDialog progress;

    private final Runnable poller = new Runnable() {
        @Override
        public void run() {
            refresh();
            if (pollMs > 0) handler.postDelayed(this, pollMs);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repo = Backend.repo(this);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            Window w = getWindow();
            w.setStatusBarColor(Ui.PRIMARY_DARK);
        }
    }

    /** Tampilkan tombol kembali di action bar */
    protected void enableBack(String title) {
        setTitle(title);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** Dipanggil saat layar tampil & setiap interval polling. */
    protected void refresh() {
    }

    /** Muat ulang data otomatis setiap ms milidetik (0 = mati). */
    protected void setPolling(long ms) {
        pollMs = ms;
    }

    /** Muat ulang sekarang & mulai ulang jadwal polling. */
    protected void restartPolling() {
        handler.removeCallbacks(poller);
        handler.post(poller);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(poller);
        handler.post(poller);
        com.antarmakan.app.notif.NotifCenter.onActivityResumed(this);
        if (requiresLocation()) {
            registerReceiver(locationSwitch, new android.content.IntentFilter(
                    android.location.LocationManager.PROVIDERS_CHANGED_ACTION));
            checkLocation();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(poller);
        com.antarmakan.app.notif.NotifCenter.onActivityPaused();
        if (requiresLocation()) {
            try {
                unregisterReceiver(locationSwitch);
            } catch (Exception ignored) {
            }
        }
    }

    // ------------------------------------------------------------------ GPS wajib aktif

    private static final int REQ_PERMS = 501;
    private android.app.AlertDialog locDialog;
    private boolean permAsked;

    /** Layar ini mewajibkan GPS aktif? (semua layar kecuali splash) */
    protected boolean requiresLocation() {
        return true;
    }

    private final android.content.BroadcastReceiver locationSwitch = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context c, android.content.Intent i) {
            checkLocation();
        }
    };

    private void checkLocation() {
        if (!com.antarmakan.app.util.Loc.hasPermission(this)) {
            if (!permAsked) {
                permAsked = true;
                requestAppPermissions();
            } else {
                showLocationDialog(false);
            }
            return;
        }
        if (!com.antarmakan.app.util.Loc.isEnabled(this)) {
            showLocationDialog(true);
            return;
        }
        if (locDialog != null) {
            locDialog.dismiss();
            locDialog = null;
            onLocationReady();
        }
    }

    /** Dipanggil saat GPS baru saja aktif kembali. */
    protected void onLocationReady() {
    }

    private void requestAppPermissions() {
        if (android.os.Build.VERSION.SDK_INT < 23) return;
        java.util.List<String> p = new java.util.ArrayList<String>();
        p.add(com.antarmakan.app.util.Loc.FINE);
        p.add(com.antarmakan.app.util.Loc.COARSE);
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            p.add("android.permission.POST_NOTIFICATIONS");
        }
        requestPermissions(p.toArray(new String[0]), REQ_PERMS);
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == REQ_PERMS && requiresLocation()) checkLocation();
    }

    private void showLocationDialog(final boolean gpsOff) {
        if (locDialog != null && locDialog.isShowing()) return;
        String msg = gpsOff
                ? "GPS / Lokasi HP Anda mati.\n\nAplikasi ini wajib memakai lokasi agar ongkir akurat "
                + "dan pengantar bisa menemukan alamat. Silakan aktifkan Lokasi."
                : "Aplikasi membutuhkan izin LOKASI agar ongkir akurat dan pengantar bisa menemukan alamat.\n\n"
                + "Silakan izinkan akses lokasi.";
        locDialog = new android.app.AlertDialog.Builder(this)
                .setTitle(gpsOff ? "📍 Aktifkan Lokasi" : "📍 Izin Lokasi Diperlukan")
                .setMessage(msg)
                .setCancelable(false)
                .setPositiveButton(gpsOff ? "Aktifkan" : "Izinkan", null)
                .setNegativeButton("Tutup Aplikasi", null)
                .create();
        locDialog.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
            @Override
            public void onShow(android.content.DialogInterface d) {
                locDialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                        new android.view.View.OnClickListener() {
                            @Override
                            public void onClick(android.view.View v) {
                                openLocationFix(gpsOff);
                            }
                        });
                locDialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).setOnClickListener(
                        new android.view.View.OnClickListener() {
                            @Override
                            public void onClick(android.view.View v) {
                                finishAffinity();
                            }
                        });
            }
        });
        locDialog.show();
    }

    private void openLocationFix(boolean gpsOff) {
        try {
            if (gpsOff) {
                startActivity(new android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            } else if (android.os.Build.VERSION.SDK_INT >= 23
                    && shouldShowRequestPermissionRationale(com.antarmakan.app.util.Loc.FINE)) {
                requestAppPermissions();
            } else {
                // izin ditolak permanen: buka pengaturan aplikasi
                android.content.Intent i = new android.content.Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                i.setData(android.net.Uri.parse("package:" + getPackageName()));
                startActivity(i);
            }
        } catch (Exception e) {
            Ui.toast(this, "Buka Setelan HP lalu aktifkan Lokasi");
        }
    }

    @Override
    protected void onDestroy() {
        hideProgress();
        super.onDestroy();
    }

    /** Muat data di background tanpa dialog. */
    protected <T> void load(Async.Task<T> task, Async.Callback<T> ok) {
        Async.run(this, task, ok);
    }

    /** Jalankan aksi dengan dialog "Mohon tunggu". */
    protected <T> void action(String msg, Async.Task<T> task, final Async.Callback<T> ok) {
        showProgress(msg);
        Async.run(this, task, new Async.Callback<T>() {
            @Override
            public void onResult(T r) {
                hideProgress();
                if (ok != null) ok.onResult(r);
            }
        }, new Async.ErrorCallback() {
            @Override
            public void onError(Exception e) {
                hideProgress();
                Ui.toast(BaseActivity.this, Ui.errorMessage(e));
            }
        });
    }

    @SuppressWarnings("deprecation")
    private void showProgress(String msg) {
        hideProgress();
        progress = new ProgressDialog(this);
        progress.setMessage(msg == null ? "Mohon tunggu..." : msg);
        progress.setCancelable(false);
        progress.show();
    }

    private void hideProgress() {
        if (progress != null) {
            try {
                progress.dismiss();
            } catch (Exception ignored) {
            }
            progress = null;
        }
    }

    /** Halaman standar: ScrollView berisi LinearLayout vertikal. */
    protected LinearLayout scrollPage() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        sv.setFillViewport(true);
        LinearLayout content = Ui.vbox(this);
        content.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 24));
        sv.addView(content);
        setContentView(sv);
        return content;
    }

    /** Kartu formulir dengan padding */
    protected LinearLayout formCard(LinearLayout parent) {
        LinearLayout card = Ui.card(this);
        parent.addView(card);
        return card;
    }
}
