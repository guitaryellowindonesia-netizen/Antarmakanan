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
        root.addView(logo);
        root.addView(name);
        root.addView(sub);
        setContentView(root);

        Async.run(this, new Async.Task<Boolean>() {
            @Override
            public Boolean run() throws Exception {
                Thread.sleep(900);
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
