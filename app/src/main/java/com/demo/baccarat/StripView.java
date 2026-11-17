package com.demo.baccarat;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import java.util.ArrayList;
import java.util.List;

/** Chuỗi chấm xanh/đỏ (đoán đúng/sai) của các ván gần nhất; chấm mới nảy lên; 🔥 khi đúng liên tiếp. */
final class StripView extends View {
    private final float d;
    private final Paint ok = new Paint(Paint.ANTI_ALIAS_FLAG), no = new Paint(Paint.ANTI_ALIAS_FLAG), tx = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Boolean> hist = new ArrayList<>();
    private int streak;
    private float pop = 1f;
    private ValueAnimator anim;

    StripView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        ok.setColor(0xFF3ECF72); no.setColor(0xFFFF4D5E);
        tx.setColor(0xFFFFD34D); tx.setTextSize(11f * d); tx.setTypeface(Typeface.DEFAULT_BOLD);
    }

    void setData(List<Boolean> h, int streak) {
        boolean grew = h.size() > hist.size();
        hist.clear(); hist.addAll(h);
        this.streak = streak;
        if (grew) {
            if (anim != null) anim.cancel();
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(500);
            anim.setInterpolator(new OvershootInterpolator(2.4f));
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator a) { pop = (Float) a.getAnimatedValue(); invalidate(); }
            });
            anim.start();
        } else { pop = 1f; invalidate(); }
        setVisibility(hist.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override protected void onMeasure(int ws, int hs) { setMeasuredDimension(View.MeasureSpec.getSize(ws), (int) (14f * d)); }

    @Override protected void onDetachedFromWindow() { if (anim != null) anim.cancel(); super.onDetachedFromWindow(); }

    @Override protected void onDraw(Canvas c) {
        float r = 5f * d, step = 15f * d, cy = getHeight() / 2f, x = r;
        for (int i = 0; i < hist.size(); i++) {
            float k = i == hist.size() - 1 ? Math.max(0f, pop) : 1f;
            c.drawCircle(x, cy, r * k, hist.get(i) ? ok : no);
            x += step;
        }
        if (streak >= 3) c.drawText("🔥 " + streak, x, cy + 4f * d, tx);
    }
}
