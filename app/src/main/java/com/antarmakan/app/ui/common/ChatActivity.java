package com.antarmakan.app.ui.common;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.antarmakan.app.model.Message;
import com.antarmakan.app.model.User;
import com.antarmakan.app.util.Async;
import com.antarmakan.app.util.Fmt;
import com.antarmakan.app.util.Ui;

import java.util.List;

/** Chat per pesanan antara pelanggan, resto, dan driver. */
public class ChatActivity extends BaseActivity {

    private String orderId;
    private LinearLayout list;
    private ScrollView scroll;
    private EditText input;
    private User me;
    private int lastCount = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        orderId = getIntent().getStringExtra("orderId");
        enableBack(getIntent().getStringExtra("title"));

        LinearLayout root = Ui.vbox(this);
        root.setBackgroundColor(Ui.BG);
        scroll = new ScrollView(this);
        list = Ui.vbox(this);
        list.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout bar = Ui.hbox(this);
        bar.setBackgroundColor(android.graphics.Color.WHITE);
        bar.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4));
        input = Ui.edit(this, "Tulis pesan...");
        bar.addView(input, Ui.weight(1));
        TextView send = Ui.smallButton(this, "Kirim", Ui.PRIMARY, true);
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                send();
            }
        });
        bar.addView(send);
        root.addView(bar, Ui.matchWrap());
        setContentView(root);
        setPolling(4000);
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
            list.addView(Ui.empty(this, "Belum ada pesan.\nChat ini bisa dibaca pelanggan, resto, dan driver."));
        }
        for (Message m : msgs) {
            boolean mine = m.senderId.equals(me.id);
            LinearLayout bubble = Ui.vbox(this);
            bubble.setBackground(Ui.rounded(mine ? Ui.PRIMARY_LIGHT : android.graphics.Color.WHITE,
                    Ui.dp(this, 12)));
            bubble.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
            if (!mine) {
                bubble.addView(Ui.text(this, m.senderName + " (" + User.roleLabel(m.senderRole) + ")",
                        11, Ui.PRIMARY_DARK, true));
            }
            bubble.addView(Ui.body(this, m.text));
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

    private void send() {
        final String text = input.getText().toString().trim();
        if (text.length() == 0 || me == null) return;
        input.setText("");
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
}
