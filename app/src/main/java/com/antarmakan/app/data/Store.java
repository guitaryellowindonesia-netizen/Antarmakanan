package com.antarmakan.app.data;

import org.json.JSONObject;

import java.util.List;

/**
 * Penyimpanan data berbentuk koleksi dokumen JSON.
 * Implementasi: FirebaseStore (Firebase Realtime Database).
 * Semua method bersifat blocking, panggil dari background thread (lihat Async).
 */
public interface Store {

    JSONObject get(String col, String id) throws Exception;

    /** Baca data yang boleh dibaca tanpa login (mis. logins/{nomorHP}). */
    JSONObject getPublic(String col, String id) throws Exception;

    List<JSONObject> list(String col) throws Exception;

    /** Ambil dokumen yang field-nya sama dengan value (String/Number/Boolean). */
    List<JSONObject> query(String col, String field, Object value) throws Exception;

    /** Dokumen dengan field (angka) > after, maksimal limit terbaru. after < 0 = semua. */
    List<JSONObject> since(String col, String field, long after, int limit) throws Exception;

    void put(String col, String id, JSONObject obj) throws Exception;

    void delete(String col, String id) throws Exception;

    /**
     * Ubah beberapa lokasi sekaligus secara atomic (semua berhasil atau semua gagal).
     * Key = path lengkap, mis. "users/abc/balance". Nilai null = hapus.
     */
    void update(java.util.Map<String, Object> paths) throws Exception;

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
