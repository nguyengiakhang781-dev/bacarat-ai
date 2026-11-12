package com.demo.baccarat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Vẽ lại một bàn theo kiểu bảng cầu: 6 hàng, điền theo cột từ trái sang phải. */
final class BeadView extends View {
    private final String seq;
    private final int cell;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    BeadView(Context c, String seq, int cell) {
        super(c);
        this.seq = seq;
        this.cell = cell;
    }

    private int cols() { return Math.max(1, (seq.length() + 5) / 6); }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(cols() * cell, 6 * cell);
    }

    @Override
    protected void onDraw(Canvas cv) {
        cv.drawColor(0xFFFFFFFF);
        int cols = cols();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f);
        p.setColor(0xFFE2E2E2);
        for (int c = 0; c <= cols; c++) cv.drawLine(c * cell, 0, c * cell, 6 * cell, p);
        for (int r = 0; r <= 6; r++) cv.drawLine(0, r * cell, cols * cell, r * cell, p);
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(cell * 0.5f);
        p.setFakeBoldText(true);
        for (int i = 0; i < seq.length(); i++) {
            char ch = seq.charAt(i);
            float cx = (i / 6) * cell + cell / 2f, cy = (i % 6) * cell + cell / 2f;
            int color = ch == 'B' ? 0xFFC0141E : ch == 'P' ? 0xFF2672B8 : ch == 'T' ? 0xFF4CAF50 : 0xFF9E9E9E;
            p.setColor(color);
            cv.drawCircle(cx, cy, cell * 0.42f, p);
            p.setColor(0xFFFFFFFF);
            cv.drawText(String.valueOf(ch == 'X' ? '?' : ch), cx, cy + cell * 0.18f, p);
        }
    }
}
