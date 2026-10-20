package com.antarmakan.app.data;

import org.json.JSONObject;

import java.util.List;

/**
 * Penyimpanan data berbentuk koleksi dokumen JSON.
 * Ada dua implementasi:
 *  - LocalStore    : SQLite di HP (mode demo offline)
 *  - FirebaseStore : Firebase Realtime Database (mode online)
 * Semua method bersifat blocking, panggil dari background thread (lihat Async).
 */
public interface Store {

    JSONObject get(String col, String id) throws Exception;

    List<JSONObject> list(String col) throws Exception;

    /** Ambil dokumen yang field-nya sama dengan value (String/Number/Boolean). */
    List<JSONObject> query(String col, String field, Object value) throws Exception;

    void put(String col, String id, JSONObject obj) throws Exception;

    void delete(String col, String id) throws Exception;

    /**
     * Ubah dokumen secara aman (atomic) walau diakses banyak HP bersamaan.
     * Updater menerima data sekarang (bisa null) dan mengembalikan data baru.
     * Lempar AppException dari updater untuk membatalkan.
     */
    JSONObject transact(String col, String id, Updater updater) throws Exception;

    interface Updater {
        JSONObject update(JSONObject current) throws Exception;
    }
}
