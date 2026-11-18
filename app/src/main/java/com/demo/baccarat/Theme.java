package com.demo.baccarat;

/** Màu sắc/tông theo trạng thái của trợ lý. */
final class Theme {
    private Theme() {}

    /** Hệ số thu nhỏ kích thước giao diện (điện thoại màn hình nhỏ) và cỡ chữ. */
    static float scale = 1f, fscale = 1f;

    static float d(android.content.Context c) { return c.getResources().getDisplayMetrics().density * scale; }

    static final int IDLE = 0, SCAN = 1, ANALYZE = 2, BANKER = 3, PLAYER = 4, WIN = 5, LOSE = 6, NOTICE = 7;

    static final int[] ACC = {0xFF8B7BFF, 0xFF34D1FF, 0xFFFFD34D, 0xFFFF4D6D, 0xFF4D8DFF, 0xFF3ECF72, 0xFFFF4D5E, 0xFFFFD34D};
    static final int[] ACC2 = {0xFFFF6B9D, 0xFF7B61FF, 0xFFFF9F43, 0xFFFF9A8B, 0xFF7BDCFF, 0xFFB4FF8A, 0xFFFF9A8B, 0xFFFFB36B};
    static final String[] MOOD = {"😊", "🔍", "🤔", "🎯", "🎯", "🎉", "😵", "🔔"};

    static int toneOf(Tracker.Ui u) {
        switch (u.phase) {
            case "scan": return SCAN;
            case "analyze": return ANALYZE;
            case "pred": return u.pred != null && u.pred.v == 'B' ? BANKER : PLAYER;
            case "result": return "win".equals(u.res) ? WIN : "lose".equals(u.res) ? LOSE : NOTICE;
            case "notice": return NOTICE;
            default: return IDLE;
        }
    }

    static int alpha(int color, int a) { return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, a)) << 24); }
}
