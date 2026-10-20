package com.antarmakan.app.data;

import android.content.Context;

import com.antarmakan.app.Config;

/** Memilih penyimpanan: online (Firebase) atau offline (SQLite). */
public final class Backend {
    private Backend() {}

    private static Store store;
    private static FirebaseAuthRest auth;
    private static Repo repo;

    public static synchronized Repo repo(Context c) {
        if (repo == null) {
            Context app = c.getApplicationContext();
            if (Config.isOnline()) {
                auth = new FirebaseAuthRest(app);
                store = new FirebaseStore(auth);
            } else {
                store = new LocalStore(app);
            }
            repo = new Repo(app, store, auth);
        }
        return repo;
    }
}
