package com.antarmakan.app.ui.common;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.antarmakan.app.model.Complaint;
import com.antarmakan.app.model.User;
import com.antarmakan.app.util.Async;
import com.antarmakan.app.util.Fmt;
import com.antarmakan.app.util.Ui;

import java.util.List;

/** Daftar komplain: pelanggan melihat miliknya, admin menindaklanjuti semua. */
public class ComplaintsActivity extends BaseActivity {

    private LinearLayout page;
    private User me;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enableBack("Komplain");
        page = scrollPage();
        page.addView(Ui.empty(this, "Memuat..."));
    }

    @Override
    protected void refresh() {
        load(new Async.Task<Object[]>() {
            @Override
            public Object[] run() throws Exception {
                User u = repo.me();
                return new Object[]{u, repo.complaints(u.isAdmin() ? null : u.id)};
            }
        }, new Async.Callback<Object[]>() {
            @Override
            @SuppressWarnings("unchecked")
            public void onResult(Object[] r) {
                me = (User) r[0];
                render((List<Complaint>) r[1]);
            }
        });
    }

    private void render(List<Complaint> list) {
        page.removeAllViews();
        if (list.isEmpty()) {
            page.addView(Ui.empty(this, "Belum ada komplain"));
            return;
        }
        for (final Complaint c : list) {
            LinearLayout card = Ui.card(this);
            LinearLayout top = Ui.hbox(this);
            top.addView(Ui.text(this, c.category, 15, Ui.TEXT, true), Ui.weight(1));
            int color = Complaint.RESOLVED.equals(c.status) ? Ui.PRIMARY
                    : Complaint.REJECTED.equals(c.status) ? Ui.RED : Ui.ACCENT;
            top.addView(Ui.badge(this, c.statusLabel(), color));
            card.addView(top);
            card.addView(Ui.muted(this, c.orderCode + " • " + c.restoName + " • " + Fmt.date(c.createdAt)));
            if (me.isAdmin()) card.addView(Ui.muted(this, "Pelanggan: " + c.customerName + " (" + c.customerPhone + ")"));
            card.addView(Ui.body(this, c.text));
            if (c.imageId.length() > 0) {
                FrameLayout pic = Ui.banner(this, c.imageId, "📷", 160);
                card.addView(pic);
            }
            if (!Complaint.OPEN.equals(c.status)) {
                String res = (c.refund > 0 ? "Dana dikembalikan " + Fmt.rp(c.refund) + ". " : "") + c.adminNote;
                if (res.trim().length() > 0) {
                    TextView t = Ui.text(this, "Admin: " + res, 13, Ui.PRIMARY_DARK, false);
                    t.setBackground(Ui.rounded(Ui.PRIMARY_LIGHT, Ui.dp(this, 8)));
                    t.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
                    card.addView(t, Ui.margins(this, 0, 6, 0, 0));
                }
            }
            LinearLayout actions = Ui.hbox(this);
            TextView open = Ui.smallButton(this, "Lihat pesanan", Ui.BLUE, false);
            open.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Views.openOrder(ComplaintsActivity.this, c.orderId);
                }
            });
            actions.addView(open);
            if (me.isAdmin() && Complaint.OPEN.equals(c.status)) {
                TextView wa = Ui.smallButton(this, "WhatsApp", Ui.PRIMARY, false);
                wa.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Ui.whatsapp(ComplaintsActivity.this, c.customerPhone,
                                "Halo " + c.customerName + ", terkait komplain pesanan " + c.orderCode + ". ");
                    }
                });
                TextView ok = Ui.smallButton(this, "✅ Tindak lanjut", Ui.PRIMARY, true);
                ok.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        decide(c, true);
                    }
                });
                TextView no = Ui.smallButton(this, "Tolak", Ui.RED, false);
                no.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        decide(c, false);
                    }
                });
                actions.addView(wa);
                actions.addView(ok);
                actions.addView(no);
            }
            card.addView(Ui.hscroll(this, actions));
            page.addView(card);
        }
    }

    private void decide(final Complaint c, final boolean accept) {
        LinearLayout box = Ui.vbox(this);
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        final EditText refund = Ui.number(this, "0");
        if (accept) {
            box.addView(Ui.label(this, "Kembalikan dana ke saldo pelanggan (maks " + Fmt.rp(c.orderTotal) + ")"));
            box.addView(refund);
        }
        box.addView(Ui.label(this, "Catatan untuk pelanggan"));
        final EditText note = Ui.multiline(this, accept ? "Mohon maaf, dana dikembalikan" : "Alasan ditolak");
        box.addView(note);
        new AlertDialog.Builder(this)
                .setTitle(accept ? "Selesaikan komplain" : "Tolak komplain")
                .setView(box)
                .setPositiveButton("Simpan", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        final long amount = accept ? Fmt.parseLong(refund.getText().toString()) : 0;
                        final String n = note.getText().toString();
                        action("Memproses...", new Async.Task<Boolean>() {
                            @Override
                            public Boolean run() throws Exception {
                                repo.resolveComplaint(c.id, accept, amount, n);
                                return true;
                            }
                        }, new Async.Callback<Boolean>() {
                            @Override
                            public void onResult(Boolean r) {
                                Ui.toast(ComplaintsActivity.this, "Komplain diproses");
                                refresh();
                            }
                        });
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }
}
