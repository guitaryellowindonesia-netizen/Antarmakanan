package com.antarmakan.app.util;

import com.antarmakan.app.Config;

import org.json.JSONObject;

/**
 * Pengaturan yang bisa diubah admin dari aplikasi (tanpa build ulang APK).
 * Nilai awal diambil dari Config.java, lalu ditimpa data "settings/app" di server.
 */
public final class AppSettings {
    private AppSettings() {}

    public static volatile int ONGKIR_DASAR = Config.ONGKIR_DASAR;
    public static volatile double JARAK_DASAR_KM = Config.JARAK_DASAR_KM;
    public static volatile int ONGKIR_PER_KM = Config.ONGKIR_PER_KM;
    public static volatile int ONGKIR_MAKS = Config.ONGKIR_MAKS;
    public static volatile int BIAYA_LAYANAN = Config.BIAYA_LAYANAN;
    public static volatile int KOMISI_PERSEN_RESTO = Config.KOMISI_PERSEN_RESTO;
    public static volatile int KOMISI_PERSEN_DRIVER = Config.KOMISI_PERSEN_DRIVER;
    public static volatile int MIN_TOPUP = Config.MIN_TOPUP;
    public static volatile String INFO_TRANSFER = Config.INFO_TRANSFER;
    public static volatile String CS_PHONE = Config.CS_PHONE;
    public static volatile int BATAS_RESPON_RESTO_MENIT = Config.BATAS_RESPON_RESTO_MENIT;
    public static volatile int BATAS_CARI_DRIVER_MENIT = Config.BATAS_CARI_DRIVER_MENIT;
    /** Driver hanya melihat order dari resto dalam radius ini (km) */
    public static volatile double RADIUS_DRIVER_KM = 10;
    /** Jarak antar maksimal semua resto (km); resto bisa memasang batas lebih kecil */
    public static volatile double JARAK_ANTAR_MAKS_KM = 25;
    /** Pembayaran QRIS (Midtrans) aktif? Nyalakan setelah server terpasang. */
    public static volatile boolean QRIS_AKTIF = false;

    public static synchronized void apply(JSONObject o) {
        if (o == null) return;
        ONGKIR_DASAR = o.optInt("ongkirDasar", ONGKIR_DASAR);
        JARAK_DASAR_KM = o.optDouble("jarakDasarKm", JARAK_DASAR_KM);
        ONGKIR_PER_KM = o.optInt("ongkirPerKm", ONGKIR_PER_KM);
        ONGKIR_MAKS = o.optInt("ongkirMaks", ONGKIR_MAKS);
        BIAYA_LAYANAN = o.optInt("biayaLayanan", BIAYA_LAYANAN);
        KOMISI_PERSEN_RESTO = o.optInt("komisiResto", KOMISI_PERSEN_RESTO);
        KOMISI_PERSEN_DRIVER = o.optInt("komisiDriver", KOMISI_PERSEN_DRIVER);
        MIN_TOPUP = o.optInt("minTopup", MIN_TOPUP);
        INFO_TRANSFER = o.optString("infoTransfer", INFO_TRANSFER);
        CS_PHONE = o.optString("csPhone", CS_PHONE);
        BATAS_RESPON_RESTO_MENIT = o.optInt("batasRestoMenit", BATAS_RESPON_RESTO_MENIT);
        BATAS_CARI_DRIVER_MENIT = o.optInt("batasDriverMenit", BATAS_CARI_DRIVER_MENIT);
        RADIUS_DRIVER_KM = o.optDouble("radiusDriverKm", RADIUS_DRIVER_KM);
        JARAK_ANTAR_MAKS_KM = o.optDouble("jarakAntarMaksKm", JARAK_ANTAR_MAKS_KM);
        QRIS_AKTIF = o.optBoolean("qrisAktif", QRIS_AKTIF);
    }

    public static synchronized JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("ongkirDasar", ONGKIR_DASAR);
            o.put("jarakDasarKm", JARAK_DASAR_KM);
            o.put("ongkirPerKm", ONGKIR_PER_KM);
            o.put("ongkirMaks", ONGKIR_MAKS);
            o.put("biayaLayanan", BIAYA_LAYANAN);
            o.put("komisiResto", KOMISI_PERSEN_RESTO);
            o.put("komisiDriver", KOMISI_PERSEN_DRIVER);
            o.put("minTopup", MIN_TOPUP);
            o.put("infoTransfer", INFO_TRANSFER);
            o.put("csPhone", CS_PHONE);
            o.put("batasRestoMenit", BATAS_RESPON_RESTO_MENIT);
            o.put("batasDriverMenit", BATAS_CARI_DRIVER_MENIT);
            o.put("radiusDriverKm", RADIUS_DRIVER_KM);
            o.put("jarakAntarMaksKm", JARAK_ANTAR_MAKS_KM);
            o.put("qrisAktif", QRIS_AKTIF);
        } catch (Exception ignored) {
        }
        return o;
    }
}
