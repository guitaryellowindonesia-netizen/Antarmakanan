package com.antarmakan.app.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Grup pilihan menu, mis. "Level pedas" (wajib pilih satu) atau "Topping" (boleh banyak). */
public class MenuOption {
    public static final String WAJIB_SATU = "wajib";
    public static final String OPSIONAL_SATU = "opsional";
    public static final String BANYAK = "banyak";

    public String name = "";
    public String type = WAJIB_SATU;
    public List<Choice> choices = new ArrayList<Choice>();

    public static class Choice {
        public String name = "";
        public long price;   // tambahan harga (0 = gratis)
    }

    public String typeLabel() {
        if (BANYAK.equals(type)) return "Boleh pilih banyak";
        if (OPSIONAL_SATU.equals(type)) return "Boleh pilih satu";
        return "Wajib pilih satu";
    }

    public Choice find(String choiceName) {
        for (Choice c : choices) if (c.name.equals(choiceName)) return c;
        return null;
    }

    /** Pilihan ditulis per baris: "Telur=3000" atau "Pedas" (gratis). */
    public static List<Choice> parseChoices(String text) {
        List<Choice> out = new ArrayList<Choice>();
        for (String line : text.split("\n")) {
            String l = line.trim();
            if (l.length() == 0) continue;
            Choice c = new Choice();
            int eq = l.lastIndexOf('=');
            if (eq > 0) {
                c.name = l.substring(0, eq).trim();
                c.price = com.antarmakan.app.util.Fmt.parseLong(l.substring(eq + 1));
            } else {
                c.name = l;
            }
            if (c.name.length() > 0) out.add(c);
        }
        return out;
    }

    public String choicesText() {
        StringBuilder sb = new StringBuilder();
        for (Choice c : choices) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(c.name);
            if (c.price > 0) sb.append('=').append(c.price);
        }
        return sb.toString();
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("name", name);
        o.put("type", type);
        JSONArray arr = new JSONArray();
        for (Choice c : choices) {
            JSONObject j = new JSONObject();
            j.put("name", c.name);
            j.put("price", c.price);
            arr.put(j);
        }
        o.put("choices", arr);
        return o;
    }

    public static MenuOption fromJson(JSONObject o) {
        MenuOption g = new MenuOption();
        g.name = o.optString("name");
        g.type = o.optString("type", WAJIB_SATU);
        JSONArray arr = o.optJSONArray("choices");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject j = arr.optJSONObject(i);
                if (j == null) continue;
                Choice c = new Choice();
                c.name = j.optString("name");
                c.price = j.optLong("price");
                g.choices.add(c);
            }
        }
        return g;
    }

    /**
     * Hitung tambahan harga dari pilihan "Grup=Pilihan;Grup=Pilihan" terhadap daftar grup.
     * Melempar error bila pilihan tidak valid (mis. sudah dihapus resto).
     */
    public static long extraPrice(List<MenuOption> groups, String selections) {
        long extra = 0;
        if (selections == null || selections.length() == 0) {
            for (MenuOption g : groups) {
                if (WAJIB_SATU.equals(g.type) && !g.choices.isEmpty()) {
                    throw new com.antarmakan.app.data.AppException("Pilih " + g.name);
                }
            }
            return 0;
        }
        java.util.Set<String> chosenGroups = new java.util.HashSet<String>();
        for (String part : selections.split(";")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            String gName = part.substring(0, eq), cName = part.substring(eq + 1);
            MenuOption g = null;
            for (MenuOption x : groups) if (x.name.equals(gName)) g = x;
            Choice c = g == null ? null : g.find(cName);
            if (c == null) {
                throw new com.antarmakan.app.data.AppException("Pilihan \"" + cName + "\" sudah tidak tersedia");
            }
            extra += c.price;
            chosenGroups.add(gName);
        }
        for (MenuOption g : groups) {
            if (WAJIB_SATU.equals(g.type) && !g.choices.isEmpty() && !chosenGroups.contains(g.name)) {
                throw new com.antarmakan.app.data.AppException("Pilih " + g.name);
            }
        }
        return extra;
    }

    /** "Level=3;Topping=Telur" -> "Level: 3, Topping: Telur" */
    public static String describe(String selections) {
        if (selections == null || selections.length() == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (String part : selections.split(";")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(part.substring(eq + 1));
        }
        return sb.toString();
    }
}
