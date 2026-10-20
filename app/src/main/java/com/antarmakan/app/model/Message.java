package com.antarmakan.app.model;

import org.json.JSONException;
import org.json.JSONObject;

public class Message {
    public String id = "";
    public String orderId = "";
    public String senderId = "";
    public String senderName = "";
    public String senderRole = "";
    public String text = "";
    public long createdAt;

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("orderId", orderId);
        o.put("senderId", senderId);
        o.put("senderName", senderName);
        o.put("senderRole", senderRole);
        o.put("text", text);
        o.put("createdAt", createdAt);
        return o;
    }

    public static Message fromJson(JSONObject o) {
        Message m = new Message();
        m.id = o.optString("id");
        m.orderId = o.optString("orderId");
        m.senderId = o.optString("senderId");
        m.senderName = o.optString("senderName");
        m.senderRole = o.optString("senderRole");
        m.text = o.optString("text");
        m.createdAt = o.optLong("createdAt");
        return m;
    }
}
