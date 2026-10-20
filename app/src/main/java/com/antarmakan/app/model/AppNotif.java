package com.antarmakan.app.model;

import org.json.JSONObject;

/** Notifikasi untuk pengguna (atau pengumuman admin). */
public class AppNotif {
    public String id = "";
    public String title = "";
    public String body = "";
    public String orderId = "";
    public String role = "all";   // untuk pengumuman: all/customer/merchant/driver
    public long createdAt;

    public static AppNotif fromJson(JSONObject o) {
        AppNotif n = new AppNotif();
        n.id = o.optString("id");
        n.title = o.optString("title");
        n.body = o.optString("body");
        n.orderId = o.optString("orderId");
        n.role = o.optString("role", "all");
        n.createdAt = o.optLong("createdAt");
        return n;
    }
}
