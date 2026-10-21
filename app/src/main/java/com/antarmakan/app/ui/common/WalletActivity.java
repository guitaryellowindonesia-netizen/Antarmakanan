package com.antarmakan.app.ui.common;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.antarmakan.app.Config;
import com.antarmakan.app.model.TopUp;
import com.antarmakan.app.model.User;
import com.antarmakan.app.model.WalletTx;
import com.antarmakan.app.util.Async;
import com.antarmakan.app.util.Fmt;
import com.antarmakan.app.util.Ui;

import java.util.List;

/** Saldo: top up, tarik saldo (mitra/driver), riwayat. */
public class WalletActivity extends BaseActivity {

    private LinearLayout page;
    private User me;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enableBack("Saldo " + Config.NAMA_APLIKASI);
        page = scrollPage();
        page.addView(Ui.empty(this, "Memuat..."));
    }

    @Override
    protected void refresh() {
        load(new Async.Task<Object[]>() {
            @Override
            public Object[] run() throws Exception {
                User u = repo.me();
                return new Object[]{u, repo.topUps(u.id), repo.walletHistory(u.id)};
            }
        }, new Async.Callback<Object[]>() {
            @Override
            @SuppressWarnings("unchecked")
            public void onResult(Object[] r) {
                me = (User) r[0];
                render((List<TopUp>) r[1], (List<WalletTx>) r[2]);
            }
        });
    }

    private void render(List<TopUp> requests, List<WalletTx> history) {
        page.removeAllViews();
        LinearLayout bal = Ui.card(this);
        bal.addView(Ui.muted(this, "Saldo kamu"));
        bal.addView(Ui.text(this, Fmt.rp(me.balance), 28, me.balance < 0 ? Ui.RED : Ui.PRIMARY_DARK, true));
        LinearLayout btns = Ui.hbox(this);
        TextView top = Ui.smallButton(this, "➕ Top Up", Ui.PRIMARY, true);
        top.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                topUpDialog();
            }
        });
        btns.addView(top);
        if (me.isMerchant() || me.isDriver()) {
            TextView wd = Ui.smallButton(this, "💸 Tarik Saldo", Ui.PRIMARY, false);
            wd.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    withdrawDialog();
                }
            });
            btns.addView(wd);
        }
        bal.addView(btns);
        page.addView(bal);

        if (!requests.isEmpty()) {
            page.addView(Ui.section(this, "Permintaan saldo"));
            LinearLayout c = Ui.card(this);
            for (TopUp t : requests) {
                LinearLayout row = Ui.hbox(this);
                row.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
                LinearLayout info = Ui.vbox(this);
                info.addView(Ui.body(this, (TopUp.TYPE_TOPUP.equals(t.type) ? "Top up " : "Tarik ") + Fmt.rp(t.amount)));
                info.addView(Ui.muted(this, Fmt.date(t.createdAt)));
                row.addView(info, Ui.weight(1));
                int color = t.status == TopUp.APPROVED ? Ui.PRIMARY : t.status == TopUp.REJECTED ? Ui.RED : Ui.ACCENT;
                row.addView(Ui.badge(this, t.statusLabel(), color));
                c.addView(row, Ui.matchWrap());
            }
            page.addView(c);
        }

        page.addView(Ui.section(this, "Riwayat mutasi"));
        LinearLayout h = Ui.card(this);
        if (history.isEmpty()) h.addView(Ui.muted(this, "Belum ada transaksi"));
        for (WalletTx t : history) {
            LinearLayout row = Ui.hbox(this);
            row.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
            LinearLayout info = Ui.vbox(this);
            info.addView(Ui.body(this, t.description));
            info.addView(Ui.muted(this, Fmt.date(t.createdAt)));
            row.addView(info, Ui.weight(1));
            row.addView(Ui.text(this, (t.amount >= 0 ? "+" : "") + Fmt.rp(t.amount), 14,
                    t.amount >= 0 ? Ui.PRIMARY_DARK : Ui.RED, true));
            h.addView(row, Ui.matchWrap());
        }
        page.addView(h);
    }

    private void topUpDialog() {
        LinearLayout box = Ui.vbox(this);
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        box.addView(Ui.body(this, com.antarmakan.app.util.AppSettings.INFO_TRANSFER));
        box.addView(Ui.spacer(this, 8));
        box.addView(Ui.label(this, "Nominal (min " + Fmt.rp(com.antarmakan.app.util.AppSettings.MIN_TOPUP) + ")"));
        final EditText amount = Ui.number(this, "50000");
        box.addView(amount);
        box.addView(Ui.label(this, "Nama pengirim / bank"));
        final EditText info = Ui.edit(this, "Contoh: Budi - BRI");
        box.addView(info);
        new AlertDialog.Builder(this)
                .setTitle("Top Up Saldo")
                .setView(box)
                .setPositiveButton("Sudah Transfer", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        final long a = Fmt.parseLong(amount.getText().toString());
                        final String i = info.getText().toString();
                        action("Mengirim...", new Async.Task<Boolean>() {
                            @Override
                            public Boolean run() throws Exception {
                                repo.requestTopUp(me, a, i);
                                return true;
                            }
                        }, new Async.Callback<Boolean>() {
                            @Override
                            public void onResult(Boolean r) {
                                Ui.toast(WalletActivity.this, "Permintaan top up dikirim, menunggu admin");
                                refresh();
                            }
                        });
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void withdrawDialog() {
        LinearLayout box = Ui.vbox(this);
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        box.addView(Ui.label(this, "Nominal (saldo " + Fmt.rp(me.balance) + ")"));
        final EditText amount = Ui.number(this, "50000");
        box.addView(amount);
        box.addView(Ui.label(this, "Rekening / e-wallet tujuan"));
        final EditText info = Ui.edit(this, "Contoh: BRI 1234567 a.n. Sari");
        box.addView(info);
        new AlertDialog.Builder(this)
                .setTitle("Tarik Saldo")
                .setView(box)
                .setPositiveButton("Ajukan", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        final long a = Fmt.parseLong(amount.getText().toString());
                        final String i = info.getText().toString();
                        action("Mengirim...", new Async.Task<Boolean>() {
                            @Override
                            public Boolean run() throws Exception {
                                repo.requestWithdraw(me, a, i);
                                return true;
                            }
                        }, new Async.Callback<Boolean>() {
                            @Override
                            public void onResult(Boolean r) {
                                Ui.toast(WalletActivity.this, "Penarikan diajukan, admin akan mentransfer");
                                refresh();
                            }
                        });
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }
}
