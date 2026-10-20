package com.antarmakan.app.util;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Menjalankan pekerjaan (akses database / internet) di background,
 * lalu mengirim hasilnya kembali ke UI thread.
 */
public final class Async {
    private Async() {}

    private static final ExecutorService POOL = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Task<T> {
        T run() throws Exception;
    }

    public interface Callback<T> {
        void onResult(T result);
    }

    public interface ErrorCallback {
        void onError(Exception e);
    }

    public static <T> void run(final Task<T> task, final Callback<T> ok) {
        run(null, task, ok, null);
    }

    public static <T> void run(final Activity owner, final Task<T> task, final Callback<T> ok) {
        run(owner, task, ok, null);
    }

    public static <T> void run(final Activity owner, final Task<T> task,
                               final Callback<T> ok, final ErrorCallback err) {
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final T result = task.run();
                    MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            if (owner != null && owner.isFinishing()) return;
                            if (ok != null) ok.onResult(result);
                        }
                    });
                } catch (final Exception e) {
                    MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            if (owner != null && owner.isFinishing()) return;
                            if (err != null) {
                                err.onError(e);
                            } else if (owner != null) {
                                Ui.toast(owner, Ui.errorMessage(e));
                            }
                        }
                    });
                }
            }
        });
    }
}
