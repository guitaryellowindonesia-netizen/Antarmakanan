package com.antarmakan.app.ui.common;

import android.app.Dialog;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.antarmakan.app.model.Message;
import com.antarmakan.app.model.User;
import com.antarmakan.app.util.Async;
import com.antarmakan.app.util.Fmt;
import com.antarmakan.app.util.Images;
import com.antarmakan.app.util.Session;
import com.antarmakan.app.util.Ui;

import java.util.List;

/** Chat per pesanan antara pelanggan, resto, dan driver. Bisa kirim foto & pesan cepat. */
public class ChatActivity extends BaseActivity {

    private static final int REQ_GALLERY = 801;
    private static final int REQ_CAMERA = 802;

    /** Pesan cepat untuk pelanggan */
    private static final String[] QUICK_CUSTOMER = {
            "Saya sudah di rumah 🏠",
            "Tolong telepon kalau sudah dekat 📞",
            "Saya tunggu di depan",
            "Mohon tunggu sebentar 🙏",
            "Rumah saya ada di foto ini 👇",
            "Terima kasih! 😊"
    };

    /** Pesan cepat untuk pengantar / resto */
    private static final String[] QUICK_COURIER = {
            "Saya sudah di resto, menunggu pesanan",
            "Pesanan sedang dalam perjalanan 🛵",
            "Saya sudah di depan 📍",
            "Mohon tunggu sebentar 🙏",
            "Bisa kirim foto patokan rumah?",
            "Terima kasih! 😊"
    };

    private String orderId;
    private LinearLayout list;
    private ScrollView scroll;
    private EditText input;
    private User me;
    private int lastCount = -1;
    private Uri cameraUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        orderId = getIntent().getStringExtra("orderId");
        if (savedInstanceState != null) cameraUri = savedInstanceState.getParcelable("cameraUri");
        enableBack(getIntent().getStringExtra("title"));

