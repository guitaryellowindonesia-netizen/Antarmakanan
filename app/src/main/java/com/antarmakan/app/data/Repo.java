package com.antarmakan.app.data;

import android.content.Context;

import com.antarmakan.app.Config;
import com.antarmakan.app.model.AppNotif;
import com.antarmakan.app.model.CartItem;
import com.antarmakan.app.model.Complaint;
import com.antarmakan.app.model.MenuItem;
import com.antarmakan.app.model.Message;
import com.antarmakan.app.model.Order;
import com.antarmakan.app.model.OrderItem;
import com.antarmakan.app.model.Promo;
import com.antarmakan.app.model.Restaurant;
import com.antarmakan.app.model.Review;
import com.antarmakan.app.model.TopUp;
import com.antarmakan.app.model.User;
import com.antarmakan.app.model.WalletTx;
import com.antarmakan.app.util.Cart;
import com.antarmakan.app.util.AppSettings;
import com.antarmakan.app.util.Fmt;
import com.antarmakan.app.util.Geo;
import com.antarmakan.app.util.Session;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Semua logika bisnis aplikasi (akun, resto, menu, pesanan, saldo, promo, ulasan, chat).
 * Semua method BLOCKING: panggil lewat Async.run(...) dari Activity.
 */
public class Repo {
    public static final String USERS = "users";
    public static final String RESTOS = "restaurants";
    public static final String MENUS = "menus";
    public static final String ORDERS = "orders";
    public static final String REVIEWS = "reviews";
    public static final String PROMOS = "promos";
    public static final String FAVS = "favorites";
    public static final String MSGS = "messages";
    public static final String WALLET = "wallet";             // wallet/{uid}/{entryId}
    public static final String TOPUPS = "topups";
    public static final String IMAGES = "images";
    public static final String NOTIFS = "notifications";       // notifications/{uid}/{id}
    public static final String BROADCASTS = "broadcasts";      // pengumuman admin

    private final Context ctx;
    private final Store s;
    private final FirebaseAuthRest auth;

    Repo(Context ctx, Store store, FirebaseAuthRest auth) {
        this.ctx = ctx;
        this.s = store;
        this.auth = auth;
        try {
            String cached = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("app", null);
            if (cached != null) AppSettings.apply(new JSONObject(cached));
        } catch (Exception ignored) {
        }
    }

    // =====================================================================
    //  PENGATURAN (diubah admin dari aplikasi)
    // =====================================================================

    public static final String SETTINGS = "settings";
    private long settingsLoadedAt = 0;

    /** Muat pengaturan dari server (paling sering tiap 5 menit). */
    public void refreshSettings(boolean force) {
        if (!force && System.currentTimeMillis() - settingsLoadedAt < 5 * 60000L) return;
        try {
            JSONObject o = s.get(SETTINGS, "app");
            settingsLoadedAt = System.currentTimeMillis();
            if (o != null) {
                AppSettings.apply(o);
                ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                        .putString("app", o.toString()).apply();
            }
        } catch (Exception ignored) {
        }
    }

