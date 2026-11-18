package com.demo.baccarat;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Pháo giấy khi đoán đúng. Vẽ phủ lên toàn bộ trợ lý, không nhận chạm. */
final class BurstView extends View {
    private static final int N = 28;
    private final float d;
    private final float[] ang = new float[N], dist = new float[N], size = new float[N], rotv = new float[N], delay = new float[N];
    private final int[] col = new int[N];
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float cx, cy, t = -1f;
    private ValueAnimator anim;

    BurstView(Context c) {
        super(c);
        d = Theme.d(c);
        setClickable(false);
    }

    void fire(float centerX, float centerY, int[] palette) {
        cx = centerX; cy = centerY;
        java.util.Random r = new java.util.Random();
        for (int i = 0; i < N; i++) {
            ang[i] = (float) (r.nextFloat() * Math.PI * 2);
            dist[i] = (34f + r.nextFloat() * 44f) * d;
            size[i] = (6f + r.nextFloat() * 7f) * d;
            rotv[i] = r.nextFloat() * 540f - 270f;
            delay[i] = r.nextFloat() * 90f;
            col[i] = palette[i % palette.length];
        }
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(1000);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) { t = (Float) a.getAnimatedValue(); invalidate(); }
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) { t = -1f; invalidate(); }
        });
        anim.start();
    }

    @Override protected void onDetachedFromWindow() { if (anim != null) anim.cancel(); super.onDetachedFromWindow(); }

    @Override protected void onDraw(Canvas c) {
        if (t < 0f) return;
        for (int i = 0; i < N; i++) {
            float tt = Math.max(0f, Math.min(1f, (t * 1000f - delay[i]) / (1000f - delay[i])));
            float e = 1f - (1f - tt) * (1f - tt) * (1f - tt);
            float x = cx + (float) Math.cos(ang[i]) * dist[i] * e, y = cy + (float) Math.sin(ang[i]) * dist[i] * e - 12f * d * e;
            p.setColor(col[i]);
            p.setAlpha((int) (255 * (1f - tt)));
            c.save();
            c.translate(x, y);
            c.rotate(rotv[i] * e);
            if (i % 3 == 0) c.drawCircle(0, 0, size[i] / 2f, p);
            else c.drawRect(-size[i] / 2f, -size[i] / 2f, size[i] / 2f, size[i] / 2f, p);
            c.restore();
        }
    }
}
