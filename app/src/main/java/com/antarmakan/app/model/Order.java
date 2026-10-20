package com.antarmakan.app.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Order {
    public static final int MENUNGGU = 0;   // menunggu konfirmasi resto
    public static final int DIMASAK = 1;    // diterima resto & sedang dimasak
    public static final int SIAP = 2;       // makanan siap, menunggu driver
    public static final int DIJEMPUT = 3;   // driver menuju / di resto
    public static final int DIANTAR = 4;    // driver mengantar ke pelanggan
    public static final int SELESAI = 5;
    public static final int DIBATALKAN = 9;

    public static final String BAYAR_TUNAI = "TUNAI";
    public static final String BAYAR_SALDO = "SALDO";

    public String id = "";
    public String code = "";
    public String customerId = "";
    public String restoId = "";
    public String driverId = "";
    public int status;
    public long subtotal;
    public long deliveryFee;
    public long serviceFee;
    public long discount;
    public long total;
    public String paymentMethod = BAYAR_TUNAI;
    public String promoCode = "";
    public String address = "";
    public String kecamatan = "";
    public String note = "";
    public String cancelReason = "";
    public double distanceKm;
    public long createdAt;
    public long updatedAt;
    public boolean rated;
    public boolean settled;
    public boolean selfDelivery;   // diantar sendiri oleh penjual
    public double custLat, custLng;     // titik antar (GPS pelanggan)
    public double restoLat, restoLng;   // titik resto

    // salinan data agar mudah ditampilkan
    public String restoName = "";
    public String restoEmoji = "🍽️";
    public String restoAddress = "";
    public String restoKecamatan = "";
    public String restoPhone = "";
    public String restoOwnerId = "";
    public String customerName = "";
    public String customerPhone = "";
    public String driverName = "";
    public String driverPhone = "";
    public String driverVehicle = "";

    public List<OrderItem> items = new ArrayList<OrderItem>();

    public boolean isActive() {
        return status != SELESAI && status != DIBATALKAN;
    }

    public boolean hasDriver() {
        return driverId != null && driverId.length() > 0;
    }

    public int itemCount() {
        int n = 0;
        for (OrderItem i : items) n += i.qty;
        return n;
    }

    public static String statusLabel(int s) {
        switch (s) {
            case MENUNGGU: return "Menunggu konfirmasi resto";
            case DIMASAK: return "Pesanan sedang dimasak";
            case SIAP: return "Makanan siap, mencari driver";
            case DIJEMPUT: return "Driver menuju resto";
            case DIANTAR: return "Pesanan sedang diantar";
            case SELESAI: return "Pesanan selesai";
            case DIBATALKAN: return "Pesanan dibatalkan";
            default: return "-";
        }
    }

    public static String statusShort(int s) {
        switch (s) {
            case MENUNGGU: return "BARU";
            case DIMASAK: return "DIMASAK";
            case SIAP: return "SIAP";
            case DIJEMPUT: return "DIJEMPUT";
            case DIANTAR: return "DIANTAR";
            case SELESAI: return "SELESAI";
            case DIBATALKAN: return "BATAL";
            default: return "-";
        }
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("code", code);
        o.put("customerId", customerId);
        o.put("restoId", restoId);
        o.put("driverId", driverId);
        o.put("status", status);
        o.put("subtotal", subtotal);
        o.put("deliveryFee", deliveryFee);
        o.put("serviceFee", serviceFee);
        o.put("discount", discount);
        o.put("total", total);
        o.put("paymentMethod", paymentMethod);
        o.put("promoCode", promoCode);
        o.put("address", address);
        o.put("kecamatan", kecamatan);
        o.put("note", note);
        o.put("cancelReason", cancelReason);
        o.put("distanceKm", distanceKm);
        o.put("createdAt", createdAt);
        o.put("updatedAt", updatedAt);
        o.put("rated", rated);
        o.put("settled", settled);
        o.put("selfDelivery", selfDelivery);
        o.put("custLat", custLat);
        o.put("custLng", custLng);
        o.put("restoLat", restoLat);
        o.put("restoLng", restoLng);
        o.put("restoName", restoName);
        o.put("restoEmoji", restoEmoji);
        o.put("restoAddress", restoAddress);
        o.put("restoKecamatan", restoKecamatan);
        o.put("restoPhone", restoPhone);
        o.put("restoOwnerId", restoOwnerId);
        o.put("customerName", customerName);
        o.put("customerPhone", customerPhone);
        o.put("driverName", driverName);
        o.put("driverPhone", driverPhone);
        o.put("driverVehicle", driverVehicle);
        JSONArray arr = new JSONArray();
        for (OrderItem i : items) arr.put(i.toJson());
        o.put("items", arr);
        return o;
    }

    public static Order fromJson(JSONObject o) {
        Order r = new Order();
        r.id = o.optString("id");
        r.code = o.optString("code");
        r.customerId = o.optString("customerId");
        r.restoId = o.optString("restoId");
        r.driverId = o.optString("driverId");
        r.status = o.optInt("status");
        r.subtotal = o.optLong("subtotal");
        r.deliveryFee = o.optLong("deliveryFee");
        r.serviceFee = o.optLong("serviceFee");
        r.discount = o.optLong("discount");
        r.total = o.optLong("total");
        r.paymentMethod = o.optString("paymentMethod", BAYAR_TUNAI);
        r.promoCode = o.optString("promoCode");
        r.address = o.optString("address");
        r.kecamatan = o.optString("kecamatan");
        r.note = o.optString("note");
        r.cancelReason = o.optString("cancelReason");
        r.distanceKm = o.optDouble("distanceKm", 0);
        r.createdAt = o.optLong("createdAt");
        r.updatedAt = o.optLong("updatedAt");
        r.rated = o.optBoolean("rated");
        r.settled = o.optBoolean("settled");
        r.selfDelivery = o.optBoolean("selfDelivery");
        r.custLat = o.optDouble("custLat", 0);
        r.custLng = o.optDouble("custLng", 0);
        r.restoLat = o.optDouble("restoLat", 0);
        r.restoLng = o.optDouble("restoLng", 0);
        r.restoName = o.optString("restoName");
        r.restoEmoji = o.optString("restoEmoji", "🍽️");
        r.restoAddress = o.optString("restoAddress");
        r.restoKecamatan = o.optString("restoKecamatan");
        r.restoPhone = o.optString("restoPhone");
        r.restoOwnerId = o.optString("restoOwnerId");
        r.customerName = o.optString("customerName");
        r.customerPhone = o.optString("customerPhone");
        r.driverName = o.optString("driverName");
        r.driverPhone = o.optString("driverPhone");
        r.driverVehicle = o.optString("driverVehicle");
        JSONArray arr = o.optJSONArray("items");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject it = arr.optJSONObject(i);
                if (it != null) r.items.add(OrderItem.fromJson(it));
            }
        }
        return r;
    }
}
