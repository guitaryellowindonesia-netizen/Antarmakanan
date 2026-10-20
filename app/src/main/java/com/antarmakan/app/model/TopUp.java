package com.antarmakan.app.model;

import org.json.JSONException;
import org.json.JSONObject;

/** Permintaan top up (isi saldo) atau penarikan saldo, disetujui admin. */
public class TopUp {
    public static final String TYPE_TOPUP = "TOPUP";
    public static final String TYPE_WITHDRAW = "WITHDRAW";
    public static final int PENDING = 0;
    public static final int APPROVED = 1;
    public static final int REJECTED = 2;

    public String id = "";
    public String userId = "";
    public String userName = "";
    public String userPhone = "";
    public String type = TYPE_TOPUP;
    public long amount;
    public String info = "";   // nama pengirim / rekening tujuan
    public int status = PENDING;
    public long createdAt;

    public String statusLabel() {
        if (status == APPROVED) return "Disetujui";
        if (status == REJECTED) return "Ditolak";
        return "Menunggu";
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("userId", userId);
        o.put("userName", userName);
        o.put("userPhone", userPhone);
        o.put("type", type);
        o.put("amount", amount);
        o.put("info", info);
        o.put("status", status);
        o.put("createdAt", createdAt);
        return o;
    }

    public static TopUp fromJson(JSONObject o) {
        TopUp t = new TopUp();
        t.id = o.optString("id");
        t.userId = o.optString("userId");
        t.userName = o.optString("userName");
        t.userPhone = o.optString("userPhone");
        t.type = o.optString("type", TYPE_TOPUP);
        t.amount = o.optLong("amount");
        t.info = o.optString("info");
        t.status = o.optInt("status");
        t.createdAt = o.optLong("createdAt");
        return t;
    }
}
