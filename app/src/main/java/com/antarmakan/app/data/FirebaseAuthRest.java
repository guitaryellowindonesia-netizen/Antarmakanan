package com.antarmakan.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.antarmakan.app.Config;

import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

/**
 * Login/daftar memakai Firebase Authentication lewat REST API.
 * Nomor HP diubah menjadi email semu: 08123@antarmakan.app
 */
public class FirebaseAuthRest implements TokenSource {
    private static final String BASE = "https://identitytoolkit.googleapis.com/v1/accounts:";
    private static final String TOKEN_URL = "https://securetoken.googleapis.com/v1/token?key=";

    private final SharedPreferences sp;

    public FirebaseAuthRest(Context c) {
        sp = c.getApplicationContext().getSharedPreferences("firebase_auth", Context.MODE_PRIVATE);
    }

    /** Email semu untuk akun nomor HP (dipakai bila tidak ada email asli). */
    public static String fakeEmail(String phone) {
        return phone.replaceAll("[^0-9]", "") + "@antarmakan.app";
    }

    public static boolean isFakeEmail(String email) {
        return email == null || email.endsWith("@antarmakan.app");
    }

    /** Daftar akun baru dengan email (asli atau semu), mengembalikan uid. */
    public synchronized String signUpEmail(String email, String password) throws Exception {
        return call("signUp", email, password);
    }

    /** Masuk dengan email (asli atau semu), mengembalikan uid. */
    public synchronized String signInEmail(String email, String password) throws Exception {
        return call("signInWithPassword", email, password);
    }

    /** Ganti email login akun yang sedang masuk (untuk pemulihan sandi). */
    public synchronized void updateEmail(String newEmail) throws Exception {
        JSONObject body = new JSONObject();
        body.put("idToken", idToken());
        body.put("email", newEmail);
        body.put("returnSecureToken", true);
        JSONObject o = post("update", body);
        if (o.has("idToken")) {
            save(o.getString("idToken"), o.getString("refreshToken"),
                    o.optLong("expiresIn", 3600), o.getString("localId"));
        }
    }

    /** Kirim email berisi link untuk membuat kata sandi baru. */
    public void sendPasswordReset(String email) throws Exception {
        JSONObject body = new JSONObject();
        body.put("requestType", "PASSWORD_RESET");
        body.put("email", email);
        post("sendOobCode", body);
    }

    /** Hapus akun login yang sedang masuk. */
    public synchronized void deleteAccount() throws Exception {
        JSONObject body = new JSONObject();
        body.put("idToken", idToken());
        post("delete", body);
    }

    private JSONObject post(String action, JSONObject body) throws Exception {
        Http.Response r = Http.request("POST", BASE + action + "?key=" + Config.FIREBASE_API_KEY,
                body.toString(), null);
        JSONObject o = new JSONObject(r.body.length() == 0 ? "{}" : r.body);
        if (r.code != 200) throw new AppException(translate(o));
        return o;
    }

    /** Hasil login Google */
    public static class GoogleResult {
        public String uid;
        public String name;
        public String email;
    }

    /** Masuk dengan Google ID token (dari Credential Manager). */
    public synchronized GoogleResult signInWithGoogle(String googleIdToken) throws Exception {
        JSONObject body = new JSONObject();
        body.put("postBody", "id_token=" + URLEncoder.encode(googleIdToken, "UTF-8")
                + "&providerId=google.com");
        body.put("requestUri", "http://localhost");
        body.put("returnSecureToken", true);
        body.put("returnIdpCredential", true);
        Http.Response r = Http.request("POST", BASE + "signInWithIdp?key=" + Config.FIREBASE_API_KEY,
                body.toString(), null);
        JSONObject o = new JSONObject(r.body.length() == 0 ? "{}" : r.body);
        if (r.code != 200) throw new AppException(translate(o));
        save(o.getString("idToken"), o.getString("refreshToken"),
                o.optLong("expiresIn", 3600), o.getString("localId"));
        GoogleResult g = new GoogleResult();
        g.uid = o.getString("localId");
        g.name = o.optString("displayName", o.optString("fullName", ""));
        g.email = o.optString("email", "");
        return g;
    }

    private String call(String action, String email, String password) throws Exception {
        JSONObject body = new JSONObject();
        body.put("email", email);
        body.put("password", password);
        body.put("returnSecureToken", true);
        Http.Response r = Http.request("POST", BASE + action + "?key=" + Config.FIREBASE_API_KEY,
                body.toString(), null);
        JSONObject o = new JSONObject(r.body.length() == 0 ? "{}" : r.body);
        if (r.code != 200) throw new AppException(translate(o));
        save(o.getString("idToken"), o.getString("refreshToken"),
                o.optLong("expiresIn", 3600), o.getString("localId"));
        return o.getString("localId");
    }

    /** Token untuk akses database; diperbarui otomatis bila kedaluwarsa. */
    public synchronized String idToken() throws Exception {
        String token = sp.getString("id_token", "");
        long exp = sp.getLong("expires_at", 0);
        if (token.length() > 0 && System.currentTimeMillis() < exp) return token;

        String refresh = sp.getString("refresh_token", "");
        if (refresh.length() == 0) throw new AppException("Sesi habis, silakan masuk lagi");
        Map<String, String> h = new HashMap<String, String>();
        h.put("Content-Type", "application/x-www-form-urlencoded");
        Http.Response r = Http.request("POST", TOKEN_URL + Config.FIREBASE_API_KEY,
                "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(refresh, "UTF-8"), h);
        if (r.code != 200) throw new AppException("Sesi habis, silakan masuk lagi");
        JSONObject o = new JSONObject(r.body);
        save(o.getString("id_token"), o.getString("refresh_token"),
                o.optLong("expires_in", 3600), o.getString("user_id"));
        return o.getString("id_token");
    }

    private void save(String idToken, String refresh, long expiresInSec, String uid) {
        sp.edit().putString("id_token", idToken)
                .putString("refresh_token", refresh)
                .putLong("expires_at", System.currentTimeMillis() + (expiresInSec - 120) * 1000)
                .putString("uid", uid)
                .apply();
    }

    private static String translate(JSONObject o) {
        JSONObject err = o.optJSONObject("error");
        String msg = err == null ? "" : err.optString("message");
        if (msg.startsWith("EMAIL_EXISTS")) return "Nomor HP / email sudah terdaftar";
        if (msg.startsWith("INVALID_EMAIL")) return "Format email tidak valid";
        if (msg.startsWith("CREDENTIAL_TOO_OLD") || msg.startsWith("TOKEN_EXPIRED"))
            return "Demi keamanan, keluar lalu masuk lagi, kemudian ulangi";
        if (msg.startsWith("EMAIL_NOT_FOUND") || msg.startsWith("INVALID_PASSWORD")
                || msg.startsWith("INVALID_LOGIN_CREDENTIALS")) return "Nomor HP atau kata sandi salah";
        if (msg.startsWith("WEAK_PASSWORD")) return "Kata sandi minimal 6 karakter";
        if (msg.startsWith("TOO_MANY_ATTEMPTS")) return "Terlalu banyak percobaan, coba lagi nanti";
        if (msg.startsWith("USER_DISABLED")) return "Akun dinonaktifkan";
        if (msg.contains("API key")) return "FIREBASE_API_KEY di Config.java salah";
        if (msg.startsWith("OPERATION_NOT_ALLOWED"))
            return "Aktifkan login Email/Password di Firebase Console";
        return msg.length() > 0 ? msg : "Gagal terhubung ke server";
    }
}
