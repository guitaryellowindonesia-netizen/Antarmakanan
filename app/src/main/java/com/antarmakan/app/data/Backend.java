package com.antarmakan.app.data;

import android.content.Context;


/** Koneksi ke server Firebase. */
public final class Backend {
    private Backend() {}

    private static Store store;
    private static FirebaseAuthRest auth;
    private static Repo repo;

    public static synchronized Repo repo(Context c) {
        if (repo == null) {
            Context app = c.getApplicationContext();
            auth = new FirebaseAuthRest(app);
            store = new FirebaseStore(auth);
            repo = new Repo(app, store, auth);
        }
        return repo;
    }
}