        LinearLayout root = Ui.vbox(this);
        root.setBackgroundColor(Ui.BG);
        scroll = new ScrollView(this);
        list = Ui.vbox(this);
        list.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // pesan cepat
        String[] quick = User.ROLE_CUSTOMER.equals(Session.role(this)) ? QUICK_CUSTOMER : QUICK_COURIER;
        LinearLayout chips = Ui.hbox(this);
        for (final String q : quick) {
            TextView chip = Ui.chip(this, q, false);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sendText(q);
                }
            });
            chips.addView(chip);
        }
        root.addView(Ui.hscroll(this, chips), Ui.matchWrap());

        LinearLayout bar = Ui.hbox(this);
        bar.setBackgroundColor(Color.WHITE);
        bar.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4));
        TextView photo = Ui.smallButton(this, "📷", Ui.PRIMARY, false);
        photo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                choosePhoto();
            }
        });
        bar.addView(photo);
        input = Ui.edit(this, "Tulis pesan...");
        bar.addView(input, Ui.weight(1));
        TextView send = Ui.smallButton(this, "Kirim", Ui.PRIMARY, true);
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = input.getText().toString().trim();
                if (text.length() == 0) return;
                input.setText("");
                sendText(text);
            }
        });
        bar.addView(send);
        root.addView(bar, Ui.matchWrap());
        setContentView(root);
        setPolling(4000);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (cameraUri != null) out.putParcelable("cameraUri", cameraUri);
    }

    @Override
    protected void refresh() {
        load(new Async.Task<Object[]>() {
            @Override
            public Object[] run() throws Exception {
                return new Object[]{repo.me(), repo.messages(orderId)};
            }
        }, new Async.Callback<Object[]>() {
            @Override
            @SuppressWarnings("unchecked")
            public void onResult(Object[] r) {
                me = (User) r[0];
                render((List<Message>) r[1]);
            }
        });
    }

    private void render(List<Message> msgs) {
        if (msgs.size() == lastCount) return;
        lastCount = msgs.size();
        list.removeAllViews();
        if (msgs.isEmpty()) {
            list.addView(Ui.empty(this, "Belum ada pesan.\nChat ini bisa dibaca pelanggan, resto, dan pengantar."));
        }
        for (final Message m : msgs) {
            boolean mine = m.senderId.equals(me.id);
            LinearLayout bubble = Ui.vbox(this);
            bubble.setBackground(Ui.rounded(mine ? Ui.PRIMARY_LIGHT : Color.WHITE, Ui.dp(this, 12)));
            bubble.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
            if (!mine) {
                bubble.addView(Ui.text(this, m.senderName + " (" + User.roleLabel(m.senderRole) + ")",
                        11, Ui.PRIMARY_DARK, true));
            }
            if (m.imageId.length() > 0) {
                FrameLayout pic = Ui.banner(this, m.imageId, "📷", 200);
                pic.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 220), Ui.dp(this, 200)));
                pic.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showFull(m.imageId);
                    }
                });
                bubble.addView(pic);
            }
            if (m.text.length() > 0) bubble.addView(Ui.body(this, m.text));
            TextView t = Ui.text(this, Fmt.time(m.createdAt), 10, Ui.MUTED, false);
            t.setGravity(Gravity.END);
            bubble.addView(t, Ui.matchWrap());
            LinearLayout.LayoutParams lp = Ui.wrap();
            lp.gravity = mine ? Gravity.END : Gravity.START;
            lp.setMargins(mine ? Ui.dp(this, 48) : 0, Ui.dp(this, 4), mine ? 0 : Ui.dp(this, 48), Ui.dp(this, 4));
            list.addView(bubble, lp);
        }
        scroll.post(new Runnable() {
            @Override
            public void run() {
                scroll.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    /** Lihat foto layar penuh. */
    private void showFull(String imageId) {
        Dialog d = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setBackgroundColor(Color.BLACK);
        d.setContentView(iv);
        if (d.getWindow() != null) d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));
        final Dialog dialog = d;
        iv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        Images.load(iv, imageId);
        d.show();
    }

    private void sendText(final String text) {
        if (me == null) return;
        load(new Async.Task<Boolean>() {
            @Override
            public Boolean run() throws Exception {
                repo.sendMessage(orderId, me, text);
                return true;
            }
        }, new Async.Callback<Boolean>() {
            @Override
            public void onResult(Boolean r) {
                refresh();
            }
        });
    }

    // ------------------------------------------------------------------ foto

    private void choosePhoto() {
        Ui.choose(this, "Kirim foto", new String[]{"📸 Ambil dari kamera", "🖼️ Pilih dari galeri"},
                new Ui.ChoiceCallback() {
                    @Override
                    public void onChoice(int index) {
                        if (index == 0) openCamera();
                        else openGallery();
                    }
                });
    }

    private void openGallery() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        try {
            startActivityForResult(Intent.createChooser(i, "Pilih foto"), REQ_GALLERY);
        } catch (Exception e) {
            Ui.toast(this, "Tidak ada aplikasi galeri");
        }
    }

    private void openCamera() {
        Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraUri = null;
        if (Build.VERSION.SDK_INT >= 29) {
            // simpan foto penuh sementara di galeri (tanpa izin penyimpanan)
            try {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Images.Media.DISPLAY_NAME, "antarmakan_" + System.currentTimeMillis() + ".jpg");
                v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                cameraUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                if (cameraUri != null) i.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
            } catch (Exception ignored) {
                cameraUri = null;
            }
        }
        try {
            startActivityForResult(i, REQ_CAMERA);
        } catch (Exception e) {
            Ui.toast(this, "Kamera tidak tersedia");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_GALLERY && req != REQ_CAMERA) return;
        if (res != RESULT_OK) {
            deleteTempPhoto();
            return;
        }
        final Uri uri = req == REQ_GALLERY ? (data == null ? null : data.getData()) : cameraUri;
        final Bitmap thumb = (req == REQ_CAMERA && uri == null && data != null && data.getExtras() != null)
                ? (Bitmap) data.getExtras().get("data") : null;
        if (uri == null && thumb == null) return;
        action("Mengirim foto...", new Async.Task<Boolean>() {
            @Override
            public Boolean run() throws Exception {
                String b64 = uri != null ? Images.encode(ChatActivity.this, uri) : Images.encode(thumb);
                String imageId = repo.uploadImage(b64);
                repo.sendMessage(orderId, me != null ? me : repo.me(), "", imageId);
                return true;
            }
        }, new Async.Callback<Boolean>() {
            @Override
            public void onResult(Boolean r) {
                deleteTempPhoto();
                refresh();
            }
        });
    }

    /** Hapus salinan foto kamera dari galeri setelah terkirim. */
    private void deleteTempPhoto() {
        if (cameraUri != null) {
            try {
                getContentResolver().delete(cameraUri, null, null);
            } catch (Exception ignored) {
            }
            cameraUri = null;
        }
    }
}
