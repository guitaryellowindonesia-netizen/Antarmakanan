package com.antarmakan.app.ui.common;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.antarmakan.app.util.Ui;

/** Layar utama dengan navigasi tab di bawah (Beranda, Pesanan, dst). */
public abstract class TabbedActivity extends BaseActivity {

    private static final int MENU_REFRESH = 1;
    private static final int MENU_NOTIF = 2;

    protected LinearLayout content;
    protected FrameLayout bottomExtra;
    protected ScrollView scroll;
    private LinearLayout tabBar;
    protected int current = 0;

    protected abstract String[] tabLabels();

    protected abstract String[] tabIcons();

    /** Isi "content" untuk tab. Cek "tab == current" di callback async. */
    protected abstract void renderTab(int tab);

    /** Interval polling per tab (0 = tidak). */
    protected long pollFor(int tab) {
        return 0;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = Ui.vbox(this);
        root.setBackgroundColor(Ui.BG);

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = Ui.vbox(this);
        content.setPadding(0, 0, 0, Ui.dp(this, 16));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        bottomExtra = new FrameLayout(this);
        root.addView(bottomExtra, Ui.matchWrap());

        tabBar = Ui.hbox(this);
        tabBar.setBackgroundColor(Color.WHITE);
        if (android.os.Build.VERSION.SDK_INT >= 21) tabBar.setElevation(Ui.dp(this, 8));
        String[] labels = tabLabels();
        String[] icons = tabIcons();
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            LinearLayout t = Ui.vbox(this);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
            TextView ic = Ui.text(this, icons[i], 20, Ui.TEXT, false);
            ic.setGravity(Gravity.CENTER);
            TextView lb = Ui.text(this, labels[i], 11, Ui.MUTED, false);
            lb.setGravity(Gravity.CENTER);
            t.addView(ic);
            t.addView(lb);
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectTab(idx);
                }
            });
            tabBar.addView(t, Ui.weight(1));
        }
        root.addView(tabBar, Ui.matchWrap());
        setContentView(root);

        // izin notifikasi diminta bersama izin lokasi (lihat BaseActivity)
        com.antarmakan.app.notif.NotifCenter.createChannels(this);

        int start = savedInstanceState == null
                ? getIntent().getIntExtra("tab", 0) : savedInstanceState.getInt("tab", 0);
        highlight(start);
        current = start;
        setPolling(pollFor(start));
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", current);
    }

    private void highlight(int idx) {
        for (int i = 0; i < tabBar.getChildCount(); i++) {
            LinearLayout t = (LinearLayout) tabBar.getChildAt(i);
            TextView lb = (TextView) t.getChildAt(1);
            boolean sel = i == idx;
            lb.setTextColor(sel ? Ui.PRIMARY : Ui.MUTED);
            lb.setTypeface(null, sel ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            t.getChildAt(0).setAlpha(sel ? 1f : 0.55f);
        }
    }

    public void selectTab(int idx) {
        current = idx;
        highlight(idx);
        content.removeAllViews();
        content.addView(Ui.empty(this, "Memuat..."));
        bottomExtra.removeAllViews();
        scroll.scrollTo(0, 0);
        setPolling(pollFor(idx));
        restartPolling();
    }

    @Override
    protected void refresh() {
        renderTab(current);
    }

    @Override
    protected void onResume() {
        super.onResume();
        com.antarmakan.app.notif.NotifCenter.ensureService(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        android.view.MenuItem n = menu.add(0, MENU_NOTIF, 0, "🔔");
        n.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(0, MENU_REFRESH, 0, "Muat ulang");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == MENU_NOTIF) {
            startActivity(new android.content.Intent(this, NotificationsActivity.class));
            return true;
        }
        if (item.getItemId() == MENU_REFRESH) {
            refresh();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** Ganti isi tab sekaligus (dipanggil setelah data selesai dimuat). */
    protected void show(int tab, View... views) {
        if (tab != current) return;
        content.removeAllViews();
        for (View v : views) content.addView(v);
    }
}
