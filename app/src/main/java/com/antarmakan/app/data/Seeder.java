package com.antarmakan.app.data;

import com.antarmakan.app.Config;
import com.antarmakan.app.model.MenuItem;
import com.antarmakan.app.model.Promo;
import com.antarmakan.app.model.Restaurant;
import com.antarmakan.app.model.User;

/**
 * Mengisi data contoh (resto, menu, promo). Silakan ubah/hapus
 * lewat menu Admin setelah aplikasi berjalan.
 */
class Seeder {
    private final Store s;

    Seeder(Store s) {
        this.s = s;
    }

    private String kec(int i) {
        return Config.KECAMATAN[i % Config.KECAMATAN.length][0];
    }

    void seed(boolean withAccounts) throws Exception {
        long now = System.currentTimeMillis();
        String merchantId = "";

        if (withAccounts) {
            // Akun demo (hanya untuk mode offline)
            user("demo_admin", "Admin " + Config.NAMA_APLIKASI, Config.ADMIN_PHONES[0], "admin123",
                    User.ROLE_ADMIN, 0, "", true);
            user("demo_pelanggan", "Budi Pelanggan", "081111111111", "123456",
                    User.ROLE_CUSTOMER, 1, "", true);
            user("demo_mitra", "Ibu Sari (Mitra)", "082222222222", "123456",
                    User.ROLE_MERCHANT, 0, "", true);
            user("demo_driver", "Andi Driver", "083333333333", "123456",
                    User.ROLE_DRIVER, 0, "Honda Beat - B 1234 XY", true);
            merchantId = "demo_mitra";
        }

        Restaurant r1 = resto("demo_r1", merchantId, "Warung Nasi Bu Sari", "Nasi",
                "🍛", "Masakan rumahan, nasi rames & lauk pauk", kec(0), "06:00", "22:00", 4.7, 32);
        menu(r1, "Nasi Rames Komplit", "Nasi, ayam goreng, sayur, sambal", 18000, "Makanan", "🍛", true);
        menu(r1, "Nasi Ayam Bakar", "Ayam bakar kecap + lalapan", 22000, "Makanan", "🍗", true);
        menu(r1, "Nasi Telur Balado", "Telur balado pedas manis", 13000, "Makanan", "🥚", false);
        menu(r1, "Sayur Asem", "Porsi keluarga", 8000, "Lauk", "🥬", false);
        menu(r1, "Tempe & Tahu Goreng", "Isi 4 potong", 5000, "Lauk", "🧈", false);
        menu(r1, "Es Teh Manis", "", 4000, "Minuman", "🧋", false);
        menu(r1, "Es Jeruk", "Jeruk peras asli", 6000, "Minuman", "🍊", false);

        Restaurant r2 = resto("demo_r2", "", "Bakso & Mie Ayam Pak Kumis", "Mie & Bakso",
                "🍜", "Bakso urat jumbo & mie ayam homemade", kec(1), "09:00", "21:00", 4.6, 58);
        menu(r2, "Bakso Urat Jumbo", "1 bakso jumbo + 4 bakso kecil", 20000, "Makanan", "🍲", true);
        menu(r2, "Mie Ayam Bakso", "Mie ayam + 3 bakso", 17000, "Makanan", "🍜", true);
        menu(r2, "Mie Ayam Original", "", 13000, "Makanan", "🍜", false);
        menu(r2, "Pangsit Goreng", "Isi 5", 7000, "Lauk", "🥟", false);
        menu(r2, "Es Teh Manis", "", 4000, "Minuman", "🧋", false);

        Restaurant r3 = resto("demo_r3", "", "Ayam Geprek Mantul", "Ayam & Bebek",
                "🍗", "Geprek level 1-10, sambal bawang khas", kec(2), "10:00", "22:00", 4.5, 120);
        menu(r3, "Ayam Geprek + Nasi", "Pilih level di catatan", 15000, "Makanan", "🍗", true);
        menu(r3, "Ayam Geprek Keju", "Topping mozarella", 21000, "Makanan", "🧀", true);
        menu(r3, "Bebek Goreng + Nasi", "", 28000, "Makanan", "🦆", false);
        menu(r3, "Es Jeruk", "", 6000, "Minuman", "🍊", false);

        Restaurant r4 = resto("demo_r4", "", "Sate Kambing Haji Udin", "Sate",
                "🍢", "Sate kambing muda & gule", kec(3), "16:00", "23:30", 4.8, 77);
        menu(r4, "Sate Kambing (10 tusuk)", "Bumbu kecap / kacang", 35000, "Makanan", "🍢", true);
        menu(r4, "Sate Ayam (10 tusuk)", "", 22000, "Makanan", "🍢", false);
        menu(r4, "Gule Kambing", "", 25000, "Makanan", "🍲", true);
        menu(r4, "Lontong", "", 4000, "Lauk", "🍙", false);

        Restaurant r5 = resto("demo_r5", "", "Kopi Senja", "Kopi",
                "☕", "Kopi susu gula aren & cemilan", kec(4), "08:00", "23:00", 4.6, 41);
        menu(r5, "Kopi Susu Gula Aren", "Es / panas", 15000, "Minuman", "☕", true);
        menu(r5, "Americano", "", 12000, "Minuman", "☕", false);
        menu(r5, "Matcha Latte", "", 18000, "Minuman", "🍵", false);
        menu(r5, "Roti Bakar Coklat Keju", "", 14000, "Makanan", "🍞", true);
        menu(r5, "Pisang Goreng Madu", "", 12000, "Makanan", "🍌", false);

        Restaurant r6 = resto("demo_r6", "", "Seafood Pesisir Bang Ucok", "Seafood",
                "🦐", "Ikan bakar, cumi, udang segar tiap hari", kec(5), "11:00", "22:00", 4.4, 23);
        menu(r6, "Ikan Bakar + Nasi", "Ikan nila / gurame", 30000, "Makanan", "🐟", true);
        menu(r6, "Cumi Goreng Tepung", "", 28000, "Makanan", "🦑", false);
        menu(r6, "Udang Saus Padang", "", 35000, "Makanan", "🦐", true);
        menu(r6, "Kelapa Muda", "", 10000, "Minuman", "🥥", false);

        Restaurant r7 = resto("demo_r7", "", "Jajanan Pasar Mbok Yem", "Jajanan",
                "🥟", "Gorengan, kue basah, cilok & seblak", kec(6), "07:00", "20:00", 4.3, 15);
        menu(r7, "Seblak Komplit", "Level pedas di catatan", 15000, "Makanan", "🍜", true);
        menu(r7, "Cilok Bumbu Kacang", "Isi 15", 10000, "Makanan", "🍡", false);
        menu(r7, "Gorengan Campur", "Isi 5", 6000, "Makanan", "🥟", false);
        menu(r7, "Klepon (isi 10)", "", 8000, "Makanan", "🟢", false);

        Restaurant r8 = resto("demo_r8", "", "Toko Kue Manis Legit", "Roti & Kue",
                "🍰", "Bolu, brownies, roti sobek", kec(0), "08:00", "20:00", 4.7, 19);
        menu(r8, "Brownies Panggang", "1 loyang kecil", 35000, "Makanan", "🍫", true);
        menu(r8, "Bolu Pandan", "", 30000, "Makanan", "🍰", false);
        menu(r8, "Roti Sobek Coklat", "", 18000, "Makanan", "🍞", false);

        promo("HEMAT10", "Diskon 10% maks Rp10.000, min belanja Rp30.000",
                Promo.TYPE_PERSEN, 10, 10000, 30000);
        promo("GRATISONGKIR", "Gratis ongkir s/d Rp8.000, min belanja Rp25.000",
                Promo.TYPE_ONGKIR, 8000, 0, 25000);
        promo("BARU5RB", "Potongan Rp5.000 tanpa minimal", Promo.TYPE_POTONGAN, 5000, 0, 0);
    }

