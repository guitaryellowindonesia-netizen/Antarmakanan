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
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(poller);
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