    public void saveSettings(JSONObject o) throws Exception {
        if (o.optInt("komisiResto") < 0 || o.optInt("komisiResto") > 50
                || o.optInt("komisiDriver") < 0 || o.optInt("komisiDriver") > 50) {
            throw new AppException("Komisi harus 0 - 50%");
        }
        if (o.optInt("ongkirDasar") < 0 || o.optInt("ongkirMaks") < o.optInt("ongkirDasar")) {
            throw new AppException("Periksa tarif ongkir (maksimal harus >= dasar)");
        }
        if (o.optInt("batasRestoMenit") < 3 || o.optInt("batasDriverMenit") < 5) {
            throw new AppException("Batas waktu terlalu kecil");
        }
        s.put(SETTINGS, "app", o);
        AppSettings.apply(o);
        settingsLoadedAt = System.currentTimeMillis();
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("app", o.toString()).apply();
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    public static String normalizePhone(String phone) {
        String p = phone == null ? "" : phone.replaceAll("[^0-9]", "");
        if (p.startsWith("62")) p = "0" + p.substring(2);
        return p;
    }

    private static boolean isAdminPhone(String phone) {
        for (String a : Config.ADMIN_PHONES) {
            if (normalizePhone(a).equals(phone)) return true;
        }
        return false;
    }

    // =====================================================================
    //  AKUN
    // =====================================================================

    public static final String LOGINS = "logins";      // logins/{nomorHP} = {uid, email}
    public static final String RECOVERY = "recovery";  // permintaan pemulihan akun

    private static boolean validEmail(String e) {
        return e != null && e.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    }

    /**
     * Daftar akun nomor HP. Email (opsional) dipakai untuk "Lupa kata sandi".
     */
    public User register(String name, String phone, String password, String email, String role,
                         String kecamatan, String address, String vehicle,
                         Restaurant restoDraft) throws Exception {
        if (password.length() < 6) throw new AppException("Kata sandi minimal 6 karakter");
        validateProfile(name, phone);
        phone = normalizePhone(phone);
        email = email == null ? "" : email.trim().toLowerCase(java.util.Locale.US);
        if (email.length() > 0 && !validEmail(email)) throw new AppException("Format email tidak valid");
        if (s.getPublic(LOGINS, phone) != null) throw new AppException("Nomor HP sudah terdaftar");
        String loginEmail = email.length() > 0 ? email : FirebaseAuthRest.fakeEmail(phone);
        String uid = auth.signUpEmail(loginEmail, password);
        User u = createProfile(uid, name, phone, role, kecamatan, address, vehicle, restoDraft, "", email);
        saveLogin(phone, uid, loginEmail);
        return u;
    }

    private void saveLogin(String phone, String uid, String loginEmail) {
        try {
            JSONObject o = new JSONObject();
            o.put("uid", uid);
            o.put("email", loginEmail);
            s.put(LOGINS, phone, o);
        } catch (Exception ignored) {
        }
    }

    /** Email login untuk nomor HP (asli bila sudah diatur, jika tidak email semu). */
    private String loginEmailOf(String phone) throws Exception {
        JSONObject m = s.getPublic(LOGINS, phone);
        String e = m == null ? "" : m.optString("email");
        return e.length() > 0 ? e : FirebaseAuthRest.fakeEmail(phone);
    }

    /**
     * Lupa kata sandi. Mengembalikan email (disamarkan) tujuan link reset,
     * atau null bila akun belum punya email pemulihan.
     */
    public String forgotPassword(String phone) throws Exception {
        phone = normalizePhone(phone);
        JSONObject m = s.getPublic(LOGINS, phone);
        if (m == null) throw new AppException("Nomor HP belum terdaftar / akun Google");
        String email = m.optString("email");
        if (FirebaseAuthRest.isFakeEmail(email)) return null;
        auth.sendPasswordReset(email);
        int at = email.indexOf('@');
        return (at > 2 ? email.substring(0, 2) + "***" : "***") + email.substring(at);
    }

    /**
     * Pemulihan lewat admin (akun tanpa email): buat login baru dengan sandi baru,
     * admin memverifikasi lewat WhatsApp lalu memindahkan data akun lama.
     */
    public void requestRecovery(String phone, String name, String newPassword) throws Exception {
        phone = normalizePhone(phone);
        if (newPassword.length() < 6) throw new AppException("Kata sandi minimal 6 karakter");
        JSONObject m = s.getPublic(LOGINS, phone);
        if (m == null) throw new AppException("Nomor HP belum terdaftar");
        String newEmail = phone + ".r" + now() + "@antarmakan.app";
        String newUid = auth.signUpEmail(newEmail, newPassword);
        JSONObject r = new JSONObject();
        r.put("id", phone);
        r.put("phone", phone);
        r.put("name", name.trim());
        r.put("oldUid", m.optString("uid"));
        r.put("newUid", newUid);
        r.put("newEmail", newEmail);
        r.put("createdAt", now());
        s.put(RECOVERY, phone, r);
        Session.logout(ctx);
    }

    public List<JSONObject> recoveryRequests() throws Exception {
        return s.list(RECOVERY);
    }

    /** Admin menyetujui pemulihan: pindahkan profil, resto & saldo ke login baru. */
    public void approveRecovery(String phone) throws Exception {
        JSONObject r = s.get(RECOVERY, phone);
        if (r == null) throw new AppException("Permintaan tidak ditemukan");
        String oldUid = r.optString("oldUid"), newUid = r.optString("newUid");
        JSONObject old = s.get(USERS, oldUid);
        if (old == null) throw new AppException("Akun lama tidak ditemukan");
        long bal = old.optLong("balance");
        JSONObject nu = new JSONObject(old.toString());
        nu.put("id", newUid);
        nu.put("balance", 0);
        nu.remove("lastTx");
        s.put(USERS, newUid, nu);
        if (bal != 0) {
            String tx = Ids.newId();
            ledger(oldUid, "admin_" + tx, -bal, "Dipindah ke akun baru (pemulihan)", "");
            ledger(newUid, "admin_" + tx, bal, "Saldo dari akun lama (pemulihan)", "");
        }
        Restaurant resto = restoOfOwner(oldUid);
        if (resto != null) {
            resto.ownerId = newUid;
            saveResto(resto);
        }
        editUser(oldUid, new UserEdit() {
            @Override
            public void edit(JSONObject o) throws Exception {
                o.put("active", false);
                o.put("phone", o.optString("phone") + "-lama");
            }
        });
        JSONObject login = new JSONObject();
        login.put("uid", newUid);
        login.put("email", r.optString("newEmail"));
        s.put(LOGINS, phone, login);
        s.delete(RECOVERY, phone);
        notify(newUid, "✅ Akun dipulihkan", "Silakan masuk dengan kata sandi baru Anda.", "");
    }

    public void rejectRecovery(String phone) throws Exception {
        s.delete(RECOVERY, phone);
    }

    /** Atur email pemulihan untuk akun nomor HP (mengganti email login). */
    public void setRecoveryEmail(String email) throws Exception {
        email = email.trim().toLowerCase(java.util.Locale.US);
        if (!validEmail(email)) throw new AppException("Format email tidak valid");
        User me = me();
        auth.updateEmail(email);
        saveLogin(me.phone, me.id, email);
        final String e = email;
        editUser(me.id, new UserEdit() {
            @Override
            public void edit(JSONObject o) throws Exception {
                o.put("email", e);
            }
        });
    }

    /** Email pemulihan akun ini ("" bila belum ada / akun Google). */
    public String recoveryEmail(User u) throws Exception {
        JSONObject m = s.getPublic(LOGINS, u.phone);
        if (m == null || !u.id.equals(m.optString("uid"))) return "";
        String e = m.optString("email");
        return FirebaseAuthRest.isFakeEmail(e) ? "" : e;
    }

    /** Akun ini login dengan nomor HP + sandi (bukan Google)? */
    public boolean isPhoneLogin(User u) throws Exception {
        JSONObject m = s.getPublic(LOGINS, u.phone);
        return m != null && u.id.equals(m.optString("uid"));
    }

    /**
     * Hapus akun beserta data pribadi. Riwayat transaksi tetap disimpan
     * untuk catatan keuangan.
     */
    public void deleteAccount() throws Exception {
        User u = me();
        if (u.isAdmin()) throw new AppException("Akun admin tidak bisa dihapus dari aplikasi");
        for (Order o : ordersOfCustomer(u.id)) {
            if (o.isActive()) throw new AppException("Selesaikan dulu pesanan " + o.code);
        }
        if (u.isDriver()) {
            for (Order o : ordersOfDriver(u.id)) {
                if (o.isActive()) throw new AppException("Selesaikan dulu pesanan " + o.code);
            }
        }
        Restaurant r = u.isMerchant() ? restoOfOwner(u.id) : null;
        if (r != null) {
            for (Order o : ordersOfResto(r.id)) {
                if (o.isActive()) throw new AppException("Selesaikan dulu pesanan " + o.code);
            }
        }
        if (u.balance > 0) throw new AppException("Tarik dulu saldo Anda (" + Fmt.rp(u.balance) + ")");
        if (u.balance < 0) throw new AppException("Lunasi dulu saldo minus Anda (" + Fmt.rp(u.balance) + ")");

        if (r != null) deleteResto(r.id);
        try {
            s.delete(FAVS, u.id);
        } catch (Exception ignored) {
        }
        try {
            s.delete(NOTIFS, u.id);
        } catch (Exception ignored) {
        }
        JSONObject m = s.getPublic(LOGINS, u.phone);
        if (m != null && u.id.equals(m.optString("uid"))) s.delete(LOGINS, u.phone);
        s.delete(USERS, u.id);
        if (auth != null) auth.deleteAccount();
        Session.logout(ctx);
    }

    private static void validateProfile(String name, String phone) {
        if (name == null || name.trim().length() < 2) throw new AppException("Nama wajib diisi");
        if (normalizePhone(phone).length() < 9) throw new AppException("Nomor HP tidak valid");
    }

    /** Langkah 1 login Google: masuk ke Firebase. Mengembalikan profil jika sudah pernah daftar. */
    public GoogleLogin loginWithGoogle(String googleIdToken) throws Exception {
        FirebaseAuthRest.GoogleResult g = auth.signInWithGoogle(googleIdToken);
        GoogleLogin out = new GoogleLogin();
        out.uid = g.uid;
        out.name = g.name;
        out.email = g.email;
        JSONObject o = s.get(USERS, g.uid);
        if (o != null) {
            User u = User.fromJson(o);
            if (!u.active && !u.isDriver()) throw new AppException("Akun Anda dinonaktifkan admin");
            Session.login(ctx, u.id, u.role);
            out.user = u;
        }
        return out;
    }

    public static class GoogleLogin {
        public String uid, name, email;
        public User user; // null = belum melengkapi profil
    }

    /** Langkah 2 login Google: lengkapi profil (no HP, alamat, peran). */
    public User completeGoogleProfile(String uid, String email, String name, String phone, String role,
                                      String kecamatan, String address, String vehicle,
                                      Restaurant restoDraft) throws Exception {
        validateProfile(name, phone);
        // nomor admin wajib daftar lewat No HP + sandi (nomor Google tidak diverifikasi)
        if (isAdminPhone(normalizePhone(phone))) {
            throw new AppException("Nomor ini khusus admin. Daftar dengan No HP & kata sandi.");
        }
        User other = userByPhone(phone);
        if (other != null && !other.id.equals(uid)) throw new AppException("Nomor HP sudah dipakai akun lain");
        return createProfile(uid, name, normalizePhone(phone), role, kecamatan, address, vehicle,
                restoDraft, "", email);
    }

    private User createProfile(String uid, String name, String phone, String role, String kecamatan,
                               String address, String vehicle, Restaurant restoDraft,
                               String passwordHash, String email) throws Exception {
        User u = new User();
        u.id = uid;
        u.name = name.trim();
        u.phone = phone;
        u.email = email == null ? "" : email;
        u.role = isAdminPhone(phone) ? User.ROLE_ADMIN : role;
        u.kecamatan = kecamatan;
        u.address = address;
        u.vehicle = vehicle == null ? "" : vehicle;
        u.passwordHash = passwordHash;
        u.createdAt = now();
        // driver harus disetujui admin dulu
        u.active = !User.ROLE_DRIVER.equals(u.role);
        s.put(USERS, u.id, u.toJson());

        if (User.ROLE_MERCHANT.equals(u.role) && restoDraft != null) {
            restoDraft.id = Ids.newId();
            restoDraft.ownerId = u.id;
            restoDraft.phone = phone;
            restoDraft.active = false; // menunggu persetujuan admin
            restoDraft.createdAt = now();
            s.put(RESTOS, restoDraft.id, restoDraft.toJson());
        }
        Session.login(ctx, u.id, u.role);
        return u;
    }

    public User login(String phone, String password) throws Exception {
        phone = normalizePhone(phone);
        boolean mapped = s.getPublic(LOGINS, phone) != null;
        String uid = auth.signInEmail(loginEmailOf(phone), password);
        JSONObject o = s.get(USERS, uid);
        if (o == null) throw new AppException("Data akun tidak ditemukan");
        User u = User.fromJson(o);
        if (!mapped) saveLogin(phone, uid, FirebaseAuthRest.fakeEmail(phone)); // akun lama
        if (!u.active && !u.isDriver()) throw new AppException("Akun Anda dinonaktifkan admin");
        Session.login(ctx, u.id, u.role);
        return u;
    }

    public void logout() {
        Session.logout(ctx);
    }

    /** Data pengguna yang sedang login (selalu terbaru dari server). */
    public User me() throws Exception {
        refreshSettings(false);
        String id = Session.userId(ctx);
        if (id.length() == 0) throw new AppException("Silakan masuk dulu");
        JSONObject o = s.get(USERS, id);
        if (o == null) throw new AppException("Akun tidak ditemukan");
        return User.fromJson(o);
    }

    public User user(String id) throws Exception {
        JSONObject o = s.get(USERS, id);
        return o == null ? null : User.fromJson(o);
    }

    public User userByPhone(String phone) throws Exception {
        List<JSONObject> l = s.query(USERS, "phone", normalizePhone(phone));
        return l.isEmpty() ? null : User.fromJson(l.get(0));
    }

    public List<User> users() throws Exception {
        List<User> out = new ArrayList<User>();
        for (JSONObject o : s.list(USERS)) out.add(User.fromJson(o));
        Collections.sort(out, new Comparator<User>() {
            @Override
            public int compare(User a, User b) {
                return a.name.compareToIgnoreCase(b.name);
            }
        });
        return out;
    }

    /** Ubah sebagian field user secara atomic (tanpa menimpa saldo). */
    private interface UserEdit {
        void edit(JSONObject o) throws Exception;
    }

    private JSONObject editUser(String uid, final UserEdit e) throws Exception {
        return s.transact(USERS, uid, new Store.Updater() {
            @Override
            public JSONObject update(JSONObject cur) throws Exception {
                if (cur == null) throw new AppException("Akun tidak ditemukan");
                e.edit(cur);
                return cur;
            }
        });
    }

    public void saveProfile(final User u) throws Exception {
        if (u.name.trim().length() < 2) throw new AppException("Nama wajib diisi");
        editUser(u.id, new UserEdit() {
            @Override
            public void edit(JSONObject o) throws Exception {
                o.put("name", u.name.trim());
                o.put("address", u.address);
                o.put("kecamatan", u.kecamatan);
                o.put("vehicle", u.vehicle);
                o.put("lat", u.lat);
                o.put("lng", u.lng);
            }
        });
    }

    public void setUserActive(String uid, final boolean active) throws Exception {
        JSONObject u = editUser(uid, new UserEdit() {
            @Override
            public void edit(JSONObject o) throws Exception {
                o.put("active", active);
                if (!active) o.put("online", false);
            }
        });
        if (active) {
            notify(uid, "✅ Akun disetujui", User.ROLE_DRIVER.equals(u.optString("role"))
                    ? "Akun driver Anda aktif. Nyalakan ONLINE untuk menerima order." : "Akun Anda aktif kembali.", "");
        }
    }

    public void setDriverOnline(String uid, final boolean online) throws Exception {
        editUser(uid, new UserEdit() {
            @Override
            public void edit(JSONObject o) throws Exception {
                if (online && !o.optBoolean("active", true)) {
                    throw new AppException("Akun driver belum disetujui admin");
                }
                o.put("online", online);
            }
        });
    }

    // =====================================================================
    //  SALDO / DOMPET
    // =====================================================================

    /**
     * Catat mutasi saldo. Saldo & riwayat ditulis sekaligus (atomic) dan diperiksa
     * oleh Rules Firebase, sehingga saldo tidak bisa diubah sembarangan dari HP.
     * entryId dibuat pasti (mis. "refund_ORDERID") agar tidak bisa tercatat dua kali.
     */
    private long ledger(String uid, String entryId, long amount, String desc, String orderId)
            throws Exception {
        if (uid == null || uid.length() == 0 || amount == 0) return 0;
        PermissionDeniedException last = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            JSONObject u = s.get(USERS, uid);
            if (u == null) throw new AppException("Akun tidak ditemukan");
            long nb = u.optLong("balance") + amount;
            JSONObject e = new JSONObject();
            e.put("id", entryId);
            e.put("userId", uid);
            e.put("amount", amount);
            e.put("description", desc);
            e.put("orderId", orderId == null ? "" : orderId);
            e.put("createdAt", now());
            Map<String, Object> m = new HashMap<String, Object>();
            m.put(WALLET + "/" + uid + "/" + entryId, e);
            m.put(USERS + "/" + uid + "/balance", nb);
            m.put(USERS + "/" + uid + "/lastTx", entryId);
            try {
                s.update(m);
                return nb;
            } catch (PermissionDeniedException pe) {
                last = pe;
                if (s.get(WALLET + "/" + uid, entryId) != null) return nb; // sudah tercatat
                Thread.sleep(300L * (attempt + 1));  // mungkin bentrok dengan mutasi lain
            }
        }
        throw amount < 0 ? new AppException("Saldo tidak cukup") : last;
    }

