package com.antarmakan.app.model;

import org.json.JSONException;
import org.json.JSONObject;

/** Riwayat mutasi saldo */
public class WalletTx {
    public String id = "";
    public String userId = "";
    public long amount;
    public String description = "";
    public long createdAt;

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("userId", userId);
        o.put("amount", amount);
        o.put("description", description);
        o.put("createdAt", createdAt);
        return o;
    }

    public static WalletTx fromJson(JSONObject o) {
        WalletTx t = new WalletTx();
        t.id = o.optString("id");
        t.userId = o.optString("userId");
        t.amount = o.optLong("amount");
        t.description = o.optString("description");
        t.createdAt = o.optLong("createdAt");
        return t;
    }
}
