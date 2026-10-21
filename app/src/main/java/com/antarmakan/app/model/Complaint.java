package com.antarmakan.app.model;

import org.json.JSONException;
import org.json.JSONObject;

/** Laporan masalah pesanan dari pelanggan, ditangani admin. */
public class Complaint {
    public static final String OPEN = "OPEN";
    public static final String RESOLVED = "RESOLVED";
    public static final String REJECTED = "REJECTED";

    public static final String[] CATEGORIES = {
            "Pesanan tidak sampai",
            "Item kurang / salah",
            "Makanan rusak / basi",
            "Pengantar tidak sopan",
            "Pengantaran terlalu lama",
            "Lainnya"
    };

    public String id = "";          // = orderId (satu komplain per pesanan)
    public String orderId = "";
    public String orderCode = "";
    public String customerId = "";
    public String customerName = "";
    public String customerPhone = "";
    public String restoName = "";
    public String restoOwnerId = "";
    public String driverId = "";
    public String category = "";
    public String text = "";
    public String imageId = "";
    public String status = OPEN;
    public long refund;
    public String adminNote = "";
    public long orderTotal;
    public long createdAt;

    public String statusLabel() {
        if (RESOLVED.equals(status)) return "Selesai";
        if (REJECTED.equals(status)) return "Ditolak";
        return "Diproses";
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("orderId", orderId);
        o.put("orderCode", orderCode);
        o.put("customerId", customerId);
        o.put("customerName", customerName);
        o.put("customerPhone", customerPhone);
        o.put("restoName", restoName);
        o.put("restoOwnerId", restoOwnerId);
        o.put("driverId", driverId);
        o.put("category", category);
        o.put("text", text);
        o.put("imageId", imageId);
        o.put("status", status);
        o.put("refund", refund);
        o.put("adminNote", adminNote);
        o.put("orderTotal", orderTotal);
        o.put("createdAt", createdAt);
        return o;
    }

    public static Complaint fromJson(JSONObject o) {
        Complaint c = new Complaint();
        c.id = o.optString("id");
        c.orderId = o.optString("orderId");
        c.orderCode = o.optString("orderCode");
        c.customerId = o.optString("customerId");
        c.customerName = o.optString("customerName");
        c.customerPhone = o.optString("customerPhone");
        c.restoName = o.optString("restoName");
        c.restoOwnerId = o.optString("restoOwnerId");
        c.driverId = o.optString("driverId");
        c.category = o.optString("category");
        c.text = o.optString("text");
        c.imageId = o.optString("imageId");
        c.status = o.optString("status", OPEN);
        c.refund = o.optLong("refund");
        c.adminNote = o.optString("adminNote");
        c.orderTotal = o.optLong("orderTotal");
        c.createdAt = o.optLong("createdAt");
        return c;
    }
}
