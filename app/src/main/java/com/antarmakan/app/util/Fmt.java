package com.antarmakan.app.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class Fmt {
    private Fmt() {}

    private static final Locale ID = new Locale("id", "ID");

    public static String rp(long value) {
        String s = String.format(Locale.US, "%,d", Math.abs(value)).replace(',', '.');
        return (value < 0 ? "-Rp" : "Rp") + s;
    }

    public static String date(long millis) {
        return new SimpleDateFormat("dd MMM yyyy, HH:mm", ID).format(new Date(millis));
    }

    public static String time(long millis) {
        return new SimpleDateFormat("HH:mm", ID).format(new Date(millis));
    }

    public static String km(double km) {
        return String.format(Locale.US, "%.1f km", km);
    }

    public static String rating(double r) {
        return String.format(Locale.US, "%.1f", r);
    }

    public static String nowHHmm() {
        Calendar c = Calendar.getInstance();
        return String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    /** true jika jam sekarang berada di antara open-close (format HH:mm). */
    public static boolean inHours(String open, String close) {
        if (open == null || close == null || open.length() < 4 || close.length() < 4) return true;
        String now = nowHHmm();
        if (open.compareTo(close) <= 0) {
            return now.compareTo(open) >= 0 && now.compareTo(close) < 0;
        }
        // lewat tengah malam, mis. 18:00 - 02:00
        return now.compareTo(open) >= 0 || now.compareTo(close) < 0;
    }

    /** Normalisasi nomor HP: 08xx -> 628xx */
    public static String waNumber(String phone) {
        if (phone == null) return "";
        String p = phone.replaceAll("[^0-9]", "");
        if (p.startsWith("0")) p = "62" + p.substring(1);
        return p;
    }

    public static long parseLong(String s) {
        try {
            return Long.parseLong(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }
}
