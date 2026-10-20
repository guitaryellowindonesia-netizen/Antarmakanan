package com.antarmakan.app.model;

import org.json.JSONException;
import org.json.JSONObject;

public class MenuItem {
    public String id = "";
    public String restoId = "";
    public String name = "";
    public String description = "";
    public String category = "Makanan";
    public String emoji = "🍽️";
    public long price;
    public boolean available = true;
    public boolean recommended;

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("restoId", restoId);
        o.put("name", name);
        o.put("description", description);
        o.put("category", category);
        o.put("emoji", emoji);
        o.put("price", price);
        o.put("available", available);
        o.put("recommended", recommended);
        return o;
    }

    public static MenuItem fromJson(JSONObject o) {
        MenuItem m = new MenuItem();
        m.id = o.optString("id");
        m.restoId = o.optString("restoId");
        m.name = o.optString("name");
        m.description = o.optString("description");
        m.category = o.optString("category", "Makanan");
        m.emoji = o.optString("emoji", "🍽️");
        m.price = o.optLong("price");
        m.available = o.optBoolean("available", true);
        m.recommended = o.optBoolean("recommended");
        return m;
    }
}
