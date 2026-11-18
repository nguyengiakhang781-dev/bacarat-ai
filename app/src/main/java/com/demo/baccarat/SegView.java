package com.demo.baccarat;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Thanh chia ô cho thấy bao nhiêu phương án đồng thuận (ví dụ 6/9). */
final class SegView extends View {
    private final float d;
    private final Paint off = new Paint(Paint.ANTI_ALIAS_FLAG), on = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private int same, total, c1 = 0xFFFF4D6D, c2 = 0xFFFF9A8B;
    private float t = 1f;
    private ValueAnimator anim;

    SegView(Context c) {
        super(c);
        d = Theme.d(c);
        off.setColor(0x24FFFFFF);
    }

    void set(int same, int total, int color) {
        this.same = same; this.total = Math.max(1, total); c1 = color;
        if (anim != null) anim.cancel();
        t = 0f;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(400 + 40L * same);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) { t = (Float) a.getAnimatedValue(); invalidate(); }
        });
        anim.start();
    }

    @Override protected void onMeasure(int ws, int hs) {
        int w = View.MeasureSpec.getSize(ws);
        setMeasuredDimension(w, (int) (6f * d));
    }

    @Override protected void onDetachedFromWindow() { if (anim != null) anim.cancel(); super.onDetachedFromWindow(); }

    @Override protected void onDraw(Canvas c) {
        float gap = 3f * d, w = (getWidth() - gap * (total - 1)) / total, h = getHeight(), dur = 400f + 40f * same;
        for (int i = 0; i < total; i++) {
            float x = i * (w + gap);
            r.set(x, 0, x + w, h);
            c.drawRoundRect(r, h / 2f, h / 2f, off);
            if (i < same) {
                float k = Math.max(0f, Math.min(1f, (t * dur - i * 40f) / 400f));
                if (k > 0f) { on.setColor(c1); on.setAlpha((int) (255 * k)); c.drawRoundRect(r, h / 2f, h / 2f, on); }
            }
        }
    }
}
