package com.antarmakan.app.data;

import android.content.Context;

import com.antarmakan.app.Config;
import com.antarmakan.app.model.CartItem;
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
    public static final String WALLET = "wallet";
    public static final String TOPUPS = "topups";

    private final Context ctx;
    private final Store s;
    private final FirebaseAuthRest auth;

    Repo(Context ctx, Store store, FirebaseAuthRest auth) {
        this.ctx = ctx;
        this.s = store;
        this.auth = auth;
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

    public User register(String name, String phone, String password, String role,
                         String kecamatan, String address, String vehicle,
                         Restaurant restoDraft) throws Exception {
        if (password.length() < 6) throw new AppException("Kata sandi minimal 6 karakter");
        validateProfile(name, phone);
        phone = normalizePhone(phone);
        String uid = auth.signUp(phone, password);
        return createProfile(uid, name, phone, role, kecamatan, address, vehicle, restoDraft, "", "");
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
        String uid = auth.signIn(phone, password);
        JSONObject o = s.get(USERS, uid);
        if (o == null) throw new AppException("Data akun tidak ditemukan");
        User u = User.fromJson(o);
        if (!u.active && !u.isDriver()) throw new AppException("Akun Anda dinonaktifkan admin");
        Session.login(ctx, u.id, u.role);
        return u;
    }

    public void logout() {
        Session.logout(ctx);
    }

    /** Data pengguna yang sedang login (selalu terbaru dari server). */
    public User me() throws Exception {
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
            }
        });
    }

    public void setUserActive(String uid, final boolean active) throws Exception {
        editUser(uid, new UserEdit() {
            @Override
            public void edit(JSONObject o) throws Exception {
                o.put("active", active);
                if (!active) o.put("online", false);
            }
        });
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

    /** Tambah (amount > 0) atau kurangi (amount < 0) saldo + catat riwayat. */
    public long adjustBalance(String uid, final long amount, String desc,
                              final boolean allowNegative) throws Exception {
        JSONObject o = editUser(uid, new UserEdit() {
            @Override
            public void edit(JSONObject o) throws Exception {
                long bal = o.optLong("balance") + amount;
                if (bal < 0 && amount < 0 && !allowNegative) {
                    throw new AppException("Saldo tidak cukup");
                }
                o.put("balance", bal);
            }
        });
        WalletTx t = new WalletTx();
        t.id = Ids.newId();
        t.userId = uid;
        t.amount = amount;
        t.description = desc;
        t.createdAt = now();
        s.put(WALLET, t.id, t.toJson());
        return o.optLong("balance");
    }

    public List<WalletTx> walletHistory(String uid) throws Exception {
        List<WalletTx> out = new ArrayList<WalletTx>();
        for (JSONObject o : s.query(WALLET, "userId", uid)) out.add(WalletTx.fromJson(o));
        Collections.sort(out, new Comparator<WalletTx>() {
            @Override
            public int compare(WalletTx a, WalletTx b) {
                return Long.compare(b.createdAt, a.createdAt);
            }
        });
        return out;
    }

    public void requestTopUp(User u, long amount, String info) throws Exception {
        if (amount < Config.MIN_TOPUP) {
            throw new AppException("Minimal top up " + com.antarmakan.app.util.Fmt.rp(Config.MIN_TOPUP));
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
        if (amount < Config.MIN_TOPUP) {
            throw new AppException("Minimal penarikan " + com.antarmakan.app.util.Fmt.rp(Config.MIN_TOPUP));
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
        adjustBalance(u.id, -amount, "Penarikan saldo (diproses)", false);
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
        if (TopUp.TYPE_TOPUP.equals(t.type)) {
            if (approve) adjustBalance(t.userId, t.amount, "Top up saldo", true);
        } else if (!approve) {
            adjustBalance(t.userId, t.amount, "Penarikan ditolak, saldo dikembalikan", true);
        }
    }

    // =====================================================================
    //  RESTO
    // =====================================================================

    public List<Restaurant> restaurants(String userKec, boolean includeInactive) throws Exception {
        List<Restaurant> out = new ArrayList<Restaurant>();
        for (JSONObject o : s.list(RESTOS)) {
            Restaurant r = Restaurant.fromJson(o);
            if (!r.active && !includeInactive) continue;
            r.distanceKm = Geo.distanceKm(userKec, r.kecamatan);
            r.deliveryFee = Geo.deliveryFee(r.distanceKm);
            out.add(r);
        }
        Collections.sort(out, new Comparator<Restaurant>() {
            @Override
            public int compare(Restaurant a, Restaurant b) {
                boolean oa = a.isOpenNow(), ob = b.isOpenNow();
                if (oa != ob) return oa ? -1 : 1;
                return Double.compare(a.distanceKm, b.distanceKm);
            }
        });
        return out;
    }

    public Restaurant resto(String id, String userKec) throws Exception {
        JSONObject o = s.get(RESTOS, id);
        if (o == null) throw new AppException("Resto tidak ditemukan");
        Restaurant r = Restaurant.fromJson(o);
        r.distanceKm = Geo.distanceKm(userKec, r.kecamatan);
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
        s.transact(RESTOS, restoId, new Store.Updater() {
            @Override
            public JSONObject update(JSONObject cur) throws Exception {
                if (cur == null) throw new AppException("Resto tidak ditemukan");
                cur.put("active", active);
                return cur;
            }
        });
    }

    public void deleteResto(String restoId) throws Exception {
        for (MenuItem m : menus(restoId)) s.delete(MENUS, m.id);
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

    public Quote quote(Restaurant r, long subtotal, String kecamatan, String promoCode) throws Exception {
        Quote q = new Quote();
        q.subtotal = subtotal;
        q.distanceKm = Geo.distanceKm(kecamatan, r.kecamatan);
        q.deliveryFee = Geo.deliveryFee(q.distanceKm);
        q.serviceFee = Config.BIAYA_LAYANAN;
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
                            String note, String payment, String promoCode) throws Exception {
        if (cart.isEmpty()) throw new AppException("Keranjang kosong");
        if (address.trim().length() < 5) throw new AppException("Alamat pengiriman wajib diisi lengkap");
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
            OrderItem it = new OrderItem();
            it.menuId = m.id;
            it.name = m.name;
            it.price = m.price;
            it.qty = ci.qty;
            it.note = ci.note;
            o.items.add(it);
            subtotal += m.price * ci.qty;
        }

        Quote q = quote(r, subtotal, kecamatan, promoCode);
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
        o.status = Order.MENUNGGU;
        o.subtotal = q.subtotal;
        o.deliveryFee = q.deliveryFee;
        o.serviceFee = q.serviceFee;
        o.discount = q.discount;
        o.total = q.total;
        o.distanceKm = q.distanceKm;
        o.paymentMethod = payment;
        o.promoCode = q.promo == null ? "" : q.promo.code;
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

        if (Order.BAYAR_SALDO.equals(payment)) {
            adjustBalance(u.id, -o.total, "Bayar pesanan " + o.code, false);
        }
        try {
            s.put(ORDERS, o.id, o.toJson());
        } catch (Exception e) {
            if (Order.BAYAR_SALDO.equals(payment)) {
                adjustBalance(u.id, o.total, "Pengembalian " + o.code, true);
            }
            throw e;
        }
        cart.clear();
        return o;
    }

    public Order order(String id) throws Exception {
        JSONObject o = s.get(ORDERS, id);
        if (o == null) throw new AppException("Pesanan tidak ditemukan");
        return Order.fromJson(o);
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
        return toOrders(s.query(ORDERS, "customerId", uid));
    }

    public List<Order> ordersOfResto(String restoId) throws Exception {
        return toOrders(s.query(ORDERS, "restoId", restoId));
    }

    public List<Order> ordersOfDriver(String driverId) throws Exception {
        return toOrders(s.query(ORDERS, "driverId", driverId));
    }

    /** Pesanan yang bisa diambil driver: sudah diterima resto & belum ada driver. */
    public List<Order> availableJobs() throws Exception {
        List<Order> out = new ArrayList<Order>();
        for (Order o : toOrders(s.query(ORDERS, "driverId", ""))) {
            if (o.status == Order.DIMASAK || o.status == Order.SIAP) out.add(o);
        }
        return out;
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
                o.updatedAt = now();
                return o.toJson();
            }
        });
        return Order.fromJson(res);
    }

    // ---- aksi resto ----

    public Order restoAccept(String orderId) throws Exception {
        return transition(orderId, new Transition() {
            @Override
            public void apply(Order o) {
                if (o.status != Order.MENUNGGU) throw new AppException("Pesanan sudah diproses");
                o.status = Order.DIMASAK;
            }
        });
    }

    public Order restoReady(String orderId) throws Exception {
        return transition(orderId, new Transition() {
            @Override
            public void apply(Order o) {
                if (o.status != Order.DIMASAK) throw new AppException("Status pesanan sudah berubah");
                o.status = o.hasDriver() ? Order.DIJEMPUT : Order.SIAP;
            }
        });
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
        return o;
    }

    /** Kembalikan saldo & kuota promo saat pesanan batal. */
    private void afterCancel(Order o) throws Exception {
        if (Order.BAYAR_SALDO.equals(o.paymentMethod)) {
            adjustBalance(o.customerId, o.total, "Pengembalian dana " + o.code, true);
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
        return transition(orderId, new Transition() {
            @Override
            public void apply(Order o) {
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
    }

    public Order driverPickedUp(String orderId, final String driverId) throws Exception {
        return transition(orderId, new Transition() {
            @Override
            public void apply(Order o) {
                if (!driverId.equals(o.driverId)) throw new AppException("Bukan pesanan Anda");
                if (o.status == Order.DIMASAK) throw new AppException("Makanan belum siap dari resto");
                if (o.status != Order.DIJEMPUT) throw new AppException("Status pesanan sudah berubah");
                o.status = Order.DIANTAR;
            }
        });
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
        return o;
    }

    /**
     * Pembagian uang saat pesanan selesai.
     * SALDO : pelanggan sudah bayar ke aplikasi -> resto & driver menerima saldo.
     * TUNAI : driver menerima uang tunai dari pelanggan & membayar resto tunai,
     *         lalu komisi aplikasi dipotong dari saldo resto & driver.
     */
    private void settle(Order o) throws Exception {
        long komisiResto = o.subtotal * Config.KOMISI_PERSEN_RESTO / 100;
        long komisiDriver = o.deliveryFee * Config.KOMISI_PERSEN_DRIVER / 100;
        if (Order.BAYAR_SALDO.equals(o.paymentMethod)) {
            if (o.restoOwnerId.length() > 0) {
                adjustBalance(o.restoOwnerId, o.subtotal - komisiResto,
                        "Penjualan " + o.code + " (potong komisi " + Config.KOMISI_PERSEN_RESTO + "%)", true);
            }
            adjustBalance(o.driverId, o.deliveryFee - komisiDriver, "Ongkir " + o.code, true);
        } else {
            if (o.restoOwnerId.length() > 0) {
                adjustBalance(o.restoOwnerId, -komisiResto, "Komisi aplikasi " + o.code, true);
            }
            long driverAdj = o.discount - komisiDriver - o.serviceFee;
            adjustBalance(o.driverId, driverAdj, "Komisi & biaya layanan " + o.code + " (tunai)", true);
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
    //  CHAT
    // =====================================================================

    public void sendMessage(String orderId, User from, String text) throws Exception {
        if (text.trim().length() == 0) return;
        Message m = new Message();
        m.id = Ids.newId();
        m.orderId = orderId;
        m.senderId = from.id;
        m.senderName = from.name;
        m.senderRole = from.role;
        m.text = text.trim();
        m.createdAt = now();
        s.put(MSGS, m.id, m.toJson());
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
