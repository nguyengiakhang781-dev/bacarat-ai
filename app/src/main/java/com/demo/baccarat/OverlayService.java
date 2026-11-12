package com.demo.baccarat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.Outline;
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
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Locale;

/** Trợ lý nổi ở góc phải màn hình: quay màn hình (MediaProjection), đọc bảng cầu, dự đoán. */
public class OverlayService extends Service {
    private static final String CH = "bca_channel";
    private static final String M_TXT = "🧪 Mô phỏng kiểm chứng (học 40 + thử 40 bàn)";

    private WindowManager wm;
    private LinearLayout root;
    private WindowManager.LayoutParams lp;
    private TextView tTitle, tSub, tHist, tLearn, tSim, tRes, tClear, tExit, tHistory;
    private HistoryPanel panel;
    private volatile boolean panelOpen = false;
    private Button btn;
    private MediaProjection projection;
    private ImageReader reader;
    private VirtualDisplay vd;
    private HandlerThread vt;
    private Handler vh, mh;
    private Tracker tracker;
    private final Vision vision = new Vision();
    private volatile boolean watching = false;
    private boolean simming = false;
    private int capW, capH, scrW, scrH;
    private int[] px = new int[0];
    private byte[] rowBuf = new byte[0];
    private double[] hint;
    private volatile int[] ovr = new int[]{0, 0, 0, 0};

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

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

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

    private void scanOnce() {
        Image img = reader.acquireLatestImage();
        if (img == null) return; // màn hình chưa có khung mới
        if (panelOpen) { img.close(); return; } // đang mở bảng lịch sử: tạm dừng đọc để khỏi đọc nhầm các chấm của chính bảng đó
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
        Vision.Read r = vision.boardSequence(bl, hint);
        if (r != null) hint = new double[]{(r.x0 + r.x1) / 2, (r.y0 + r.y1) / 2, Math.max(r.x1 - r.x0, r.y1 - r.y0) / 2};
        tracker.onRead(r == null ? null : r.seq, bl.size(), vision.lastBeadSize, W, H, SystemClock.uptimeMillis());
        mh.post(new Runnable() { @Override public void run() { render(); } });
    }

    private void startWatching() {
        watching = true;
        hint = null;
        btn.setText("Dừng");
        btn.getBackground().setAlpha(150);
        tracker.start();
        render();
        vd.setSurface(reader.getSurface());
        vh.post(loop);
    }

    private void stopWatching() {
        watching = false;
        vh.removeCallbacks(loop);
        try { vd.setSurface(null); } catch (Exception ignored) { }
        btn.setText("Bắt đầu xem màn hình");
        btn.getBackground().setAlpha(255);
        vh.post(new Runnable() {
            @Override public void run() {
                tracker.stop();
                mh.post(new Runnable() { @Override public void run() { render(); } });
            }
        });
    }

