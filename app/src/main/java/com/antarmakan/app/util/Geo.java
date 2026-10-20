package com.antarmakan.app.util;

import com.antarmakan.app.Config;

public final class Geo {
    private Geo() {}

    public static double lat(String kec) {
        return Double.parseDouble(Config.KECAMATAN[Config.indexKecamatan(kec)][1]);
    }

    public static double lng(String kec) {
        return Double.parseDouble(Config.KECAMATAN[Config.indexKecamatan(kec)][2]);
    }

    /** Perkiraan jarak jalan (km) antara dua kecamatan. */
    public static double distanceKm(String kecA, String kecB) {
        if (kecA == null || kecB == null || kecA.equalsIgnoreCase(kecB)) {
            return Config.JARAK_DALAM_KECAMATAN_KM;
        }
        double d = haversine(lat(kecA), lng(kecA), lat(kecB), lng(kecB)) * Config.FAKTOR_JALAN;
        return Math.max(d, Config.JARAK_DALAM_KECAMATAN_KM);
    }

    public static int deliveryFee(double km) {
        double fee = Config.ONGKIR_DASAR;
        if (km > Config.JARAK_DASAR_KM) {
            fee += Math.ceil(km - Config.JARAK_DASAR_KM) * Config.ONGKIR_PER_KM;
        }
        int f = (int) Math.min(fee, Config.ONGKIR_MAKS);
        return (f + 499) / 500 * 500; // bulatkan ke 500 ke atas
    }

    /** Estimasi waktu: 15 menit masak + 3 menit/km */
    public static int etaMinutes(double km) {
        return 15 + (int) Math.ceil(km * 3);
    }

    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
