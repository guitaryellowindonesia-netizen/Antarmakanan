# 🛵 AntarMakan — Aplikasi Pesan-Antar Makanan Kabupaten Dharmasraya

Aplikasi Android mirip GrabFood/GoFood, ditulis dengan **Java murni** (tanpa library tambahan)
agar mudah dibuka & di-build di **CodeAssist / AIDE / Android Studio**.

Satu aplikasi untuk 4 peran: **Pelanggan, Mitra Resto, Driver, Admin**.

---

## ✨ Fitur

### 🙋 Pelanggan
- Daftar / masuk dengan nomor HP
- Beranda: cari resto **atau nama menu**, kategori, banner promo, resto terdekat
- Ongkir & estimasi waktu otomatis berdasarkan jarak antar-kecamatan
- Status buka/tutup resto (saklar mitra + jam operasional)
- Keranjang + catatan per menu (mis. "pedas level 3")
- Kode promo (diskon %, potongan Rp, gratis ongkir; minimal belanja & kuota)
- Bayar **Tunai** atau **Saldo AntarMakan**
- Lacak pesanan langsung (status, driver, perkiraan tiba) — diperbarui otomatis
- Chat dengan driver/resto, telepon & WhatsApp
- Batalkan pesanan (selama belum diterima resto, saldo dikembalikan)
- Ulasan & bintang untuk resto dan driver
- Resto favorit, riwayat pesanan, top up saldo, ubah alamat

### 🏪 Mitra Resto
- Notifikasi bunyi saat ada pesanan baru
- Terima / tolak pesanan (dengan alasan), tandai "makanan siap"
- Kelola menu: tambah, ubah, hapus, tandai habis, menu rekomendasi
- Buka/tutup toko, ubah info & jam buka
- Laporan penjualan harian & total, balas ulasan pelanggan
- Saldo & tarik saldo

### 🛵 Driver
- Saklar Online/Offline, bunyi saat ada order baru
- Ambil order (aman: 1 order hanya bisa diambil 1 driver)
- Navigasi Google Maps ke resto & pelanggan, telepon/WA/chat
- Info tagihan tunai, pendapatan harian, riwayat, rating

### 🛠️ Admin
- Dasbor: pesanan hari ini, nilai transaksi, pendapatan aplikasi, driver online
- Setujui/nonaktifkan resto & driver, kelola semua resto dan menunya
- Kelola pengguna, koreksi saldo, lihat & batalkan pesanan
- Kelola kode promo
- Setujui top up & penarikan saldo
- Tombol "Isi data contoh"

### 💰 Bagi hasil otomatis saat pesanan selesai
| Bayar | Resto | Driver |
|---|---|---|
| Saldo | + harga makanan − komisi 10% | + ongkir − komisi 10% |
| Tunai | driver bayar resto tunai; saldo resto − komisi 10% | driver terima uang tunai; saldo driver − komisi − biaya layanan (+ diskon promo diganti aplikasi) |

Persen komisi, ongkir, biaya layanan bisa diubah di `Config.java`.

---

## 📲 Cara Mendapatkan APK

### Cara 1 — Otomatis dari GitHub (paling mudah)
Setiap kali kode di-push ke GitHub, **GitHub Actions** mem-build APK otomatis.
- Buka repo di GitHub → **Releases** → `AntarMakan APK (build terbaru)` → unduh **AntarMakan.apk**, atau
- Tab **Actions** → pilih build terakhir → bagian **Artifacts** → `AntarMakan-apk`.

Install di HP (izinkan "Instal dari sumber tidak dikenal").

### Cara 2 — Build di CodeAssist (di HP)
1. Salin folder `AntarMakan` ke penyimpanan HP (mis. `CodeAssistProjects/AntarMakan`).
2. Buka CodeAssist → **Open project** → pilih folder `AntarMakan`.
3. Tekan **Run / Build**. Proyek tidak memakai library luar, jadi tidak perlu unduh dependensi.

### Cara 3 — Android Studio
Open folder `AntarMakan` → Run.

---

## 🗺️ Wilayah Layanan: Kabupaten Dharmasraya
Melayani 11 kecamatan: Pulau Punjung, Sitiung, Koto Salak, Padang Laweh, Tiumang, Koto Baru,
Sembilan Koto, Timpeh, Koto Besar, Asam Jujuhan, Sungai Rumbai.

