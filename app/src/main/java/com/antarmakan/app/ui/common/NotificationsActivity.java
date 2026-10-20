package com.antarmakan.app.ui.common;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import com.antarmakan.app.model.AppNotif;
import com.antarmakan.app.util.Async;
import com.antarmakan.app.util.Fmt;
import com.antarmakan.app.util.Session;
import com.antarmakan.app.util.Ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Daftar notifikasi & pengumuman terbaru. */
public class NotificationsActivity extends BaseActivity {

    private LinearLayout page;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enableBack("Notifikasi");
        page = scrollPage();
        page.addView(Ui.empty(this, "Memuat..."));
        setPolling(15000);
    }

    @Override
    protected void refresh() {
        if (!Session.isLoggedIn(this)) {
            finish();
            return;
        }
        final String uid = Session.userId(this);
        final String role = Session.role(this);
        load(new Async.Task<List<AppNotif>>() {
            @Override
            public List<AppNotif> run() throws Exception {
                List<AppNotif> all = new ArrayList<AppNotif>(repo.notificationsSince(uid, -1));
                for (AppNotif n : repo.broadcastsSince(role, -1)) {
                    n.title = "📢 " + n.title;
                    all.add(n);
                }
                Collections.sort(all, new Comparator<AppNotif>() {
                    @Override
                    public int compare(AppNotif a, AppNotif b) {
                        return Long.compare(b.createdAt, a.createdAt);
                    }
                });
                return all;
            }
        }, new Async.Callback<List<AppNotif>>() {
            @Override
            public void onResult(List<AppNotif> list) {
                render(list);
            }
        });
    }

    private void render(List<AppNotif> list) {
        page.removeAllViews();
        if (list.isEmpty()) {
            page.addView(Ui.empty(this, "Belum ada notifikasi 🔔"));
            return;
        }
        for (final AppNotif n : list) {
            LinearLayout c = Ui.card(this);
            c.addView(Ui.text(this, n.title, 15, Ui.TEXT, true));
            if (n.body.length() > 0) c.addView(Ui.body(this, n.body));
            c.addView(Ui.muted(this, Fmt.date(n.createdAt)));
            if (n.orderId.length() > 0) {
                c.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Views.openOrder(NotificationsActivity.this, n.orderId);
                    }
                });
            }
            page.addView(c);
        }
    }
}
