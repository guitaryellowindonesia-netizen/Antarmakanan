package com.antarmakan.app.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.antarmakan.app.model.CartItem;
import com.antarmakan.app.model.MenuItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Keranjang belanja (satu resto per keranjang), disimpan di SharedPreferences. */
public class Cart {
    private static Cart instance;

    private final SharedPreferences sp;
    private String restoId = "";
    private String restoName = "";
    private final List<CartItem> items = new ArrayList<CartItem>();

    private Cart(Context c) {
        sp = c.getApplicationContext().getSharedPreferences("cart", Context.MODE_PRIVATE);
        load();
    }

    public static synchronized Cart get(Context c) {
        if (instance == null) instance = new Cart(c);
        return instance;
    }

    public String getRestoId() { return restoId; }
    public String getRestoName() { return restoName; }
    public List<CartItem> getItems() { return items; }
    public boolean isEmpty() { return items.isEmpty(); }

    public boolean isFromOtherResto(String rid) {
        return !items.isEmpty() && !restoId.equals(rid);
    }

    /** Total jumlah sebuah menu (semua varian). */
    public int qtyOf(String menuId) {
        int n = 0;
        for (CartItem ci : items) if (ci.menuId.equals(menuId)) n += ci.qty;
        return n;
    }

    public void add(String rid, String rname, MenuItem m) {
        add(rid, rname, m, "", 0);
    }

    /** Tambah menu dengan pilihan varian (options) & tambahan harga. */
    public void add(String rid, String rname, MenuItem m, String options, long extra) {
        if (isFromOtherResto(rid)) clear();
        restoId = rid;
        restoName = rname;
        String key = m.id + "|" + (options == null ? "" : options);
        CartItem ci = find(key);
        if (ci == null) {
            ci = new CartItem();
            ci.menuId = m.id;
            ci.name = m.name;
            ci.emoji = m.emoji;
            ci.price = m.price + extra;
            ci.options = options == null ? "" : options;
            ci.qty = 0;
            items.add(ci);
        }
        ci.qty++;
        save();
    }

    /** Tambah baris utuh (untuk "Pesan lagi"). */
    public void addLine(String rid, String rname, CartItem line) {
        if (isFromOtherResto(rid)) clear();
        restoId = rid;
        restoName = rname;
        CartItem ci = find(line.key());
        if (ci == null) {
            items.add(line);
        } else {
            ci.qty += line.qty;
        }
        save();
    }

    /** Kurangi satu berdasarkan kunci baris. */
    public void remove(String key) {
        CartItem ci = find(key);
        if (ci == null) return;
        ci.qty--;
        if (ci.qty <= 0) items.remove(ci);
        if (items.isEmpty()) restoId = "";
        save();
    }

    /** Kurangi satu dari menu (baris terakhir yang ditambahkan). */
    public void removeOne(String menuId) {
        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i).menuId.equals(menuId)) {
                remove(items.get(i).key());
                return;
            }
        }
    }

    public void increase(String key) {
        CartItem ci = find(key);
        if (ci != null) {
            ci.qty++;
            save();
        }
    }

    public void setNote(String key, String note) {
        CartItem ci = find(key);
        if (ci != null) {
            ci.note = note;
            save();
        }
    }

    public int count() {
        int n = 0;
        for (CartItem ci : items) n += ci.qty;
        return n;
    }

    public long subtotal() {
        long t = 0;
        for (CartItem ci : items) t += ci.total();
        return t;
    }

    public void clear() {
        items.clear();
        restoId = "";
        restoName = "";
        save();
    }

    private CartItem find(String key) {
        for (CartItem ci : items) if (ci.key().equals(key)) return ci;
        return null;
    }

    private void save() {
        try {
            JSONArray arr = new JSONArray();
            for (CartItem ci : items) {
                JSONObject o = new JSONObject();
                o.put("m", ci.menuId);
                o.put("n", ci.name);
                o.put("e", ci.emoji);
                o.put("p", ci.price);
                o.put("q", ci.qty);
                o.put("t", ci.note);
                o.put("o", ci.options);
                arr.put(o);
            }
            sp.edit().putString("resto", restoId).putString("rname", restoName)
                    .putString("items", arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private void load() {
        try {
            restoId = sp.getString("resto", "");
            restoName = sp.getString("rname", "");
            JSONArray arr = new JSONArray(sp.getString("items", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                CartItem ci = new CartItem();
                ci.menuId = o.getString("m");
                ci.name = o.getString("n");
                ci.emoji = o.optString("e", "🍽️");
                ci.price = o.getLong("p");
                ci.qty = o.getInt("q");
                ci.note = o.optString("t", "");
                ci.options = o.optString("o", "");
                items.add(ci);
            }
        } catch (Exception e) {
            items.clear();
        }
    }
}