Koordinat tiap kecamatan masih **perkiraan**; perbaiki dari Google Maps agar ongkir lebih akurat.
Untuk mengubah wilayah/tarif, edit **`app/src/main/java/com/antarmakan/app/Config.java`**:
- `NAMA_KABUPATEN` — nama kabupaten
- `KECAMATAN` — daftar kecamatan + koordinat titik tengah (lihat di Google Maps: tekan lama → salin angka)
- `ONGKIR_DASAR`, `ONGKIR_PER_KM`, `BIAYA_LAYANAN`, `KOMISI_PERSEN_*`
- `CS_PHONE` — nomor WhatsApp layanan pelanggan
- `ADMIN_PHONES` — **nomor HP Anda** (otomatis jadi admin saat mendaftar)
- `INFO_TRANSFER` — rekening untuk top up saldo

---

## 🌐 Membuat Online (semua HP terhubung)

Tanpa pengaturan, aplikasi berjalan **mode demo offline** (data hanya di HP itu, dengan akun demo).
Agar pelanggan, resto, dan driver di HP berbeda saling terhubung, gunakan **Firebase** (gratis):

1. Buka https://console.firebase.google.com → **Add project** (mis. `antarmakan`).
2. Menu **Build → Authentication → Get started → Sign-in method → Email/Password → Enable**.
3. Menu **Build → Realtime Database → Create database** → pilih lokasi **Singapore (asia-southeast1)** → *Start in locked mode*.
4. Tab **Rules** → hapus isinya, tempel isi file [`database.rules.json`](database.rules.json) → **Publish**.
5. Salin **URL database** (di atas tab Data, mis. `https://antarmakan-xxx-default-rtdb.asia-southeast1.firebasedatabase.app`).
6. **Project settings (⚙️) → General → Web API Key** → salin.
7. Tempel keduanya di `Config.java`:
   ```java
   public static final String FIREBASE_DB_URL = "https://antarmakan-xxx-default-rtdb.asia-southeast1.firebasedatabase.app";
   public static final String FIREBASE_API_KEY = "AIzaSy....";
   ```
8. Build ulang APK. Daftar memakai nomor yang ada di `ADMIN_PHONES` → Anda jadi Admin.
   Di Admin → tab **Lainnya → Isi data contoh** bila ingin data contoh.

Aplikasi memakai Firebase lewat REST API (HTTP biasa), jadi **tidak perlu** `google-services.json`
maupun plugin Google — aman untuk CodeAssist.

---

## 🔑 Akun demo (mode offline)
| Peran | No HP | Sandi |
|---|---|---|
| Pelanggan | 081111111111 | 123456 |
| Mitra Resto | 082222222222 | 123456 |
| Driver | 083333333333 | 123456 |
| Admin | 081200000000 | admin123 |

Di mode offline Anda bisa mencoba seluruh alur di satu HP: pesan sebagai pelanggan → keluar →
masuk sebagai mitra untuk menerima → masuk sebagai driver untuk mengantar.

---

## 📁 Struktur Kode
```
app/src/main/java/com/antarmakan/app/
├── Config.java              ← pengaturan kabupaten, tarif, Firebase
├── model/                   ← User, Restaurant, MenuItem, Order, Promo, Review, ...
├── data/
│   ├── Repo.java            ← semua logika bisnis (pesanan, saldo, promo, ...)
│   ├── Store.java           ← antarmuka penyimpanan
│   ├── LocalStore.java      ← SQLite (offline)
│   ├── FirebaseStore.java   ← Firebase Realtime Database (online, REST)
│   ├── FirebaseAuthRest.java← login Firebase (REST)
│   └── Seeder.java          ← data contoh
├── util/                    ← Ui (pembuat tampilan), Cart, Fmt, Geo (jarak & ongkir), Async
└── ui/
    ├── auth/                ← Splash, Login, Daftar
    ├── customer/            ← Beranda, Resto, Keranjang
    ├── merchant/            ← Panel Mitra, form Menu & Resto
    ├── driver/              ← Panel Driver
    ├── admin/               ← Panel Admin
    └── common/              ← Detail pesanan, Chat, Ulasan, Saldo, Profil
```
Tampilan dibuat lewat kode Java (`util/Ui.java`) sehingga hampir tidak ada file layout XML.

---

## ⚠️ Catatan sebelum dipakai sungguhan
- **Keamanan:** Rules Firebase di atas hanya mewajibkan login. Pengguna yang paham teknis
  secara teori bisa mengubah data (mis. saldo) lewat API. Untuk skala besar/uang sungguhan,
  pindahkan logika saldo & status pesanan ke **Firebase Cloud Functions** atau server sendiri,
  dan perketat Rules per peran.
- **Top up saldo** diverifikasi manual oleh admin (belum terhubung payment gateway seperti Midtrans/Xendit).
- **Lokasi** dihitung per kecamatan (bukan GPS), cukup untuk tarif ongkir sederhana.
- **Notifikasi** berupa bunyi saat aplikasi terbuka; notifikasi push saat aplikasi tertutup butuh Firebase Cloud Messaging.
- Data diperbarui otomatis setiap beberapa detik (polling).
