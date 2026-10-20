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
    public double ratingSum;
    public int ratingCount;
    public long createdAt;

    /** Dihitung di aplikasi (tidak disimpan) */
    public double distanceKm;
    public int deliveryFee;

    public double rating() {
        return ratingCount == 0 ? 0 : ratingSum / ratingCount;
    }

    /** Buka = saklar mitra ON dan sedang dalam jam operasional. */
    public boolean isOpenNow() {
        return isOpen && active && Fmt.inHours(openHour, closeHour);
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
        o.put("ratingSum", ratingSum);
        o.put("ratingCount", ratingCount);
        o.put("createdAt", createdAt);
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
        r.ratingSum = o.optDouble("ratingSum", 0);
        r.ratingCount = o.optInt("ratingCount");
        r.createdAt = o.optLong("createdAt");
        return r;
    }
}
