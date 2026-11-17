package com.demo.baccarat;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/** Nút lớn "CÁI" (đỏ) / "CON" (xanh) với vệt sáng lướt qua định kỳ. */
final class ChipView extends View {
    private final float d;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), txt = new Paint(Paint.ANTI_ALIAS_FLAG), shinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path clip = new Path(), band = new Path();
    private String text = "CÁI";
    private char side = 'B';
    private boolean weak;
    private float shine = -1f;
    private ValueAnimator shineAnim;
    private final Runnable loop = new Runnable() {
        @Override public void run() {
            if (getVisibility() != VISIBLE || !isAttachedToWindow()) return;
            shineAnim = ValueAnimator.ofFloat(0f, 1f);
            shineAnim.setDuration(900);
            shineAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator a) { shine = -0.4f + 1.8f * (Float) a.getAnimatedValue(); invalidate(); }
            });
            shineAnim.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator a) { shine = -1f; invalidate(); }
            });
            shineAnim.start();
            postDelayed(this, 4200);
        }
    };

    ChipView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        txt.setTextAlign(Paint.Align.CENTER); txt.setTextSize(26f * d); txt.setTypeface(Typeface.DEFAULT_BOLD); txt.setColor(0xFFFFFFFF);
        txt.setLetterSpacing(0.03f);
    }

    void set(char v, boolean weak) {
        side = v; this.weak = weak; text = v == 'B' ? "CÁI" : "CON";
        rebuild();
        invalidate();
        setScaleX(0.6f); setScaleY(0.6f); setAlpha(0f);
        animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(450).setInterpolator(new OvershootInterpolator(2f)).start();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { rebuild(); }

    private void rebuild() {
        int w = getWidth(), h = getHeight();
        if (w <= 0) return;
        rect.set(0, 0, w, h);
        clip.reset(); clip.addRoundRect(rect, 17f * d, 17f * d, Path.Direction.CW);
        int c1 = side == 'B' ? 0xFFFF8294 : 0xFF86B8FF, c2 = side == 'B' ? 0xFFE11D48 : 0xFF2563EB;
        fill.setShader(new LinearGradient(0, 0, w, h, c1, c2, Shader.TileMode.CLAMP));
        fill.setAlpha(weak ? 215 : 255);
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); removeCallbacks(loop); postDelayed(loop, 700); }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(loop);
        if (shineAnim != null) shineAnim.cancel();
        super.onDetachedFromWindow();
    }

    @Override protected void onVisibilityChanged(View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility);
        removeCallbacks(loop);
        if (visibility == VISIBLE && isAttachedToWindow()) postDelayed(loop, 700);
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        c.drawRoundRect(rect, 17f * d, 17f * d, fill);
        c.drawText(text, w / 2f, h / 2f + 9.2f * d, txt);
        if (shine >= 0f) {
            c.save();
            c.clipPath(clip);
            float bw = w * 0.38f, x = w * shine - bw / 2f, skew = h * 0.36f;
            band.reset();
            band.moveTo(x + skew, 0); band.lineTo(x + skew + bw, 0); band.lineTo(x + bw, h); band.lineTo(x, h); band.close();
            shinePaint.setShader(new LinearGradient(x, 0, x + bw, 0, new int[]{0x00FFFFFF, 0x80FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
            c.drawPath(band, shinePaint);
            c.restore();
        }
    }
}