    /** Koreksi saldo manual oleh admin. */
    public long adjustBalance(String uid, long amount, String desc, boolean ignored) throws Exception {
        return ledger(uid, "admin_" + Ids.newId(), amount, desc, "");
    }

    public List<WalletTx> walletHistory(String uid) throws Exception {
        List<WalletTx> out = new ArrayList<WalletTx>();
        for (JSONObject o : s.list(WALLET + "/" + uid)) out.add(WalletTx.fromJson(o));
        Collections.sort(out, new Comparator<WalletTx>() {
            @Override
            public int compare(WalletTx a, WalletTx b) {
                return Long.compare(b.createdAt, a.createdAt);
            }
        });
        return out;
    }

    public void requestTopUp(User u, long amount, String info) throws Exception {
        if (amount < com.antarmakan.app.util.AppSettings.MIN_TOPUP) {
            throw new AppException("Minimal top up " + com.antarmakan.app.util.Fmt.rp(com.antarmakan.app.util.AppSettings.MIN_TOPUP));
        }
        TopUp t = new TopUp();
        t.id = Ids.newId();
        t.userId = u.id;
        t.userName = u.name;
        t.userPhone = u.phone;
        t.type = TopUp.TYPE_TOPUP;
        t.amount = amount;
        t.info = info;
        t.createdAt = now();
        s.put(TOPUPS, t.id, t.toJson());
    }

    /** Penarikan saldo: saldo langsung ditahan, dikembalikan jika ditolak. */
    public void requestWithdraw(User u, long amount, String info) throws Exception {
        if (amount < com.antarmakan.app.util.AppSettings.MIN_TOPUP) {
            throw new AppException("Minimal penarikan " + com.antarmakan.app.util.Fmt.rp(com.antarmakan.app.util.AppSettings.MIN_TOPUP));
        }
        if (info.trim().length() < 5) throw new AppException("Isi nomor rekening / e-wallet tujuan");
        TopUp t = new TopUp();
        t.id = Ids.newId();
        t.userId = u.id;
        t.userName = u.name;
        t.userPhone = u.phone;
        t.type = TopUp.TYPE_WITHDRAW;
        t.amount = amount;
        t.info = info;
        t.createdAt = now();
        if (u.balance < amount) throw new AppException("Saldo tidak cukup");
        ledger(u.id, "wd_" + t.id, -amount, "Penarikan saldo (diproses)", "");
        s.put(TOPUPS, t.id, t.toJson());
    }

    public List<TopUp> topUps(String uidOrNull) throws Exception {
        List<JSONObject> raw = uidOrNull == null ? s.list(TOPUPS) : s.query(TOPUPS, "userId", uidOrNull);
        List<TopUp> out = new ArrayList<TopUp>();
        for (JSONObject o : raw) out.add(TopUp.fromJson(o));
        Collections.sort(out, new Comparator<TopUp>() {
            @Override
            public int compare(TopUp a, TopUp b) {
                if (a.status != b.status) return a.status == TopUp.PENDING ? -1 : (b.status == TopUp.PENDING ? 1 : 0);
                return Long.compare(b.createdAt, a.createdAt);
            }
        });
        return out;
    }

    public void decideTopUp(String id, final boolean approve) throws Exception {
        JSONObject o = s.transact(TOPUPS, id, new Store.Updater() {
            @Override
            public JSONObject update(JSONObject cur) throws Exception {
                if (cur == null) throw new AppException("Data tidak ditemukan");
                if (cur.optInt("status") != TopUp.PENDING) throw new AppException("Sudah diproses");
                cur.put("status", approve ? TopUp.APPROVED : TopUp.REJECTED);
                return cur;
            }
        });
        TopUp t = TopUp.fromJson(o);
        boolean top = TopUp.TYPE_TOPUP.equals(t.type);
        if (top) {
            if (approve) ledger(t.userId, "topup_" + t.id, t.amount, "Top up saldo", "");
        } else if (!approve) {
            ledger(t.userId, "wdrefund_" + t.id, t.amount, "Penarikan ditolak, saldo dikembalikan", "");
        }
        notify(t.userId, (top ? "Top up " : "Penarikan ") + (approve ? "disetujui ✅" : "ditolak ❌"),
                Fmt.rp(t.amount), "");
    }

    // =====================================================================
    //  RESTO
    // =====================================================================

    public List<Restaurant> restaurants(String userKec, boolean includeInactive) throws Exception {
        return restaurants(userKec, 0, 0, includeInactive);
    }

    /** Daftar resto, jarak & ongkir dihitung dari titik GPS pengguna (jika ada). */
    public List<Restaurant> restaurants(String userKec, double lat, double lng, boolean includeInactive)
            throws Exception {
        refreshSettings(false);
        List<Restaurant> out = new ArrayList<Restaurant>();
        for (JSONObject o : s.list(RESTOS)) {
            Restaurant r = Restaurant.fromJson(o);
            if (!r.active && !includeInactive) continue;
            r.distanceKm = Geo.distanceKm(userKec, lat, lng, r.kecamatan, r.lat, r.lng);
            r.deliveryFee = Geo.deliveryFee(r.distanceKm);
            out.add(r);
        }
        Collections.sort(out, new Comparator<Restaurant>() {
            @Override
            public int compare(Restaurant a, Restaurant b) {
                boolean oa = a.isOpenNow() && a.inRange(), ob = b.isOpenNow() && b.inRange();
                if (oa != ob) return oa ? -1 : 1;
                return Double.compare(a.distanceKm, b.distanceKm);
            }
        });
        return out;
    }

    public Restaurant resto(String id, String userKec) throws Exception {
        return resto(id, userKec, 0, 0);
    }

    public Restaurant resto(String id, String userKec, double lat, double lng) throws Exception {
        JSONObject o = s.get(RESTOS, id);
        if (o == null) throw new AppException("Resto tidak ditemukan");
        Restaurant r = Restaurant.fromJson(o);
        r.distanceKm = Geo.distanceKm(userKec, lat, lng, r.kecamatan, r.lat, r.lng);
        r.deliveryFee = Geo.deliveryFee(r.distanceKm);
        return r;
    }

    public Restaurant restoOfOwner(String ownerId) throws Exception {
        List<JSONObject> l = s.query(RESTOS, "ownerId", ownerId);
        return l.isEmpty() ? null : Restaurant.fromJson(l.get(0));
    }

