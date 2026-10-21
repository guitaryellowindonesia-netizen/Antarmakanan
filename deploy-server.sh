#!/bin/bash
# =====================================================================
#  Pasang server AntarMakan (Cloud Functions + Rules) ke Firebase.
#  Jalankan di Google Cloud Shell (https://shell.cloud.google.com):
#
#    git clone https://github.com/guitaryellowindonesia-netizen/Antarmakanan.git
#    cd Antarmakanan && bash deploy-server.sh
# =====================================================================
set -e
PROJECT=antarmakan-dharmasraya
FB="npx -y firebase-tools@13"

echo "== 1/4 Login Firebase (ikuti link yang muncul, salin kodenya ke sini) =="
$FB login --no-localhost

echo "== 2/4 Menyiapkan kode server =="
npm install --prefix functions --no-audit --no-fund

if ! $FB functions:secrets:access MIDTRANS_SERVER_KEY --project $PROJECT >/dev/null 2>&1; then
  echo "== 3/4 Masukkan SERVER KEY Midtrans (Sandbox: SB-Mid-server-...) =="
  echo "   (tidak akan tampil di layar; ketik/tempel lalu Enter)"
  $FB functions:secrets:set MIDTRANS_SERVER_KEY --project $PROJECT
else
  echo "== 3/4 Server Key Midtrans sudah tersimpan (lewati). Untuk mengganti:"
  echo "   $FB functions:secrets:set MIDTRANS_SERVER_KEY --project $PROJECT"
fi

echo "== 4/4 Memasang server & Rules =="
$FB deploy --only functions,database --project $PROJECT --force

echo ""
echo "SELESAI. Isi URL notifikasi ini di Midtrans Dashboard"
echo "(Settings > Configuration > Payment Notification URL):"
echo "  https://asia-southeast1-$PROJECT.cloudfunctions.net/midtransWebhook"