    private void user(String id, String name, String phone, String pw, String role,
                      int kecIdx, String vehicle, boolean active) throws Exception {
        if (s.get(Repo.USERS, id) != null) return;
        User u = new User();
        u.id = id;
        u.name = name;
        u.phone = Repo.normalizePhone(phone);
        u.role = role;
        u.kecamatan = kec(kecIdx);
        u.address = "Jl. Contoh No. " + (kecIdx + 1) + ", Kec. " + u.kecamatan;
        u.vehicle = vehicle;
        u.active = active;
        u.balance = User.ROLE_CUSTOMER.equals(role) ? 100000 : 0;
        u.passwordHash = Ids.sha256(u.phone + ":" + pw);
        u.createdAt = System.currentTimeMillis();
        s.put(Repo.USERS, u.id, u.toJson());
    }

    private Restaurant resto(String id, String ownerId, String name, String cat, String emoji,
                             String desc, String kec, String open, String close,
                             double rating, int count) throws Exception {
        Restaurant r = new Restaurant();
        r.id = id;
        r.ownerId = ownerId;
        r.name = name;
        r.category = cat;
        r.emoji = emoji;
        r.description = desc;
        r.kecamatan = kec;
        r.address = "Jl. Raya " + kec + " No. " + (10 + name.length());
        r.phone = "08120000" + (1000 + name.length());
        r.openHour = open;
        r.closeHour = close;
        r.ratingSum = rating * count;
        r.ratingCount = count;
        r.createdAt = System.currentTimeMillis();
        s.put(Repo.RESTOS, r.id, r.toJson());
        return r;
    }

    private int menuNo = 0;

    private void menu(Restaurant r, String name, String desc, long price, String cat,
                      String emoji, boolean rec) throws Exception {
        MenuItem m = new MenuItem();
        m.id = r.id + "_m" + (++menuNo);
        m.restoId = r.id;
        m.name = name;
        m.description = desc;
        m.price = price;
        m.category = cat;
        m.emoji = emoji;
        m.recommended = rec;
        s.put(Repo.MENUS, m.id, m.toJson());
    }

    private void promo(String code, String desc, String type, long value, long max, long min)
            throws Exception {
        Promo p = new Promo();
        p.id = "demo_" + code.toLowerCase(java.util.Locale.US);
        p.code = code;
        p.description = desc;
        p.type = type;
        p.value = value;
        p.maxDiscount = max;
        p.minOrder = min;
        p.quota = 100;
        s.put(Repo.PROMOS, p.id, p.toJson());
    }
}
