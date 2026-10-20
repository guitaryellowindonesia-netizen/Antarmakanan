package com.antarmakan.app.ui.auth;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.antarmakan.app.Config;
import com.antarmakan.app.ui.common.BaseActivity;
import com.antarmakan.app.ui.common.Nav;
import com.antarmakan.app.util.Async;
import com.antarmakan.app.util.Session;
import com.antarmakan.app.util.Ui;

public class SplashActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = Ui.vbox(this);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Ui.PRIMARY);
        TextView logo = Ui.text(this, "🛵", 64, Color.WHITE, false);
        logo.setGravity(Gravity.CENTER);
        TextView name = Ui.text(this, Config.NAMA_APLIKASI, 32, Color.WHITE, true);
        name.setGravity(Gravity.CENTER);
        TextView sub = Ui.text(this, "Pesan makanan favorit se-" + Config.NAMA_KABUPATEN, 14,
                Color.WHITE, false);
        sub.setGravity(Gravity.CENTER);
        TextView mode = Ui.text(this, Config.isOnline() ? "" : "Mode demo (offline)", 12,
                Color.parseColor("#C8F7CF"), false);
        mode.setGravity(Gravity.CENTER);
        mode.setPadding(0, Ui.dp(this, 24), 0, 0);
        root.addView(logo);
        root.addView(name);
        root.addView(sub);
        root.addView(mode);
        setContentView(root);

        Async.run(this, new Async.Task<Boolean>() {
            @Override
            public Boolean run() throws Exception {
                long start = System.currentTimeMillis();
                // mode offline: isi data contoh saat pertama kali dibuka
                if (!repo.isOnline() && !repo.hasAnyResto()) repo.seedDemo(true);
                long wait = 900 - (System.currentTimeMillis() - start);
                if (wait > 0) Thread.sleep(wait);
                return true;
            }
        }, new Async.Callback<Boolean>() {
            @Override
            public void onResult(Boolean r) {
                go();
            }
        }, new Async.ErrorCallback() {
            @Override
            public void onError(Exception e) {
                go();
            }
        });
    }

    private void go() {
        if (Session.isLoggedIn(this)) {
            Nav.home(this, Session.role(this));
        } else {
            Nav.login(this);
        }
    }
}
