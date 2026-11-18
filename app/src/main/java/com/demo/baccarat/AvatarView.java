package com.demo.baccarat;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

/** Nhân vật: bồng bềnh, vòng quét/tiến trình, huy hiệu cảm xúc, nhảy/lắc khi đúng/sai. */
final class AvatarView extends View {
    private final float d;
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), prog = new Paint(Paint.ANTI_ALIAS_FLAG), spinP = new Paint(Paint.ANTI_ALIAS_FLAG),
            bodyP = new Paint(Paint.ANTI_ALIAS_FLAG), shadowP = new Paint(Paint.ANTI_ALIAS_FLAG), edgeP = new Paint(Paint.ANTI_ALIAS_FLAG),
            glowP = new Paint(Paint.ANTI_ALIAS_FLAG), badgeBg = new Paint(Paint.ANTI_ALIAS_FLAG), badgeLine = new Paint(Paint.ANTI_ALIAS_FLAG),
            emoji = new Paint(Paint.ANTI_ALIAS_FLAG), miniBg = new Paint(Paint.ANTI_ALIAS_FLAG), miniTx = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF ringRect = new RectF();
    private final Matrix mtx = new Matrix();
    private final ColorMatrixColorFilter boost, dull;
    private Bitmap bmp;
    private BitmapShader shader;
    private int accent = Theme.ACC[0], tone = Theme.IDLE;
    private String mood = "😊";
    private char mini = 0;
    private float progress, spin, dy, dx, sc = 1f, rot, moodScale = 1f;
    private boolean progOn, scanning;
    private ObjectAnimator floatAnim;
    private ValueAnimator spinAnim, progAnim, fxAnim, accAnim, moodAnim;

    AvatarView(Context c) {
        super(c);
        d = Theme.d(c);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        track.setStyle(Paint.Style.STROKE); track.setStrokeWidth(3.5f * d); track.setColor(0x24FFFFFF);
        prog.setStyle(Paint.Style.STROKE); prog.setStrokeWidth(4f * d); prog.setStrokeCap(Paint.Cap.ROUND);
        spinP.setStyle(Paint.Style.STROKE); spinP.setStrokeWidth(4f * d); spinP.setStrokeCap(Paint.Cap.ROUND);
        glowP.setStyle(Paint.Style.STROKE);
        shadowP.setColor(0x66000000);
        edgeP.setStyle(Paint.Style.STROKE); edgeP.setStrokeWidth(3f * d); edgeP.setColor(0x1AFFFFFF);
        badgeBg.setColor(0xF5121328);
        badgeLine.setStyle(Paint.Style.STROKE); badgeLine.setStrokeWidth(1.5f * d);
        emoji.setTextAlign(Paint.Align.CENTER); emoji.setTextSize(15f * d);
        miniBg.setStyle(Paint.Style.FILL);
        miniTx.setTextAlign(Paint.Align.CENTER); miniTx.setTextSize(13f * d); miniTx.setTypeface(Typeface.DEFAULT_BOLD); miniTx.setColor(0xFFFFFFFF);
        ColorMatrix up = new ColorMatrix(); up.setSaturation(1.3f); boost = new ColorMatrixColorFilter(up);
        ColorMatrix down = new ColorMatrix(); down.setSaturation(0.6f); dull = new ColorMatrixColorFilter(down);
        bmp = BitmapFactory.decodeResource(c.getResources(), R.drawable.avatar);
        if (bmp != null) { shader = new BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP); bodyP.setShader(shader); }
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        float cx = w / 2f, cy = h / 2f, rr = Math.min(w, h) / 2f * 0.92f;
        ringRect.set(cx - rr, cy - rr, cx + rr, cy + rr);
        if (shader != null && bmp != null) {
            float bodyR = Math.min(w, h) / 2f * 0.84f;
            mtx.reset();
            mtx.setScale(2 * bodyR / bmp.getWidth(), 2 * bodyR / bmp.getHeight());
            mtx.postTranslate(cx - bodyR, cy - bodyR);
            shader.setLocalMatrix(mtx);
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        floatAnim = ObjectAnimator.ofFloat(this, View.TRANSLATION_Y, 0f, -5f * d);
        floatAnim.setDuration(2200);
        floatAnim.setRepeatCount(ValueAnimator.INFINITE);
        floatAnim.setRepeatMode(ValueAnimator.REVERSE);
        floatAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        floatAnim.start();
        if (scanning) startSpin();
    }

    @Override protected void onDetachedFromWindow() {
        if (floatAnim != null) floatAnim.cancel();
        stopSpin();
        if (progAnim != null) progAnim.cancel();
        if (fxAnim != null) fxAnim.cancel();
        if (accAnim != null) accAnim.cancel();
        if (moodAnim != null) moodAnim.cancel();
        super.onDetachedFromWindow();
    }

    // ----- trạng thái -----
    void setTone(int t) {
        if (t == tone) return;
        tone = t;
        if (accAnim != null) accAnim.cancel();
        accAnim = ValueAnimator.ofObject(new ArgbEvaluator(), accent, Theme.ACC[t]);
        accAnim.setDuration(250);
        accAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) { accent = (Integer) a.getAnimatedValue(); invalidate(); }
        });
        accAnim.start();
        if (t == Theme.WIN) bodyP.setColorFilter(boost);
        else if (t == Theme.LOSE) bodyP.setColorFilter(dull);
        else bodyP.setColorFilter(null);
        setMood(Theme.MOOD[t]);
    }

    private void setMood(String m) {
        if (m.equals(mood)) return;
        mood = m;
        if (moodAnim != null) moodAnim.cancel();
        moodAnim = ValueAnimator.ofFloat(0.3f, 1f);
        moodAnim.setDuration(450);
        moodAnim.setInterpolator(new OvershootInterpolator(2.2f));
        moodAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) { moodScale = (Float) a.getAnimatedValue(); invalidate(); }
        });
        moodAnim.start();
    }

    /** Chữ cái nhỏ ở góc (khi thu nhỏ trợ lý): 'B', 'P' hoặc 0 để ẩn. */
    void setMini(char c) { if (c != mini) { mini = c; invalidate(); } }

    void setScanning(boolean on) {
        if (on == scanning) return;
        scanning = on;
        if (on) startSpin(); else { stopSpin(); invalidate(); }
    }

    private void startSpin() {
        if (spinAnim != null || !isAttachedToWindow()) return;
        spinAnim = ValueAnimator.ofFloat(0f, 360f);
        spinAnim.setDuration(1100);
        spinAnim.setRepeatCount(ValueAnimator.INFINITE);
        spinAnim.setInterpolator(new LinearInterpolator());
        spinAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) { spin = (Float) a.getAnimatedValue(); invalidate(); }
        });
        spinAnim.start();
    }

    private void stopSpin() { if (spinAnim != null) { spinAnim.cancel(); spinAnim = null; } }

    void startProgress(long ms) {
        stopProgress();
        progOn = true; progress = 0f;
        progAnim = ValueAnimator.ofFloat(0f, 1f);
        progAnim.setDuration(Math.max(200, ms));
        progAnim.setInterpolator(new LinearInterpolator());
        progAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) { progress = (Float) a.getAnimatedValue(); invalidate(); }
        });
        progAnim.start();
    }

    void stopProgress() {
        if (progAnim != null) { progAnim.cancel(); progAnim = null; }
        if (progOn) { progOn = false; invalidate(); }
    }

    /** 0 = nhảy (đoán đúng), 1 = lắc (đoán sai), 2 = chào (sự kiện khác). */
    void play(final int type) {
        if (fxAnim != null) fxAnim.cancel();
        fxAnim = ValueAnimator.ofFloat(0f, 1f);
        fxAnim.setDuration(type == 0 ? 900 : type == 1 ? 600 : 700);
        fxAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                float t = (Float) a.getAnimatedValue(), s = (float) Math.sin(Math.PI * t), k = 1f - t;
                dx = 0; dy = 0; sc = 1f; rot = 0;
                if (type == 0) { dy = -16f * d * s; sc = 1f + 0.1f * s; }
                else if (type == 1) { double w = Math.sin(6 * Math.PI * t); dx = (float) (7f * d * w * k); rot = (float) (4 * w * k); }
                else { sc = 1f + 0.12f * s - 0.2f * k * k; rot = -8f * k * k; }
                invalidate();
            }
        });
        fxAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) { dx = dy = rot = 0; sc = 1f; invalidate(); }
        });
        fxAnim.start();
    }

    /** Tâm avatar trong toạ độ của view cha (để vẽ pháo giấy). */
    float centerX() { return getX() + getWidth() / 2f; }
    float centerY() { return getY() + getHeight() / 2f; }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f, R = Math.min(w, h) / 2f;
        // ánh sáng toả quanh nhân vật
        glowP.setColor(accent);
        for (int i = 0; i < 3; i++) {
            glowP.setAlpha(40 - i * 12);
            glowP.setStrokeWidth(d * (5 + i * 5));
            c.drawCircle(cx, cy, R * 0.84f + d * (2 + i * 3), glowP);
        }
        c.drawArc(ringRect, 0, 360, false, track);
        if (progOn) { prog.setColor(accent); c.drawArc(ringRect, -90, 360f * progress, false, prog); }
        if (scanning) {
            spinP.setColor(accent);
            for (int i = 0; i < 14; i++) { spinP.setAlpha((int) (255f * (i + 1) / 14f)); c.drawArc(ringRect, spin + i * 14f, 15f, false, spinP); }
        }
        float bodyR = R * 0.84f;
        c.save();
        c.translate(dx, dy);
        c.scale(sc, sc, cx, cy);
        c.rotate(rot, cx, cy);
        c.drawCircle(cx, cy + 3f * d, bodyR, shadowP);
        c.drawCircle(cx, cy, bodyR, bodyP);
        c.drawCircle(cx, cy, bodyR + 1.5f * d, edgeP);
        c.restore();
        // huy hiệu cảm xúc
        float bx = w - 15f * d, by = h - 14f * d;
        c.save();
        c.scale(moodScale, moodScale, bx, by);
        c.drawCircle(bx, by, 15f * d, badgeBg);
        badgeLine.setColor(accent);
        c.drawCircle(bx, by, 15f * d, badgeLine);
        c.drawText(mood, bx, by + 5.4f * d, emoji);
        c.restore();
        if (mini != 0) {
            miniBg.setColor(mini == 'B' ? 0xFFE11D48 : 0xFF2563EB);
            c.drawCircle(13f * d, 13f * d, 13f * d, miniBg);
            c.drawText(String.valueOf(mini), 13f * d, 13f * d + 4.6f * d, miniTx);
        }
    }
}
