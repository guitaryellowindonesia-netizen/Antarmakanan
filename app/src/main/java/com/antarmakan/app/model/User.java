package com.antarmakan.app.model;

import org.json.JSONException;
import org.json.JSONObject;

public class User {
    public static final String ROLE_CUSTOMER = "customer";
    public static final String ROLE_MERCHANT = "merchant";
    public static final String ROLE_DRIVER = "driver";
    public static final String ROLE_ADMIN = "admin";

    public String id = "";
    public String name = "";
    public String phone = "";
    public String email = "";        // akun Google
    public String role = ROLE_CUSTOMER;
    public String address = "";
    public String kecamatan = "";
    public String vehicle = "";      // driver: motor & plat nomor
    public long balance;
    public boolean online;           // driver sedang aktif
    public boolean active = true;    // driver/mitra harus disetujui admin
    public String passwordHash = ""; // hanya dipakai di mode offline
    public long createdAt;
    public double lat, lng;          // titik lokasi (GPS), 0 = belum diatur
    public double ratingSum;         // rating driver
    public int ratingCount;

    public double rating() {
        return ratingCount == 0 ? 0 : ratingSum / ratingCount;
    }

    public boolean isCustomer() { return ROLE_CUSTOMER.equals(role); }
    public boolean isMerchant() { return ROLE_MERCHANT.equals(role); }
    public boolean isDriver() { return ROLE_DRIVER.equals(role); }
    public boolean isAdmin() { return ROLE_ADMIN.equals(role); }

    public static String roleLabel(String role) {
        if (ROLE_MERCHANT.equals(role)) return "Mitra Resto";
        if (ROLE_DRIVER.equals(role)) return "Driver";
        if (ROLE_ADMIN.equals(role)) return "Admin";
        return "Pelanggan";
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("phone", phone);
        if (email.length() > 0) o.put("email", email);
        o.put("role", role);
        o.put("address", address);
        o.put("kecamatan", kecamatan);
        o.put("vehicle", vehicle);
        o.put("balance", balance);
        o.put("online", online);
        o.put("active", active);
        if (passwordHash.length() > 0) o.put("passwordHash", passwordHash);
        o.put("createdAt", createdAt);
        o.put("lat", lat);
        o.put("lng", lng);
        o.put("ratingSum", ratingSum);
        o.put("ratingCount", ratingCount);
        return o;
    }

    public static User fromJson(JSONObject o) {
        User u = new User();
        u.id = o.optString("id");
        u.name = o.optString("name");
        u.phone = o.optString("phone");
        u.email = o.optString("email");
        u.role = o.optString("role", ROLE_CUSTOMER);
        u.address = o.optString("address");
        u.kecamatan = o.optString("kecamatan");
        u.vehicle = o.optString("vehicle");
        u.balance = o.optLong("balance");
        u.online = o.optBoolean("online");
        u.active = o.optBoolean("active", true);
        u.passwordHash = o.optString("passwordHash");
        u.createdAt = o.optLong("createdAt");
        u.lat = o.optDouble("lat", 0);
        u.lng = o.optDouble("lng", 0);
        u.ratingSum = o.optDouble("ratingSum", 0);
        u.ratingCount = o.optInt("ratingCount");
        return u;
    }
}
