package com.antarmakan.app.model;

import org.json.JSONException;
import org.json.JSONObject;

public class Promo {
    public static final String TYPE_PERSEN = "PERSEN";
    public static final String TYPE_POTONGAN = "POTONGAN";
    public static final String TYPE_ONGKIR = "ONGKIR";

    public String id = "";
    public String code = "";
    public String description = "";
    public String type = TYPE_POTONGAN;
    public long value;        // persen (untuk PERSEN) atau rupiah
    public long maxDiscount;  // batas potongan untuk PERSEN (0 = tanpa batas)
    public long minOrder;
    public int quota = -1;    // sisa kuota (-1 = tanpa batas)
    public boolean active = true;

    /** Hitung potongan; 0 jika promo tidak berlaku. */
    public long calcDiscount(long subtotal, long ongkir) {
        if (!active || subtotal < minOrder || quota == 0) return 0;
        long d;
        if (TYPE_PERSEN.equals(type)) {
            d = subtotal * value / 100;
            if (maxDiscount > 0 && d > maxDiscount) d = maxDiscount;
        } else if (TYPE_ONGKIR.equals(type)) {
            d = Math.min(value, ongkir);
        } else {
            d = value;
        }
        return Math.max(0, Math.min(d, subtotal + ongkir));
    }

    public String typeLabel() {
        if (TYPE_PERSEN.equals(type)) return "Diskon " + value + "%";
        if (TYPE_ONGKIR.equals(type)) return "Gratis ongkir";
        return "Potongan harga";
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("code", code);
        o.put("description", description);
        o.put("type", type);
        o.put("value", value);
        o.put("maxDiscount", maxDiscount);
        o.put("minOrder", minOrder);
        o.put("quota", quota);
        o.put("active", active);
        return o;
    }

    public static Promo fromJson(JSONObject o) {
        Promo p = new Promo();
        p.id = o.optString("id");
        p.code = o.optString("code");
        p.description = o.optString("description");
        p.type = o.optString("type", TYPE_POTONGAN);
        p.value = o.optLong("value");
        p.maxDiscount = o.optLong("maxDiscount");
        p.minOrder = o.optLong("minOrder");
        p.quota = o.optInt("quota", -1);
        p.active = o.optBoolean("active", true);
        return p;
    }
}
