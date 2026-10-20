package com.antarmakan.app.data;

import com.antarmakan.app.Config;

import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Penyimpanan online memakai Firebase Realtime Database (REST API). */
public class FirebaseStore implements Store {

    private final FirebaseAuthRest auth;
    private final String base;

    public FirebaseStore(FirebaseAuthRest auth) {
        this.auth = auth;
        String b = Config.FIREBASE_DB_URL.trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        this.base = b;
    }

    private String url(String col, String id, String extra) throws Exception {
        StringBuilder sb = new StringBuilder(base).append('/').append(col);
        if (id != null) sb.append('/').append(URLEncoder.encode(id, "UTF-8"));
        sb.append(".json?auth=").append(auth.idToken());
        if (extra != null) sb.append('&').append(extra);
        return sb.toString();
    }

    private static void check(Http.Response r) {
        if (r.code == 401 || r.code == 403) {
            throw new AppException("Akses ditolak server. Cek Rules Firebase / login ulang");
        }
        if (r.code == 400 && r.body.contains("Index not defined")) {
            throw new AppException("Index belum dibuat. Pasang Rules dari README di Firebase");
        }
        if (r.code >= 400) throw new AppException("Server error (" + r.code + ")");
    }

    @Override
    public JSONObject get(String col, String id) throws Exception {
        Http.Response r = Http.request("GET", url(col, id, null), null, null);
        check(r);
        return parseObj(r.body);
    }

    @Override
    public List<JSONObject> list(String col) throws Exception {
        Http.Response r = Http.request("GET", url(col, null, null), null, null);
        check(r);
        return children(r.body);
    }

    @Override
    public List<JSONObject> query(String col, String field, Object value) throws Exception {
        String v = value instanceof String ? JSONObject.quote((String) value) : String.valueOf(value);
        String extra = "orderBy=" + URLEncoder.encode("\"" + field + "\"", "UTF-8")
                + "&equalTo=" + URLEncoder.encode(v, "UTF-8");
        Http.Response r = Http.request("GET", url(col, null, extra), null, null);
        check(r);
        return children(r.body);
    }

    @Override
    public void put(String col, String id, JSONObject obj) throws Exception {
        Http.Response r = Http.request("PUT", url(col, id, "print=silent"), obj.toString(), null);
        check(r);
    }

    @Override
    public void delete(String col, String id) throws Exception {
        Http.Response r = Http.request("DELETE", url(col, id, null), null, null);
        check(r);
    }

    @Override
    public JSONObject transact(String col, String id, Updater updater) throws Exception {
        Map<String, String> h = new HashMap<String, String>();
        h.put("X-Firebase-ETag", "true");
        for (int attempt = 0; attempt < 10; attempt++) {
            Http.Response r = Http.request("GET", url(col, id, null), null, h);
            check(r);
            JSONObject cur = parseObj(r.body);
            JSONObject next = updater.update(cur);

            Map<String, String> ph = new HashMap<String, String>();
            ph.put("if-match", r.etag == null ? "" : r.etag);
            Http.Response w;
            if (next == null) {
                w = Http.request("DELETE", url(col, id, null), null, ph);
            } else {
                w = Http.request("PUT", url(col, id, "print=silent"), next.toString(), ph);
            }
            if (w.code == 412) continue; // data berubah oleh HP lain, ulangi
            check(w);
            return next;
        }
        throw new AppException("Server sibuk, coba lagi");
    }

    private static JSONObject parseObj(String body) throws Exception {
        String b = body == null ? "" : body.trim();
        if (b.length() == 0 || b.equals("null") || !b.startsWith("{")) return null;
        return new JSONObject(b);
    }

    private static List<JSONObject> children(String body) throws Exception {
        List<JSONObject> out = new ArrayList<JSONObject>();
        JSONObject all = parseObj(body);
        if (all == null) return out;
        Iterator<String> keys = all.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            JSONObject o = all.optJSONObject(k);
            if (o == null) continue;
            if (o.optString("id").length() == 0) o.put("id", k);
            out.add(o);
        }
        return out;
    }
}