    /** Simpan resto (baru / edit). Rating lama tetap dipertahankan. */
    public void saveResto(final Restaurant r) throws Exception {
        if (r.name.trim().length() < 2) throw new AppException("Nama resto wajib diisi");
        if (r.id.length() == 0) {
            r.id = Ids.newId();
            r.createdAt = now();
        }
        s.transact(RESTOS, r.id, new Store.Updater() {
            @Override
            public JSONObject update(JSONObject cur) throws Exception {
                JSONObject n = r.toJson();
                if (cur != null) {
                    n.put("ratingSum", cur.optDouble("ratingSum", 0));
                    n.put("ratingCount", cur.optInt("ratingCount"));
                    n.put("createdAt", cur.optLong("createdAt"));
                    if (!User.ROLE_ADMIN.equals(Session.role(ctx))) {
                        // hanya admin yang boleh mengubah status aktif & pemilik
                        n.put("active", cur.optBoolean("active"));
                        n.put("ownerId", cur.optString("ownerId"));
                    }
                }
                return n;
            }
        });
    }

    public void setRestoOpen(String restoId, final boolean open) throws Exception {
        s.transact(RESTOS, restoId, new Store.Updater() {
            @Override
            public JSONObject update(JSONObject cur) throws Exception {
                if (cur == null) throw new AppException("Resto tidak ditemukan");
                cur.put("isOpen", open);
                return cur;
            }
        });
    }

    public void setRestoActive(String restoId, final boolean active) throws Exception {
        JSONObject r = s.transact(RESTOS, restoId, new Store.Updater() {
            @Override
            public JSONObject update(JSONObject cur) throws Exception {
                if (cur == null) throw new AppException("Resto tidak ditemukan");
                cur.put("active", active);
                return cur;
            }
        });
        if (active) {
            notify(r.optString("ownerId"), "🎉 Resto disetujui",
                    r.optString("name") + " sudah tampil di aplikasi. Selamat berjualan!", "");
        }
    }

    public void setRestoSelfDelivery(String restoId, final boolean self) throws Exception {
        s.transact(RESTOS, restoId, new Store.Updater() {
            @Override
            public JSONObject update(JSONObject cur) throws Exception {
                if (cur == null) throw new AppException("Resto tidak ditemukan");
                cur.put("selfDelivery", self);
                return cur;
            }
        });
    }

    public void deleteResto(String restoId) throws Exception {
        for (MenuItem m : menus(restoId)) {
            deleteImage(m.imageId);
            s.delete(MENUS, m.id);
        }
        JSONObject r = s.get(RESTOS, restoId);
        if (r != null) deleteImage(r.optString("imageId"));
        s.delete(RESTOS, restoId);
    }

    // =====================================================================
    //  MENU
    // =====================================================================

    public List<MenuItem> menus(String restoId) throws Exception {
        List<MenuItem> out = new ArrayList<MenuItem>();
        for (JSONObject o : s.query(MENUS, "restoId", restoId)) out.add(MenuItem.fromJson(o));
        sortMenus(out);
        return out;
    }

    public MenuItem menu(String id) throws Exception {
        JSONObject o = s.get(MENUS, id);
        if (o == null) throw new AppException("Menu tidak ditemukan");
        return MenuItem.fromJson(o);
    }

    public List<MenuItem> allMenus() throws Exception {
        List<MenuItem> out = new ArrayList<MenuItem>();
        for (JSONObject o : s.list(MENUS)) out.add(MenuItem.fromJson(o));
        return out;
    }

    private static void sortMenus(List<MenuItem> l) {
        Collections.sort(l, new Comparator<MenuItem>() {
            @Override
            public int compare(MenuItem a, MenuItem b) {
                if (a.recommended != b.recommended) return a.recommended ? -1 : 1;
                int c = a.category.compareTo(b.category);
                return c != 0 ? c : a.name.compareToIgnoreCase(b.name);
            }
        });
    }

    public void saveMenu(MenuItem m) throws Exception {
        if (m.name.trim().length() < 2) throw new AppException("Nama menu wajib diisi");
        if (m.price <= 0) throw new AppException("Harga harus lebih dari 0");
        if (m.id.length() == 0) m.id = Ids.newId();
        s.put(MENUS, m.id, m.toJson());
    }

    public void deleteMenu(String id) throws Exception {
        JSONObject o = s.get(MENUS, id);
        if (o != null) deleteImage(o.optString("imageId"));
        s.delete(MENUS, id);
    }

    // =====================================================================
    //  FAVORIT
    // =====================================================================

