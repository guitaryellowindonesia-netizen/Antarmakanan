package com.antarmakan.app.model;

import org.json.JSONException;
import org.json.JSONObject;

public class Review {
    public String id = "";
    public String orderId = "";
    public String restoId = "";
    public String customerId = "";
    public String customerName = "";
    public String driverId = "";
    public int rating;
    public int driverRating;
    public String comment = "";
    public String reply = "";
    public long createdAt;

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("orderId", orderId);
        o.put("restoId", restoId);
        o.put("customerId", customerId);
        o.put("customerName", customerName);
        o.put("driverId", driverId);
        o.put("rating", rating);
        o.put("driverRating", driverRating);
        o.put("comment", comment);
        o.put("reply", reply);
        o.put("createdAt", createdAt);
        return o;
    }

    public static Review fromJson(JSONObject o) {
        Review r = new Review();
        r.id = o.optString("id");
        r.orderId = o.optString("orderId");
        r.restoId = o.optString("restoId");
        r.customerId = o.optString("customerId");
        r.customerName = o.optString("customerName");
        r.driverId = o.optString("driverId");
        r.rating = o.optInt("rating");
        r.driverRating = o.optInt("driverRating");
        r.comment = o.optString("comment");
        r.reply = o.optString("reply");
        r.createdAt = o.optLong("createdAt");
        return r;
    }
}
