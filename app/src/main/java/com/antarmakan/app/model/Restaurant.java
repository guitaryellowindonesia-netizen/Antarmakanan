package com.antarmakan.app.model;

import com.antarmakan.app.util.Fmt;

import org.json.JSONException;
import org.json.JSONObject;

public class Restaurant {
    public String id = "";
    public String ownerId = "";
    public String name = "";
    public String description = "";
    public String category = "Nasi";
    public String kecamatan = "";
    public String address = "";
    public String phone = "";
    public String emoji = "🍽️";
    public String imageId = "";   // foto (koleksi "images")
    public String openHour = "08:00";
    public String closeHour = "21:00";
    public boolean isOpen = true;     // saklar buka/tutup dari mitra
    public boolean active = true;     // disetujui admin
    public boolean selfDelivery;      // diantar sendiri oleh penjual
    public double maxKm;              // jarak antar maksimal resto ini (0 = ikut aturan umum)
    public String closedDays = "";    // hari libur rutin, mis. "5" (Jumat) atau "6,7"
    public String holidayUntil = "";  // libur sementara sampai tanggal (yyyy-MM-dd), "" = tidak
    public double ratingSum;
    public int ratingCount;
    public long createdAt;
    public double lat, lng;          // titik lokasi (GPS), 0 = belum diatur

    /** Dihitung di aplikasi (tidak disimpan) */
    public double distanceKm;
    public int deliveryFee;

    /** Jarak antar maksimal yang berlaku (km). */
    public double effectiveMaxKm() {
        double global = com.antarmakan.app.util.AppSettings.JARAK_ANTAR_MAKS_KM;
        return maxKm > 0 ? Math.min(maxKm, global) : global;
    }

    /** Lokasi pelanggan masih dalam jangkauan antar? */
    public boolean inRange() {
        return distanceKm <= effectiveMaxKm();
    }

    /** Hari ini libur (libur rutin mingguan atau libur sementara)? */
    public boolean isHolidayToday() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        // Calendar: Minggu=1 .. Sabtu=7  ->  1=Senin .. 7=Minggu
        int dow = c.get(java.util.Calendar.DAY_OF_WEEK) == 1 ? 7 : c.get(java.util.Calendar.DAY_OF_WEEK) - 1;
        for (String d : closedDays.split(",")) {
            if (d.trim().equals(String.valueOf(dow))) return true;
        }
        if (holidayUntil.length() == 10) {
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    .format(new java.util.Date());
            return today.compareTo(holidayUntil) <= 0;
        }
        return false;
    }

    public double rating() {
        return ratingCount == 0 ? 0 : ratingSum / ratingCount;
    }

    /** Buka = saklar mitra ON dan sedang dalam jam operasional. */
    public boolean isOpenNow() {
        return isOpen && active && !isHolidayToday() && Fmt.inHours(openHour, closeHour);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("ownerId", ownerId);
        o.put("name", name);
        o.put("description", description);
        o.put("category", category);
        o.put("kecamatan", kecamatan);
        o.put("address", address);
        o.put("phone", phone);
        o.put("emoji", emoji);
        o.put("imageId", imageId);
        o.put("openHour", openHour);
        o.put("closeHour", closeHour);
        o.put("isOpen", isOpen);
        o.put("active", active);
        o.put("selfDelivery", selfDelivery);
        o.put("maxKm", maxKm);
        o.put("closedDays", closedDays);
        o.put("holidayUntil", holidayUntil);
        o.put("ratingSum", ratingSum);
        o.put("ratingCount", ratingCount);
        o.put("createdAt", createdAt);
        o.put("lat", lat);
        o.put("lng", lng);
        return o;
    }

    public static Restaurant fromJson(JSONObject o) {
        Restaurant r = new Restaurant();
        r.id = o.optString("id");
        r.ownerId = o.optString("ownerId");
        r.name = o.optString("name");
        r.description = o.optString("description");
        r.category = o.optString("category", "Nasi");
        r.kecamatan = o.optString("kecamatan");
        r.address = o.optString("address");
        r.phone = o.optString("phone");
        r.emoji = o.optString("emoji", "🍽️");
        r.imageId = o.optString("imageId");
        r.openHour = o.optString("openHour", "08:00");
        r.closeHour = o.optString("closeHour", "21:00");
        r.isOpen = o.optBoolean("isOpen", true);
        r.active = o.optBoolean("active", true);
        r.selfDelivery = o.optBoolean("selfDelivery");
        r.maxKm = o.optDouble("maxKm", 0);
        r.closedDays = o.optString("closedDays");
        r.holidayUntil = o.optString("holidayUntil");
        r.ratingSum = o.optDouble("ratingSum", 0);
        r.ratingCount = o.optInt("ratingCount");
        r.createdAt = o.optLong("createdAt");
        r.lat = o.optDouble("lat", 0);
        r.lng = o.optDouble("lng", 0);
        return r;
    }
}
