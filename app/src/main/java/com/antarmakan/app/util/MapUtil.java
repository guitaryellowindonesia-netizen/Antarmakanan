package com.antarmakan.app.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.MotionEvent;
import android.view.View;

import com.antarmakan.app.Config;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

/** Peta OpenStreetMap (osmdroid) & navigasi Google Maps. */
public final class MapUtil {
    private MapUtil() {}

    private static boolean initialized;

    public static void init(Context c) {
        if (initialized) return;
        Context app = c.getApplicationContext();
        Configuration.getInstance().load(app, app.getSharedPreferences("osmdroid", Context.MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue(app.getPackageName());
        initialized = true;
    }

    /** Titik tengah kabupaten (kecamatan pertama di Config). */
    public static GeoPoint defaultCenter() {
        return new GeoPoint(Double.parseDouble(Config.KECAMATAN[0][1]),
                Double.parseDouble(Config.KECAMATAN[0][2]));
    }

    @SuppressLint("ClickableViewAccessibility")
    public static MapView create(Context c) {
        init(c);
        MapView m = new MapView(c);
        m.setTileSource(TileSourceFactory.MAPNIK);
        m.setMultiTouchControls(true);
        m.setTilesScaledToDpi(true);
        m.getController().setZoom(15.0);
        m.getController().setCenter(defaultCenter());
        // agar peta bisa digeser walau berada di dalam ScrollView
        m.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                int a = e.getAction();
                if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE) {
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                }
                return false;
            }
        });
        return m;
    }

    /** Ikon penanda dari emoji. */
    public static Drawable emoji(Context c, String emoji, int sizeDp) {
        int px = Ui.dp(c, sizeDp);
        Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(px * 0.8f);
        p.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = p.getFontMetrics();
        cv.drawText(emoji, px / 2f, px / 2f - (fm.ascent + fm.descent) / 2f, p);
        return new BitmapDrawable(c.getResources(), b);
    }

    public static Marker marker(MapView m, String emoji, String title, double lat, double lng) {
        Marker mk = new Marker(m);
        mk.setPosition(new GeoPoint(lat, lng));
        mk.setIcon(emoji(m.getContext(), emoji, 36));
        mk.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        mk.setTitle(title);
        m.getOverlays().add(mk);
        return mk;
    }

    /** Buka navigasi Google Maps (mode motor) ke koordinat. */
    public static void navigate(Context c, double lat, double lng) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + lat + "," + lng + "&mode=l"));
            i.setPackage("com.google.android.apps.maps");
            c.startActivity(i);
        } catch (Exception e) {
            try {
                c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                        "https://www.google.com/maps/dir/?api=1&destination=" + lat + "," + lng + "&travelmode=driving")));
            } catch (Exception e2) {
                Ui.toast(c, "Tidak bisa membuka peta");
            }
        }
    }
}
