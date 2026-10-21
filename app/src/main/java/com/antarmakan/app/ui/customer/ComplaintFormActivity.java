package com.antarmakan.app.ui.customer;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;

import com.antarmakan.app.model.Complaint;
import com.antarmakan.app.model.Order;
import com.antarmakan.app.ui.merchant.BaseFormActivity;
import com.antarmakan.app.util.Async;
import com.antarmakan.app.util.Ui;

/** Pelanggan melaporkan masalah pesanan (bisa dengan foto bukti). */
public class ComplaintFormActivity extends BaseFormActivity {

    private Order order;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String orderId = getIntent().getStringExtra("orderId");
        enableBack("Laporkan Masalah");
        final LinearLayout page = scrollPage();
        page.addView(Ui.empty(this, "Memuat..."));
        load(new Async.Task<Order>() {
            @Override
            public Order run() throws Exception {
                return repo.order(orderId);
            }
        }, new Async.Callback<Order>() {
            @Override
            public void onResult(Order o) {
                order = o;
                build(page);
            }
        });
    }

    private void build(LinearLayout page) {
        page.removeAllViews();
        LinearLayout c = formCard(page);
        c.addView(Ui.title(this, "Pesanan " + order.code));
        c.addView(Ui.muted(this, order.restoName));
        c.addView(Ui.label(this, "Masalahnya apa?"));
        final Spinner cat = Ui.spinner(this, Complaint.CATEGORIES, 0);
        c.addView(cat);
        final EditText text = field(c, "Ceritakan detailnya", "",
                Ui.multiline(this, "Contoh: es teh tidak ada di pesanan"));
        text.setMinLines(4);
        photoSection(c, "Foto bukti (opsional)", "", "📷");
        Button send = Ui.primary(this, "Kirim Laporan");
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String category = (String) cat.getSelectedItem();
                final String t = text.getText().toString();
                action("Mengirim laporan...", new Async.Task<Boolean>() {
                    @Override
                    public Boolean run() throws Exception {
                        String imageId = applyPhoto("");
                        repo.createComplaint(order, category, t, imageId);
                        return true;
                    }
                }, new Async.Callback<Boolean>() {
                    @Override
                    public void onResult(Boolean r) {
                        Ui.toast(ComplaintFormActivity.this, "Laporan terkirim. Admin akan menindaklanjuti.");
                        finish();
                    }
                });
            }
        });
        c.addView(send);
    }
}
