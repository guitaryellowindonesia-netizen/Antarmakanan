package com.antarmakan.app.ui.admin;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import com.antarmakan.app.model.MenuItem;
import com.antarmakan.app.ui.common.BaseActivity;
import com.antarmakan.app.ui.merchant.MenuEditActivity;
import com.antarmakan.app.util.Async;
import com.antarmakan.app.util.Fmt;
import com.antarmakan.app.util.Ui;

import java.util.List;

/** Admin mengelola menu sebuah resto. */
public class AdminMenuActivity extends BaseActivity {

    private String restoId;
    private LinearLayout page;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        restoId = getIntent().getStringExtra("restoId");
        enableBack("Menu " + getIntent().getStringExtra("name"));
        page = scrollPage();
    }

    @Override
    protected void refresh() {
        load(new Async.Task<List<MenuItem>>() {
            @Override
            public List<MenuItem> run() throws Exception {
                return repo.menus(restoId);
            }
        }, new Async.Callback<List<MenuItem>>() {
            @Override
            public void onResult(List<MenuItem> list) {
                page.removeAllViews();
                LinearLayout box = Ui.vbox(AdminMenuActivity.this);
                box.setPadding(Ui.dp(AdminMenuActivity.this, 12), 0, Ui.dp(AdminMenuActivity.this, 12), 0);
                Button add = Ui.primary(AdminMenuActivity.this, "+ Tambah Menu");
                add.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        open(null);
                    }
                });
                box.addView(add);
                page.addView(box);
                for (final MenuItem m : list) {
                    LinearLayout c = Ui.card(AdminMenuActivity.this);
                    LinearLayout row = Ui.hbox(AdminMenuActivity.this);
                    row.addView(Ui.emojiIcon(AdminMenuActivity.this, m.emoji, 44));
                    LinearLayout info = Ui.vbox(AdminMenuActivity.this);
                    info.addView(Ui.text(AdminMenuActivity.this, m.name, 15, Ui.TEXT, true));
                    info.addView(Ui.muted(AdminMenuActivity.this, m.category + " • " + Fmt.rp(m.price)
                            + (m.available ? "" : " • HABIS")));
                    row.addView(info, Ui.weight(1));
                    c.addView(row);
                    c.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            open(m.id);
                        }
                    });
                    page.addView(c);
                }
                if (list.isEmpty()) page.addView(Ui.empty(AdminMenuActivity.this, "Belum ada menu"));
            }
        });
    }

    private void open(String menuId) {
        Intent i = new Intent(this, MenuEditActivity.class);
        i.putExtra("restoId", restoId);
        if (menuId != null) i.putExtra("menuId", menuId);
        startActivity(i);
    }
}