    // ---------- giao diện nổi ----------
    private TextView text(int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private void buildOverlay() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xEB141628);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), 0x26FFFFFF);
        bubble.setBackground(bg);
        bubble.setPadding(dp(10), dp(8), dp(10), dp(8));

        tTitle = text(18, true, Color.WHITE);
        tSub = text(11, false, 0xB3FFFFFF);
        tHist = text(13, false, Color.WHITE);
        tLearn = text(10, false, 0xB3FFFFFF);
        tSim = text(11, false, 0xB3FFFFFF);
        tSim.setPaintFlags(tSim.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        tSim.setText(M_TXT);
        tSim.setVisibility(View.GONE);
        tRes = text(11, false, 0xFFFFD34D);
        tRes.setVisibility(View.GONE);
        tHistory = text(11, false, 0xFFFFD34D);
        tHistory.setPaintFlags(tHistory.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        tHistory.setText("📚 Xem các bàn đã học");
        tClear = text(10, false, 0x80FFFFFF);
        tClear.setPaintFlags(tClear.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        tClear.setText("Xóa dữ liệu đã học");
        tExit = text(10, false, 0x80FFFFFF);
        tExit.setPaintFlags(tExit.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        tExit.setText("Thoát trợ lý");
        for (TextView t : new TextView[]{tTitle, tSub, tHist, tLearn, tRes, tSim, tHistory, tClear, tExit}) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            p.topMargin = dp(3);
            bubble.addView(t, p);
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        btn = new Button(this);
        btn.setText("Bắt đầu xem màn hình");
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(12);
        btn.setAllCaps(false);
        GradientDrawable bb = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFFFF6B9D, 0xFF7B61FF});
        bb.setCornerRadius(dp(22));
        btn.setBackground(bb);
        row.addView(btn, new LinearLayout.LayoutParams(0, dp(40), 1f));

        ImageView av = new ImageView(this);
        av.setImageResource(R.drawable.avatar);
        av.setScaleType(ImageView.ScaleType.CENTER_CROP);
        av.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) { outline.setOval(0, 0, view.getWidth(), view.getHeight()); }
        });
        av.setClipToOutline(true);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(dp(56), dp(56));
        ap.leftMargin = dp(8);
        row.addView(av, ap);

        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.bottomMargin = dp(8);
        root.addView(bubble, bp);
        root.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        lp = new WindowManager.LayoutParams(dp(230), WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = dp(8);
        lp.y = dp(140);
        wm.addView(root, lp);
        root.post(new Runnable() { @Override public void run() { updateRect(); } });

        // kéo avatar để di chuyển trợ lý
        final float[] d = new float[4];
        av.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        d[0] = e.getRawX(); d[1] = e.getRawY(); d[2] = lp.x; d[3] = lp.y;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        lp.x = (int) (d[2] - (e.getRawX() - d[0]));
                        lp.y = (int) (d[3] + (e.getRawY() - d[1]));
                        wm.updateViewLayout(root, lp);
                        updateRect();
                        return true;
                    default:
                        return true;
                }
            }
        });

        btn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (watching) stopWatching(); else startWatching(); }
        });
        tExit.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { stopSelf(); }
        });
        tClear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                vh.post(new Runnable() {
                    @Override public void run() {
                        tracker.clearLearned();
                        mh.post(new Runnable() { @Override public void run() { render(); } });
                    }
                });
            }
        });
        tHistory.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openHistory(); }
        });
        tSim.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runSimulation(); }
        });
    }

    // ---------- xem lại các bàn đã học ----------
    private void openHistory() {
        if (panel != null || tracker == null) return;
        panel = new HistoryPanel(this, new HistoryPanel.Actions() {
            @Override public void close() { closeHistory(); }
            @Override public void delete(final int idx) {
                vh.post(new Runnable() {
                    @Override public void run() {
                        tracker.deleteTable(idx);
                        mh.post(new Runnable() { @Override public void run() { render(); loadHistory(); } });
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
        wm.addView(panel.view(), plp);
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

    private void closeHistory() {
        panelOpen = false;
        if (panel != null) {
            try { wm.removeView(panel.view()); } catch (Exception ignored) { }
            panel = null;
        }
    }

    private void updateRect() {
        int[] loc = new int[2];
        root.getLocationOnScreen(loc);
        ovr = new int[]{loc[0], loc[1], loc[0] + root.getWidth(), loc[1] + root.getHeight()};
    }

    private static int col(int c) {
        switch (c) {
            case 1: return 0xFFFF4D5E;
            case 2: return 0xFF4D8DFF;
            case 3: return 0xFF3ECF72;
            case 4: return 0xFFFFD34D;
            default: return Color.WHITE;
        }
    }

    private void render() {
        if (tracker == null || root == null) return;
        Tracker.Ui u = tracker.ui();
        tTitle.setText(u.title);
        tTitle.setTextColor(col(u.color));
        tSub.setText(u.sub);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (Boolean ok : u.hist) {
            int s = sb.length();
            sb.append("● ");
            sb.setSpan(new ForegroundColorSpan(ok ? 0xFF3ECF72 : 0xFFFF4D5E), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        tHist.setText(sb);
        tHist.setVisibility(u.hist.isEmpty() ? View.GONE : View.VISIBLE);
        tLearn.setText(u.learn);
        tSim.setVisibility(u.simVisible && watching ? View.VISIBLE : View.GONE);
        root.post(new Runnable() { @Override public void run() { updateRect(); } });
    }

    private void runSimulation() {
        if (simming) return;
        simming = true;
        tRes.setVisibility(View.GONE);
        new Thread(new Runnable() {
            @Override public void run() {
                final Sim.Result r = Sim.run(40, 40, new Sim.Progress() {
                    @Override public void on(final String phase, final int k, final int n) {
                        if (k % 5 == 0 || k == n) {
                            mh.post(new Runnable() { @Override public void run() { tSim.setText("🧪 Đang " + phase + " " + k + "/" + n + "…"); } });
                        }
                    }
                });
                mh.post(new Runnable() {
                    @Override public void run() {
                        simming = false;
                        tSim.setText(M_TXT);
                        boolean sig = Math.max(Math.abs(r.accA - 0.5), Math.abs(r.accB - 0.5)) > r.ci;
                        tRes.setText(String.format(Locale.US,
                                "Mô phỏng %d lần: có học %.1f%% · không học %.1f%% · luôn đoán Cái %.1f%% (±%.1f%%) · %s",
                                r.n, r.accA * 100, r.accB * 100, r.base * 100, r.ci * 100,
                                sig ? "lệch khỏi 50%" : "không khác ngẫu nhiên"));
                        tRes.setVisibility(View.VISIBLE);
                    }
                });
            }
        }).start();
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
