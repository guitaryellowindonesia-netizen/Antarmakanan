/**
 * Server AntarMakan (Firebase Cloud Functions)
 * --------------------------------------------
 * Pembayaran QRIS otomatis lewat Midtrans:
 *  - createQrisPayment : membuat QR dengan nominal pas untuk sebuah pesanan
 *  - checkQrisPayment  : cek status ke Midtrans (cadangan bila notifikasi terlambat)
 *  - midtransWebhook   : menerima notifikasi pembayaran dari Midtrans
 *
 * Server Key Midtrans disimpan sebagai SECRET (tidak pernah ada di aplikasi / GitHub):
 *   firebase functions:secrets:set MIDTRANS_SERVER_KEY
 * Mode produksi diatur lewat file functions/.env :  MIDTRANS_PRODUCTION=true
 */
const crypto = require("crypto");
const {onCall, onRequest, HttpsError} = require("firebase-functions/v2/https");
const {defineSecret, defineString} = require("firebase-functions/params");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");

admin.initializeApp();

const SERVER_KEY = defineSecret("MIDTRANS_SERVER_KEY");
const IS_PRODUCTION = defineString("MIDTRANS_PRODUCTION", {default: "false"});
const REGION = "asia-southeast1";
const QR_MINUTES = 15;

const db = () => admin.database();

function apiBase() {
  return IS_PRODUCTION.value() === "true" ? "https://api.midtrans.com" : "https://api.sandbox.midtrans.com";
}