    public Set<String> favoriteIds(String uid) throws Exception {
        Set<String> out = new HashSet<String>();
        JSONObject o = s.get(FAVS, uid);
        if (o != null) {
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                if (!k.equals("id") && o.optBoolean(k)) out.add(k);
            }
        }
        return out;
    }

    public boolean toggleFavorite(String uid, final String restoId) throws Exception {
        JSONObject o = s.transact(FAVS, uid, new Store.Updater() {
            @Override
            public JSONObject update(JSONObject cur) throws Exception {
                if (cur == null) cur = new JSONObject();
                if (cur.optBoolean(restoId)) {
                    cur.remove(restoId);
                } else {
                    cur.put(restoId, true);
                }
                return cur;
            }
        });
        return o != null && o.optBoolean(restoId);
    }

    // =====================================================================
    //  PROMO
    // =====================================================================

    public List<Promo> promos(boolean onlyActive) throws Exception {
        List<Promo> out = new ArrayList<Promo>();
        for (JSONObject o : s.list(PROMOS)) {
            Promo p = Promo.fromJson(o);
            if (onlyActive && (!p.active || p.quota == 0)) continue;
            out.add(p);
        }
        return out;
    }

    public Promo findPromo(String code) throws Exception {
        if (code == null || code.trim().length() == 0) return null;
        for (Promo p : promos(false)) {
            if (p.code.equalsIgnoreCase(code.trim())) return p;
        }
        return null;
    }

    public void savePromo(Promo p) throws Exception {
        p.code = p.code.trim().toUpperCase(java.util.Locale.US);
        if (p.code.length() < 3) throw new AppException("Kode promo minimal 3 huruf");
        if (p.value <= 0) throw new AppException("Nilai promo harus lebih dari 0");
        if (Promo.TYPE_PERSEN.equals(p.type) && p.value > 100) throw new AppException("Persen maksimal 100");
        if (Promo.TYPE_PERSEN.equals(p.type) && p.maxDiscount <= 0) {
            throw new AppException("Isi maksimal potongan (Rp) untuk promo persen");
        }
        Promo existing = findPromo(p.code);
        if (existing != null && !existing.id.equals(p.id)) throw new AppException("Kode promo sudah ada");
        if (p.id.length() == 0) p.id = Ids.newId();
        s.put(PROMOS, p.id, p.toJson());
    }

    public void deletePromo(String id) throws Exception {
        s.delete(PROMOS, id);
    }

    // =====================================================================
    //  PESANAN
    // =====================================================================

    /** Rincian biaya sebelum pesan. */
    public static class Quote {
        public long subtotal, deliveryFee, serviceFee, discount, total;
        public double distanceKm;
        public String promoMessage = "";
        public Promo promo;
    }

    public Quote quote(Restaurant r, long subtotal, String kecamatan, double lat, double lng,
                       String promoCode) throws Exception {
        Quote q = new Quote();
        q.subtotal = subtotal;
        q.distanceKm = Geo.distanceKm(kecamatan, lat, lng, r.kecamatan, r.lat, r.lng);
        q.deliveryFee = Geo.deliveryFee(q.distanceKm);
        q.serviceFee = com.antarmakan.app.util.AppSettings.BIAYA_LAYANAN;
        if (promoCode != null && promoCode.trim().length() > 0) {
            Promo p = findPromo(promoCode);
            if (p == null || !p.active) {
                q.promoMessage = "Kode promo tidak ditemukan";
            } else if (p.quota == 0) {
                q.promoMessage = "Kuota promo sudah habis";
            } else if (subtotal < p.minOrder) {
                q.promoMessage = "Minimal belanja " + com.antarmakan.app.util.Fmt.rp(p.minOrder);
            } else {
                q.promo = p;
                q.discount = p.calcDiscount(subtotal, q.deliveryFee);
                q.promoMessage = "Promo dipakai: hemat " + com.antarmakan.app.util.Fmt.rp(q.discount);
            }
        }
        q.total = Math.max(0, q.subtotal + q.deliveryFee + q.serviceFee - q.discount);
        return q;
    }

    public Order placeOrder(User u, String restoId, Cart cart, String address, String kecamatan,
                            double lat, double lng, String note, String payment, String promoCode)
            throws Exception {
        if (cart.isEmpty()) throw new AppException("Keranjang kosong");
        if (address.trim().length() < 5) throw new AppException("Alamat pengiriman wajib diisi lengkap");
        if (!Geo.has(lat, lng)) throw new AppException("Pilih titik lokasi pengantaran di peta");
        Restaurant r = resto(restoId, kecamatan);
        if (!r.isOpenNow()) throw new AppException("Maaf, resto sedang tutup");

        // pakai harga & ketersediaan terbaru dari server
        Map<String, MenuItem> latest = new HashMap<String, MenuItem>();
        for (MenuItem m : menus(restoId)) latest.put(m.id, m);
        Order o = new Order();
        long subtotal = 0;
        for (CartItem ci : cart.getItems()) {
            MenuItem m = latest.get(ci.menuId);
            if (m == null || !m.available) throw new AppException(ci.name + " sedang tidak tersedia");
            long unit = m.price + com.antarmakan.app.model.MenuOption.extraPrice(m.options, ci.options);
            OrderItem it = new OrderItem();
            it.menuId = m.id;
            it.name = m.name;
            it.price = unit;
            it.qty = ci.qty;
            it.note = ci.note;
            it.options = ci.options;
            o.items.add(it);
            subtotal += unit * ci.qty;
        }

        Quote q = quote(r, subtotal, kecamatan, lat, lng, promoCode);
        if (q.distanceKm > r.effectiveMaxKm()) {
            throw new AppException("Lokasi di luar jangkauan antar resto ini (maks "
                    + Fmt.km(r.effectiveMaxKm()) + ")");
        }
        if (promoCode != null && promoCode.trim().length() > 0 && q.promo == null) {
            throw new AppException(q.promoMessage);
        }

        o.id = Ids.newId();
        o.code = Ids.orderCode();
        o.customerId = u.id;
        o.customerName = u.name;
        o.customerPhone = u.phone;
        o.restoId = r.id;
        o.restoName = r.name;
        o.restoEmoji = r.emoji;
        o.restoAddress = r.address;
        o.restoKecamatan = r.kecamatan;
        o.restoPhone = r.phone;
        o.restoOwnerId = r.ownerId;
        o.selfDelivery = r.selfDelivery;
        o.custLat = lat;
        o.custLng = lng;
        o.restoLat = r.lat;
        o.restoLng = r.lng;
        o.status = Order.MENUNGGU;
        o.subtotal = q.subtotal;
        o.deliveryFee = q.deliveryFee;
        o.serviceFee = q.serviceFee;
        o.discount = q.discount;
        o.total = q.total;
        o.distanceKm = q.distanceKm;
        o.paymentMethod = payment;
        o.promoCode = q.promo == null ? "" : q.promo.code;
        o.promoId = q.promo == null ? "" : q.promo.id;
        o.address = address.trim();
        o.kecamatan = kecamatan;
        o.note = note == null ? "" : note.trim();
        o.createdAt = now();
        o.updatedAt = o.createdAt;

        // kurangi kuota promo
        if (q.promo != null && q.promo.quota > 0) {
            s.transact(PROMOS, q.promo.id, new Store.Updater() {
                @Override
                public JSONObject update(JSONObject cur) throws Exception {
                    if (cur == null) throw new AppException("Promo tidak ditemukan");
                    int quota = cur.optInt("quota", -1);
                    if (quota == 0) throw new AppException("Kuota promo sudah habis");
                    if (quota > 0) cur.put("quota", quota - 1);
                    return cur;
                }
            });
        }

        if (Order.BAYAR_SALDO.equals(payment) && u.balance < o.total) {
            throw new AppException("Saldo tidak cukup");
        }
        s.put(ORDERS, o.id, o.toJson());
        if (Order.BAYAR_SALDO.equals(payment)) {
            try {
                ledger(u.id, "pay_" + o.id, -o.total, "Bayar pesanan " + o.code, o.id);
            } catch (Exception e) {
                // pembayaran gagal: batalkan pesanan (tanpa pengembalian karena belum terbayar)
                try {
                    transition(o.id, new Transition() {
                        @Override
                        public void apply(Order x) {
                            x.status = Order.DIBATALKAN;
                            x.cancelReason = "Pembayaran saldo gagal";
                        }
                    });
                } catch (Exception ignored) {
                }
                throw e;
            }
        }
        cart.clear();
        // pesanan QRIS baru diteruskan ke resto oleh server setelah lunas
        if (!Order.BAYAR_QRIS.equals(payment)) {
            notify(o.restoOwnerId, "🔔 Pesanan baru " + o.code,
                    o.customerName + " • " + o.itemCount() + " item • " + Fmt.rp(o.subtotal), o.id);
        }
        return o;
    }

    /**
     * Isi ulang keranjang dari pesanan lama ("Pesan lagi") memakai harga terbaru.
     * Mengembalikan jumlah menu yang tidak bisa dipesan lagi.
     */
    public int reorder(Order old, Cart cart) throws Exception {
        Restaurant r = resto(old.restoId, old.kecamatan);
        if (!r.active) throw new AppException("Resto sudah tidak aktif");
        Map<String, MenuItem> latest = new HashMap<String, MenuItem>();
        for (MenuItem m : menus(old.restoId)) latest.put(m.id, m);
        int skipped = 0;
        boolean first = true;
        for (OrderItem it : old.items) {
            MenuItem m = latest.get(it.menuId);
            long extra;
            try {
                if (m == null || !m.available) throw new AppException("habis");
                extra = com.antarmakan.app.model.MenuOption.extraPrice(m.options, it.options);
            } catch (AppException e) {
                skipped++;
                continue;
            }
            if (first) {
                cart.clear();
                first = false;
            }
            CartItem ci = new CartItem();
            ci.menuId = m.id;
            ci.name = m.name;
            ci.emoji = m.emoji;
            ci.price = m.price + extra;
            ci.qty = it.qty;
            ci.note = it.note;
            ci.options = it.options;
            cart.addLine(r.id, r.name, ci);
        }
        if (first) throw new AppException("Semua menu di pesanan ini sedang tidak tersedia");
        return skipped;
    }

    public Order order(String id) throws Exception {
        JSONObject o = s.get(ORDERS, id);
        if (o == null) throw new AppException("Pesanan tidak ditemukan");
        List<Order> one = new ArrayList<Order>();
        one.add(Order.fromJson(o));
        expireStale(one);
        return one.get(0);
    }

    /** Pesanan menunggu resto terlalu lama? */
    public static boolean isStale(Order o) {
        if (o.status != Order.MENUNGGU) return false;
        long now = System.currentTimeMillis();
        // QRIS belum dibayar: batal bila lewat masa berlaku QR (+ cadangan)
        if (o.awaitingPayment()) return now - o.createdAt > 30 * 60000L;
        long start = o.paidAt > 0 ? o.paidAt : o.createdAt;
        return now - start > com.antarmakan.app.util.AppSettings.BATAS_RESPON_RESTO_MENIT * 60000L;
    }

    /** Pelanggan boleh membatalkan karena tidak ada driver? */
    public static boolean canCancelNoDriver(Order o) {
        return (o.status == Order.DIMASAK || o.status == Order.SIAP) && !o.hasDriver() && !o.selfDelivery
                && System.currentTimeMillis() - o.updatedAt > com.antarmakan.app.util.AppSettings.BATAS_CARI_DRIVER_MENIT * 60000L;
    }

    /**
     * Batalkan otomatis pesanan yang tidak direspons resto. Dijalankan oleh HP
     * pelanggan / resto saat memuat pesanan (tidak perlu server).
     */
    private void expireStale(List<Order> list) {
        String me = Session.userId(ctx);
        for (int i = 0; i < list.size(); i++) {
            final Order o = list.get(i);
            if (!isStale(o)) continue;
            if (!me.equals(o.customerId) && !me.equals(o.restoOwnerId)) continue;
            try {
                Order c = transition(o.id, new Transition() {
                    @Override
                    public void apply(Order x) {
                        if (x.status != Order.MENUNGGU) throw new AppException("sudah diproses");
                        x.status = Order.DIBATALKAN;
                        x.cancelReason = "Otomatis dibatalkan: resto tidak merespons "
                                + com.antarmakan.app.util.AppSettings.BATAS_RESPON_RESTO_MENIT + " menit";
                    }
                });
                afterCancel(c);
                notify(c.customerId, "❌ Pesanan " + c.code + " dibatalkan",
                        "Resto tidak merespons. Dana dikembalikan bila bayar saldo.", c.id);
                notify(c.restoOwnerId, "Pesanan " + c.code + " dibatalkan otomatis",
                        "Tidak direspons dalam " + com.antarmakan.app.util.AppSettings.BATAS_RESPON_RESTO_MENIT + " menit", c.id);
                list.set(i, c);
            } catch (Exception ignored) {
            }
        }
    }

    private static List<Order> toOrders(List<JSONObject> raw) {
        List<Order> out = new ArrayList<Order>();
        for (JSONObject o : raw) out.add(Order.fromJson(o));
        Collections.sort(out, new Comparator<Order>() {
            @Override
            public int compare(Order a, Order b) {
                if (a.isActive() != b.isActive()) return a.isActive() ? -1 : 1;
                return Long.compare(b.createdAt, a.createdAt);
            }
        });
        return out;
    }

    public List<Order> ordersOfCustomer(String uid) throws Exception {
        List<Order> l = toOrders(s.query(ORDERS, "customerId", uid));
        expireStale(l);
        return l;
    }

    public List<Order> ordersOfResto(String restoId) throws Exception {
        List<Order> l = toOrders(s.query(ORDERS, "restoId", restoId));
        expireStale(l);
        return l;
    }

    public List<Order> ordersOfDriver(String driverId) throws Exception {
        return toOrders(s.query(ORDERS, "driverId", driverId));
    }

    /** Pesanan yang bisa diambil driver: sudah diterima resto & belum ada driver. */
    public List<Order> availableJobs() throws Exception {
        return availableJobs(0, 0);
    }

    /** Order yang bisa diambil driver di sekitar posisinya (radius dari pengaturan). */
    public List<Order> availableJobs(double lat, double lng) throws Exception {
        refreshSettings(false);
        List<Order> out = new ArrayList<Order>();
        for (Order o : toOrders(s.query(ORDERS, "driverId", ""))) {
            if (o.selfDelivery || (o.status != Order.DIMASAK && o.status != Order.SIAP)) continue;
            if (Geo.has(lat, lng) && distanceToResto(o, lat, lng) > AppSettings.RADIUS_DRIVER_KM) continue;
            out.add(o);
        }
        return out;
    }

    private static double distanceToResto(Order o, double lat, double lng) {
        if (Geo.has(o.restoLat, o.restoLng)) return Geo.distanceKm(lat, lng, o.restoLat, o.restoLng);
        return Geo.distanceKm(lat, lng, Geo.lat(o.restoKecamatan), Geo.lng(o.restoKecamatan));
    }

    private long lastLocSave = 0;

    /** Simpan posisi terakhir driver (paling sering tiap 2 menit) untuk pembagian order. */
    public void updateMyLocation(final double lat, final double lng) {
        if (!Geo.has(lat, lng) || System.currentTimeMillis() - lastLocSave < 120000) return;
        lastLocSave = System.currentTimeMillis();
        try {
            editUser(Session.userId(ctx), new UserEdit() {
                @Override
                public void edit(JSONObject o) throws Exception {
                    o.put("lat", lat);
                    o.put("lng", lng);
                }
            });
        } catch (Exception ignored) {
        }
    }

    public List<Order> allOrders() throws Exception {
        return toOrders(s.list(ORDERS));
    }

    private interface Transition {
        void apply(Order o) throws Exception;
    }

    private Order transition(String orderId, final Transition t) throws Exception {
        JSONObject res = s.transact(ORDERS, orderId, new Store.Updater() {
            @Override
            public JSONObject update(JSONObject cur) throws Exception {
                if (cur == null) throw new AppException("Pesanan tidak ditemukan");
                Order o = Order.fromJson(cur);
                t.apply(o);
                JSONObject j = o.toJson();
                j.put("updatedAt", serverTime()); // waktu server (dipakai Rules)
                return j;
            }
        });
        Order out = Order.fromJson(res);
        out.updatedAt = now();
        return out;
    }

    // ---- aksi resto ----

    public Order restoAccept(String orderId) throws Exception {
        Order o = transition(orderId, new Transition() {
            @Override
            public void apply(Order o) {
                if (o.status != Order.MENUNGGU) throw new AppException("Pesanan sudah diproses");
                o.status = Order.DIMASAK;
            }
        });
        notify(o.customerId, "👨‍🍳 Pesanan diterima", o.restoName + " sedang menyiapkan pesananmu", o.id);
        if (!o.selfDelivery) notifyOnlineDrivers(o);
        return o;
    }

    /** Resto mengantar sendiri: mulai antar. */
    public Order restoStartDelivery(String orderId) throws Exception {
        Order o = transition(orderId, new Transition() {
            @Override
            public void apply(Order o) {
                if (!o.selfDelivery) throw new AppException("Pesanan ini diantar driver");
                if (o.status != Order.DIMASAK && o.status != Order.SIAP) {
                    throw new AppException("Status pesanan sudah berubah");
                }
                o.status = Order.DIANTAR;
                o.driverName = o.restoName + " (penjual)";
                o.driverPhone = o.restoPhone;
            }
        });
        notify(o.customerId, "🛵 Pesanan sedang diantar", "Diantar langsung oleh " + o.restoName, o.id);
        return o;
    }

    /** Resto mengantar sendiri: pesanan sampai. */
    public Order restoDelivered(String orderId) throws Exception {
        Order o = transition(orderId, new Transition() {
            @Override
            public void apply(Order o) {
                if (!o.selfDelivery) throw new AppException("Pesanan ini diantar driver");
                if (o.status != Order.DIANTAR) throw new AppException("Status pesanan sudah berubah");
                o.status = Order.SELESAI;
                o.settled = true;
            }
        });
        settle(o);
        clearTracking(o.id);
        notify(o.customerId, "🎉 Pesanan sampai", "Selamat makan! Beri ulasan untuk " + o.restoName, o.id);
        return o;
    }

    public Order restoReady(String orderId) throws Exception {
        Order o = transition(orderId, new Transition() {
            @Override
            public void apply(Order o) {
                if (o.status != Order.DIMASAK) throw new AppException("Status pesanan sudah berubah");
                o.status = o.hasDriver() ? Order.DIJEMPUT : Order.SIAP;
            }
        });
        if (o.hasDriver()) notify(o.driverId, "🥡 Makanan siap diambil", o.restoName + " • " + o.code, o.id);
        return o;
    }

    public Order restoReject(String orderId, final String reason) throws Exception {
        Order o = transition(orderId, new Transition() {
            @Override
            public void apply(Order o) {
                if (o.status != Order.MENUNGGU) throw new AppException("Pesanan sudah diproses");
                o.status = Order.DIBATALKAN;
                o.cancelReason = "Ditolak resto: " + reason;
            }
        });
        afterCancel(o);
        notify(o.customerId, "❌ Pesanan ditolak resto", reason, o.id);
        return o;
    }

    // ---- aksi pelanggan ----

    public Order customerCancel(String orderId) throws Exception {
        Order o = transition(orderId, new Transition() {
            @Override
            public void apply(Order o) {
                if (o.status != Order.MENUNGGU) {
                    throw new AppException("Pesanan sudah diproses resto, tidak bisa dibatalkan");
                }
                o.status = Order.DIBATALKAN;
                o.cancelReason = "Dibatalkan pelanggan";
            }
        });
        afterCancel(o);
        notify(o.restoOwnerId, "Pesanan " + o.code + " dibatalkan", "Dibatalkan oleh pelanggan", o.id);
        return o;
    }

    /** Batal karena tidak ada driver dalam batas waktu. */
    public Order customerCancelNoDriver(String orderId) throws Exception {
        Order o = transition(orderId, new Transition() {
            @Override
            public void apply(Order o) {
                if (!canCancelNoDriver(o)) throw new AppException("Pesanan tidak bisa dibatalkan sekarang");
                o.status = Order.DIBATALKAN;
                o.cancelReason = "Dibatalkan pelanggan: tidak ada driver";
            }
        });
        afterCancel(o);
        notify(o.restoOwnerId, "Pesanan " + o.code + " dibatalkan", "Tidak ada driver yang mengambil", o.id);
        return o;
    }

    // ---- aksi admin ----

    public Order adminCancel(String orderId, final String reason) throws Exception {
        Order o = transition(orderId, new Transition() {
            @Override
            public void apply(Order o) {
                if (!o.isActive()) throw new AppException("Pesanan sudah selesai/batal");
                o.status = Order.DIBATALKAN;
                o.cancelReason = "Dibatalkan admin: " + reason;
            }
        });
        afterCancel(o);
        String t = "Pesanan " + o.code + " dibatalkan admin";
        notify(o.customerId, t, reason, o.id);
        notify(o.restoOwnerId, t, reason, o.id);
        notify(o.driverId, t, reason, o.id);
        return o;
    }

    /** Kembalikan saldo & kuota promo saat pesanan batal. */
    private void clearTracking(String orderId) {
        try {
            s.delete(TRACKING, orderId);
        } catch (Exception ignored) {
        }
    }

    private void afterCancel(Order o) throws Exception {
        clearTracking(o.id);
        if (o.isPrepaid()
                && s.get(WALLET + "/" + o.customerId, "pay_" + o.id) != null) {
            ledger(o.customerId, "refund_" + o.id, o.total, "Pengembalian dana " + o.code, o.id);
        }
        if (o.promoCode.length() > 0) {
            Promo p = findPromo(o.promoCode);
            if (p != null && p.quota >= 0) {
                s.transact(PROMOS, p.id, new Store.Updater() {
                    @Override
                    public JSONObject update(JSONObject cur) throws Exception {
                        if (cur == null) return null;
                        int q = cur.optInt("quota", -1);
                        if (q >= 0) cur.put("quota", q + 1);
                        return cur;
                    }
                });
            }
        }
    }

    // ---- aksi driver ----

    public Order driverTake(String orderId, final User driver) throws Exception {
        if (!driver.active) throw new AppException("Akun driver belum disetujui admin");
        for (Order mine : ordersOfDriver(driver.id)) {
            if (mine.isActive()) throw new AppException("Selesaikan dulu pesanan " + mine.code);
        }
        Order taken = transition(orderId, new Transition() {
            @Override
            public void apply(Order o) {
                if (o.selfDelivery) throw new AppException("Pesanan ini diantar sendiri oleh resto");
                if (o.hasDriver()) throw new AppException("Pesanan sudah diambil driver lain");
                if (o.status != Order.DIMASAK && o.status != Order.SIAP) {
                    throw new AppException("Pesanan tidak tersedia");
                }
                o.driverId = driver.id;
                o.driverName = driver.name;
                o.driverPhone = driver.phone;
                o.driverVehicle = driver.vehicle;
                if (o.status == Order.SIAP) o.status = Order.DIJEMPUT;
            }
        });
        notify(taken.customerId, "🛵 Driver ditemukan", driver.name + " akan mengantar pesananmu", taken.id);
        notify(taken.restoOwnerId, "🛵 Driver menuju resto", driver.name + " • " + taken.code, taken.id);
        return taken;
    }

    public Order driverPickedUp(String orderId, final String driverId) throws Exception {
        Order o = transition(orderId, new Transition() {
            @Override
            public void apply(Order o) {
                if (!driverId.equals(o.driverId)) throw new AppException("Bukan pesanan Anda");
                if (o.status == Order.DIMASAK) throw new AppException("Makanan belum siap dari resto");
                if (o.status != Order.DIJEMPUT) throw new AppException("Status pesanan sudah berubah");
                o.status = Order.DIANTAR;
            }
        });
        notify(o.customerId, "🛵 Pesanan sedang diantar", o.driverName + " menuju lokasimu", o.id);
        return o;
    }

    public Order driverDelivered(String orderId, final String driverId) throws Exception {
        Order o = transition(orderId, new Transition() {
            @Override
            public void apply(Order o) {
                if (!driverId.equals(o.driverId)) throw new AppException("Bukan pesanan Anda");
                if (o.status != Order.DIANTAR) throw new AppException("Status pesanan sudah berubah");
                o.status = Order.SELESAI;
                o.settled = true;
            }
        });
        settle(o);
        clearTracking(o.id);
        notify(o.customerId, "🎉 Pesanan sampai", "Selamat makan! Beri ulasan untuk " + o.restoName, o.id);
        return o;
    }

    /**
     * Pembagian uang saat pesanan selesai.
     * SALDO : pelanggan sudah bayar ke aplikasi -> resto & driver menerima saldo.
     * TUNAI : driver menerima uang tunai dari pelanggan & membayar resto tunai,
     *         lalu komisi aplikasi dipotong dari saldo resto & driver.
     */
    private void settle(Order o) throws Exception {
        long komisiResto = o.subtotal * com.antarmakan.app.util.AppSettings.KOMISI_PERSEN_RESTO / 100;
        long komisiDriver = o.deliveryFee * com.antarmakan.app.util.AppSettings.KOMISI_PERSEN_DRIVER / 100;
        if (o.selfDelivery) {
            // diantar sendiri: ongkir 100% untuk penjual
            if (o.restoOwnerId.length() == 0) return;
            if (o.isPrepaid()) {
                ledger(o.restoOwnerId, "settle_" + o.id, o.subtotal - komisiResto + o.deliveryFee,
                        "Penjualan + ongkir " + o.code + " (potong komisi " + com.antarmakan.app.util.AppSettings.KOMISI_PERSEN_RESTO + "%)", o.id);
            } else {
                ledger(o.restoOwnerId, "settle_" + o.id, o.discount - komisiResto - o.serviceFee,
                        "Komisi & biaya layanan " + o.code + " (tunai)", o.id);
            }
            return;
        }
        if (o.isPrepaid()) {
            ledger(o.restoOwnerId, "settle_" + o.id, o.subtotal - komisiResto,
                    "Penjualan " + o.code + " (potong komisi " + com.antarmakan.app.util.AppSettings.KOMISI_PERSEN_RESTO + "%)", o.id);
            ledger(o.driverId, "fee_" + o.id, o.deliveryFee - komisiDriver, "Ongkir " + o.code, o.id);
        } else {
            ledger(o.restoOwnerId, "settle_" + o.id, -komisiResto, "Komisi aplikasi " + o.code, o.id);
            long driverAdj = o.discount - komisiDriver - o.serviceFee;
            ledger(o.driverId, "fee_" + o.id, driverAdj, "Komisi & biaya layanan " + o.code + " (tunai)", o.id);
        }
    }

    // =====================================================================
    //  ULASAN
    // =====================================================================

    public void addReview(String orderId, final int rating, final int driverRating, String comment)
            throws Exception {
        Order o = transition(orderId, new Transition() {
            @Override
            public void apply(Order o) {
                if (o.status != Order.SELESAI) throw new AppException("Pesanan belum selesai");
                if (o.rated) throw new AppException("Pesanan sudah diulas");
                o.rated = true;
            }
        });
        Review r = new Review();
        r.id = Ids.newId();
        r.orderId = o.id;
        r.restoId = o.restoId;
        r.customerId = o.customerId;
        r.customerName = o.customerName;
        r.driverId = o.driverId;
        r.rating = rating;
        r.driverRating = driverRating;
        r.comment = comment == null ? "" : comment.trim();
        r.createdAt = now();
        s.put(REVIEWS, r.id, r.toJson());
        notify(o.restoOwnerId, "⭐ Ulasan baru (" + rating + " bintang)",
                o.customerName + (r.comment.length() > 0 ? ": " + r.comment : ""), o.id);

        s.transact(RESTOS, o.restoId, new Store.Updater() {
            @Override
            public JSONObject update(JSONObject cur) throws Exception {
                if (cur == null) return null;
                cur.put("ratingSum", cur.optDouble("ratingSum", 0) + rating);
                cur.put("ratingCount", cur.optInt("ratingCount") + 1);
                return cur;
            }
        });
        if (o.hasDriver() && driverRating > 0) {
            editUser(o.driverId, new UserEdit() {
                @Override
                public void edit(JSONObject u) throws Exception {
                    u.put("ratingSum", u.optDouble("ratingSum", 0) + driverRating);
                    u.put("ratingCount", u.optInt("ratingCount") + 1);
                }
            });
        }
    }

    public List<Review> reviews(String restoId) throws Exception {
        List<Review> out = new ArrayList<Review>();
        for (JSONObject o : s.query(REVIEWS, "restoId", restoId)) out.add(Review.fromJson(o));
        Collections.sort(out, new Comparator<Review>() {
            @Override
            public int compare(Review a, Review b) {
                return Long.compare(b.createdAt, a.createdAt);
            }
        });
        return out;
    }

    public void replyReview(String reviewId, final String reply) throws Exception {
        s.transact(REVIEWS, reviewId, new Store.Updater() {
            @Override
            public JSONObject update(JSONObject cur) throws Exception {
                if (cur == null) throw new AppException("Ulasan tidak ditemukan");
                cur.put("reply", reply.trim());
                return cur;
            }
        });
    }

    // =====================================================================
    //  FOTO
    // =====================================================================

    /** Simpan foto (base64 JPEG), kembalikan id-nya. */
    public String uploadImage(String base64) throws Exception {
        if (base64.length() > 400000) throw new AppException("Ukuran foto terlalu besar");
        String id = Ids.newId();
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("data", base64);
        o.put("createdAt", now());
        s.put(IMAGES, id, o);
        return id;
    }

    public String imageData(String id) throws Exception {
        JSONObject o = s.get(IMAGES, id);
        return o == null ? null : o.optString("data", null);
    }

    public void deleteImage(String id) throws Exception {
        if (id != null && id.length() > 0) s.delete(IMAGES, id);
    }

    // =====================================================================
    //  QRIS (Midtrans, lewat server Cloud Functions)
    // =====================================================================

    /** Panggil fungsi server (Firebase callable) dengan token login. */
    private JSONObject callFunction(String name, JSONObject data) throws Exception {
        java.util.Map<String, String> h = new java.util.HashMap<String, String>();
        h.put("Authorization", "Bearer " + auth.idToken());
        JSONObject body = new JSONObject().put("data", data);
        Http.Response r = Http.request("POST", Config.FUNCTIONS_URL + "/" + name, body.toString(), h);
        JSONObject o = new JSONObject(r.body.length() == 0 ? "{}" : r.body);
        if (o.has("error")) {
            JSONObject e = o.optJSONObject("error");
            throw new AppException(e != null ? e.optString("message", "Server error") : "Server error");
        }
        if (r.code >= 400) throw new AppException("Server pembayaran belum siap (" + r.code + ")");
        return o.optJSONObject("result") != null ? o.optJSONObject("result") : new JSONObject();
    }

    /** Buat / ambil QRIS untuk pesanan: {qrString, expiresAt, amount} atau {paid:true}. */
    public JSONObject createQris(String orderId) throws Exception {
        return callFunction("createQrisPayment", new JSONObject().put("orderId", orderId));
    }

    /** Cek langsung ke Midtrans apakah sudah dibayar. */
    public boolean checkQris(String orderId) throws Exception {
        return callFunction("checkQrisPayment", new JSONObject().put("orderId", orderId)).optBoolean("paid");
    }

    // =====================================================================
    //  KOMPLAIN
    // =====================================================================

    public static final String COMPLAINTS = "complaints";

    public Complaint complaintOf(String orderId) throws Exception {
        JSONObject o = s.get(COMPLAINTS, orderId);
        return o == null ? null : Complaint.fromJson(o);
    }

    /** Pelanggan melaporkan masalah pesanan (satu laporan per pesanan). */
    public void createComplaint(Order o, String category, String text, String imageId) throws Exception {
        if (text.trim().length() < 5) throw new AppException("Ceritakan masalahnya (minimal 5 huruf)");
        if (complaintOf(o.id) != null) throw new AppException("Pesanan ini sudah dilaporkan");
        Complaint c = new Complaint();
        c.id = o.id;
        c.orderId = o.id;
        c.orderCode = o.code;
        c.customerId = o.customerId;
        c.customerName = o.customerName;
        c.customerPhone = o.customerPhone;
        c.restoName = o.restoName;
        c.restoOwnerId = o.restoOwnerId;
        c.driverId = o.driverId;
        c.category = category;
        c.text = text.trim();
        c.imageId = imageId == null ? "" : imageId;
        c.orderTotal = o.total;
        c.createdAt = now();
        s.put(COMPLAINTS, c.id, c.toJson());
        try {
            for (JSONObject u : s.query(USERS, "role", User.ROLE_ADMIN)) {
                notify(u.optString("id"), "⚠️ Komplain baru " + o.code, category + ": " + c.text, o.id);
            }
        } catch (Exception ignored) {
        }
    }

    public List<Complaint> complaints(String customerIdOrNull) throws Exception {
        List<JSONObject> raw = customerIdOrNull == null ? s.list(COMPLAINTS)
                : s.query(COMPLAINTS, "customerId", customerIdOrNull);
        List<Complaint> out = new ArrayList<Complaint>();
        for (JSONObject o : raw) out.add(Complaint.fromJson(o));
        Collections.sort(out, new Comparator<Complaint>() {
            @Override
            public int compare(Complaint a, Complaint b) {
                boolean oa = Complaint.OPEN.equals(a.status), ob = Complaint.OPEN.equals(b.status);
                if (oa != ob) return oa ? -1 : 1;
                return Long.compare(b.createdAt, a.createdAt);
            }
        });
        return out;
    }

    /** Admin menyelesaikan komplain, opsional dengan pengembalian dana ke saldo pelanggan. */
    public void resolveComplaint(final String id, final boolean accept, final long refund, final String note)
            throws Exception {
        JSONObject res = s.transact(COMPLAINTS, id, new Store.Updater() {
            @Override
            public JSONObject update(JSONObject cur) throws Exception {
                if (cur == null) throw new AppException("Komplain tidak ditemukan");
                if (!Complaint.OPEN.equals(cur.optString("status"))) throw new AppException("Sudah diproses");
                if (refund < 0 || refund > cur.optLong("orderTotal")) {
                    throw new AppException("Pengembalian maksimal " + Fmt.rp(cur.optLong("orderTotal")));
                }
                cur.put("status", accept ? Complaint.RESOLVED : Complaint.REJECTED);
                cur.put("refund", accept ? refund : 0);
                cur.put("adminNote", note.trim());
                return cur;
            }
        });
        Complaint c = Complaint.fromJson(res);
        if (accept && refund > 0) {
            ledger(c.customerId, "complaint_" + c.orderId, refund, "Pengembalian komplain " + c.orderCode, c.orderId);
        }
        notify(c.customerId, accept ? "✅ Komplain " + c.orderCode + " diselesaikan" : "Komplain " + c.orderCode + " ditolak",
                (accept && refund > 0 ? "Dana " + Fmt.rp(refund) + " dikembalikan ke saldo. " : "") + note, c.orderId);
        notify(c.restoOwnerId, "Komplain pesanan " + c.orderCode, c.category + (accept ? " (diterima admin)" : " (ditolak)"), c.orderId);
    }

    // =====================================================================
    //  CHAT
    // =====================================================================

    public void sendMessage(String orderId, User from, String text) throws Exception {
        sendMessage(orderId, from, text, "");
    }

    /** Kirim pesan chat (teks dan/atau foto). */
    public void sendMessage(String orderId, User from, String text, String imageId) throws Exception {
        if (text.trim().length() == 0 && (imageId == null || imageId.length() == 0)) return;
        Message m = new Message();
        m.id = Ids.newId();
        m.orderId = orderId;
        m.senderId = from.id;
        m.senderName = from.name;
        m.senderRole = from.role;
        m.text = text.trim();
        m.imageId = imageId == null ? "" : imageId;
        m.createdAt = now();
        s.put(MSGS, m.id, m.toJson());
        try {
            Order o = order(orderId);
            String title = "💬 " + from.name + " (" + o.code + ")";
            Set<String> to = new HashSet<String>();
            to.add(o.customerId);
            to.add(o.restoOwnerId);
            if (o.hasDriver()) to.add(o.driverId);
            to.remove(from.id);
            String body = m.imageId.length() > 0 ? "📷 Foto" + (m.text.length() > 0 ? ": " + m.text : "") : m.text;
            for (String uid : to) notify(uid, title, body, orderId);
        } catch (Exception ignored) {
        }
    }

    // =====================================================================
    //  LACAK PENGANTAR
    // =====================================================================

    public static final String TRACKING = "tracking";   // tracking/{orderId} = {lat,lng,at}

    /** Pengantar mengirim posisinya. */
    public void updateTracking(String orderId, double lat, double lng) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", orderId);
        o.put("lat", lat);
        o.put("lng", lng);
        o.put("at", serverTime());
        s.put(TRACKING, orderId, o);
    }

    /** Posisi terakhir pengantar: {lat, lng, at} atau null. */
    public JSONObject tracking(String orderId) throws Exception {
        return s.get(TRACKING, orderId);
    }

    /** Pesanan yang sedang diantar oleh pengguna ini (driver / penjual antar sendiri). */
    public List<Order> deliveriesInProgress(User u) throws Exception {
        List<Order> out = new ArrayList<Order>();
        if (u.isDriver()) {
            for (Order o : ordersOfDriver(u.id)) {
                if (o.isActive() && o.status != Order.MENUNGGU) out.add(o);
            }
        } else if (u.isMerchant()) {
            Restaurant r = restoOfOwner(u.id);
            if (r != null) {
                for (Order o : ordersOfResto(r.id)) {
                    if (o.selfDelivery && o.status == Order.DIANTAR) out.add(o);
                }
            }
        }
        return out;
    }

    // =====================================================================
    //  NOTIFIKASI
    // =====================================================================

    private static JSONObject serverTime() throws Exception {
        return new JSONObject().put(".sv", "timestamp");
    }

    /** Kirim notifikasi ke satu pengguna. Tidak pernah menggagalkan aksi utama. */
    private void notify(String uid, String title, String body, String orderId) {
        if (uid == null || uid.length() == 0) return;
        try {
            JSONObject o = new JSONObject();
            String id = Ids.newId();
            o.put("id", id);
            o.put("title", title);
            o.put("body", body);
            o.put("orderId", orderId == null ? "" : orderId);
            o.put("createdAt", serverTime());
            s.put(NOTIFS + "/" + uid, id, o);
        } catch (Exception ignored) {
        }
    }

    private void notifyOnlineDrivers(Order o) {
        try {
            for (JSONObject u : s.query(USERS, "role", User.ROLE_DRIVER)) {
                if (u.optBoolean("online") && u.optBoolean("active", true)) {
                    double dl = u.optDouble("lat", 0), dg = u.optDouble("lng", 0);
                    if (Geo.has(dl, dg) && distanceToResto(o, dl, dg) > AppSettings.RADIUS_DRIVER_KM) continue;
                    notify(u.optString("id"), "📦 Order baru tersedia",
                            o.restoName + " → Kec. " + o.kecamatan + " • ongkir " + Fmt.rp(o.deliveryFee), o.id);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static List<AppNotif> toNotifs(List<JSONObject> raw) {
        List<AppNotif> out = new ArrayList<AppNotif>();
        for (JSONObject o : raw) out.add(AppNotif.fromJson(o));
        Collections.sort(out, new Comparator<AppNotif>() {
            @Override
            public int compare(AppNotif a, AppNotif b) {
                return Long.compare(a.createdAt, b.createdAt);
            }
        });
        return out;
    }

    /** Notifikasi pribadi yang lebih baru dari "after" (urut lama ke baru). after < 0 = 50 terakhir. */
    public List<AppNotif> notificationsSince(String uid, long after) throws Exception {
        return toNotifs(s.since(NOTIFS + "/" + uid, "createdAt", after, 50));
    }

    /** Pengumuman admin untuk peran tertentu. */
    public List<AppNotif> broadcastsSince(String role, long after) throws Exception {
        List<AppNotif> out = new ArrayList<AppNotif>();
        for (AppNotif n : toNotifs(s.since(BROADCASTS, "createdAt", after, 20))) {
            if ("all".equals(n.role) || n.role.equals(role)) out.add(n);
        }
        return out;
    }

    /** Admin mengirim pengumuman ke semua pengguna atau peran tertentu. */
    public void broadcast(String title, String body, String role) throws Exception {
        if (title.trim().length() < 3) throw new AppException("Judul pengumuman wajib diisi");
        JSONObject o = new JSONObject();
        String id = Ids.newId();
        o.put("id", id);
        o.put("title", title.trim());
        o.put("body", body.trim());
        o.put("role", role);
        o.put("orderId", "");
        o.put("createdAt", serverTime());
        s.put(BROADCASTS, id, o);
    }

    public List<Message> messages(String orderId) throws Exception {
        List<Message> out = new ArrayList<Message>();
        for (JSONObject o : s.query(MSGS, "orderId", orderId)) out.add(Message.fromJson(o));
        Collections.sort(out, new Comparator<Message>() {
            @Override
            public int compare(Message a, Message b) {
                return Long.compare(a.createdAt, b.createdAt);
            }
        });
        return out;
    }
}
