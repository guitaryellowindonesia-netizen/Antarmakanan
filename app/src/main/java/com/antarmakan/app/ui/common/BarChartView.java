package com.antarmakan.app.ui.common;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import com.antarmakan.app.util.Ui;

/** Grafik batang sederhana (tanpa library). */
public class BarChartView extends View {

    private String[] labels = new String[0];
    private long[] values = new long[0];
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);

    public BarChartView(Context c) {
        super(c);
        bar.setColor(Ui.PRIMARY);
        text.setColor(Ui.MUTED);
        text.setTextSize(Ui.dp(c, 10));
        text.setTextAlign(Paint.Align.CENTER);
        grid.setColor(Ui.LINE);
        grid.setStrokeWidth(Math.max(1, Ui.dp(c, 1)));
    }

    public void setData(String[] labels, long[] values) {
        this.labels = labels;
        this.values = values;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        int n = values.length;
        if (n == 0) return;
        long max = 1;
        for (long v : values) max = Math.max(max, v);
        float w = getWidth(), h = getHeight();
        float bottom = h - Ui.dp(getContext(), 18);
        float top = Ui.dp(getContext(), 14);
        c.drawLine(0, bottom, w, bottom, grid);
        float slot = w / n;
        float bw = Math.max(2, slot * 0.6f);
        int labelEvery = n > 14 ? 5 : n > 7 ? 2 : 1;
        for (int i = 0; i < n; i++) {
            float x = slot * i + slot / 2;
            float bh = (bottom - top) * values[i] / max;
            c.drawRect(x - bw / 2, bottom - bh, x + bw / 2, bottom, bar);
            if (i % labelEvery == 0 || i == n - 1) c.drawText(labels[i], x, h - Ui.dp(getContext(), 4), text);
        }
        text.setTextAlign(Paint.Align.LEFT);
        c.drawText("maks " + com.antarmakan.app.util.Fmt.rp(max).replace("Rp", ""), 0, top - 2, text);
        text.setTextAlign(Paint.Align.CENTER);
    }
}
