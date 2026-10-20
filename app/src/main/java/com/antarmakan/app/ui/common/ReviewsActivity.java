package com.antarmakan.app.ui.common;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.antarmakan.app.model.Review;
import com.antarmakan.app.model.User;
import com.antarmakan.app.util.Async;
import com.antarmakan.app.util.Fmt;
import com.antarmakan.app.util.Ui;

import java.util.List;

/** Daftar ulasan resto. Mitra pemilik resto bisa membalas. */
public class ReviewsActivity extends BaseActivity {

    private String restoId;
    private LinearLayout page;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        restoId = getIntent().getStringExtra("restoId");
        enableBack("Ulasan " + getIntent().getStringExtra("name"));
        page = scrollPage();
    }

    @Override
    protected void refresh() {
        load(new Async.Task<Object[]>() {
            @Override
            public Object[] run() throws Exception {
                User me = repo.me();
                boolean owner = me.isMerchant() && repo.restoOfOwner(me.id) != null
                        && repo.restoOfOwner(me.id).id.equals(restoId);
                return new Object[]{repo.reviews(restoId), owner};
            }
        }, new Async.Callback<Object[]>() {
            @Override
            @SuppressWarnings("unchecked")
            public void onResult(Object[] r) {
                render((List<Review>) r[0], (Boolean) r[1]);
            }
        });
    }

    private static String stars(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) sb.append(i < n ? "★" : "☆");
        return sb.toString();
    }

    private void render(List<Review> reviews, boolean owner) {
        page.removeAllViews();
        if (reviews.isEmpty()) {
            page.addView(Ui.empty(this, "Belum ada ulasan"));
            return;
        }
        double sum = 0;
        for (Review r : reviews) sum += r.rating;
        LinearLayout head = Ui.card(this);
        head.addView(Ui.text(this, "⭐ " + Fmt.rating(sum / reviews.size()), 26, Ui.TEXT, true));
        head.addView(Ui.muted(this, "dari " + reviews.size() + " ulasan di aplikasi"));
        page.addView(head);

        for (final Review r : reviews) {
            LinearLayout c = Ui.card(this);
            LinearLayout top = Ui.hbox(this);
            top.addView(Ui.text(this, r.customerName, 14, Ui.TEXT, true), Ui.weight(1));
            top.addView(Ui.text(this, stars(r.rating), 14, Ui.YELLOW, false));
            c.addView(top);
            c.addView(Ui.muted(this, Fmt.date(r.createdAt)));
            if (r.comment.length() > 0) c.addView(Ui.body(this, r.comment));
            if (r.reply.length() > 0) {
                TextView rep = Ui.text(this, "Balasan resto: " + r.reply, 13, Ui.PRIMARY_DARK, false);
                rep.setBackground(Ui.rounded(Ui.PRIMARY_LIGHT, Ui.dp(this, 8)));
                rep.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
                c.addView(rep, Ui.margins(this, 0, 6, 0, 0));
            }
            if (owner) {
                TextView b = Ui.smallButton(this, r.reply.length() > 0 ? "Ubah balasan" : "Balas", Ui.PRIMARY, false);
                b.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Ui.prompt(ReviewsActivity.this, "Balas ulasan", "Terima kasih...", r.reply,
                                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
                                new Ui.TextCallback() {
                                    @Override
                                    public void onText(final String text) {
                                        action("Menyimpan...", new Async.Task<Boolean>() {
                                            @Override
                                            public Boolean run() throws Exception {
                                                repo.replyReview(r.id, text);
                                                return true;
                                            }
                                        }, new Async.Callback<Boolean>() {
                                            @Override
                                            public void onResult(Boolean ok) {
                                                refresh();
                                            }
                                        });
                                    }
                                });
                    }
                });
                c.addView(b);
            }
            page.addView(c);
        }
    }
}
