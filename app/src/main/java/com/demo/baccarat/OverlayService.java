package com.demo.baccarat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Locale;

/** Trợ lý nổi: quay màn hình (MediaProjection), đọc bảng cầu, dự đoán; giao diện có nhân vật hoạt hình. */
public class OverlayService extends Service {
    private static final String CH = "bca_channel";

    private WindowManager wm;
    private FrameLayout root;
    private WindowManager.LayoutParams lp;
    private LinearLayout card, head, predRow, menuBox;
    private GradientDrawable cardBg;
    private AvatarView avatar;
    private BurstView burst;
    private ChipView chip;
    private SegView seg;
    private StripView strip;
    private TextView tTitle, tSub, tWhy, tAcc, tSimRes, tLearn;
    private Button btnGo, btnHist, btnSim, btnMore;
    private HistoryPanel panel;
    private MediaProjection projection;
    private ImageReader reader;
    private VirtualDisplay vd;
    private HandlerThread vt;
    private Handler vh, mh;
    private Tracker tracker;
    private final Vision vision = new Vision();
    private volatile boolean watching = false, panelOpen = false;
    private boolean simming = false, minimized = false;
    private int capW, capH, scrW, scrH;
    private int[] px = new int[0];
    private byte[] rowBuf = new byte[0];
    private double[] hint;
    private volatile int[] ovr = new int[]{0, 0, 0, 0};
    private String lastKey = "", lastTitle = "", lastSub = "";
    private int lastEvt = 0, lastTone = -1;
    private int strokeColor = Theme.ACC[0];

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        mh = new Handler(Looper.getMainLooper());
        vt = new HandlerThread("bca-vision");
        vt.start();
        vh = new Handler(vt.getLooper());
    }

    @SuppressWarnings("deprecation")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || projection != null) return START_NOT_STICKY;
        startAsForeground();
        int code = intent.getIntExtra("code", 0);
        Intent data = intent.getParcelableExtra("data");
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        try {
            projection = mpm.getMediaProjection(code, data);
        } catch (Exception e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (projection == null) { stopSelf(); return START_NOT_STICKY; }
        projection.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() { mh.post(new Runnable() { @Override public void run() { stopSelf(); } }); }
        }, mh);
        setupCapture();
        tracker = new Tracker(new PrefsStore(this));
        buildOverlay();
        render();
        return START_NOT_STICKY;
    }

    private void startAsForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CH, "Trợ lý Baccarat", NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, CH)
                .setContentTitle("Trợ lý đang chạy")
                .setContentText("Kéo avatar để di chuyển trợ lý")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else startForeground(1, n);
    }

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    // ---------- quay màn hình ----------
    private void setupCapture() {
        int dpi = getResources().getConfiguration().densityDpi;
        if (Build.VERSION.SDK_INT >= 30) {
            Rect b = wm.getMaximumWindowMetrics().getBounds();
            scrW = b.width();
            scrH = b.height();
        } else {
            DisplayMetrics dm = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(dm);
            scrW = dm.widthPixels;
            scrH = dm.heightPixels;
        }
        // điện thoại độ phân giải cao: thu nhỏ một nửa cho nhẹ (chấm bảng cầu vẫn đủ lớn để đọc)
        float sc = Math.max(scrW, scrH) > 1700 ? 0.5f : 1f;
        capW = Math.max(2, Math.round(scrW * sc));
        capH = Math.max(2, Math.round(scrH * sc));
        reader = ImageReader.newInstance(capW, capH, PixelFormat.RGBA_8888, 2);
        vd = projection.createVirtualDisplay("bca", capW, capH, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, null, null, null);
    }

    private final Runnable loop = new Runnable() {
        @Override public void run() {
            if (!watching) return;
            long t0 = SystemClock.uptimeMillis();
            try {
                scanOnce();
            } catch (Throwable e) {
                final String m = String.valueOf(e.getMessage());
                mh.post(new Runnable() { @Override public void run() { tSub.setText("Lỗi quét: " + m); } });
            }
            if (watching) vh.postDelayed(this, Math.max(500L, (SystemClock.uptimeMillis() - t0) * 8));
        }
    };

    /** Nhịp 4 lần/giây: chạy các pha theo thời gian (đang phân tích → dự đoán…) và vẽ lại giao diện. */
    private final Runnable clock = new Runnable() {
        @Override public void run() {
            if (!watching) return;
            if (!panelOpen) tracker.tickTime(SystemClock.uptimeMillis());
            mh.post(new Runnable() { @Override public void run() { render(); } });
            vh.postDelayed(this, 250);
        }
    };

    private void scanOnce() {
        Image img = reader.acquireLatestImage();
        if (img == null) return; // màn hình chưa có khung mới
        if (panelOpen) { img.close(); return; } // đang mở bảng lịch sử: tạm dừng để khỏi đọc nhầm các chấm của chính bảng đó
        int W, H;
        try {
            W = img.getWidth();
            H = img.getHeight();
            Image.Plane pl = img.getPlanes()[0];
            ByteBuffer bb = pl.getBuffer();
            int rs = pl.getRowStride(), ps = pl.getPixelStride();
            if (px.length < W * H) px = new int[W * H];
            if (rowBuf.length < rs) rowBuf = new byte[rs];
            for (int y = 0; y < H; y++) {
                int pos = y * rs;
                if (pos >= bb.limit()) break;
                int len = Math.min(rs, bb.limit() - pos);
                bb.position(pos);
                bb.get(rowBuf, 0, len);
                int base = y * W;
                for (int x = 0; x < W; x++) {
                    int o = x * ps;
                    if (o + 2 >= len) break;
                    px[base + x] = ((rowBuf[o] & 255) << 16) | ((rowBuf[o + 1] & 255) << 8) | (rowBuf[o + 2] & 255);
                }
            }
        } finally {
            img.close();
        }
        // bỏ qua vùng của chính trợ lý (nó cũng nằm trong ảnh quay được)
        int[] o = ovr;
        float k = (float) W / Math.max(1, scrW);
        int[] mask = new int[]{(int) (o[0] * k) - 4, (int) (o[1] * k) - 4, (int) (o[2] * k) + 4, (int) (o[3] * k) + 4};
        List<Vision.Blob> bl = vision.findBlobs(px, W, H, mask);
        Vision.Read r = vision.refine(vision.boardSequence(bl, hint), px, W, H, mask);
        if (r != null) hint = new double[]{(r.x0 + r.x1) / 2, (r.y0 + r.y1) / 2, Math.max(r.x1 - r.x0, r.y1 - r.y0) / 2};
        tracker.onRead(r == null ? null : r.seq, bl.size(), vision.lastBeadSize, W, H, SystemClock.uptimeMillis());
        mh.post(new Runnable() { @Override public void run() { render(); } });
    }

    private void startWatching() {
        watching = true;
        hint = null;
        btnGo.setText("■ Dừng");
        btnGo.getBackground().setAlpha(110);
        tracker.start();
        lastKey = "";
        render();
        vd.setSurface(reader.getSurface());
        vh.post(loop);
        vh.postDelayed(clock, 250);
    }

    private void stopWatching() {
        watching = false;
        vh.removeCallbacks(loop);
        vh.removeCallbacks(clock);
        try { vd.setSurface(null); } catch (Exception ignored) { }
        btnGo.setText("▶ Bắt đầu xem màn hình");
        btnGo.getBackground().setAlpha(255);
        vh.post(new Runnable() {
            @Override public void run() {
                tracker.stop();
                mh.post(new Runnable() { @Override public void run() { lastKey = ""; render(); } });
            }
        });
    }

    // ---------- giao diện nổi ----------
    private TextView tv(float sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    /** Button mặc định của Material có minWidth/minHeight và bóng đổ: bỏ đi để kích thước theo ý mình. */
    private void flat(Button b) {
        b.setMinWidth(0);
        b.setMinHeight(0);
        b.setMinimumWidth(0);
        b.setMinimumHeight(0);
        b.setStateListAnimator(null);
        b.setIncludeFontPadding(false);
    }

    private Button iconButton(String text) {
        Button b = new Button(this);
        flat(b);
        b.setText(text);
        b.setTextSize(16);
        b.setAllCaps(false);
        b.setPadding(0, 0, 0, 0);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x14FFFFFF);
        g.setCornerRadius(dp(14));
        b.setBackground(g);
        return b;
    }

    private void pressFeedback(final View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View view, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(90).start();
                else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) view.animate().scaleX(1f).scaleY(1f).setDuration(140).setInterpolator(new OvershootInterpolator(2f)).start();
                return false;
            }
        });
    }

    private TextView menuItem(String text) {
        TextView t = tv(12, false, Color.WHITE);
        t.setText(text);
        t.setPadding(dp(12), dp(9), dp(12), dp(9));
        return t;
    }

    private void buildOverlay() {
        final int cardW = Math.min(dp(320), scrW - dp(30));
        root = new FrameLayout(this);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setPadding(0, dp(28), dp(14), 0);

        card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        cardBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xF51E203E, 0xF70D0E1E});
        cardBg.setCornerRadius(dp(24));
        cardBg.setStroke(dp(1.5f), strokeColor);
        card.setBackground(cardBg);
        card.setPadding(dp(16), dp(16), dp(16), dp(14));
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(cardW, FrameLayout.LayoutParams.WRAP_CONTENT);
        cp.topMargin = dp(36);
        root.addView(card, cp);

        head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(0, 0, dp(92), 0);
        head.setMinimumHeight(dp(58));
        tTitle = tv(18, true, Color.WHITE);
        tTitle.setText("Sẵn sàng");
        tSub = tv(11.5f, false, 0xA8FFFFFF);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(5);
        head.addView(tTitle);
        head.addView(tSub, sp);
        card.addView(head);

        predRow = new LinearLayout(this);
        predRow.setOrientation(LinearLayout.HORIZONTAL);
        predRow.setGravity(Gravity.CENTER_VERTICAL);
        predRow.setVisibility(View.GONE);
        chip = new ChipView(this);
        predRow.addView(chip, new LinearLayout.LayoutParams(dp(116), dp(52)));
        LinearLayout agree = new LinearLayout(this);
        agree.setOrientation(LinearLayout.VERTICAL);
        seg = new SegView(this);
        tWhy = tv(10.5f, false, 0x9EFFFFFF);
        agree.addView(seg, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(6)));
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wp.topMargin = dp(6);
        agree.addView(tWhy, wp);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        ap.leftMargin = dp(12);
        predRow.addView(agree, ap);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pp.topMargin = dp(12);
        card.addView(predRow, pp);

        strip = new StripView(this);
        strip.setVisibility(View.GONE);
        LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(14));
        stp.topMargin = dp(12);
        card.addView(strip, stp);
        tAcc = tv(10.5f, false, 0x8CFFFFFF);
        LinearLayout.LayoutParams acp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        acp.topMargin = dp(6);
        card.addView(tAcc, acp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        btnGo = new Button(this);
        flat(btnGo);
        btnGo.setText("▶ Bắt đầu xem màn hình");
        btnGo.setTextColor(Color.WHITE);
        btnGo.setTypeface(null, Typeface.BOLD);
        btnGo.setAllCaps(false);
        btnGo.setMaxLines(1);
        btnGo.setPadding(dp(8), 0, dp(8), 0);
        btnGo.setAutoSizeTextTypeUniformWithConfiguration(9, 13, 1, TypedValue.COMPLEX_UNIT_SP);
        GradientDrawable gb = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFFFF6B9D, 0xFF7B61FF});
        gb.setCornerRadius(dp(20));
        btnGo.setBackground(gb);
        actions.addView(btnGo, new LinearLayout.LayoutParams(0, dp(40), 1f));
        btnHist = iconButton("📚");
        btnSim = iconButton("🧪");
        btnMore = iconButton("⋯");
        for (Button b : new Button[]{btnHist, btnSim, btnMore}) {
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(37), dp(40));
            ip.leftMargin = dp(7);
            actions.addView(b, ip);
        }
        LinearLayout.LayoutParams acl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        acl.topMargin = dp(12);
        card.addView(actions, acl);

        menuBox = new LinearLayout(this);
        menuBox.setOrientation(LinearLayout.VERTICAL);
        menuBox.setVisibility(View.GONE);
        GradientDrawable mg = new GradientDrawable();
        mg.setColor(0xF7121328);
        mg.setCornerRadius(dp(14));
        menuBox.setBackground(mg);
        TextView mMin = menuItem("Thu nhỏ về avatar"), mClr = menuItem("Xóa dữ liệu đã học"), mExit = menuItem("Thoát trợ lý");
        menuBox.addView(mMin);
        menuBox.addView(mClr);
        menuBox.addView(mExit);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mp.topMargin = dp(8);
        card.addView(menuBox, mp);

        tSimRes = tv(11, false, 0xFFFFD34D);
        tSimRes.setVisibility(View.GONE);
        GradientDrawable sg = new GradientDrawable();
        sg.setColor(0x1AFFD34D);
        sg.setCornerRadius(dp(12));
        tSimRes.setBackground(sg);
        tSimRes.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout.LayoutParams srp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        srp.topMargin = dp(10);
        card.addView(tSimRes, srp);

        tLearn = tv(10.5f, false, 0x80FFFFFF);
        LinearLayout.LayoutParams lnp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lnp.topMargin = dp(10);
        card.addView(tLearn, lnp);

        avatar = new AvatarView(this);
        FrameLayout.LayoutParams avp = new FrameLayout.LayoutParams(dp(88), dp(88), Gravity.TOP | Gravity.END);
        avp.rightMargin = dp(16);
        root.addView(avatar, avp);
        burst = new BurstView(this);
        root.addView(burst, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        lp = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = dp(4);
        lp.y = dp(110);
        wm.addView(root, lp);
        root.post(new Runnable() { @Override public void run() { updateRect(); } });

        attachDrag(avatar, true);
        attachDrag(head, false);
        pressFeedback(btnGo);
        pressFeedback(btnHist);
        pressFeedback(btnSim);
        pressFeedback(btnMore);
        btnGo.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (watching) stopWatching(); else startWatching(); }
        });
        btnHist.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { menuBox.setVisibility(View.GONE); if (panel == null) openHistory(); else closeHistory(); }
        });
        btnSim.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { menuBox.setVisibility(View.GONE); runSimulation(); }
        });
        btnMore.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { menuBox.setVisibility(menuBox.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE); root.post(new Runnable() { @Override public void run() { updateRect(); } }); }
        });
        mMin.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setMinimized(true); } });
        mExit.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { stopSelf(); } });
        mClr.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                menuBox.setVisibility(View.GONE);
                vh.post(new Runnable() {
                    @Override public void run() {
                        tracker.clearLearned();
                        mh.post(new Runnable() { @Override public void run() { lastKey = ""; render(); } });
                    }
                });
            }
        });
    }

    /** Kéo để di chuyển trợ lý; chạm nhẹ vào avatar để thu nhỏ/mở rộng. */
    private void attachDrag(View v, final boolean toggles) {
        final float[] s = new float[5];
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View view, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        s[0] = e.getRawX(); s[1] = e.getRawY(); s[2] = lp.x; s[3] = lp.y; s[4] = 0;
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float dx = e.getRawX() - s[0], dy = e.getRawY() - s[1];
                        if (s[4] == 0 && Math.hypot(dx, dy) < dp(6)) return true;
                        s[4] = 1;
                        lp.x = (int) Math.max(0, Math.min(scrW - dp(60), s[2] - dx));
                        lp.y = (int) Math.max(0, Math.min(scrH - dp(100), s[3] + dy));
                        wm.updateViewLayout(root, lp);
                        updateRect();
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                        if (s[4] == 0 && toggles) setMinimized(!minimized);
                        return true;
                    default:
                        return true;
                }
            }
        });
    }

    private void setMinimized(boolean m) {
        minimized = m;
        menuBox.setVisibility(View.GONE);
        card.setVisibility(m ? View.GONE : View.VISIBLE);
        if (m) closeHistory();
        avatar.play(2);
        lastKey = "";
        render();
        root.post(new Runnable() { @Override public void run() { updateRect(); } });
    }

    private void swap(TextView t, String s) {
        if (s.contentEquals(t.getText())) return;
        t.setText(s);
        t.setAlpha(0f);
        t.setTranslationY(dp(7));
        t.animate().alpha(1f).translationY(0f).setDuration(380).start();
    }

    private void setStroke(int color) {
        if (color == strokeColor) return;
        final int from = strokeColor;
        strokeColor = color;
        final android.animation.ValueAnimator a = android.animation.ValueAnimator.ofObject(new android.animation.ArgbEvaluator(), from, color);
        a.setDuration(250);
        a.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(android.animation.ValueAnimator va) { cardBg.setStroke(dp(1.5f), (Integer) va.getAnimatedValue()); }
        });
        a.start();
    }

    private void render() {
        if (tracker == null || root == null) return;
        Tracker.Ui u = tracker.ui();
        String key = u.ver + "|" + u.evtId + "|" + watching + "|" + u.hist + "|" + u.learn + "|" + u.streak + "|" + minimized;
        if (key.equals(lastKey)) return;
        lastKey = key;
        int tone = Theme.toneOf(u);
        avatar.setTone(tone);
        avatar.setScanning(tone == Theme.SCAN);
        setStroke(Theme.ACC[tone]);
        swap(tTitle, u.title);
        swap(tSub, u.sub);
        boolean showPred = "pred".equals(u.phase) && u.pred != null;
        if (showPred) {
            boolean fresh = predRow.getVisibility() != View.VISIBLE || lastTone != tone || !String.valueOf(u.predN).equals(predRow.getTag());
            predRow.setTag(String.valueOf(u.predN));
            predRow.setVisibility(View.VISIBLE);
            if (fresh) {
                chip.set(u.pred.v, u.pred.weak);
                seg.set(u.pred.same, u.pred.total, Theme.ACC[tone]);
            }
            tWhy.setText((u.pred.weak ? "Chưa có tín hiệu rõ · theo xu hướng" : u.pred.same + "/" + u.pred.total + " phương án đồng thuận\n" + u.pred.name)
                        + "\nXác suất thật mỗi ván: Cái " + Deep.pct(Deep.TB, 1) + " · Con " + Deep.pct(Deep.TP, 1));
        } else {
            predRow.setVisibility(View.GONE);
        }
        lastTone = tone;
        avatar.setMini(minimized && showPred ? u.pred.v : (char) 0);
        strip.setData(u.hist, u.streak);
        tAcc.setText(u.accN > 0 ? "Đúng " + u.accOk + "/" + u.accN + " (" + Math.round(u.accOk * 100f / u.accN) + "%) · bàn hiện tại" : "");
        tAcc.setVisibility(u.accN > 0 ? View.VISIBLE : View.GONE);
        tLearn.setText(u.learn);
        btnGo.setText(watching ? "■ Dừng" : "▶ Bắt đầu xem màn hình");
        btnGo.getBackground().setAlpha(watching ? 110 : 255);
        if ("analyze".equals(u.phase) && u.analyzeMs > 0) avatar.startProgress(u.analyzeMs); else avatar.stopProgress();
        if (u.evtId != lastEvt) {
            lastEvt = u.evtId;
            if ("win".equals(u.evtType)) {
                avatar.play(0);
                burst.fire(avatar.centerX(), avatar.centerY(), new int[]{0xFF3ECF72, 0xFFB4FF8A, 0xFFFFD34D, 0xFF7BDCFF, 0xFFFF9AD5});
            } else if ("lose".equals(u.evtType)) avatar.play(1);
            else avatar.play(2);
        }
        root.post(new Runnable() { @Override public void run() { updateRect(); } });
    }

    // ---------- xem lại các bàn đã học ----------
    private void openHistory() {
        if (panel != null || tracker == null) return;
        panel = new HistoryPanel(this, new HistoryPanel.Actions() {
            @Override public void close() { closeHistory(); }
            @Override public void list() { loadHistory(); }
            @Override public void deep() { loadDeep(); }
            @Override public void delete(final int idx) {
                vh.post(new Runnable() {
                    @Override public void run() {
                        tracker.deleteTable(idx);
                        mh.post(new Runnable() { @Override public void run() { lastKey = ""; render(); loadHistory(); } });
                    }
                });
            }
        });
        WindowManager.LayoutParams plp = new WindowManager.LayoutParams((int) (scrW * 0.94f), (int) (scrH * 0.72f),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        plp.gravity = Gravity.CENTER;
        panelOpen = true;
        View pv = panel.view();
        pv.setAlpha(0f);
        pv.setTranslationY(dp(16));
        wm.addView(pv, plp);
        pv.animate().alpha(1f).translationY(0f).setDuration(260).start();
        loadHistory();
    }

    private void loadHistory() {
        if (panel == null) return;
        panel.showLoading();
        final List<String> tb = tracker.tablesSnapshot();
        final int cur = tracker.currentIndex();
        new Thread(new Runnable() {
            @Override public void run() {
                final Analysis.Report r = Analysis.run(tb, cur);
                mh.post(new Runnable() { @Override public void run() { if (panel != null) panel.showReport(r); } });
            }
        }).start();
    }

    /** Phân tích sâu: chạy ở luồng nền (mất vài giây), pause đọc màn hình trong lúc bảng đang mở. */
    private void loadDeep() {
        if (panel == null) return;
        panel.showLoading("Đang phân tích sâu… (mất vài giây)");
        final List<String> tb = tracker.tablesSnapshot();
        final int cur = tracker.currentIndex();
        new Thread(new Runnable() {
            @Override public void run() {
                final Deep.Result r = Deep.run(tb, cur);
                mh.post(new Runnable() { @Override public void run() { if (panel != null) panel.showDeep(r); } });
            }
        }).start();
    }

    private void closeHistory() {
        panelOpen = false;
        if (panel != null) {
            try { wm.removeView(panel.view()); } catch (Exception ignored) { }
            panel = null;
        }
    }

    private void updateRect() {
        if (root == null) return;
        int[] loc = new int[2];
        root.getLocationOnScreen(loc);
        ovr = new int[]{loc[0], loc[1], loc[0] + root.getWidth(), loc[1] + root.getHeight()};
    }

    private void runSimulation() {
        if (!watching || !tracker.hasTable()) {
            showSim("Cần chia sẻ màn hình và thấy bảng cầu trước rồi mới mô phỏng được.");
            return;
        }
        if (simming) return;
        simming = true;
        new Thread(new Runnable() {
            @Override public void run() {
                final Sim.Result r = Sim.run(40, 40, new Sim.Progress() {
                    @Override public void on(final String phase, final int k, final int n) {
                        if (k % 5 == 0 || k == n) {
                            mh.post(new Runnable() { @Override public void run() { showSim("🧪 Đang " + phase + " " + k + "/" + n + "…"); } });
                        }
                    }
                });
                mh.post(new Runnable() {
                    @Override public void run() {
                        simming = false;
                        boolean sig = Math.max(Math.abs(r.accA - 0.5), Math.abs(r.accB - 0.5)) > r.ci;
                        showSim(String.format(Locale.US,
                                "Mô phỏng %d lần: có học %.1f%% · không học %.1f%% · luôn đoán Cái %.1f%% (±%.1f%%) · %s",
                                r.n, r.accA * 100, r.accB * 100, r.base * 100, r.ci * 100,
                                sig ? "lệch khỏi 50%" : "không khác ngẫu nhiên"));
                    }
                });
            }
        }).start();
    }

    private void showSim(String text) {
        tSimRes.setText(text);
        tSimRes.setVisibility(View.VISIBLE);
        root.post(new Runnable() { @Override public void run() { updateRect(); } });
    }

    @Override
    public void onDestroy() {
        watching = false;
        if (vh != null) vh.removeCallbacksAndMessages(null);
        closeHistory();
        try { if (root != null) wm.removeView(root); } catch (Exception ignored) { }
        try { if (vd != null) vd.release(); } catch (Exception ignored) { }
        try { if (reader != null) reader.close(); } catch (Exception ignored) { }
        try { if (projection != null) projection.stop(); } catch (Exception ignored) { }
        if (vt != null) vt.quitSafely();
        super.onDestroy();
    }
}
