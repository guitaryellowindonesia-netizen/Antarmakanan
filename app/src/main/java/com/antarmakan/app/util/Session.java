package com.antarmakan.app.util;

import android.content.Context;
import android.content.SharedPreferences;

public final class Session {
    private static final String PREF = "session";
    private Session() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static void login(Context c, String userId, String role) {
        sp(c).edit().putString("user_id", userId).putString("role", role).apply();
    }

    public static String userId(Context c) {
        return sp(c).getString("user_id", "");
    }

    public static String role(Context c) {
        return sp(c).getString("role", "");
    }

    public static boolean isLoggedIn(Context c) {
        return userId(c).length() > 0;
    }

    public static void logout(Context c) {
        sp(c).edit().clear().apply();
        c.getApplicationContext().getSharedPreferences("firebase_auth", Context.MODE_PRIVATE)
                .edit().clear().apply();
        Cart.get(c).clear();
    }
}
