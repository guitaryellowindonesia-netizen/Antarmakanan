package com.antarmakan.app.util;

import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

/** Lokasi GPS memakai LocationManager bawaan Android (tanpa Google Play Services). */
public final class Loc {
    private Loc() {}

    public static final String FINE = "android.permission.ACCESS_FINE_LOCATION";
    public static final String COARSE = "android.permission.ACCESS_COARSE_LOCATION";

    public static boolean hasPermission(Context c) {
        if (Build.VERSION.SDK_INT < 23) return true;
        return c.checkSelfPermission(FINE) == PackageManager.PERMISSION_GRANTED
                || c.checkSelfPermission(COARSE) == PackageManager.PERMISSION_GRANTED;
    }

    /** GPS / layanan lokasi HP menyala? */
    public static boolean isEnabled(Context c) {
        LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return false;
        if (Build.VERSION.SDK_INT >= 28) return lm.isLocationEnabled();
        try {
            return lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception e) {
            return false;
        }
    }

    /** Lokasi terakhir yang diketahui HP (cepat, bisa null). */
    @SuppressWarnings("MissingPermission")
    public static Location lastKnown(Context c) {
        if (!hasPermission(c)) return null;
        LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        Location best = null;
        for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER}) {
            try {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (Exception ignored) {
            }
        }
        return best;
    }

    public interface Callback {
        /** l bisa null bila lokasi tidak didapat. */
        void onLocation(Location l);
    }

    /** Ambil lokasi terkini (maks ~15 detik), cadangannya lokasi terakhir. */
    @SuppressWarnings("MissingPermission")
    public static void current(final Context c, final Callback cb) {
        if (!hasPermission(c)) {
            cb.onLocation(null);
            return;
        }
        final LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        final Handler h = new Handler(Looper.getMainLooper());
        final boolean[] done = {false};
        final LocationListener[] holder = new LocationListener[1];
        final Runnable timeout = new Runnable() {
            @Override
            public void run() {
                if (done[0]) return;
                done[0] = true;
                try {
                    lm.removeUpdates(holder[0]);
                } catch (Exception ignored) {
                }
                cb.onLocation(lastKnown(c));
            }
        };
        holder[0] = new LocationListener() {
            @Override
            public void onLocationChanged(Location l) {
                if (done[0]) return;
                // tunggu akurasi cukup (<= 50 m) atau pakai yang ada saat timeout
                if (l.hasAccuracy() && l.getAccuracy() > 50) return;
                done[0] = true;
                h.removeCallbacks(timeout);
                try {
                    lm.removeUpdates(this);
                } catch (Exception ignored) {
                }
                cb.onLocation(l);
            }

            public void onStatusChanged(String p, int s, Bundle b) {}
            public void onProviderEnabled(String p) {}
            public void onProviderDisabled(String p) {}
        };
        for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            try {
                if (lm.isProviderEnabled(p)) lm.requestLocationUpdates(p, 1000, 0, holder[0], Looper.getMainLooper());
            } catch (Exception ignored) {
            }
        }
        h.postDelayed(timeout, 15000);
    }
}
