package com.antarmakan.app.model;

import org.json.JSONException;
import org.json.JSONObject;

public class OrderItem {
    public String menuId = "";
    public String name = "";
    public long price;
    public int qty;
    public String note = "";
    public String options = "";   // pilihan varian "Grup=Pilihan;..."

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("menuId", menuId);
        o.put("name", name);
        o.put("price", price);
        o.put("qty", qty);
        o.put("note", note);
        o.put("options", options);
        return o;
    }

    public static OrderItem fromJson(JSONObject o) {
        OrderItem i = new OrderItem();
        i.menuId = o.optString("menuId");
        i.name = o.optString("name");
        i.price = o.optLong("price");
        i.qty = o.optInt("qty");
        i.note = o.optString("note");
        i.options = o.optString("options");
        return i;
    }
}
