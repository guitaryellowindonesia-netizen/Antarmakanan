package com.antarmakan.app.ui.common;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.antarmakan.app.util.Geo;
import com.antarmakan.app.util.Loc;
import com.antarmakan.app.util.MapUtil;
import com.antarmakan.app.util.Ui;

import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import java.util.Locale;

/**
 * Pilih titik lokasi: geser peta sampai pin 📍 tepat di atas rumah/resto.
 * Hasil: extra "lat" & "lng".
 */
public class MapPickerActivity extends BaseActivity {

    public static final int REQ = 601;

    private MapView map;
    private TextView info;

    public static void open(Activity a, String title, double lat, double lng) {
        Intent i = new Intent(a, MapPickerActivity.class);
        i.putExtra("title", title);
        i.putExtra("lat", lat);
        i.putExtra("lng", lng);
        a.startActivityForResult(i, REQ);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String title = getIntent().getStringExtra("title");
        enableBack(title == null ? "Pilih Titik Lokasi" : title);

        LinearLayout root = Ui.vbox(this);
        FrameLayout frame = new FrameLayout(this);
        map = MapUtil.create(this);
        map.getController().setZoom(18.0);
        frame.addView(map, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        // pin tetap di tengah; ujung bawah pin = titik yang dipilih
        TextView pin = Ui.text(this, "📍", 40, Ui.RED, false);
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        pl.bottomMargin = Ui.dp(this, 48);
        frame.addView(pin, pl);
        root.addView(frame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout bar = Ui.vbox(this);
        bar.setBackgroundColor(Color.WHITE);
        bar.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 10));
        bar.addView(Ui.muted(this, "Geser peta sampai pin 📍 tepat di lokasi."));
        info = Ui.text(this, "", 14, Ui.TEXT, true);
        bar.addView(info);
        LinearLayout btns = Ui.hbox(this);
        Button mine = Ui.outlineButton(this, "🎯 Lokasi saya", Ui.PRIMARY);
        mine.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gotoMyLocation();
            }
        });
        Button ok = Ui.primary(this, "Pilih titik ini");
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GeoPoint c = (GeoPoint) map.getMapCenter();
                Intent r = new Intent();
                r.putExtra("lat", c.getLatitude());
                r.putExtra("lng", c.getLongitude());
                setResult(RESULT_OK, r);
                finish();
            }
        });
        LinearLayout.LayoutParams w1 = Ui.weight(1);
        w1.setMargins(0, 0, Ui.dp(this, 6), 0);
        btns.addView(mine, w1);
        btns.addView(ok, Ui.weight(1));
        bar.addView(btns);
        root.addView(bar, Ui.matchWrap());
        setContentView(root);

        map.addMapListener(new MapListener() {
            @Override
            public boolean onScroll(ScrollEvent e) {
                updateInfo();
                return false;
            }

            @Override
            public boolean onZoom(ZoomEvent e) {
                return false;
            }
        });

        double lat = getIntent().getDoubleExtra("lat", 0);
        double lng = getIntent().getDoubleExtra("lng", 0);
        if (Geo.has(lat, lng)) {
            map.getController().setCenter(new GeoPoint(lat, lng));
            updateInfo();
        } else {
            gotoMyLocation();
        }
    }

    private void gotoMyLocation() {
        info.setText("Mencari lokasi Anda...");
        Loc.current(this, new Loc.Callback() {
            @Override
            public void onLocation(Location l) {
                if (l == null) {
                    Ui.toast(MapPickerActivity.this, "Lokasi belum didapat, geser peta manual");
                } else {
                    map.getController().animateTo(new GeoPoint(l.getLatitude(), l.getLongitude()));
                }
                updateInfo();
            }
        });
    }

    private void updateInfo() {
        GeoPoint c = (GeoPoint) map.getMapCenter();
        info.setText(String.format(Locale.US, "Kec. %s  (%.5f, %.5f)",
                Geo.nearestKecamatan(c.getLatitude(), c.getLongitude()), c.getLatitude(), c.getLongitude()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        map.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        map.onPause();
    }
}
