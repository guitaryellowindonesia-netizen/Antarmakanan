package com.antarmakan.app;

/**
 * ============================================================
 *  PENGATURAN APLIKASI - UBAH SESUAI KABUPATEN ANDA
 * ============================================================
 * Ganti nama kabupaten, daftar kecamatan (beserta koordinat
 * titik tengahnya), tarif ongkir, dan komisi di file ini.
 * Koordinat bisa dilihat di Google Maps (tekan lama di peta).
 */
public final class Config {

    private Config() {}

    /** Nama aplikasi & wilayah layanan */
    public static final String NAMA_APLIKASI = "AntarMakan";
    public static final String NAMA_KABUPATEN = "Kabupaten Dharmasraya";

    /**
     * ------------------------------------------------------------
     *  KONEKSI ONLINE (FIREBASE)
     * ------------------------------------------------------------
     * Biarkan KOSONG ("") untuk MODE DEMO OFFLINE (data hanya di HP ini).
     * Isi keduanya agar aplikasi ONLINE (semua HP terhubung).
     * Cara mendapatkan: lihat README.md bagian "Membuat Online".
     *
     * Contoh:
     *   FIREBASE_DB_URL  = "https://antarmakan-123-default-rtdb.asia-southeast1.firebasedatabase.app"
     *   FIREBASE_API_KEY = "AIzaSy...."
     */
    public static final String FIREBASE_DB_URL = "";
    public static final String FIREBASE_API_KEY = "";

    /**
     * Nomor HP yang otomatis menjadi ADMIN saat mendaftar.
     * Ganti dengan nomor HP Anda sendiri.
     */
    public static final String[] ADMIN_PHONES = {"081200000000"};

    public static boolean isOnline() {
        return FIREBASE_DB_URL.length() > 0 && FIREBASE_API_KEY.length() > 0;
    }

    /** Nomor WhatsApp / telepon layanan pelanggan */
    public static final String CS_PHONE = "6281234567890";

    /**
     * Daftar kecamatan yang dilayani: {nama, latitude, longitude}.
     * Ganti dengan kecamatan di kabupaten Anda.
     */
    public static final String[][] KECAMATAN = {
            // Koordinat = PERKIRAAN titik tengah/ibu kota kecamatan.
            // Silakan perbaiki dari Google Maps agar ongkir lebih akurat.
            {"Pulau Punjung", "-1.0330", "101.6880"},
            {"Sitiung",       "-1.0800", "101.6300"},
            {"Koto Salak",    "-1.1200", "101.6700"},
            {"Padang Laweh",  "-1.1300", "101.7200"},
            {"Tiumang",       "-1.1300", "101.5400"},
            {"Koto Baru",     "-1.1800", "101.5800"},
            {"Sembilan Koto", "-0.9200", "101.6200"},
            {"Timpeh",        "-1.0500", "101.4800"},
            {"Koto Besar",    "-1.3300", "101.4800"},
            {"Asam Jujuhan",  "-1.3400", "101.7200"},
            {"Sungai Rumbai", "-1.4200", "101.5100"},
    };

    /** Tarif ongkos kirim */
    public static final int ONGKIR_DASAR = 5000;      // untuk jarak <= JARAK_DASAR_KM
    public static final double JARAK_DASAR_KM = 2.0;
    public static final int ONGKIR_PER_KM = 2000;     // per km selanjutnya
    public static final int ONGKIR_MAKS = 40000;
    public static final double JARAK_DALAM_KECAMATAN_KM = 2.5;
    public static final double FAKTOR_JALAN = 1.3;    // jarak jalan ~ 1.3x garis lurus

    /** Biaya layanan per pesanan & komisi untuk aplikasi */
    public static final int BIAYA_LAYANAN = 2000;
    public static final int KOMISI_PERSEN_RESTO = 10;
    public static final int KOMISI_PERSEN_DRIVER = 10;

    /** Petunjuk transfer untuk top up saldo (diverifikasi manual oleh admin) */
    public static final String INFO_TRANSFER =
            "Transfer ke:\nBank BRI 0000-01-000000-00-0\na.n. Admin AntarMakan\n"
            + "atau DANA/OVO 081200000000\n\nSaldo masuk setelah dicek admin.";

    /** Minimal top up saldo */
    public static final int MIN_TOPUP = 10000;

    /** Kategori resto/makanan (emoji + nama) */
    public static final String[] KATEGORI = {
            "Semua", "Nasi", "Mie & Bakso", "Ayam & Bebek", "Seafood",
            "Sate", "Jajanan", "Minuman", "Kopi", "Roti & Kue"
    };

    public static final String[] KATEGORI_EMOJI = {
            "🍽️", "🍚", "🍜", "🍗", "🦐", "🍢", "🥟", "🥤", "☕", "🍰"
    };

    public static String[] namaKecamatan() {
        String[] n = new String[KECAMATAN.length];
        for (int i = 0; i < KECAMATAN.length; i++) n[i] = KECAMATAN[i][0];
        return n;
    }

    public static int indexKecamatan(String nama) {
        for (int i = 0; i < KECAMATAN.length; i++) {
            if (KECAMATAN[i][0].equalsIgnoreCase(nama)) return i;
        }
        return 0;
    }

    public static String emojiKategori(String kategori) {
        for (int i = 0; i < KATEGORI.length; i++) {
            if (KATEGORI[i].equals(kategori)) return KATEGORI_EMOJI[i];
        }
        return "🍽️";
    }
}