/** Panggil API Midtrans (Basic auth dengan Server Key). */
async function midtrans(method, path, body) {
  const res = await fetch(apiBase() + path, {
    method,
    headers: {
      "Authorization": "Basic " + Buffer.from(SERVER_KEY.value() + ":").toString("base64"),
      "Content-Type": "application/json",
      "Accept": "application/json",
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  return res.json();
}

/** Id pesanan dari order_id Midtrans ("ORDERID-2" -> "ORDERID"). */
function orderIdOf(mtOrderId) {
  return String(mtOrderId || "").replace(/-\d+$/, "");
}

function isPaidStatus(st) {
  return st.transaction_status === "settlement" ||
    (st.transaction_status === "capture" && st.fraud_status === "accept");
}

async function notify(uid, title, body, orderId) {
  if (!uid) return;
  const ref = db().ref("notifications/" + uid).push();
  await ref.set({id: ref.key, title, body, orderId: orderId || "", createdAt: admin.database.ServerValue.TIMESTAMP});
}

/**
 * Tandai pesanan lunas (sekali saja, aman dipanggil berulang).
 * Dana QRIS dicatat di buku besar pelanggan: +total (qris_) lalu -total (pay_),
 * sehingga pesanan diperlakukan sama seperti bayar saldo (bagi hasil otomatis).
 * Jika pesanan sudah batal sebelum pembayaran terkonfirmasi, dana masuk ke saldo pelanggan.
 */
async function markPaid(orderId, mtOrderId, grossAmount) {
  const oRef = db().ref("orders/" + orderId);
  const o = (await oRef.get()).val();
  if (!o) {
    logger.warn("Pesanan tidak ditemukan", {orderId});
    return false;
  }
  if (Math.round(Number(grossAmount)) !== Math.round(Number(o.total))) {
    logger.error("Nominal tidak cocok", {orderId, grossAmount, total: o.total});
    return false;
  }
  // kunci: hanya satu pemanggilan yang berhasil menandai lunas
  const tx = await oRef.child("paid").transaction((p) => (p === true ? undefined : true));
  if (!tx.committed) return true; // sudah lunas sebelumnya

  const uid = o.customerId;
  const now = Date.now();
  const cancelled = o.status === 9;
  const updates = {};
  updates[`wallet/${uid}/qris_${orderId}`] = {
    id: `qris_${orderId}`, userId: uid, amount: o.total, orderId, createdAt: now,
    description: "Pembayaran QRIS " + o.code,
  };
  if (!cancelled) {
    updates[`wallet/${uid}/pay_${orderId}`] = {
      id: `pay_${orderId}`, userId: uid, amount: -o.total, orderId, createdAt: now,
      description: "Bayar pesanan " + o.code,
    };
  }
  updates[`orders/${orderId}/paidAt`] = now;
  updates[`payments/${orderId}/status`] = "paid";
  updates[`payments/${orderId}/paidMtOrderId`] = mtOrderId;
  await db().ref().update(updates);

  if (cancelled) {
    // pesanan sudah batal: dana jadi saldo pelanggan
    await db().ref(`users/${uid}/balance`).transaction((b) => (b || 0) + o.total);
    await notify(uid, "💰 Dana QRIS masuk ke saldo", `Pesanan ${o.code} sudah batal, ${o.total} masuk ke saldo Anda.`, orderId);
  } else {
    await notify(uid, "✅ Pembayaran QRIS diterima", `Pesanan ${o.code} diteruskan ke ${o.restoName}.`, orderId);
    await notify(o.restoOwnerId, "🔔 Pesanan baru " + o.code,
        `${o.customerName} • lunas QRIS • ${o.subtotal}`, orderId);
  }
  logger.info("Pesanan lunas QRIS", {orderId, mtOrderId});
  return true;
}

/** Buat QRIS untuk pesanan milik pengguna yang login. */
exports.createQrisPayment = onCall({region: REGION, secrets: [SERVER_KEY]}, async (req) => {
  if (!req.auth) throw new HttpsError("unauthenticated", "Silakan masuk dulu");
  const orderId = String((req.data || {}).orderId || "");
  const o = (await db().ref("orders/" + orderId).get()).val();
  if (!o) throw new HttpsError("not-found", "Pesanan tidak ditemukan");
  if (o.customerId !== req.auth.uid) throw new HttpsError("permission-denied", "Bukan pesanan Anda");
  if (o.paymentMethod !== "QRIS") throw new HttpsError("failed-precondition", "Pesanan ini bukan QRIS");
  if (o.paid === true) return {paid: true};
  if (o.status !== 0) throw new HttpsError("failed-precondition", "Pesanan sudah dibatalkan");

  const payRef = db().ref("payments/" + orderId);
  const existing = (await payRef.get()).val();
  if (existing && existing.status === "pending" && existing.expiresAt > Date.now() + 60000) {
    return {qrString: existing.qrString, expiresAt: existing.expiresAt, amount: existing.amount};
  }
  const attempt = ((existing && existing.attempt) || 0) + 1;
  const mtOrderId = `${orderId}-${attempt}`;
  const r = await midtrans("POST", "/v2/charge", {
    payment_type: "qris",
    transaction_details: {order_id: mtOrderId, gross_amount: Math.round(o.total)},
    qris: {acquirer: "gopay"},
    custom_expiry: {expiry_duration: QR_MINUTES, unit: "minute"},
    customer_details: {first_name: o.customerName || "Pelanggan", phone: o.customerPhone || ""},
  });
  if (!r.qr_string) {
    logger.error("Gagal membuat QRIS", r);
    throw new HttpsError("internal", "Gagal membuat QRIS: " + (r.status_message || "coba lagi"));
  }
  const expiresAt = Date.now() + QR_MINUTES * 60000;
  await payRef.set({
    orderId, mtOrderId, attempt, qrString: r.qr_string, amount: o.total,
    status: "pending", expiresAt, createdAt: Date.now(), customerId: o.customerId,
  });
  return {qrString: r.qr_string, expiresAt, amount: o.total};
});

/** Cek status pembayaran langsung ke Midtrans (tombol "Saya sudah bayar"). */
exports.checkQrisPayment = onCall({region: REGION, secrets: [SERVER_KEY]}, async (req) => {
  if (!req.auth) throw new HttpsError("unauthenticated", "Silakan masuk dulu");
  const orderId = String((req.data || {}).orderId || "");
  const o = (await db().ref("orders/" + orderId).get()).val();
  if (!o || o.customerId !== req.auth.uid) throw new HttpsError("permission-denied", "Bukan pesanan Anda");
  if (o.paid === true) return {paid: true};
  const p = (await db().ref("payments/" + orderId).get()).val();
  if (!p) return {paid: false};
  const st = await midtrans("GET", `/v2/${encodeURIComponent(p.mtOrderId)}/status`);
  if (isPaidStatus(st)) {
    await markPaid(orderId, st.order_id, st.gross_amount);
    return {paid: true};
  }
  return {paid: false, status: st.transaction_status || "pending"};
});

/**
 * Notifikasi dari Midtrans. Isi URL ini di Midtrans Dashboard:
 * Settings > Configuration > Payment Notification URL
 */
exports.midtransWebhook = onRequest({region: REGION, secrets: [SERVER_KEY]}, async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).send("POST only");
    return;
  }
  const n = req.body || {};
  const expected = crypto.createHash("sha512")
      .update(String(n.order_id) + String(n.status_code) + String(n.gross_amount) + SERVER_KEY.value())
      .digest("hex");
  if (!n.signature_key || expected !== n.signature_key) {
    logger.warn("Tanda tangan tidak valid", {order_id: n.order_id});
    res.status(403).send("invalid signature");
    return;
  }
  try {
    // konfirmasi ulang langsung ke Midtrans (jangan percaya isi notifikasi begitu saja)
    const st = await midtrans("GET", `/v2/${encodeURIComponent(n.order_id)}/status`);
    const orderId = orderIdOf(st.order_id);
    if (isPaidStatus(st)) {
      await markPaid(orderId, st.order_id, st.gross_amount);
    } else if (["expire", "cancel", "deny"].includes(st.transaction_status)) {
      const pRef = db().ref("payments/" + orderId);
      const p = (await pRef.get()).val();
      if (p && p.mtOrderId === st.order_id && p.status === "pending") {
        await pRef.update({status: st.transaction_status});
      }
    }
    res.status(200).send("OK");
  } catch (e) {
    logger.error("Webhook gagal", e);
    res.status(500).send("error");
  }
});

// untuk pengujian
exports._markPaid = markPaid;
