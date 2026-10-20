package com.antarmakan.app.util;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.antarmakan.app.data.AppException;

/**
 * Pembuat tampilan (UI) lewat kode Java, agar proyek tidak bergantung
 * pada banyak file layout XML dan mudah dikompilasi di CodeAssist.
 */
public final class Ui {
    private Ui() {}

    // ---- warna tema ----
    public static final int PRIMARY = Color.parseColor("#00AA13");
    public static final int PRIMARY_DARK = Color.parseColor("#00880F");
    public static final int PRIMARY_LIGHT = Color.parseColor("#E6F7E8");
    public static final int ACCENT = Color.parseColor("#FF6D00");
    public static final int BG = Color.parseColor("#F3F4F6");
    public static final int CARD = Color.WHITE;
    public static final int TEXT = Color.parseColor("#1F2937");
    public static final int MUTED = Color.parseColor("#6B7280");
    public static final int LINE = Color.parseColor("#E5E7EB");
    public static final int RED = Color.parseColor("#E53935");
    public static final int BLUE = Color.parseColor("#1E88E5");
    public static final int YELLOW = Color.parseColor("#F9A825");

    public static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics());
    }

    // ---- pesan ----

    public static void toast(Context c, String msg) {
        Toast.makeText(c, msg, Toast.LENGTH_SHORT).show();
    }

    public static String errorMessage(Exception e) {
        if (e instanceof AppException) return e.getMessage();
        if (e instanceof java.io.IOException) return "Gagal terhubung. Periksa koneksi internet";
        String m = e.getMessage();
        return "Terjadi kesalahan" + (m == null ? "" : ": " + m);
    }

    public static void confirm(Context c, String title, String msg, String ok, final Runnable onOk) {
        new AlertDialog.Builder(c)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton(ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        onOk.run();
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    public interface TextCallback {
        void onText(String text);
    }

    public static void prompt(Context c, String title, String hint, String initial, int inputType,
                              final TextCallback cb) {
        final EditText et = edit(c, hint, inputType);
        if (initial != null) et.setText(initial);
        LinearLayout box = vbox(c);
        box.setPadding(dp(c, 20), dp(c, 8), dp(c, 20), 0);
        box.addView(et);
        new AlertDialog.Builder(c)
                .setTitle(title)
                .setView(box)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        cb.onText(et.getText().toString().trim());
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    public interface ChoiceCallback {
        void onChoice(int index);
    }

    public static void choose(Context c, String title, String[] items, final ChoiceCallback cb) {
        new AlertDialog.Builder(c)
                .setTitle(title)
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        cb.onChoice(which);
                    }
                })
                .show();
    }

    // ---- intent luar ----

    public static void call(Context c, String phone) {
        try {
            c.startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
        } catch (Exception e) {
            toast(c, "Tidak bisa membuka telepon");
        }
    }

    public static void whatsapp(Context c, String phone, String text) {
        try {
            String url = "https://wa.me/" + Fmt.waNumber(phone) + "?text=" + Uri.encode(text);
            c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            toast(c, "WhatsApp tidak tersedia");
        }
    }

    /** Buka Google Maps ke alamat (ditambah nama kecamatan & kabupaten). */
    public static void maps(Context c, String address, String kecamatan) {
        try {
            String q = address + ", " + kecamatan + ", " + com.antarmakan.app.Config.NAMA_KABUPATEN;
            c.startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(q))));
        } catch (Exception e) {
            toast(c, "Tidak bisa membuka peta");
        }
    }

    // ---- latar ----

    public static GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    public static GradientDrawable outline(int fill, int stroke, int strokePx, float radiusPx) {
        GradientDrawable g = rounded(fill, radiusPx);
        g.setStroke(strokePx, stroke);
        return g;
    }

    // ---- layout ----

    public static LinearLayout vbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout hbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
    }

    public static LinearLayout.LayoutParams margins(Context c, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = matchWrap();
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }

    /** Kartu putih bersudut bulat */
    public static LinearLayout card(Context c) {
        LinearLayout l = vbox(c);
        l.setBackground(rounded(CARD, dp(c, 12)));
        int p = dp(c, 14);
        l.setPadding(p, p, p, p);
        l.setLayoutParams(margins(c, 12, 6, 12, 6));
        if (android.os.Build.VERSION.SDK_INT >= 21) l.setElevation(dp(c, 1));
        return l;
    }

    public static View spacer(Context c, int heightDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, heightDp)));
        return v;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 1)));
        p.setMargins(0, dp(c, 8), 0, dp(c, 8));
        v.setLayoutParams(p);
        return v;
    }

    // ---- teks ----

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    public static TextView title(Context c, String s) {
        return text(c, s, 17, TEXT, true);
    }

    public static TextView body(Context c, String s) {
        return text(c, s, 14, TEXT, false);
    }

    public static TextView muted(Context c, String s) {
        return text(c, s, 13, MUTED, false);
    }

    public static TextView section(Context c, String s) {
        TextView t = text(c, s, 16, TEXT, true);
        t.setLayoutParams(margins(c, 16, 14, 16, 4));
        return t;
    }

    /** Label kecil berwarna (status, dll) */
    public static TextView badge(Context c, String s, int color) {
        TextView t = text(c, s, 11, color, true);
        t.setBackground(outline(Color.WHITE, color, dp(c, 1), dp(c, 10)));
        t.setPadding(dp(c, 8), dp(c, 2), dp(c, 8), dp(c, 2));
        return t;
    }

    /** Lingkaran berisi emoji sebagai "foto" */
    public static TextView emojiIcon(Context c, String emoji, int sizeDp) {
        TextView t = new TextView(c);
        t.setText(emoji);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeDp * 0.45f);
        t.setBackground(rounded(PRIMARY_LIGHT, dp(c, 12)));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp));
        p.setMargins(0, 0, dp(c, 12), 0);
        t.setLayoutParams(p);
        return t;
    }

    public static TextView keyValue(Context c, LinearLayout parent, String key, String value,
                                    boolean bold) {
        LinearLayout row = hbox(c);
        row.setPadding(0, dp(c, 3), 0, dp(c, 3));
        TextView k = text(c, key, 14, bold ? TEXT : MUTED, bold);
        row.addView(k, weight(1));
        TextView v = text(c, value, 14, TEXT, bold);
        row.addView(v, wrap());
        parent.addView(row, matchWrap());
        return v;
    }

    // ---- tombol & input ----

    public static Button button(Context c, String label, int color) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(rounded(color, dp(c, 10)));
        b.setPadding(dp(c, 14), dp(c, 10), dp(c, 14), dp(c, 10));
        if (android.os.Build.VERSION.SDK_INT >= 21) b.setStateListAnimator(null);
        LinearLayout.LayoutParams p = matchWrap();
        p.setMargins(0, dp(c, 6), 0, dp(c, 6));
        b.setLayoutParams(p);
        return b;
    }

    public static Button primary(Context c, String label) {
        return button(c, label, PRIMARY);
    }

    public static Button outlineButton(Context c, String label, int color) {
        Button b = button(c, label, color);
        b.setTextColor(color);
        b.setBackground(outline(Color.WHITE, color, dp(c, 1), dp(c, 10)));
        return b;
    }

    /** Tombol kecil (untuk aksi dalam kartu) */
    public static TextView smallButton(Context c, String label, int color, boolean filled) {
        TextView t = text(c, label, 13, filled ? Color.WHITE : color, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(filled ? rounded(color, dp(c, 8)) : outline(Color.WHITE, color, dp(c, 1), dp(c, 8)));
        t.setPadding(dp(c, 12), dp(c, 6), dp(c, 12), dp(c, 6));
        LinearLayout.LayoutParams p = wrap();
        p.setMargins(dp(c, 4), dp(c, 4), dp(c, 4), dp(c, 4));
        t.setLayoutParams(p);
        t.setClickable(true);
        return t;
    }

    public static TextView chip(Context c, String label, boolean selected) {
        TextView t = text(c, label, 13, selected ? Color.WHITE : TEXT, selected);
        t.setBackground(selected ? rounded(PRIMARY, dp(c, 16))
                : outline(Color.WHITE, LINE, dp(c, 1), dp(c, 16)));
        t.setPadding(dp(c, 14), dp(c, 7), dp(c, 14), dp(c, 7));
        LinearLayout.LayoutParams p = wrap();
        p.setMargins(dp(c, 4), 0, dp(c, 4), 0);
        t.setLayoutParams(p);
        return t;
    }

    public static HorizontalScrollView hscroll(Context c, View child) {
        HorizontalScrollView h = new HorizontalScrollView(c);
        h.setHorizontalScrollBarEnabled(false);
        h.setPadding(dp(c, 8), dp(c, 6), dp(c, 8), dp(c, 6));
        h.setClipToPadding(false);
        h.addView(child);
        return h;
    }

    public static EditText edit(Context c, String hint, int inputType) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setInputType(inputType);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        e.setTextColor(TEXT);
        e.setHintTextColor(Color.parseColor("#9CA3AF"));
        e.setBackground(outline(Color.WHITE, LINE, dp(c, 1), dp(c, 10)));
        e.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        LinearLayout.LayoutParams p = matchWrap();
        p.setMargins(0, dp(c, 4), 0, dp(c, 8));
        e.setLayoutParams(p);
        return e;
    }

    public static EditText edit(Context c, String hint) {
        return edit(c, hint, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    }

    public static EditText multiline(Context c, String hint) {
        EditText e = edit(c, hint, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setMinLines(2);
        e.setGravity(Gravity.TOP | Gravity.START);
        return e;
    }

    public static EditText number(Context c, String hint) {
        return edit(c, hint, InputType.TYPE_CLASS_NUMBER);
    }

    public static EditText phone(Context c, String hint) {
        return edit(c, hint, InputType.TYPE_CLASS_PHONE);
    }

    public static EditText password(Context c, String hint) {
        return edit(c, hint, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    }

    public static TextView label(Context c, String s) {
        TextView t = text(c, s, 13, MUTED, true);
        t.setPadding(0, dp(c, 4), 0, 0);
        return t;
    }

    public static Spinner spinner(Context c, String[] items, int selected) {
        Spinner sp = new Spinner(c);
        ArrayAdapter<String> ad = new ArrayAdapter<String>(c,
                android.R.layout.simple_spinner_item, items);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(ad);
        if (selected >= 0 && selected < items.length) sp.setSelection(selected);
        sp.setBackground(outline(Color.WHITE, LINE, dp(c, 1), dp(c, 10)));
        sp.setPadding(dp(c, 4), dp(c, 4), dp(c, 4), dp(c, 4));
        LinearLayout.LayoutParams p = matchWrap();
        p.setMargins(0, dp(c, 4), 0, dp(c, 8));
        sp.setLayoutParams(p);
        return sp;
    }

    public static int indexOf(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(v)) return i;
        return 0;
    }

    /** Tampilan kosong / info di tengah */
    public static TextView empty(Context c, String s) {
        TextView t = text(c, s, 14, MUTED, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 24), dp(c, 40), dp(c, 24), dp(c, 40));
        t.setLayoutParams(matchWrap());
        return t;
    }

    public static int statusColor(int status) {
        switch (status) {
            case 0: return ACCENT;
            case 1: return YELLOW;
            case 2: return BLUE;
            case 3: return BLUE;
            case 4: return PRIMARY;
            case 5: return PRIMARY_DARK;
            default: return RED;
        }
    }
}
